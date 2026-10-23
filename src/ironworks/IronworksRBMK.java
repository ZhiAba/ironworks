package ironworks;

import ironworks.core.CoreBars;
import ironworks.core.LiquidKit;
import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.entities.Damage;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.Liquid;
import mindustry.ui.Bar;
import arc.scene.ui.TextButton;
import mindustry.ui.Styles;
import mindustry.world.blocks.power.PowerGenerator;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.StatValue;
import arc.util.Strings;
import mindustry.gen.Icon;

import static mindustry.Vars.*;
import static mindustry.type.ItemStack.with;

/**
 * RBMK 核电站：石墨慢化、水冷、控制棒调功率。
 *
 * <p>复用清单（这是这个机器最省钱的地方）：
 * <ul>
 *   <li>冷却水闭环复用老的：反应堆把水烧成 {@link IronworksLiquids#steam}，
 *       汽轮机吃蒸汽发电 + 吐温水，冷却塔再把温水冷凝回水；</li>
 *   <li>燃料直接用原版 {@link Items#thorium}，不新增物品；</li>
 *   <li>造价复用现有材料（碳化硅 / 钢板 / 量子核心 / 航空铝）；</li>
 *   <li>熔毁用引擎现成的 {@link Fx#reactorExplosion} + {@link Damage#damage}，
 *       辐射用我们自己的状态框架 {@link IronworksStatus#radiation}；</li>
 *   <li>UI 沿用 MCT 节点的做法：{@code config(Class, Cons2)} + {@code buildConfiguration(Table)}。</li>
 * </ul>
 *
 * <p>核心机制（全部按 60 tick = 1 秒的真实量纲算）：
 * <ul>
 *   <li>控制棒 0=全提 / 100=全插；棒位决定**通量上限**，通量一阶滞后追随；</li>
 *   <li>水→汽 **1:1**：100% 通量 = 980 L/s 给水、980 L/s 蒸汽；水罐 10000 L、汽包 2000 L；</li>
 *   <li>堆芯温度由「发热（通量）− 冷却（给水流量 × 堆温）」实时积分，不是写死的曲线；</li>
 *   <li>**空泡正反应性**：给水/排汽不足时通量上限反而上涨（RBMK 的经典缺陷）；</li>
 *   <li>**AZ-5 憋压引爆**：汽路堵死 + 罐满 + 控制棒插到底 → 正反应性尖峰，几秒内炸堆；</li>
 *   <li>三种模式：半自动（棒有行程 + 自动保护 + 自动水位）、全自动（滑条设目标功率，
 *       后台自动算棒位与水位）、强制手动（棒瞬动、水位手动、无自动保护）；</li>
 *   <li>氙毒：降功率后碘衰变成氙，短时间内压着反应性（碘坑）；</li>
 *   <li>堆芯温度 &gt; 125% 持续 1 秒 → 熔毁：炸场 + 大范围辐射。</li>
 * </ul>
 */
public class IronworksRBMK{

    public static RBMKReactor reactor;
    public static SteamTurbine turbine;

    // ---- 反应堆参数 ----
    /** 额定给水/蒸汽流量（/秒，100% 通量）；水→汽 1:1，不多不少。 */
    public static final float waterPerSecond = 980f;
    /** 水罐容量（L）：10 秒额定给水。 */
    public static final float waterCapacity = 10000f;
    /** 蒸汽缓冲容量（L）：约 2 秒额定产汽，排不出去就先憋在这里。 */
    public static final float steamCapacity = 2000f;
    /** 给水泵最大抽水速率（/秒）：反应堆自己从相邻管道/水罐抽水。 */
    public static final float feedwaterRate = 1400f;
    /** 满通量烧钍（/秒）：1 根 = 200 秒。 */
    public static final float thoriumPerSecond = 0.05f;
    /** 满通量烧燃料棒（/秒）：1 根 = 900 秒（加工过的燃料耐烧得多）。 */
    public static final float rodPerSecond = 1f / 900f;
    /** 硼酸注入的负反应性权重与消散时间（秒）。 */
    public static final float boronReactivity = 0.05f, boronDecaySeconds = 45f;
    /** 控制棒行程速度（每 tick 的插入比例）：正常 ~2000 tick 走完全程，急停 ~180 tick。 */
    public static final float rodSpeedNormal = 1f / 2000f, rodSpeedScram = 1f / 180f;
    /** 控制模式：0 半自动（棒有行程 + 自动保护）、1 全自动（自动跟踪目标功率）、2 强制手动（无缓冲无保护）。 */
    public static final int MODE_SEMI = 0, MODE_AUTO = 1, MODE_MANUAL = 2;
    /** 通量上限。 */
    public static final float fluxMax = 1.25f;
    /** 熔毁温度阈值（归一化）。 */
    public static final float meltTemp = 1.25f;
    /** 汽路憋压 + 插棒到底后的引爆倒计时（秒）。 */
    public static final float pressureFuse = 2f;
    /** 熔毁后爆炸半径（tile）与伤害。 */
    public static final int meltRadius = 26;
    public static final float meltDamage = 12000f;
    /** 辐射持续时间（tick）。 */
    public static final float radiationTime = 900f;
    /** 辐射沉降半径（比爆炸大：落尘范围更广）。 */
    public static final float falloutRadius = meltRadius * 8f * 1.6f;

    // ---- 汽轮机参数 ----
    /** 满流发电（/秒）。 */
    public static final float turbinePower = 45000f;
    /** 满流吃蒸汽（/秒）—— 一台汽轮机正好配一台反应堆。 */
    public static final float turbineSteamPerSecond = 980f;
    /** 冷凝水回收比例（1 = 质量守恒，蒸汽 980 → 温水 980）。 */
    public static final float turbineWaterReturn = 1f;

    public static void load(){
        reactor = new RBMKReactor("rbmk-reactor");
        turbine = new SteamTurbine("steam-turbine");

        reactor.requirements(Category.power,
            with(IronworksMaterials.siliconCarbide, 120, IronworksItems.steelPlate, 400,
                 IronworksSilicon.quantumCore, 4, IronworksAluminum.aviationAluminum, 80,
                 Items.silicon, 500, Items.metaglass, 200));
        reactor.health = 9000;

        turbine.requirements(Category.power,
            with(IronworksItems.steelPlate, 150, IronworksMaterials.siliconCarbide, 40,
                 IronworksAluminum.aviationAluminum, 30, Items.silicon, 200, Items.metaglass, 120));
        turbine.health = 2600;
    }

    /**
     * 引爆当前地图所有正在工作的反应堆，返回个数（彩蛋用）。
     *
     * <p>必须先快照再引爆：detonate() 会 kill() 把建筑从 Groups.build 里移除，
     * 边遍历边删除会跳过后面的元素（全量回归里就因此漏掉了目标堆）。
     */
    public static int detonateAllWorking(){
        arc.struct.Seq<RBMKReactor.RBMKReactorBuild> targets = new arc.struct.Seq<>();
        for(mindustry.gen.Building b : mindustry.gen.Groups.build){
            if(b instanceof RBMKReactor.RBMKReactorBuild r && !r.melted && !b.dead && (r.flux > 0.01f || r.heat > 0.05f || (r.items != null && (r.items.get(IronworksNuclear.fuelRod) > 0 || r.items.get(mindustry.content.Items.thorium) > 0)))){
                targets.add(r);
            }
        }
        for(RBMKReactor.RBMKReactorBuild r : targets){
            r.detonate();
        }
        return targets.size;
    }

    // ------------------------------------------------------------------
    // 反应堆
    // ------------------------------------------------------------------

    public static class RBMKReactor extends PowerGenerator{
        public RBMKReactor(String name){
            super(name);
            size = 5;
            rotate = false;
            solid = true;
            update = true;
            hasItems = true;
            itemCapacity = 40;
            hasLiquids = true;
            // 水罐 10000 + 汽包 2000：两者共用这一个总容量，罐满 = 水和汽都无处可去
            liquidCapacity = waterCapacity + steamCapacity;
            // 熔毁自己算（带辐射），别让引擎再炸一次
            explosionDamage = 0;
            explosionRadius = 0;
            baseExplosiveness = 0f;
            // 反应堆**本身不发电**：它只把水烧成蒸汽，电由汽轮机发（符合真实 RBMK 原理）
            outputsPower = false;
            powerProduction = 0f;
            buildType = RBMKReactorBuild::new;

            //Block.config() 只注册处理器，不会打开 configurable——漏了这句，点击方块就没有面板
            configurable = true;
            config(Integer.class, (RBMKReactorBuild b, Integer v) -> b.applyConfig(v));
        }

        @Override
        public void load(){
            super.load();
            uiIcon = fullIcon = Core.atlas.find(name + "-ui", region);
        }

        @Override
        public void setStats(){
            super.setStats();
            stats.remove(Stat.basePowerGeneration);

            // 堆芯参数：一块灰底规格表（图标 + 名称 + 带色数值）
            stats.add(new Stat("ironworks-rbmk-specs"), (StatValue)table -> {
                IronworksStats.specRow(table, IronworksLiquids.steam.uiIcon,
                    Core.bundle.get("ironworks.rbmk.spec.steam"),
                    (int)waterPerSecond + "/" + StatUnit.perSecond.localized(),
                    IronworksLiquids.steam.color);
                IronworksStats.specRow(table, Liquids.water.uiIcon,
                    Core.bundle.get("ironworks.rbmk.spec.water"),
                    (int)waterPerSecond + "/" + StatUnit.perSecond.localized(), Liquids.water.color);
                IronworksStats.specRow(table, Liquids.water.uiIcon,
                    Core.bundle.get("ironworks.rbmk.spec.tank"),
                    (int)waterCapacity + " / " + (int)steamCapacity + " L", Color.valueOf("8fd4ff"));
                IronworksStats.specRow(table, IronworksNuclear.fuelRod.uiIcon,
                    Core.bundle.get("ironworks.rbmk.spec.fuel"),
                    Core.bundle.get("ironworks.rbmk.spec.fuel.value"),
                    IronworksNuclear.fuelRod.color);
                IronworksStats.specRow(table, Icon.paste,
                    Core.bundle.get("ironworks.rbmk.spec.rods"),
                    "0–100%", Pal.accent);
            });

            // 运行风险：橙色提示，跟参数分开
            stats.add(new Stat("ironworks-rbmk-risk"), (StatValue)table ->
                IronworksStats.notes(table, Color.valueOf("ffb066"),
                    Core.bundle.get("ironworks.rbmk.risk.1"),
                    Core.bundle.get("ironworks.rbmk.risk.2"),
                    Core.bundle.get("ironworks.rbmk.risk.3")));
        }

        @Override
        public void setBars(){
            super.setBars();

            // 引擎自动加的 "liquid" 条显示 liquids.current()（水/汽来回跳）→ 换成每罐一条，按各自容量算
            CoreBars.removeGenericLiquidBar(this);

            addBar("rbmk-water", (RBMKReactorBuild b) -> new Bar(
                () -> Core.bundle.format("bar.ironworks-rbmk-water",
                    Mathf.round(b.liquids.get(Liquids.water)), (int)waterCapacity),
                () -> Liquids.water.color,
                () -> Mathf.clamp(b.liquids.get(Liquids.water) / waterCapacity)));

            addBar("rbmk-steam", (RBMKReactorBuild b) -> new Bar(
                () -> Core.bundle.format("bar.ironworks-rbmk-steam",
                    Mathf.round(b.liquids.get(IronworksLiquids.steam)), (int)steamCapacity),
                () -> IronworksLiquids.steam.color,
                () -> Mathf.clamp(b.liquids.get(IronworksLiquids.steam) / steamCapacity)));

            addBar("rbmk-heat", (RBMKReactorBuild b) -> new Bar(
                () -> Core.bundle.format("bar.ironworks-rbmk-heat", Mathf.round(b.heat * 100f)),
                () -> heatColor(b.heat),
                () -> Mathf.clamp(b.heat / meltTemp)));

            addBar("rbmk-xenon", (RBMKReactorBuild b) -> b.xenon <= 0.01f ? null : new Bar(
                () -> Core.bundle.format("bar.ironworks-rbmk-xenon", Mathf.round(b.xenon * 100f)),
                () -> Color.valueOf("b6a8ff"),
                () -> Mathf.clamp(b.xenon / 1.2f)));

            addBar("rbmk-coolant", (RBMKReactorBuild b) -> new Bar(
                () -> Core.bundle.format("bar.ironworks-rbmk-coolant", Mathf.round(b.flow * 100f)),
                () -> b.flow < 0.9f ? Pal.remove : Liquids.water.color,
                () -> Mathf.clamp(b.flow)));

            addBar("rbmk-pressure", (RBMKReactorBuild b) -> b.coreBoom <= 0.001f ? null : new Bar(
                () -> Core.bundle.format("bar.ironworks-rbmk-pressure", Mathf.round(b.coreBoom / pressureFuse * 100f)),
                () -> Pal.remove,
                () -> Mathf.clamp(b.coreBoom / pressureFuse)));

            addBar("rbmk-fuel", (RBMKReactorBuild b) -> new Bar(
                () -> Core.bundle.format("bar.ironworks-rbmk-fuel",
                    b.items.get(IronworksNuclear.fuelRod), b.items.get(Items.thorium),
                    b.items.get(IronworksNuclear.depletedRod), b.block.itemCapacity),
                () -> IronworksNuclear.fuelRod.color,
                () -> (float)(b.items.get(IronworksNuclear.fuelRod) + b.items.get(Items.thorium)) / Math.max(b.block.itemCapacity - 8, 1)));
        }

        static Color heatColor(float heat){
            if(heat < 0.6f) return Color.valueOf("7fe08a");
            if(heat < 0.9f) return Color.valueOf("ffd166");
            return Color.valueOf("ff5c5c");
        }

        @Override
        public boolean outputsItems(){
            return false;
        }

        public class RBMKReactorBuild extends PowerGenerator.GeneratorBuild{
            /** 控制棒**目标**插入深度 0..1（滑条设定的值）。 */
            public float targetRods = 1f;
            /** 控制棒**实际**插入深度 0..1，会以有限速度向目标移动（真实棒有行程时间）。 */
            public float rods = 1f;
            /** 控制模式，见 {@link IronworksRBMK#MODE_SEMI} 等。 */
            public int controlMode = MODE_SEMI;
            /** 全自动模式的目标功率 0..1（占满通量比例）。 */
            public float autoPower = 0.7f;
            /** 中子通量 0..1.25。 */
            public float flux = 0f;
            /** 堆芯温度（归一化，>1.25 熔毁）。 */
            public float heat = 0f;
            /** 氙毒 0..1.5。 */
            public float xenon = 0f, iodine = 0f;
            /** 实际冷却液满足率 0..1（给水量 / 需水量）。 */
            public float flow = 1f;
            /** 强制手动模式下的水位上限 0..1（占 10000 L 水罐的比例）。 */
            public float waterTarget = 1f;
            /** 当前生效的水位上限 0..1：手动=滑条，半自动/全自动=后台算法。 */
            public float waterLevel = 1f;
            /** 平滑后的水→汽流量（L/s），面板显示用。 */
            public float steamRate = 0f;
            /** 汽路憋压 + 插棒到底的引爆倒计时（秒）。 */
            public float coreBoom = 0f;
            /** 水从哪一侧进来（0=右 1=上 2=左 3=下）；汽绝不往这一侧排。 */
            public int waterSide = -1;
            /** 烧掉的钍的余数。 */
            public float fuelUse = 0f;
            /** 熔毁计时（tick）。 */
            public float meltTimer = 0f;
            /** 显示温度（开尔文）。 */
            public float tempK = 300f;
            /** 当前这根燃料棒的燃耗 0..1。 */
            public float rodBurn = 0f;
            /** 硼酸注入量 0..1（强负反应性；吸够了就永久中毒）。 */
            public float boron = 0f;
            /** 是否已「武装」注硼（点了按钮才接收硼酸液体）。 */
            public boolean boronArmed = false;
            /** 堆芯永久中毒：注硼吸够了，反应堆再也起不来，只能拆。 */
            public boolean poisoned = false;
            /** SCRAM 引爆倒计时（AZ-5 效应：高功率瞬间插棒 → 正反应性尖峰）。 */
            public float scramBoom = 0f;
            /** 当前棒位对应的功率上限（%），面板显示用。 */
            public float rodCeilingPct = 0f;
            /** 是否已熔毁（防止重复爆炸）。 */
            public boolean melted = false;

            @Override
            public float getPowerProduction(){
                return 0f;   // 反应堆不出电
            }

            @Override
            public float warmup(){
                return Mathf.clamp(heat);
            }

            public void applyConfig(int v){
                if(v == -10){ controlMode = MODE_SEMI; return; }
                if(v == -11){ controlMode = MODE_AUTO; return; }
                if(v == -12){ controlMode = MODE_MANUAL; return; }
                if(v == -1){
                    // SCRAM：棒全部插进去。高功率时插棒会触发 AZ-5 正反应性尖峰 → 可能直接炸
                    if(heat > 0.75f && flux > 0.9f && !melted) scramBoom = 30f;
                    targetRods = 1f;
                    return;
                }
                if(v == -2){
                    // 注硼：只把管线「武装」起来，之后才接收硼酸液体（不是免费按钮）
                    boronArmed = true;
                    return;
                }
                if(v >= 1000){
                    // 1000 + 百分比：强制手动的水位上限（最大可接受液体容量）
                    waterTarget = Mathf.clamp((v - 1000) / 100f, 0f, 1f);
                    return;
                }
                if(v < 0) return;
                if(controlMode == MODE_AUTO){
                    // 自动模式下滑条设的是目标功率
                    autoPower = Mathf.clamp(v / 100f, 0f, 1f);
                }else{
                    targetRods = Mathf.clamp(v / 100f, 0f, 1f);
                }
            }

            @Override
            public void updateTile(){
                float dt = delta() / 60f;
                float second = delta();

                boolean hasFuel = items.get(IronworksNuclear.fuelRod) > 0 || items.get(Items.thorium) > 0;

                // —— 0. 控制棒：三种模式 ——
                if(controlMode == MODE_MANUAL){
                    // 强制手动：严格按操作，棒立刻就位，没有缓冲
                    rods = targetRods;
                }else if(controlMode == MODE_AUTO){
                    // 全自动：滑条给的是**目标功率**，后台反算棒位（含氙/硼压制）
                    float poison = Mathf.clamp(1f - xenon * 0.65f - boron * 1.2f, 0.05f, 1f);
                    float needCeil = autoPower / (fluxMax * poison);
                    float idealRods = Mathf.clamp(1f - needCeil, 0f, 1f);
                    if(heat > 0.95f) idealRods = Math.min(1f, idealRods + (heat - 0.95f) * 5f);
                    targetRods = idealRods;
                    rods = Mathf.approachDelta(rods, targetRods, targetRods > rods ? rodSpeedScram : rodSpeedNormal);
                }else{
                    // 半自动：棒按行程速度追目标（正常 33s，急停 3s）
                    rods = Mathf.approachDelta(rods, targetRods, targetRods > rods ? rodSpeedScram : rodSpeedNormal);
                }

                // —— 1. 水位上限：强制手动=滑条；半自动/全自动=后台算法 ——
                if(controlMode == MODE_MANUAL){
                    waterLevel = waterTarget;
                }else{
                    // 高功率多留水（热惯性大），低功率少留水；55%~90% 之间自动找平衡
                    float autoTarget = Mathf.clamp(0.55f + 0.35f * Math.min(flux, 1f), 0.55f, 0.9f);
                    waterLevel = Mathf.lerpDelta(waterLevel, autoTarget, 0.02f);
                }

                // —— 2. 排汽：先把上一 tick 的蒸汽推给汽轮机/蒸汽管 ——
                if(liquids.get(IronworksLiquids.steam) > 0.001f){
                    // 只往非进水侧排汽：进水导管在被给水泵抽空时会接受任何液体，
                    // 如果蒸汽灌进去，这条水管就变成汽管，反应堆立刻断水（实测会熔）。
                    for(int d = 0; d < 4; d++){
                        if(d == waterSide) continue;
                        dumpLiquid(IronworksLiquids.steam, 2f, d);
                    }
                }

                // —— 3. 给水泵：从相邻管路/水罐主动抽水，补到水位上限 ——
                pullFeedwater(dt);

                // —— 4. 水 → 汽 1:1（受水、汽包空间、总容量三重限制）——
                float demand = Math.max(flux, 0f) * waterPerSecond * dt;
                float water = liquids.get(Liquids.water);
                float steamRoom = steamCapacity - liquids.get(IronworksLiquids.steam);
                float totalRoom = LiquidKit.totalSpace(this);
                float converted = Math.min(demand, Math.min(water, Math.min(steamRoom, totalRoom)));
                if(converted > 0.0001f){
                    liquids.remove(Liquids.water, converted);
                    liquids.add(IronworksLiquids.steam, converted);
                }
                float flowRaw = demand <= 0.0001f ? 1f : converted / demand;
                flow = Mathf.lerpDelta(flow, flowRaw, 0.08f);
                // 流量读数（L/s）：1:1，给水多少就产汽多少
                float instRate = dt <= 0.00001f ? 0f : converted / dt;
                steamRate = Mathf.lerpDelta(steamRate, instRate, 0.03f);

                // —— 5. 棒位 = 功率上限（拟真核心）——
                // 每根棒的插入深度直接决定**稳态上限**，通量只朝这个上限收敛：
                //   rods = 0   → 上限 100%（满功率）
                //   rods = 50% → 上限 50%
                //   rods = 90% → 上限 10%
                // 氙毒/硼酸按比例压低上限；失冷（空泡正反应性）**顶高**上限 → 才会失控。
                float rodCeiling = (1f - rods) * (hasFuel ? 1f : 0f);
                float poisonFactor = Mathf.clamp(1f - xenon * 0.65f - boron * 1.2f, 0f, 1f);
                if(poisoned) poisonFactor = 0f;

                float target = fluxMax * rodCeiling * poisonFactor;
                // 空泡正反应性：没水/没汽路时上限被顶到 2.2 倍，可越过棒位限制（RBMK 的致命点）
                target *= 1f + (1f - flow) * 1.2f;
                target = Mathf.clamp(target, 0f, fluxMax);

                // 一阶滞后（中子动力学）：插棒/提棒后通量慢慢追上限
                flux = Mathf.lerpDelta(flux, target, 0.008f);
                productionEfficiency = flux;
                rodCeilingPct = rodCeiling * 100f;

                // —— 6. 燃料：燃料棒优先（耐烧 + 吐乏燃料），钍是应急替代 ——
                if(hasFuel && flux > 0.01f){
                    boolean rod = items.get(IronworksNuclear.fuelRod) > 0;
                    float perSecond = rod ? rodPerSecond : thoriumPerSecond;
                    fuelUse += flux * perSecond * dt;
                    while(fuelUse >= 1f){
                        fuelUse -= 1f;
                        if(items.get(IronworksNuclear.fuelRod) > 0){
                            // 乏燃料排不出去就别换料了（真实：乏燃料池满了就不换棒）
                            if(items.get(IronworksNuclear.depletedRod) >= itemCapacity - 1){
                                fuelUse = 0f;
                                break;
                            }
                            items.remove(IronworksNuclear.fuelRod, 1);
                            items.add(IronworksNuclear.depletedRod, 1);
                            rodBurn = 0f;
                        }else if(items.get(Items.thorium) > 0){
                            items.remove(Items.thorium, 1);
                            rodBurn = 0f;
                        }else{
                            break;
                        }
                    }
                }
                // 燃耗进度 = 当前这根烧到哪儿了（0..1）
                if(hasFuel && flux > 0.01f) rodBurn = Mathf.clamp(fuelUse);

                // —— 7. 热：发热 ∝ 通量；冷却 ∝ 给水流量 × 堆温（含少量本底散热）——
                // 满通量 + 满给水：平衡温度 ≈ 79%；给水掉一半 → 平衡温度 >100%（会烧）；
                // 完全断水：约 9 秒从 0 升到 125% → 再加 1 秒熔毁确认。
                float heatGen = flux * 0.14f;
                float cooling = flow * (0.06f + 0.09f * heat) + 0.01f;
                heat = Mathf.clamp(heat + (heatGen - cooling) * dt, 0f, 2f);

                // —— 8. 氙毒（碘 → 氙，氙被通量烧掉）——
                // 碘半衰期 ~90 秒（游戏内压缩），氙被通量强烈烧掉、停堆后衰变很慢
                // → 满功率运行时氙毒很小，降功率/停堆后反而堆起来（碘坑）
                iodine = Mathf.clamp(iodine + (flux * 0.02f - iodine * 0.011f) * dt, 0f, 1.5f);
                xenon = Mathf.clamp(xenon + (iodine * 0.012f - xenon * (0.0012f + flux * 0.06f)) * dt, 0f, 1.5f);

                // —— 硼酸注入：武装后才消耗液体，吸中子 → 硼浓度上升 → 够了就永久中毒 ——
                if(boronArmed && liquids.get(IronworksLiquids.boricAcid) > 0.01f){
                    float take = Math.min(liquids.get(IronworksLiquids.boricAcid), 4f * dt);
                    liquids.remove(IronworksLiquids.boricAcid, take);
                    boron = Mathf.clamp(boron + take * 0.25f, 0f, 1f);
                    if(boron >= 1f) poisoned = true;
                }
                // 永久中毒：堆芯封死，再也不起反应（拆了重建是唯一出路）
                if(poisoned){
                    targetRods = 1f;
                    rods = Mathf.approachDelta(rods, 1f, rodSpeedScram);
                    flux = Mathf.approachDelta(flux, 0f, 0.02f);
                }

                // —— 9. 汽路憋压引爆：罐满 + 蒸汽排不出去 + 控制棒插到底 ——
                // 真实 RBMK 的 AZ-5：堆芯被汽泡占据时把棒插到底，石墨补偿段先挤走水
                // → 正反应性尖峰。这里用 2 秒倒计时让你看到「憋压」警告条。
                checkPressureBoom(dt, hasFuel);
                if(melted) return;

                // SCRAM 引爆倒计时
                if(scramBoom > 0f){
                    scramBoom -= dt * 60f;
                    if(scramBoom <= 0f) detonate();
                }

                // —— 10. 自动 SCRAM：半自动/全自动才有；强制手动模式没有任何自动保护 ——
                if(controlMode != MODE_MANUAL && heat > 1.05f && targetRods < 0.999f){
                    targetRods = 1f;
                }

                // —— 11. 熔毁 ——
                if(heat >= meltTemp && !melted){
                    meltTimer += second;
                    if(meltTimer >= 60f) detonate();
                }else if(meltTimer > 0f){
                    meltTimer = Math.max(0f, meltTimer - second * 2f);
                }

                // —— 12. 显示/视觉 ——
                // 冷却剂出口温度（°C）：名义工况 ~280°C，熔毁前逼近 900°C
                tempK = 280f + heat * 500f;
                if(flux > 0.05f && Mathf.chanceDelta(flux * 0.15f)){
                    Fx.smoke.at(x + Mathf.range(size * 4f), y + Mathf.range(size * 4f));
                }
            }

            /**
             * 汽路堵死 + 水罐满 + 控制棒插到底：AZ-5 式的正反应性尖峰，2 秒后炸堆。
             *
             * <p>「水罐满」按**当前水位上限**算（手动=滑条，自动=算法），
             * 而不是按 12000 L 总容量算：给水泵顶到上限、汽包也顶满，
             * 就表示水进不去、汽出不来 —— 这时候插棒才是致命的。
             */
            void checkPressureBoom(float dt, boolean hasFuel){
                boolean waterFull = liquids.get(Liquids.water) >= waterLevel * waterCapacity - 2f;
                boolean steamStuck = liquids.get(IronworksLiquids.steam) >= steamCapacity - 2f;
                boolean rodsBottom = targetRods >= 0.995f && rods >= 0.995f;
                boolean live = hasFuel && (heat > 0.1f || flux > 0.02f);
                if(waterFull && steamStuck && rodsBottom && live && !melted){
                    coreBoom += dt;
                    if(coreBoom >= pressureFuse) detonate();
                }else{
                    coreBoom = Math.max(0f, coreBoom - dt * 1.5f);
                }
            }

            /** 从相邻管路/水罐主动抽水到水位上限（反应堆自带给水泵，不靠导管慢慢渗）。 */
            void pullFeedwater(float dt){
                float target = waterLevel * waterCapacity;
                float need = target - liquids.get(Liquids.water);
                if(need <= 0.5f) return;
                float budget = Math.min(need, feedwaterRate * dt);
                if(budget <= 0.001f) return;

                for(Building other : proximity){
                    if(budget <= 0.001f) break;
                    if(other == null || other == this || other.team != team || other.liquids == null) continue;
                    float avail = other.liquids.get(Liquids.water);
                    if(avail <= 0.001f) continue;
                    float before = liquids.get(Liquids.water);
                    other.transferLiquid(this, Math.min(budget, avail), Liquids.water);
                    float moved = liquids.get(Liquids.water) - before;
                    if(moved > 0f) budget -= moved;
                }
            }

            /** 运行时被炸毁 / 被玩家拆除：会炸；冷停堆状态拆就没事。 */
            void explodeIfLive(){
                if(melted || state.rules.reactorExplosions == false) return;
                if(flux > 0.15f || heat > 0.5f) detonate();
            }

            @Override
            public void onDestroyed(){
                explodeIfLive();
                super.onDestroyed();
            }

            @Override
            public void onDeconstructed(mindustry.gen.Unit unit){
                // 只结算爆炸，不自己移除方块：让引擎继续把 tile 换成 ConstructBlock，
                // 否则 beginBreak 里后面的 tile.build.dead 会 NPE（实测崩溃）。
                if(!melted && state.rules.reactorExplosions && (flux > 0.15f || heat > 0.5f)){
                    detonate(false);
                }
                super.onDeconstructed(unit);
            }

            public void detonate(){
                detonate(true);
            }

            /**
             * @param removeSelf true = 自己 kill 掉；false = 只造成爆炸效果，方块本身交给引擎处理。
             * 拆除流程里必须传 false：{@code Build.beginBreak} 在调用 onDeconstructed 之后
             * 还要写 {@code tile.build.dead}，我们自己把 tile 移除会变成 null → 引擎 NPE 崩溃。
             */
            public void detonate(boolean removeSelf){
                // 注意：不能判 dead——被炸死时引擎先置 dead 再调 onDestroyed，
                // 这里如果因为 dead 提前 return，运行时被摧毁就永远不会炸了。
                if(melted) return;
                melted = true;

                if(state.rules.reactorExplosions){
                    // 功率越高、堆芯越热，炸得越狠（冷停堆被拆只会小爆）
                    float scale = 0.45f + Math.min(flux, 1.4f) * 0.45f + Math.min(heat, 1.6f) * 0.35f;
                    Fx.reactorExplosion.at(x, y);
                    Fx.massiveExplosion.at(x, y);
                    Damage.damage(x, y, meltRadius * 8f * Math.min(scale, 1.6f), meltDamage * Math.min(scale, 1.6f));

                    // 堆芯熔融物：在中心铺 1~5 格 corium，持续辐射 + 熔穿周围
                    IronworksNuclear.spawnCorium(tile, team);
                    // 落尘：给爆心外的单位挂辐射（保留原逻辑）
                    //落尘范围比爆炸更大，站在爆心外也可能吃到辐射
                    for(Unit u : Groups.unit){
                        if(u.dst2(x, y) < falloutRadius * falloutRadius){
                            u.apply(IronworksStatus.radiation, radiationTime);
                        }
                    }
                }else{
                    Fx.dynamicExplosion.at(x, y);
                }

                if(removeSelf && !dead) kill();
            }

            // 只收水，热冷却液不被邻居倒灌
            @Override
            public boolean acceptItem(Building source, mindustry.type.Item item){
                if(item == IronworksNuclear.fuelRod || item == Items.thorium){
                    // 留 8 格给乏燃料，不然烧出来的乏燃料会把自己憋死
                    return items.get(item) < itemCapacity - 8;
                }
                return false;
            }

            /**
             * 注意：**不能调 super.acceptLiquid**——引擎的实现是 block.consumesLiquid(liquid)，
             * 那是 ConsumeLiquid 生成的过滤器。反应堆是手动消耗液体、没有消费者，
             * 过滤器全 false，导管就永远推不进来（实测就是这个 bug）。
             */
            @Override
            public boolean acceptLiquid(Building source, Liquid liquid){
                // 水：只接到「水位上限」为止（手动=滑条，自动=算法），超过就不再收
                if(liquid == Liquids.water){
                    if(source != null){
                        int side = relativeTo(source);
                        if(side >= 0) waterSide = side;
                    }
                    return liquids.get(Liquids.water) < waterLevel * waterCapacity - 0.5f
                        && LiquidKit.totalSpace(this) > LiquidKit.EPS;
                }
                // 硼酸只有在「已武装注入」后才收（点击按钮之后才允许接液）
                if(boronArmed && liquid == IronworksLiquids.boricAcid) return LiquidKit.hasSpace(this, liquid);
                return false;
            }

            @Override
            public void draw(){
                super.draw();

                // 堆芯余热/中子辉光
                if(heat > 0.15f){
                    float a = Mathf.clamp((heat - 0.15f) * 0.6f, 0f, 0.55f) * Mathf.absin(Time.time, 5f, 1f);
                    Draw.color(Color.valueOf("ff7a3c"), a);
                    Draw.rect(Core.atlas.white(), x, y, size * 8f * 0.5f, size * 8f * 0.5f);
                    Draw.color();
                }

                // 危险工况：画一圈红色虚线警示（辐射/熔毁风险可视化）
                if(heat > 1.0f || boron > 0.5f){
                    Drawf.dashCircle(x, y, size * 4f + 6f, Pal.remove);
                }

                // 控制棒指示：插入越深，顶部亮条越短
                Draw.color(Pal.accent);
                float w = size * 8f * 0.7f;
                Draw.rect(Core.atlas.white(), x, y + size * 4f - 6f, w * (1f - rods * 0.8f), 3f);
                Draw.color();
            }

            // ---------------- UI ----------------

            @Override
            public void buildConfiguration(Table table){
                table.defaults().pad(2f);
                float w = 262f;

                // 标题
                table.add(Core.bundle.get("ironworks.rbmk.panel.title")).color(Pal.accent)
                    .padBottom(3f).row();

                // —— 控制模式 ——
                table.table(t -> {
                    TextButton semi = new TextButton(Core.bundle.get("ironworks.rbmk.mode.semi"), Styles.flatTogglet);
                    semi.clicked(() -> configure(-10));
                    semi.update(() -> semi.setChecked(controlMode == MODE_SEMI));
                    t.add(semi).size(82f, 34f).padRight(4f);

                    TextButton auto = new TextButton(Core.bundle.get("ironworks.rbmk.mode.auto"), Styles.flatTogglet);
                    auto.clicked(() -> configure(-11));
                    auto.update(() -> auto.setChecked(controlMode == MODE_AUTO));
                    t.add(auto).size(82f, 34f).padRight(4f);

                    TextButton manual = new TextButton(Core.bundle.get("ironworks.rbmk.mode.manual"), Styles.flatTogglet);
                    manual.clicked(() -> configure(-12));
                    manual.update(() -> manual.setChecked(controlMode == MODE_MANUAL));
                    t.add(manual).size(82f, 34f);
                }).row();

                // —— 控制棒 ——
                table.table(Styles.grayPanel, c -> {
                    c.defaults().pad(5f);
                    Label rodLabel = new Label("");
                    rodLabel.update(() -> rodLabel.setText(controlMode == MODE_AUTO
                        ? Core.bundle.format("ironworks.rbmk.power.now",
                            Mathf.round(autoPower * 100f), Mathf.round(flux / fluxMax * 100f))
                        : Core.bundle.format("ironworks.rbmk.rods.now",
                            Mathf.round(targetRods * 100f), Mathf.round(rods * 100f))));
                    c.add(rodLabel).left().growX().row();

                    Slider slider = new Slider(0f, 100f, 1f, false);
                    slider.moved(v -> configure((int)v));
                    slider.update(() -> {
                        // 自动模式下显示/拖动的是目标功率，其它模式是棒位
                        float shown = controlMode == MODE_AUTO ? autoPower : targetRods;
                        if(!slider.isDragging()) slider.setValue(shown * 100f);
                    });
                    c.add(slider).width(230f).row();

                    c.add(Core.bundle.get("ironworks.rbmk.rodhint")).wrap()
                        .width(230f).color(Color.lightGray).left().row();
                }).width(w).row();

                // —— 水位上限（最大可接受液体容量）：仅强制手动可拖，半自动/全自动后台算 ——
                table.table(Styles.grayPanel, c -> {
                    c.defaults().pad(5f);
                    Label waterLabel = new Label("");
                    waterLabel.update(() -> {
                        int cur = Mathf.round(liquids.get(Liquids.water));
                        waterLabel.setText(controlMode == MODE_MANUAL
                            ? Core.bundle.format("ironworks.rbmk.water.now",
                                Mathf.round(waterTarget * 100f), cur, (int)waterCapacity)
                            : Core.bundle.format("ironworks.rbmk.water.auto",
                                Mathf.round(waterLevel * 100f), cur, (int)waterCapacity));
                    });
                    c.add(waterLabel).left().growX().row();

                    Slider waterSlider = new Slider(0f, 100f, 1f, false);
                    waterSlider.moved(v -> {
                        // 半自动/全自动「不接这条」：后台算法说了算
                        if(controlMode == MODE_MANUAL) configure(1000 + (int)v);
                    });
                    waterSlider.update(() -> {
                        waterSlider.touchable = controlMode == MODE_MANUAL
                            ? arc.scene.event.Touchable.enabled : arc.scene.event.Touchable.disabled;
                        float shown = controlMode == MODE_MANUAL ? waterTarget : waterLevel;
                        if(!waterSlider.isDragging()) waterSlider.setValue(shown * 100f);
                    });
                    c.add(waterSlider).width(230f).row();

                    Label waterHint = new Label("");
                    waterHint.update(() -> waterHint.setText(Core.bundle.get(
                        controlMode == MODE_MANUAL ? "ironworks.rbmk.waterhint.manual" : "ironworks.rbmk.waterhint.auto")));
                    c.add(waterHint).wrap().width(230f).color(Color.lightGray).left().row();
                }).width(w).row();

                // —— 实时读数 ——
                table.table(Styles.grayPanel, c -> {
                    c.defaults().padLeft(8f).padRight(8f).padTop(2f).padBottom(2f);
                    readout(c, Core.bundle.get("ironworks.rbmk.row.ceiling"),
                        () -> Mathf.round(rodCeilingPct) + "%",
                        () -> Pal.accent);
                    readout(c, Core.bundle.get("ironworks.rbmk.row.flux"),
                        () -> Mathf.round(flux * 100f) + "%",
                        () -> Pal.accent);
                    readout(c, Core.bundle.get("ironworks.rbmk.row.heat"),
                        () -> Mathf.round(heat * 100f) + "%  (" + Mathf.round(tempK) + "°C)",
                        () -> heat >= 1.05f ? Pal.remove : heat >= 0.9f ? Color.valueOf("ffd166") : Color.valueOf("7fe08a"));
                    readout(c, Core.bundle.get("ironworks.rbmk.row.water"),
                        () -> Mathf.round(liquids.get(Liquids.water)) + "/" + (int)waterCapacity + " L",
                        () -> Liquids.water.color);
                    readout(c, Core.bundle.get("ironworks.rbmk.row.steam"),
                        () -> Mathf.round(liquids.get(IronworksLiquids.steam)) + "/" + (int)steamCapacity + " L",
                        () -> IronworksLiquids.steam.color);
                    readout(c, Core.bundle.get("ironworks.rbmk.row.rate"),
                        () -> Mathf.round(steamRate) + " L/s",
                        () -> Liquids.water.color);
                    readout(c, Core.bundle.get("ironworks.rbmk.row.xenon"),
                        () -> Mathf.round(xenon * 100f) + "%",
                        () -> Color.valueOf("b6a8ff"));
                    readout(c, Core.bundle.get("ironworks.rbmk.row.coolant"),
                        () -> Mathf.round(flow * 100f) + "%",
                        () -> flow < 0.9f ? Pal.remove : Color.valueOf("7fe08a"));
                    readout(c, Core.bundle.get("ironworks.rbmk.row.burn"),
                        () -> Mathf.round(rodBurn * 100f) + "%",
                        () -> Color.valueOf("9fe8b0"));
                    readout(c, Core.bundle.get("ironworks.rbmk.row.fuel"),
                        () -> Core.bundle.format("ironworks.rbmk.fuelcount",
                            items.get(IronworksNuclear.fuelRod), items.get(Items.thorium),
                            items.get(IronworksNuclear.depletedRod), itemCapacity),
                        () -> IronworksNuclear.fuelRod.color);
                    if(boron > 0.001f){
                        readout(c, Core.bundle.get("ironworks.rbmk.row.boron"),
                            () -> Mathf.round(boron * 100f) + "%",
                            () -> Color.valueOf("8fd4ff"));
                    }
                    c.add().height(2f).row();
                }).width(w).row();

                // —— 状态 ——
                Label status = new Label("");
                status.update(() -> {
                    if(poisoned){
                        status.setText(Core.bundle.get("ironworks.rbmk.state.poisoned"));
                        status.setColor(Color.valueOf("8fd4ff"));
                        return;
                    }
                    boolean steamStuck = liquids.get(IronworksLiquids.steam) >= steamCapacity - 2f;
                    boolean waterFull = liquids.get(Liquids.water) >= waterLevel * waterCapacity - 2f;
                    if(coreBoom > 0f){
                        status.setText(Core.bundle.get("ironworks.rbmk.state.pressure"));
                        status.setColor(Pal.remove);
                    }else if(steamStuck && waterFull){
                        status.setText(Core.bundle.get("ironworks.rbmk.state.blocked"));
                        status.setColor(Pal.remove);
                    }else if(heat >= meltTemp){
                        status.setText(Core.bundle.get("ironworks.rbmk.state.melt"));
                        status.setColor(Pal.remove);
                    }else if(heat > 1.05f){
                        status.setText(Core.bundle.get("ironworks.rbmk.state.danger"));
                        status.setColor(Pal.remove);
                    }else if(flow < 0.9f){
                        status.setText(Core.bundle.get("ironworks.rbmk.state.dry"));
                        status.setColor(Color.valueOf("ffd166"));
                    }else{
                        status.setText(Core.bundle.get("ironworks.rbmk.state.ok"));
                        status.setColor(Color.valueOf("7fe08a"));
                    }
                });
                table.add(status).padTop(4f).row();

                // —— 紧急停堆 ——
                table.button(Core.bundle.get("ironworks.rbmk.scram"), Styles.defaultt, () -> configure(-1))
                    .size(w, 42f).padTop(6f).with(b -> b.setColor(Pal.remove)).row();

                // 硼酸注入：比 SCRAM 更狠，几十秒内别想再启动
                TextButton boronBtn = new TextButton("", Styles.defaultt);
                boronBtn.clicked(() -> configure(-2));
                boronBtn.update(() -> boronBtn.setText(Core.bundle.get(
                    boronArmed ? "ironworks.rbmk.boron.armed" : "ironworks.rbmk.boron.idle")));
                table.add(boronBtn).size(w, 36f).padTop(4f).with(b -> b.setColor(Color.valueOf("8fd4ff"))).row();
            }

            /** 面板里的一行读数：左灰标签，右带色实时值。 */
            void readout(Table c, String label, arc.func.Prov<String> value, arc.func.Prov<Color> color){
                c.add(label).left().color(Color.lightGray).growX();
                Label v = new Label("");
                v.update(() -> {
                    v.setText(value.get());
                    v.setColor(color.get());
                });
                c.add(v).right().padLeft(8f).row();
            }

            @Override
            public byte version(){
                return 6;
            }

            @Override
            public void write(Writes write){
                super.write(write);
                write.f(rods);
                write.f(flux);
                write.f(heat);
                write.f(xenon);
                write.f(iodine);
                write.f(fuelUse);
                write.bool(melted);
                write.f(rodBurn);
                write.f(boron);
                write.bool(boronArmed);
                write.bool(poisoned);
                write.f(targetRods);
                write.i(controlMode);
                write.f(autoPower);
                write.f(waterTarget);
                write.b(waterSide);
            }

            @Override
            public void read(Reads read, byte revision){
                super.read(read, revision);
                if(revision >= 1){
                    rods = read.f();
                    flux = read.f();
                    heat = read.f();
                    xenon = read.f();
                    iodine = read.f();
                    fuelUse = read.f();
                    melted = read.bool();
                    rodBurn = read.f();
                    boron = read.f();
                    boronArmed = read.bool();
                    poisoned = read.bool();
                    targetRods = read.f();
                    controlMode = read.i();
                    autoPower = read.f();
                    if(revision >= 5) waterTarget = read.f();
                    if(revision >= 6) waterSide = read.b();
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 汽轮机：热冷却液 → 电 + 回收水
    // ------------------------------------------------------------------

    public static class SteamTurbine extends PowerGenerator{
        public SteamTurbine(String name){
            super(name);
            size = 3;
            rotate = false;
            solid = true;
            update = true;
            hasLiquids = true;
            // 1.2 秒的蒸汽缓冲，够一根导管供汽
            liquidCapacity = 1200f;
            explosionDamage = 0;
            explosionRadius = 0;
            baseExplosiveness = 0f;
            powerProduction = turbinePower / 60f;
            buildType = SteamTurbineBuild::new;
        }

        @Override
        public void load(){
            super.load();
            uiIcon = fullIcon = Core.atlas.find(name + "-ui", region);
        }

        @Override
        public void setStats(){
            super.setStats();
            stats.remove(Stat.basePowerGeneration);

            stats.add(new Stat("ironworks-rbmk-turbine-specs"), (StatValue)table -> {
                IronworksStats.specRow(table, Icon.power,
                    Core.bundle.get("ironworks.turbine.spec.power"),
                    (int)turbinePower + StatUnit.powerSecond.localized(), Pal.accent);
                IronworksStats.specRow(table, IronworksLiquids.steam.uiIcon,
                    Core.bundle.get("ironworks.turbine.spec.steam"),
                    (int)turbineSteamPerSecond + "/" + StatUnit.perSecond.localized(),
                    IronworksLiquids.steam.color);
                IronworksStats.specRow(table, Liquids.water.uiIcon,
                    Core.bundle.get("ironworks.turbine.spec.water"),
                    (int)(turbineSteamPerSecond * turbineWaterReturn) + "/" + StatUnit.perSecond.localized(),
                    IronworksLiquids.warmWater.color);
            });
        }

        @Override
        public void setBars(){
            super.setBars();
            // 通用条会显示 liquids.current()（热液/温水来回跳）→ 每种液体各一条
            CoreBars.perLiquid(this, IronworksLiquids.steam, IronworksLiquids.warmWater);
            addBar("turbine-flow", (SteamTurbineBuild b) -> new Bar(
                () -> Core.bundle.format("bar.ironworks-rbmk-turbine", Mathf.round(b.flow * 100f)),
                () -> IronworksLiquids.steam.color,
                () -> Mathf.clamp(b.flow)));
        }

        public class SteamTurbineBuild extends PowerGenerator.GeneratorBuild{
            public float flow = 0f;
            public float spin = 0f;
            /** 蒸汽从哪一侧进来（0=右 1=上 2=左 3=下）。温水不能从这一侧倒回蒸汽管。 */
            public int inputSide = -1;

            @Override
            public float getPowerProduction(){
                return enabled ? flow * powerProduction : 0f;
            }

            @Override
            public void handleLiquid(Building source, Liquid liquid, float amount){
                if(liquid == IronworksLiquids.steam && source != null){
                    int side = relativeTo(source);
                    if(side >= 0) inputSide = side;
                }
                super.handleLiquid(source, liquid, amount);
            }

            @Override
            public void updateTile(){
                float dt = delta() / 60f;

                float need = turbineSteamPerSecond * dt;
                float have = liquids.get(IronworksLiquids.steam);
                float used = Math.min(need, have);
                if(used > 0f) liquids.remove(IronworksLiquids.steam, used);

                flow = need < 0.0001f ? 0f : used / need;
                productionEfficiency = flow;
                spin = Mathf.lerp(spin, flow, 0.05f * delta());

                // 冷凝水回收
                if(used > 0f){
                    // 做完功的乏汽冷凝成「温水」，要过冷却塔才能回反应堆。
                    // 注意用 addCapped（按单液体容量）而不是 addCappedShared：
                    // 汽轮机里蒸汽和温水是两条独立的腔室，不能让蒸汽把温水的空间吃掉。
                    LiquidKit.addCapped(this, IronworksLiquids.warmWater, used * turbineWaterReturn);
                }
                if(liquids.get(IronworksLiquids.warmWater) > 0.01f){
                    // 只从「非进汽侧」排温水：否则蒸汽管一旦被抽空，
                    // 温水就会倒灌进去把导管变成温水，蒸汽线直接死掉（实测）。
                    for(int d = 0; d < 4; d++){
                        if(d == inputSide) continue;
                        dumpLiquid(IronworksLiquids.warmWater, 2f, d);
                    }
                }
            }

            // 同 reactor：不能走 super（没有消费者 → 过滤器全 false）
            @Override
            public boolean acceptLiquid(Building source, Liquid liquid){
                return liquid == IronworksLiquids.steam && LiquidKit.hasSpace(this, liquid);
            }

            @Override
            public void draw(){
                super.draw();
                if(spin > 0.05f){
                    float a = Mathf.clamp(spin) * Mathf.absin(Time.time, 3f, 0.5f) + 0.1f;
                    Draw.color(IronworksLiquids.steam.color, a);
                    Draw.rect(Core.atlas.white(), x, y + size * 4f - 8f, size * 8f * 0.5f, 2f);
                    Draw.color();
                }
            }

            @Override
            public byte version(){
                return 1;
            }

            @Override
            public void write(Writes write){
                super.write(write);
                write.f(flow);
            }

            @Override
            public void read(Reads read, byte revision){
                super.read(read, revision);
                if(revision >= 1) flow = read.f();
            }
        }
    }
}
