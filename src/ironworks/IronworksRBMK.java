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
 *   <li>旧 5×5：水→汽 1:1，100% 通量=980 L/s；水罐 10000 L、汽包 2000 L；</li>
 *   <li>RBMK-9：9 区独立水/汽账面与独立转化，燃料/冷却剂双节点热积分；</li>
 *   <li>燃料温度触发多普勒负反馈；0～20% 棒尖先引入正反应性；停堆后仍有衰变热；</li>
 *   <li>**空泡正反应性**：给水/排汽不足时通量上限反而上涨（RBMK 的经典缺陷）；</li>
 *   <li>**AZ-5 憋压引爆**：汽路堵死 + 罐满 + 控制棒插到底 → 正反应性尖峰，几秒内炸堆；</li>
 *   <li>三种模式：半自动（棒有行程 + 自动保护 + 自动水位）、全自动（滑条设目标功率，
 *       后台自动算棒位与水位）、强制手动（棒瞬动、水位手动、无自动保护）；</li>
 *   <li>氙毒：降功率后碘衰变成氙，短时间内压着反应性（碘坑）；</li>
 *   <li>旧 5×5：堆芯温度 &gt;125% 持续1秒熔毁；RBMK-9：燃料温度 ≥200% 累积逐区损伤，任意一区到1爆炸。</li>
 * </ul>
 */
public class IronworksRBMK{

    public static RBMKReactor reactor;
    /** 12×12 分区堆芯：3×3 个 4×4 区块，每区独立热/通量/棒位；水汽分区记账，公共汽包兼容原版汽轮机。 */
    public static RBMKReactor zonedReactor;
    public static SteamTurbine turbine;
    /** 4×4 大型汽轮机：360 蒸汽/秒 → 72000 电/秒 + 360 温水/秒。 */
    public static SteamTurbine largeTurbine;

    // ---- 反应堆参数 ----
    /** 额定给水/蒸汽流量（/秒，100% 通量）；水→汽 1:1，不多不少。 */
    public static final float waterPerSecond = 980f;
    /** 12×12 分区堆额定总通量：3240 L/s，单区 360 L/s；125% 峰值 4050 L/s。 */
    public static final float zonedWaterPerSecond = 3240f;
    public static final float zonedWaterPerZone = 360f;
    public static final float zonedWaterCapacity = 32400f;
    public static final float zonedSteamCapacity = 6480f;
    /** 单区水/汽基准容量：9×3600=32400，9×720=6480 */
    public static final float zonedWaterPerZoneCapacity = 3600f;
    public static final float zonedSteamPerZoneCapacity = 720f;
    public static final float zonedFeedwaterRate = 4700f;
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
    /** 控制棒全行程时间（秒）：正常插拔 20 秒，SCRAM 3 秒，自动保护 1.5 秒。 */
    public static final float rodTravelNormal = 20f, rodTravelScram = 3f, rodTravelAuto = 1.5f;
    /** 冷却节流阀全行程时间（秒）；手动/自动都走实际阀位，而不是瞬间跳变。 */
    public static final float throttleTravel = 8f;
    /** 高/低水位设定值执行器全行程时间（秒）。 */
    public static final float waterSetpointTravel = 10f;
    /** 手动高线以下溢流阀的最大排水速率（L/s）：调低高线后罐内多余水缓慢排掉。 */
    public static final float overflowRate = 600f;
    /** 自动目标功率的 UI/配置上限（百分比/100）；算法本身不再截断通量。 */
    public static final float autoPowerMax = 9.99f;
    /** 控制模式：0 半自动（棒有行程 + 自动保护）、1 全自动（自动跟踪目标功率）、2 强制手动（无缓冲无保护）。 */
    public static final int MODE_SEMI = 0, MODE_AUTO = 1, MODE_MANUAL = 2;
    /** 自动保护跳闸原因；用于 UI 与告警，不直接参与物理计算。 */
    public static final int TRIP_NONE = 0, TRIP_HIGH_HEAT = 1, TRIP_LOW_CORE = 2, TRIP_LOW_FLOW = 3,
        TRIP_HIGH_STEAM = 4, TRIP_HIGH_PRESSURE = 5, TRIP_ROD_JAM = 6;
    /** 额定通量/棒尖最大价值：只作为基准，不再作为硬上限。 */
    public static final float fluxMax = 1.25f;
    /** 旧 5×5 熔毁温度阈值（归一化）。 */
    public static final float meltTemp = 1.25f;
    /** RBMK-9 双节点燃料温度熔毁阈值；2.0 约等于建议里的 200%。 */
    public static final float zonedMeltTemp = 2.0f;
    /** 三节点热工：燃料芯块热容远大于包壳，停堆后仍有明显蓄热。 */
    public static final float fuelHeatCapacity = 6.0f, cladHeatCapacity = 1.5f;
    public static final float fuelToCladK = 0.9f, cladToCoolantK = 3.0f;
    /** 双节点兼容常数：旧 5×5 仍用燃料→冷却剂直接传热。 */
    public static final float transferK = 2.0f;
    public static final float evapK = 0.6f, evapRef = 0.5f, evapMax = 1.6f;
    /** 蒸汽干度：水位越低，夹带水滴越多；干度低于 0.9 会析出湿蒸汽。 */
    public static final float drySteamThreshold = 0.90f;
    /** 主泵惰转时间常数（秒）：断电后流量按 exp(-t/tau) 衰减。 */
    public static final float pumpCoastTau = 45f;
    /** 堆芯注水最大速率（额定流量的倍数）。 */
    public static final float coreInjectMax = 1.5f;
    /** 一回路压力/水锤阈值。 */
    public static final float pressureLimit = 1.4f;
    public static final float hammerThreshold = 1.15f;
    /** 燃料温度超过阈值后的熔毁损伤积分速率，以及冷却后的恢复速率（每秒）。 */
    public static final float meltRate = 0.04f, meltRecover = 0.02f;
    /** 衰变热：满功率历史的 7%，时间常数 300 秒。 */
    public static final float decayHeatFraction = 0.07f, decayHeatTau = 300f;
    /** 多普勒负反馈：燃料温度超过 0.8 后，每 1.0 温度降低 30% 反应性。 */
    public static final float dopplerSlope = 0.30f, dopplerRef = 0.8f;
    /** 控制棒棒尖（石墨 displacer）正反应性峰值；按现有 (1-rod) 上限调成可见的 +25%。 */
    public static final float rodTipStrength = 0.35f;
    /** 汽路憋压 + 插棒到底后的引爆倒计时（秒）。 */
    public static final float pressureFuse = 2f;
    /** 熔毁后爆炸半径（tile）与伤害。 */
    public static final int meltRadius = 26;
    /** 第一次主爆炸的固定伤害。 */
    public static final float meltDamage = 999999f;
    /** 辐射持续时间（tick）。 */
    public static final float radiationTime = 900f;
    /** 辐射沉降半径（比爆炸大：落尘范围更广）。 */
    /** 伤害半径与特效半径统一倍率：先对齐原 2 倍视觉，再整体扩大 1.5 倍。 */
    public static final float blastRangeMultiplier = 3f;

    // ---- 汽轮机参数 ----
    /** 满流发电（/秒）。 */
    public static final float turbinePower = 24000f;
    /** 满流吃蒸汽（/秒）—— 一台汽轮机正好配一台反应堆。 */
    public static final float turbineSteamPerSecond = 122.5f;
    /** 冷凝水回收比例（1 = 质量守恒，蒸汽 980 → 温水 980）。 */
    public static final float turbineWaterReturn = 1f;

    /**
     * 控制棒插入深度 r 的有效反应性因子。
     *
     * <p>0～20% 段模拟 RBMK 石墨棒尖：先引入正反应性，再转为负；
     * 20% 以上按原来的线性棒价值，100% 时仍严格归零，保证 SCRAM 能停堆。
     * 返回值乘到通量目标上，因此 0.1 左右会比全提略高，这是棒尖效应的“味道”。
     */
    public static float rodEffect(float r){
        r = Mathf.clamp(r, 0f, 1f);
        float worth = r;
        if(r < 0.2f){
            // 正弦凸起：r=0 时 0，r=0.1 左右为负棒价值（正反应性），r=0.2 回到 0.2
            worth = r - rodTipStrength * Mathf.sin(Mathf.PI * (r / 0.2f));
        }
        return Mathf.clamp(1f - worth, 0f, 1.25f);
    }

    /** 控制棒单 tick 最大行程比例；dt 秒、travel 秒走完全程。 */
    public static float rodStep(float dt, float travel){
        return Mathf.clamp(dt / Math.max(travel, 0.001f), 0f, 1f);
    }

    /** 多普勒负反馈：燃料温度越高，目标通量越低。 */
    public static float dopplerFactor(float fuelHeat){
        return Mathf.clamp(1f - dopplerSlope * Math.max(0f, fuelHeat - dopplerRef), 0.05f, 1f);
    }

    /**
     * 反算全自动棒位：给定目标有效通量，解 rodEffect(r) = effective / base。
     *
     * <p>棒尖曲线在 0～0.2 不是单调的，所以先处理正常段 r≥0.2；
     * 如果目标高到需要进入棒尖段，再从 0.2 往 0 扫描，取较大的解，避免自动钻到正反应性峰上。
     */
    public static float autoRodsFor(float effective, float poison, float doppler, float voidFactor){
        float base = fluxMax * Math.max(poison, 0.0001f) * Math.max(doppler, 0.0001f) * Math.max(voidFactor, 0.0001f);
        float ratio = Math.max(0f, effective / base);
        float atTip = rodEffect(0.2f);
        // 目标超过棒尖最大价值时，唯一可做的就是全提棒；不在这里人为截断目标。
        if(ratio >= rodEffect(0.1f)) return 0f;

        if(ratio <= atTip){
            float lo = 0.2f, hi = 1f;
            for(int i = 0; i < 24; i++){
                float mid = (lo + hi) * 0.5f;
                if(rodEffect(mid) > ratio) lo = mid;
                else hi = mid;
            }
            return Mathf.clamp((lo + hi) * 0.5f, 0.2f, 1f);
        }

        // 目标落在 0～0.2 的棒尖段：从 0.2 向 0 找最后一个 effect >= ratio 的点。
        float best = 0f;
        for(int i = 1; i <= 200; i++){
            float r = 0.2f - i * 0.001f;
            if(rodEffect(r) >= ratio){
                best = r;
                break;
            }
        }
        return Mathf.clamp(best, 0f, 0.2f);
    }

    public static void load(){
        reactor = new RBMKReactor("rbmk-reactor");
        zonedReactor = new RBMKReactor("rbmk-reactor-9", 12, true);
        turbine = new SteamTurbine("steam-turbine");
        largeTurbine = new SteamTurbine("steam-turbine-4", 4, 360f, 72000f);

        reactor.requirements(Category.power,
            with(IronworksMaterials.siliconCarbide, 120, IronworksItems.steelPlate, 400,
                 IronworksSilicon.quantumCore, 4, IronworksAluminum.aviationAluminum, 80,
                 Items.silicon, 500, Items.metaglass, 200));
        reactor.health = 9000;

        // 12×12 分区堆芯：九个 4×4 分区，额定 3240 L/s，造价显著更高
        zonedReactor.requirements(Category.power,
            with(IronworksMaterials.siliconCarbide, 600, IronworksItems.steelPlate, 2000,
                 IronworksSilicon.quantumCore, 20, IronworksAluminum.aviationAluminum, 400,
                 Items.silicon, 2500, Items.metaglass, 1000));
        zonedReactor.health = 32000;

        turbine.requirements(Category.power,
            with(IronworksItems.steelPlate, 150, IronworksMaterials.siliconCarbide, 40,
                 IronworksAluminum.aviationAluminum, 30, Items.silicon, 200, Items.metaglass, 120));
        turbine.health = 2600;

        largeTurbine.requirements(Category.power,
            with(IronworksItems.steelPlate, 800, IronworksMaterials.siliconCarbide, 200,
                 IronworksSilicon.quantumCore, 16, IronworksAluminum.aviationAluminum, 400,
                 Items.silicon, 1200, Items.metaglass, 600));
        largeTurbine.health = 7000;
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
        /** 是否启用 3×3 分区堆芯（12×12）。旧 5×5 实验堆保持 false。 */
        public boolean zoned = false;
        /** 本方块的水罐/汽包/给水泵/额定通量；旧堆 980，分区堆 3240。 */
        public float waterCap = waterCapacity, steamCap = steamCapacity, wetSteamCap = steamCapacity * 0.5f,
            feedRate = feedwaterRate, ratedFlow = waterPerSecond;

        public RBMKReactor(String name){
            this(name, 5, false);
        }

        public RBMKReactor(String name, int blockSize, boolean zoned){
            super(name);
            this.zoned = zoned;
            size = blockSize;
            rotate = false;
            solid = true;
            update = true;
            hasItems = true;
            // 12×12 分区堆的燃料/乏燃料通道更多，料仓同步放大
            itemCapacity = zoned ? 180 : 40;
            hasLiquids = true;
            if(zoned){
                waterCap = zonedWaterCapacity;
                steamCap = zonedSteamCapacity;
                feedRate = zonedFeedwaterRate;
                ratedFlow = zonedWaterPerSecond;
                wetSteamCap = steamCap * 0.5f;
            }
            // 水罐 + 干汽 + 湿汽共用这个总容量；任一满了都会形成背压。
            liquidCapacity = waterCap + steamCap + wetSteamCap;
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
                    (int)ratedFlow + "/" + StatUnit.perSecond.localized(),
                    IronworksLiquids.steam.color);
                IronworksStats.specRow(table, Liquids.water.uiIcon,
                    Core.bundle.get("ironworks.rbmk.spec.water"),
                    (int)ratedFlow + "/" + StatUnit.perSecond.localized(), Liquids.water.color);
                IronworksStats.specRow(table, Liquids.water.uiIcon,
                    Core.bundle.get("ironworks.rbmk.spec.tank"),
                    (int)waterCap + " / " + (int)steamCap + " L", Color.valueOf("8fd4ff"));
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
                    Mathf.round(b.liquids.get(Liquids.water)), (int)((RBMKReactor)b.block).waterCap),
                () -> Liquids.water.color,
                () -> Mathf.clamp(b.liquids.get(Liquids.water) / ((RBMKReactor)b.block).waterCap)));

            addBar("rbmk-steam", (RBMKReactorBuild b) -> new Bar(
                () -> Core.bundle.format("bar.ironworks-rbmk-steam",
                    Mathf.round(b.liquids.get(IronworksLiquids.steam)), (int)((RBMKReactor)b.block).steamCap),
                () -> IronworksLiquids.steam.color,
                () -> Mathf.clamp(b.liquids.get(IronworksLiquids.steam) / ((RBMKReactor)b.block).steamCap)));

            addBar("rbmk-heat", (RBMKReactorBuild b) -> {
                RBMKReactor r = (RBMKReactor)b.block;
                float limit = r.zoned ? zonedMeltTemp : meltTemp;
                return new Bar(
                    () -> Core.bundle.format("bar.ironworks-rbmk-heat", Mathf.round(b.heat * 100f)),
                    () -> heatColor(b.heat / limit),
                    () -> Mathf.clamp(b.heat / limit));
            });

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

        static Color heatColor(float fraction){
            if(fraction < 0.6f) return Color.valueOf("7fe08a");
            if(fraction < 0.9f) return Color.valueOf("ffd166");
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
            /** 全自动模式的目标功率（1 = 100% 额定；允许 >1，不再有 125% 硬上限）。 */
            public float autoPower = 0.7f;
            /** 全自动功率 PI 控制的积分项（运行时状态）。 */
            public float autoIntegral = 0f;
            /** 中子通量 0..1.25。 */
            public float flux = 0f;
            /** 堆芯温度（归一化，>1.25 熔毁）。 */
            public float heat = 0f;
            /** 氙毒 0..1.5。 */
            public float xenon = 0f, iodine = 0f;
            /** 实际冷却液满足率 0..1（给水量 / 需水量）。 */
            public float flow = 1f;
            /** 强制手动模式下的最高水位线（水罐硬上限）0..1。 */
            public float waterTarget = 1f;
            /** 当前生效的最高水位线 0..1：手动=滑条，半自动/全自动=后台算法（≤90%）。 */
            public float waterLevel = 1f;
            /** 强制手动模式下的最低水位线 0..1；低于它才启动给水泵。 */
            public float waterLowTarget = 0.55f;
            /** 当前生效的最低水位线 0..1：手动=滑条，半自动/全自动=后台算法。 */
            public float waterLowLevel = 0.55f;
            /** 给水泵锁存：低于低线启动，补到高线才停（不写入存档）。 */
            public boolean feedwaterActive = false;
            /** 平滑后的水→汽流量（L/s），面板显示用。 */
            public float steamRate = 0f, wetSteamRate = 0f;
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
            /**
             * SCRAM 闪蒸瞬态剩余秒数（AZ-5 效应：高功率瞬间插棒 → 正反应性尖峰）。
             * 它**不再直接引爆**，只用于蒸汽特效和警报去重；真正的熔毁仍然走堆温积分。
             */
            public float scramBoom = 0f;
            /** 闪蒸后额外的堆芯发热率（每秒）：正反应性尖峰的热冲击，会随时间衰减。 */
            public float promptHeat = 0f;
            /** 最近一次 AZ-5 闪蒸掉的水量（仅调试/测试观测，不写存档）。 */
            public float lastFlash = 0f;
            /** 当前棒位对应的功率上限（%），面板显示用。 */
            public float rodCeilingPct = 0f;
            /** 是否已熔毁（防止重复爆炸）。 */
            public boolean melted = false;
            /** 通知去重位掩码；运行时状态，不写入存档（重载后若工况仍在会重新提醒一次）。 */
            public int alertMask = 0;
            /** 每个通知条件恢复后的静默计时（秒），同样是运行时状态。 */
            public float[] alertQuiet = new float[12];
            /** 手动模式开关：关闭本反应堆的异常广播（含熔毁通知），不影响反应堆物理。 */
            public boolean alertsMuted = false;
            /** SCRAM 锁存：自动模式不会在下一 tick 用目标功率把棒重新拔出来；按复位解除。 */
            public boolean scramLatch = false;
            /** 半自动/全自动的实时保护插棒量（0..1）；按水位、给水、温度趋势等动态计算。 */
            public float protectRods = 0f;
            /** 平滑后的水位变化率（L/s）和堆温变化率（每秒），运行时状态。 */
            public float waterTrend = 0f, heatTrend = 0f;
            private float lastWater = -1f, lastHeat = -1f;

            // ---- 12×12 分区堆芯（3×3 个 4×4 区块）----
            /** 每区独立热/通量/流量/实际棒位/目标棒位/闪蒸热冲击。 */
            public final float[] zoneHeat = new float[9], zoneFlux = new float[9],
                zoneFlow = new float[9], zoneRods = new float[9], zoneTarget = new float[9],
                zonePrompt = new float[9];
            /** 三节点：zoneHeat 燃料芯块温度、zoneClad 包壳温度、zoneCoolant 冷却剂温度。 */
            public final float[] zoneClad = new float[9];
            public final float[] zoneCoolant = new float[9], zoneMeltDamage = new float[9];
            /** 每区当前蒸汽干度与湿蒸汽量（湿汽按公共湿汽包记账）。 */
            public final float[] zoneDryness = new float[9];
            /** 主泵惰转：实际泵速 0..1，断电后按指数衰减。 */
            public float pumpSpeed = 1f;
            /** 堆芯注水泵自己的惰转转速：与给水泵分开，避免公共水罐满时给水泵停转而注水中断。 */
            public float corePumpSpeed = 1f;
            /** 一回路压力（归一化）与水锤/超压损伤积分 0..1。 */
            public float loopPressure = 0.5f, pressureDamage = 0f, waterHammer = 0f;
            /** 堆芯注水开关与注水率 0..1（从水罐注入堆芯，不等于直接往罐里灌水）。 */
            public boolean coreInjectEnabled = true;
            public float coreInjectRate = 1f;
            /** 堆芯是否已经完成首次灌水；之后水罐不会再自动倒灌，必须走注水泵。 */
            boolean corePrimed = false;
            /** 上一 tick 实际注入堆芯的流量（L/s），用于水锤判定与 UI。 */
            public float lastCoreInjection = 0f;
            /** 控制棒卡死：高温导致通道/包壳变形后，目标值还能动，实际棒位动不了。 */
            public boolean rodJammed = false;
            public float jamTimer = 0f;
            /** 保护跳闸锁存：自动/半自动越限后保持全插，直到手动复位或安全重启。 */
            public boolean tripLatched = false;
            public int tripReason = TRIP_NONE;
            /** 分区水汽账面：总和始终与公共水罐/汽包同步；汽轮机仍吃公共汽包，保持管路兼容。 */
            public final float[] zoneWater = new float[9], zoneSteam = new float[9];
            public final float[] zoneWaterCap = new float[9], zoneSteamCap = new float[9];
            public final float[] zoneConverted = new float[9];
            /** 每区本 tick 的理论冷却需求（L），用于把实际转化量折算成真实流量比例。 */
            public final float[] zoneDemand = new float[9];
            /** 全局包壳温度/冷却剂温度（显示用，取最热区）与当前衰变热。 */
            public float cladHeat = 0f, coolantHeat = 0f, decayHeat = 0f;
            /** 平滑通量历史与停堆计时，用于衰变热。 */
            public float fluxHistory = 0f, decayTimer = 0f;
            /** 冷却剂温度显示（K）。 */
            public float coolantK = 300f;
            /** 并联冷却通道的固定节流偏差（0.96..1.00）：公共集管下每区流量略有差异。 */
            public final float[] zoneBias = new float[9];
            /** 手动模式下各区冷却节流阀目标（0..1）：旧存档缺省 1。 */
            public final float[] zoneThrottle = new float[9];
            /** 节流阀实际阀位（0..1），按有限行程追随 zoneThrottle；自动模式目标恒为 1。 */
            public final float[] zoneThrottleActual = new float[9];
            /** 当前选中：-1 = 总控（全堆），0..8 = 单个分区。 */
            public int selectedZone = -1;
            private boolean zonesInit = false;

            @Override
            public float getPowerProduction(){
                return 0f;   // 反应堆不出电
            }

            /** 查询控制棒曲线（测试/UI 用，不改变棒位）。 */
            public float rodCurve(float r){
                return IronworksRBMK.rodEffect(r);
            }

            void resetAutoControl(){
                autoIntegral = 0f;
            }

            /** 手动复位保护跳闸；自动模式回到保守目标，半自动回到较深棒位。 */
            void resetTrip(){
                if(!tripLatched) return;
                tripLatched = false;
                tripReason = TRIP_NONE;
                protectRods = 0f;
                autoIntegral = 0f;
                if(controlMode == MODE_AUTO) autoPower = Math.min(autoPower, 0.60f);
                if(controlMode == MODE_SEMI) targetRods = Math.min(targetRods, 0.50f);
            }

            /** 卡棒维护复位：只有燃料温度已经降到安全值才允许。 */
            void resetRodJam(){
                if(rodJammed && heat < 1.10f && !melted){
                    rodJammed = false;
                    jamTimer = 0f;
                }
            }

            @Override
            public float warmup(){
                return Mathf.clamp(heat);
            }

            public void applyConfig(int v){
                if(((RBMKReactor)block).zoned){
                    applyZonedConfig(v);
                    return;
                }
                // 通知静音只属于强制手动；切回半自动/全自动时立即恢复通知
                if(v == -10){ controlMode = MODE_SEMI; alertsMuted = false; resetAutoControl(); return; }
                if(v == -11){ controlMode = MODE_AUTO; alertsMuted = false; resetAutoControl(); return; }
                if(v == -12){ controlMode = MODE_MANUAL; syncManualWaterLines(); resetAutoControl(); return; }
                if(v == -5){ coreInjectEnabled = !coreInjectEnabled; return; }
                if(v >= 4000){ coreInjectRate = Mathf.clamp((v - 4000) / 100f, 0f, 1f); return; }
                if(v == -1){
                    scramLatch = true;
                    resetAutoControl();
                    // SCRAM：棒全部插进去。高功率时 AZ-5 会先闪蒸冷却水、抬高堆温，
                    // 是否熔毁由后续的堆温积分决定，不再在倒计时结束时直接 detonate()。
                    if(heat > 0.75f && flux > 0.9f && !melted) startScramFlash();
                    targetRods = 1f;
                    return;
                }
                if(v == -2){
                    // 注硼：只把管线「武装」起来，之后才接收硼酸液体（不是免费按钮）
                    boronArmed = true;
                    return;
                }
                if(v == -3){
                    // 手动模式专用：只关本堆的通知，不改任何物理/水量/温度逻辑
                    if(controlMode == MODE_MANUAL) alertsMuted = !alertsMuted;
                    return;
                }
                if(v == -4){ scramLatch = false; resetTrip(); resetAutoControl(); return; }
                if(v == -6){ resetRodJam(); return; }
                if(v >= 2000){
                    // 2000 + 百分比：强制手动的最低水位线（低于它才启动给水泵）
                    waterLowTarget = Mathf.clamp((v - 2000) / 100f, 0f, waterTarget);
                    if(controlMode == MODE_MANUAL) syncManualWaterLines();
                    return;
                }
                if(v >= 1000){
                    // 1000 + 百分比：强制手动的最高水位线（水罐硬上限）
                    waterTarget = Mathf.clamp((v - 1000) / 100f, 0f, 1f);
                    waterLowTarget = Math.min(waterLowTarget, waterTarget);
                    if(controlMode == MODE_MANUAL) syncManualWaterLines();
                    return;
                }
                if(v < 0) return;
                if(controlMode == MODE_AUTO){
                    // 自动模式下滑条设的是目标功率
                    autoPower = Mathf.clamp(v / 100f, 0f, autoPowerMax);
                }else{
                    targetRods = Mathf.clamp(v / 100f, 0f, 1f);
                }
            }

            void applyZonedConfig(int v){
                // 首次操作面板前就把 9 区数组建好，否则第一次 update 会用旧标量覆盖刚下发的目标
                initZones();
                if(v == -10){ controlMode = MODE_SEMI; alertsMuted = false; resetAutoControl(); return; }
                if(v == -11){ controlMode = MODE_AUTO; alertsMuted = false; resetAutoControl(); return; }
                if(v == -12){ controlMode = MODE_MANUAL; syncManualWaterLines(); resetAutoControl(); return; }
                if(v == -30){ selectedZone = -1; syncFocusFromZones(); return; }
                if(v <= -20 && v >= -28){
                    selectedZone = -20 - v;
                    syncFocusFromZones();
                    return;
                }
                if(v == -5){ coreInjectEnabled = !coreInjectEnabled; return; }
                if(v >= 4000){ coreInjectRate = Mathf.clamp((v - 4000) / 100f, 0f, 1f); return; }
                if(v == -1){
                    scramLatch = true;
                    resetAutoControl();
                    float beforeHeat = heat;
                    if(heat > 0.75f && flux > 0.9f && !melted) startScramFlash();
                    // 闪蒸是整堆现象：把全局热冲击均摊到 9 个分区，而不是只留在全局标量里
                    float jump = Math.max(0f, heat - beforeHeat);
                    for(int i = 0; i < 9; i++){
                        zoneTarget[i] = 1f;
                        zoneHeat[i] = Math.min(3f, zoneHeat[i] + jump);
                        if(promptHeat > 0f) zonePrompt[i] = Math.max(zonePrompt[i], promptHeat);
                    }
                    targetRods = 1f;
                    return;
                }
                if(v == -2){ boronArmed = true; return; }
                if(v == -3){ if(controlMode == MODE_MANUAL) alertsMuted = !alertsMuted; return; }
                if(v == -4){ scramLatch = false; resetTrip(); resetAutoControl(); return; }
                if(v == -6){ resetRodJam(); return; }
                if(v >= 3000){
                    // 3000 + 百分比：手动模式的单区冷却节流阀；总控时写入全部 9 区
                    if(controlMode == MODE_MANUAL){
                        float th = Mathf.clamp((v - 3000) / 100f, 0f, 1f);
                        if(selectedZone >= 0) zoneThrottle[selectedZone] = th;
                        else for(int i = 0; i < 9; i++) zoneThrottle[i] = th;
                    }
                    return;
                }
                if(v >= 2000){
                    waterLowTarget = Mathf.clamp((v - 2000) / 100f, 0f, waterTarget);
                    if(controlMode == MODE_MANUAL) syncManualWaterLines();
                    return;
                }
                if(v >= 1000){
                    waterTarget = Mathf.clamp((v - 1000) / 100f, 0f, 1f);
                    waterLowTarget = Math.min(waterLowTarget, waterTarget);
                    if(controlMode == MODE_MANUAL) syncManualWaterLines();
                    return;
                }
                if(v < 0) return;
                if(controlMode == MODE_AUTO){
                    autoPower = Mathf.clamp(v / 100f, 0f, autoPowerMax);
                }else if(controlMode == MODE_MANUAL){
                    float r = Mathf.clamp(v / 100f, 0f, 1f);
                    if(selectedZone >= 0) zoneTarget[selectedZone] = r;
                    else for(int i = 0; i < 9; i++) zoneTarget[i] = r;
                    syncFocusFromZones();
                }else{
                    targetRods = Mathf.clamp(v / 100f, 0f, 1f);
                }
            }

            /**
             * AZ-5 正反应性尖峰的真实后果：插棒瞬间把堆芯里的过冷水闪蒸成蒸汽。
             *
             * <p>闪蒸不是「倒计时引爆」：它会
             * <ol>
             *   <li>把相当一部分冷却水转成蒸汽（汽包可能超过 2000 L 的软上限）；</li>
             *   <li>让有效给水/排汽暴跌，空泡正反应性把通量顶高；</li>
             *   <li>给堆芯一个额外的热冲击（{@link #promptHeat}），随后由温度积分自然升温；</li>
             * </ol>
             * 如果给水泵还能补水、汽路还能排汽，反应堆有机会挺过去；否则堆温越过
             * {@link #meltTemp} 后，仍由原有的 1 秒熔毁确认逻辑爆炸。
             */
            void startScramFlash(){
                if(melted || scramBoom > 0f) return;
                boolean zoned = ((RBMKReactor)block).zoned;
                if(zoned){
                    initZones();
                    float core = 0f;
                    for(int i = 0; i < 9; i++) core += zoneWater[i];
                    if(core < 100f) syncZoneWater();
                }
                float water = 0f;
                if(zoned){
                    for(int i = 0; i < 9; i++) water += zoneWater[i];
                }else{
                    water = liquids.get(Liquids.water);
                }
                if(water < 100f) return;

                float waterFrac = zoned
                    ? water / Math.max(waterTotalCap(), 1f)
                    : water / waterCap();
                // 水越满、通量越高、堆越热，闪蒸越剧烈；上限 75%，避免一次把水全蒸干
                float severity = Mathf.clamp(0.30f + flux * 0.25f + heat * 0.12f + waterFrac * 0.18f,
                    0.30f, 0.75f);
                float flash = water * severity;
                float steamBefore = liquids.get(IronworksLiquids.steam);

                // 1:1 水→汽。蒸汽可以短暂超过汽包容积，这正是「憋压」的来源；
                // 多出来的蒸汽会由 dumpLiquid 往相邻汽路排，排不掉就留在堆里。
                if(zoned){
                    // 闪蒸的是堆芯内水，而不是水罐里的补给水。
                    float remain = flash;
                    for(int i = 0; i < 9 && remain > 0.001f; i++){
                        float take = Math.min(zoneWater[i], flash * zoneWater[i] / Math.max(water, 1f));
                        take = Math.min(take, remain);
                        zoneWater[i] = Math.max(0f, zoneWater[i] - take);
                        remain -= take;
                    }
                }else{
                    liquids.remove(Liquids.water, flash);
                }
                liquids.add(IronworksLiquids.steam, flash);
                float flashed = liquids.get(IronworksLiquids.steam) - steamBefore;

                // 正反应性尖峰的热冲击：不是直接加 40% 堆温，而是一段持续数秒的额外发热率，
                // 让温度曲线看起来像「瞬跳 + 快速爬升」，也给操作员留出补救窗口。
                promptHeat = Math.max(promptHeat, 0.16f + severity * 0.18f);
                heat = Math.min(((RBMKReactor)block).zoned ? 3f : 2f,
                    heat + 0.02f + severity * 0.06f);
                scramBoom = 4f;

                if(!headless){
                    for(int i = 0; i < 8; i++){
                        Fx.steam.at(x + Mathf.range(size * 4f), y + Mathf.range(size * 4f));
                    }
                    Fx.reactorsmoke.at(x, y);
                }
                // 仅用于调试/测试观测：闪蒸掉的水量（运行时状态，不写存档）
                lastFlash = flashed;
            }

            /** 本方块的水罐容量（旧 5×5=10000 L；12×12 分区堆=32400 L）。 */
            float waterCap(){ return ((RBMKReactor)block).waterCap; }
            float waterTotalCap(){ return zonedWaterCapacity; }
            /** 温度加速蒸发：冷却剂超过 0.5 后最高 1.6×。 */
            float evapFactor(int zone){
                return Mathf.clamp(1f + evapK * Math.max(0f, zoneCoolant[zone] - evapRef), 1f, evapMax);
            }

            void initZoneCaps(){
                for(int i = 0; i < 9; i++){
                    zoneWaterCap[i] = zonedWaterPerZoneCapacity;
                    zoneSteamCap[i] = zonedSteamPerZoneCapacity;
                }
            }

            /** 把公共水罐的当前总量按容量×固定偏差分到 9 区（只做账面分摊，物理水仍走公共罐）。 */
            void distributeZone(float[] values, float[] caps, float amount){
                for(int pass = 0; pass < 9 && amount > 0.0001f; pass++){
                    float totalW = 0f;
                    for(int i = 0; i < 9; i++){
                        if(caps[i] - values[i] > 0.0001f) totalW += caps[i] * zoneBias[i];
                    }
                    if(totalW <= 0.0001f) break;
                    float remaining = amount;
                    for(int i = 0; i < 9; i++){
                        float room = caps[i] - values[i];
                        if(room <= 0.0001f) continue;
                        float add = Math.min(room, amount * (caps[i] * zoneBias[i]) / totalW);
                        values[i] += add;
                        remaining -= add;
                    }
                    amount = remaining;
                }
            }

            /**
             * 堆芯内水与公共水罐分离：只在首次/读档时把公共水按容量分到堆芯；
             * 之后由堆芯注水泵按注水开关/速率补充，不再每 tick 自动搬水。
             */
            void syncZoneWater(){
                initZoneCaps();
                if(corePrimed) return;
                corePrimed = true;
                float sum = 0f;
                for(int i = 0; i < 9; i++) sum += zoneWater[i];
                if(sum > 0.0001f) return;
                float total = liquids.get(Liquids.water);
                if(total > 0.0001f){
                    distributeZone(zoneWater, zoneWaterCap, total);
                    float filled = 0f;
                    for(int i = 0; i < 9; i++) filled += zoneWater[i];
                    // 第一次把水罐水搬进堆芯时，罐里要同步扣掉，不能凭空多一份水。
                    liquids.remove(Liquids.water, Math.min(total, filled));
                }
            }

            /** 注水泵自己的惰转：公共给水泵因水罐满而停转时，注水泵仍可保持转速。 */
            void updateCorePump(float dt){
                boolean running = coreInjectEnabled && !melted && liquids.get(Liquids.water) > 0.001f;
                float target = running ? 1f : 0f;
                float alpha = target > corePumpSpeed
                    ? 1f - (float)Math.exp(-dt / 2f)
                    : 1f - (float)Math.exp(-dt / pumpCoastTau);
                corePumpSpeed += (target - corePumpSpeed) * Mathf.clamp(alpha, 0f, 1f);
            }

            /** 从水罐向堆芯注水；注水率决定流量，注水泵惰转时会一起变慢。 */
            void injectCoreWater(float dt){
                lastCoreInjection = 0f;
                if(!coreInjectEnabled || dt <= 0f) return;
                float available = liquids.get(Liquids.water);
                if(available <= 0.001f) return;
                float budget = Math.min(available, coreInjectMax() * coreInjectRate * corePumpSpeed * dt);
                float requested = budget;
                for(int pass = 0; pass < 9 && budget > 0.001f; pass++){
                    float totalRoom = 0f;
                    for(int i = 0; i < 9; i++) totalRoom += Math.max(0f, zoneWaterCap[i] - zoneWater[i]);
                    if(totalRoom <= 0.001f) break;
                    float remaining = budget;
                    for(int i = 0; i < 9; i++){
                        float room = Math.max(0f, zoneWaterCap[i] - zoneWater[i]);
                        if(room <= 0.001f) continue;
                        float add = Math.min(room, budget * room / totalRoom);
                        zoneWater[i] += add;
                        remaining -= add;
                    }
                    float moved = budget - remaining;
                    if(moved <= 0.0001f) break;
                    liquids.remove(Liquids.water, moved);
                    budget = remaining;
                }
                lastCoreInjection = dt <= 0f ? 0f : (requested - budget) / dt;
            }

            void syncZoneSteam(){
                initZoneCaps();
                float total = liquids.get(IronworksLiquids.steam);
                float sum = 0f;
                for(int i = 0; i < 9; i++) sum += zoneSteam[i];
                if(total <= 0.0001f){
                    for(int i = 0; i < 9; i++) zoneSteam[i] = 0f;
                }else if(sum <= 0.0001f){
                    distributeZone(zoneSteam, zoneSteamCap, total);
                }else if(total > sum + 0.001f){
                    distributeZone(zoneSteam, zoneSteamCap, total - sum);
                }else if(total < sum - 0.001f){
                    float scale = total / sum;
                    for(int i = 0; i < 9; i++) zoneSteam[i] *= scale;
                }
            }

            float zoneWaterFrac(int zone){
                return Mathf.clamp(zoneWater[zone] / Math.max(zoneWaterCap[zone], 1f), 0f, 1f);
            }
            /** 水位越低，蒸汽夹带水滴越多；水位 1.0 → 干度 1.0。 */
            float steamDryness(int zone){
                return Mathf.clamp(0.45f + 0.55f * zoneWaterFrac(zone), 0.35f, 1f);
            }
            /** 本方块的汽包容量（旧 5×5=2000 L；12×12 分区堆=6480 L）。 */
            float steamCap(){ return ((RBMKReactor)block).steamCap; }
            /** 本方块的给水泵最大速率（旧 5×5=1400 L/s；12×12 分区堆=4700 L/s）。 */
            float feedRate(){ return ((RBMKReactor)block).feedRate; }
            /** 本方块额定总通量（旧 980 L/s；12×12 分区堆 3240 L/s）。 */
            float ratedFlow(){ return ((RBMKReactor)block).ratedFlow; }
            /** 堆芯注水泵最大速率（L/s）。 */
            float coreInjectMax(){ return ratedFlow() * IronworksRBMK.coreInjectMax; }
            /** 公共湿蒸汽包容量（L）。 */
            float wetSteamCap(){ return ((RBMKReactor)block).wetSteamCap; }

            /** 当前允许的最高水位（L）：始终取有行程的执行器位置，滑条只是目标。 */
            float waterLimit(){
                return waterLevel * waterCap();
            }

            /** 手动高线以上的溢流：按有限排水速率排出，不再瞬间把整罐水砍到新线。 */
            void enforceWaterLimit(float dt){
                if(controlMode != MODE_MANUAL || dt <= 0f) return;
                float cap = waterLevel * waterCap();
                float water = liquids.get(Liquids.water);
                if(water > cap + 0.01f){
                    liquids.remove(Liquids.water, Math.min(water - cap, overflowRate * dt));
                }
            }

            /** 只修正目标线之间的关系；真正的执行器位置由 updateWaterSetpoints 按行程追随。 */
            void syncManualWaterLines(){
                waterLowTarget = Math.min(waterLowTarget, waterTarget);
            }

            /** 水位设定值执行器：手动追随滑条，自动追随算法目标；带 10 秒有限行程。 */
            void updateWaterSetpoints(float dt, float highTarget, float lowTarget){
                float step = rodStep(dt, waterSetpointTravel);
                waterLevel = Mathf.approach(waterLevel, highTarget, step);
                waterLowLevel = Mathf.approach(waterLowLevel, lowTarget, step);
                if(waterLowLevel > waterLevel - 0.005f){
                    waterLowLevel = Math.max(0f, waterLevel - 0.005f);
                }
                enforceWaterLimit(dt);
            }

            /**
             * 半自动/全自动的实时保护：不是等到温度 100% 才动作，而是同时看
             * 水位余量、水位下降速度、给水实际流量、堆温及其变化率、汽包压力。
             *
             * <p>核心目标是「在水烧干之前先把功率降下来」：水位低于低线
             * 约 25% 时开始插棒；水位继续下降、流量掉、温度上升都会加权。
             * 保护量 {@link #protectRods} 是杆位下限，不覆盖用户/自动设定的目标，
             * 风险解除后缓慢回落。
             */
            void updateProtection(float dt, boolean hasFuel){
                if(dt <= 0.0001f) return;
                boolean zoned = ((RBMKReactor)block).zoned;
                // 分区堆的给水保护必须看堆芯水位；公共水罐已经和堆芯拆开，看罐子会永远判定无水。
                float water = 0f, waterScale = 1f;
                if(zoned){
                    for(int i = 0; i < 9; i++) water += zoneWater[i];
                    waterScale = Math.max(1f, waterTotalCap());
                }else{
                    water = liquids.get(Liquids.water);
                    waterScale = Math.max(1f, waterCap());
                }
                if(lastWater < 0f){
                    lastWater = water;
                    lastHeat = heat;
                    return;
                }
                waterTrend = Mathf.lerpDelta(waterTrend, (water - lastWater) / dt, 0.15f);
                heatTrend = Mathf.lerpDelta(heatTrend, (heat - lastHeat) / dt, 0.15f);
                lastWater = water;
                lastHeat = heat;

                if(controlMode == MODE_MANUAL || !hasFuel){
                    protectRods = 0f;
                    return;
                }

                float waterFrac = water / waterScale;
                // 保护必须看最坏分区，不能被 9 区平均值掩盖。
                float worstFlow = flow;
                if(zoned){
                    for(int i = 0; i < 9; i++){
                        waterFrac = Math.min(waterFrac, zoneWaterFrac(i));
                        worstFlow = Math.min(worstFlow, zoneFlow[i]);
                    }
                }
                float low = Math.max(waterLowLevel, 0.10f);
                // 低于低线的 75% 才开始硬保护；正常「低线启泵→高线」循环不会误触发
                float trigger = Math.max(0.12f, low * 0.75f);
                float below = Mathf.clamp((trigger - waterFrac) / Math.max(trigger * 0.75f, 0.05f), 0f, 1f);

                // 附近有没有可抽的水源：没有水源时不能等「低线」才动作，
                // 按当前库存/消耗算剩余时间，提前降功率。
                boolean feedwaterAvailable = false;
                for(Building other : proximity){
                    if(other != null && other != this && other.team == team
                        && other.liquids != null && other.liquids.get(Liquids.water) > 0.5f){
                        feedwaterAvailable = true;
                        break;
                    }
                }

                float demand = Math.max(flux, 0f) * ratedFlow();
                float runway = demand > 1f ? water / demand : 99f;
                float runwayRisk = Mathf.clamp((9f - runway) / 6f, 0f, 1f);
                float waterRisk = below;
                if(!feedwaterAvailable){
                    // 断水时用剩余时间提前介入；有水源时只在低于保护线后参考 runway
                    waterRisk = Math.max(waterRisk, runwayRisk);
                }else if(below > 0f){
                    waterRisk = Math.max(waterRisk, runwayRisk);
                }

                // 温度趋势往前看 2.5 秒：刚出现上升趋势就提前插棒，不等熔毁线。
                // 分区堆用双节点：燃料温度是主判据，冷却剂温度乘 0.85 作为辅助。
                float futureHeat = heat + Math.max(heatTrend, 0f) * 2.5f;
                float heatRisk;
                if(zoned){
                    float thermal = Math.max(futureHeat, coolantHeat * 0.85f);
                    heatRisk = Mathf.clamp((thermal - 1.15f) / 0.55f, 0f, 1f);
                }else{
                    // 旧 5×5 正常满功率平衡温度约 0.8；保护点靠近 125% 熔毁线，
                    // 不能在高功率刚起来时就把棒压回去。
                    heatRisk = Mathf.clamp((futureHeat - 1.05f) / 0.15f, 0f, 1f);
                }

                float flowRisk = (waterFrac < trigger || heat > (zoned ? 1.4f : 0.9f))
                    ? Mathf.clamp((0.9f - worstFlow) / 0.4f, 0f, 1f) : 0f;
                float steamFrac = Mathf.clamp(liquids.get(IronworksLiquids.steam) / steamCap(), 0f, 1f);
                float steamRisk = Mathf.clamp((steamFrac - 0.8f) / 0.15f, 0f, 1f);

                float risk = Math.max(waterRisk, Math.max(heatRisk,
                    Math.max(flowRisk * 0.9f, steamRisk * 0.85f)));
                float hotLimit = zoned ? 1.8f : meltTemp - 0.05f;
                if(waterFrac <= 0.02f) risk = Math.max(risk, 0.9f);
                if(heat >= hotLimit) risk = 1f;

                // 越限即跳闸锁存：保护不能只是杆位下限，否则风险一降就自动回到高目标。
                if(!tripLatched && hasFuel && controlMode != MODE_MANUAL){
                    int reason = TRIP_NONE;
                    if(heat >= hotLimit - 0.02f){
                        reason = TRIP_HIGH_HEAT;
                    }else if(waterFrac <= 0.02f){
                        reason = TRIP_LOW_CORE;
                    }else if(loopPressure >= pressureLimit || pressureDamage >= 0.45f){
                        reason = TRIP_HIGH_PRESSURE;
                    }else if(steamFrac >= 0.97f){
                        reason = TRIP_HIGH_STEAM;
                    }else if(heat > 0.8f && worstFlow < 0.05f){
                        reason = TRIP_LOW_FLOW;
                    }else if(risk >= 0.92f){
                        reason = TRIP_LOW_CORE;
                    }
                    if(reason != TRIP_NONE){
                        tripLatched = true;
                        tripReason = reason;
                        autoIntegral = 0f;
                        protectRods = 1f;
                    }
                }
                if(tripLatched) protectRods = 1f;
                float rate = risk > protectRods ? 0.10f : 0.02f;
                if(!tripLatched) protectRods = Mathf.lerpDelta(protectRods, risk, rate);
                if(heat >= hotLimit) protectRods = Math.max(protectRods, 0.98f);
            }

            void initZoneBias(){
                for(int i = 0; i < 9; i++){
                    if(zoneBias[i] <= 0f){
                        // 固定但不对称的通道节流；平均值约 96%，不随机、可复现
                        zoneBias[i] = 0.96f + ((i * 7) % 9) * 0.005f;
                    }
                }
            }

            void initZones(){
                initZoneBias();
                if(zonesInit) return;
                initZoneCaps();
                for(int i = 0; i < 9; i++){
                    zoneHeat[i] = heat;
                    zoneClad[i] = Math.max(0f, heat * 0.9f);
                    zoneCoolant[i] = Math.max(0f, heat * 0.8f);
                    zoneDryness[i] = 1f;
                    zoneMeltDamage[i] = 0f;
                    zoneFlux[i] = 0f;
                    zoneFlow[i] = Mathf.clamp(Math.max(flow, 0.01f) * zoneBias[i], 0.005f, 1f);
                    zoneRods[i] = rods;
                    zoneTarget[i] = targetRods;
                    zonePrompt[i] = 0f;
                    zoneThrottle[i] = 1f;
                    zoneThrottleActual[i] = 1f;
                    zoneWater[i] = 0f;
                    zoneSteam[i] = 0f;
                    zoneConverted[i] = 0f;
                }
                fluxHistory = flux;
                decayTimer = 0f;
                decayHeat = 0f;
                zonesInit = true;
                syncZoneWater();
                syncZoneSteam();
            }

            /** 把全局显示量同步成当前选中的区（或总控平均），避免 selectedZone=-1 越界。 */
            void syncFocusFromZones(){
                // 非手动模式没有单区目标：选择只影响面板显示，不准改半自动的公共目标
                if(controlMode != MODE_MANUAL) return;
                if(selectedZone >= 0 && selectedZone < 9){
                    targetRods = zoneTarget[selectedZone];
                    rods = zoneRods[selectedZone];
                }else{
                    float t = 0f, r = 0f;
                    for(int i = 0; i < 9; i++){
                        t += zoneTarget[i];
                        r += zoneRods[i];
                    }
                    targetRods = t / 9f;
                    rods = r / 9f;
                }
            }

            /**
             * 12×12 分区堆芯：3×3 个 4×4 区块，每区独立热/通量/棒位/流量。
             * 水/汽分区记账、独立转化；公共水罐/汽包仍作为物理缓冲和原版管路接口。
             * 热工为燃料/冷却剂双节点，熔毁为逐区损伤积分。
             */
            /** 高温持续导致通道变形/包壳膨胀，控制棒会卡死；SCRAM 也只能眼睁睁看着。 */
            void updateRodJam(float dt, float meltGuard){
                if(rodJammed) return;
                if(heat > meltGuard) jamTimer += dt;
                else jamTimer = Math.max(0f, jamTimer - dt * 2f);
                if(jamTimer > 2f) rodJammed = true;
            }

            void updateZoned(float dt, float second, boolean hasFuel){
                initZones();
                updateRodJam(dt, 2.20f);
                syncZoneSteam();

                // 节流阀也是执行器：手动追随滑条，自动追随 100% 开度。
                float valveStep = rodStep(dt, throttleTravel);
                for(int i = 0; i < 9; i++){
                    float throttleTarget = controlMode == MODE_MANUAL ? zoneThrottle[i] : 1f;
                    zoneThrottleActual[i] = Mathf.approach(zoneThrottleActual[i], throttleTarget, valveStep);
                }

                // 水位线仍是整堆公共目标；设定值同样走有限行程执行器。
                if(controlMode == MODE_MANUAL){
                    updateWaterSetpoints(dt, waterTarget, waterLowTarget);
                }else{
                    float autoHigh = Mathf.clamp(0.55f + 0.35f * Math.min(flux, 1f), 0.55f, 0.9f);
                    // 冷却剂热但燃料不热，说明传热差/水位低：优先加泵，不要先插棒。
                    if(coolantHeat > 0.9f && heat < 1.2f) autoHigh = Math.min(autoHigh + 0.05f, 0.95f);
                    float autoLow = Mathf.clamp(autoHigh - 0.2f, 0.25f, autoHigh - 0.05f);
                    updateWaterSetpoints(dt, autoHigh, autoLow);
                }

                // 给保护算法看的全局量 = 最热燃料区 + 平均流量/通量 + 最热冷却剂
                float maxHeat = 0f, maxCoolant = 0f, sumFlux = 0f, sumFlow = 0f;
                for(int i = 0; i < 9; i++){
                    maxHeat = Math.max(maxHeat, zoneHeat[i]);
                    maxCoolant = Math.max(maxCoolant, zoneCoolant[i]);
                    sumFlux += zoneFlux[i];
                    sumFlow += zoneFlow[i];
                }
                heat = maxHeat;
                coolantHeat = maxCoolant;
                flux = sumFlux / 9f;
                flow = sumFlow / 9f;
                updateProtection(dt, hasFuel);

                // —— 控制棒：手动按区分目标；自动/半自动下发同一个公共目标 ——
                if(controlMode == MODE_MANUAL){
                    float travel = scramLatch ? rodTravelScram : rodTravelNormal;
                    if(!rodJammed){
                        for(int i = 0; i < 9; i++){
                            zoneRods[i] = Mathf.approach(zoneRods[i], zoneTarget[i], rodStep(dt, travel));
                        }
                    }
                    syncFocusFromZones();
                }else{
                    float common;
                    if(controlMode == MODE_SEMI){
                        common = (scramLatch || tripLatched) ? 1f : Math.max(targetRods, protectRods);
                    }else{
                        float steamFrac = Mathf.clamp(liquids.get(IronworksLiquids.steam) / steamCap(), 0f, 1f);
                        // 给水能力看最干的分区，避免平均值掩盖单区失水。
                        float coreWorst = 1f;
                        for(int z = 0; z < 9; z++) coreWorst = Math.min(coreWorst, zoneWaterFrac(z));
                        float steamFactor = Mathf.clamp(1f - (steamFrac - 0.5f) / 0.4f, 0f, 1f);
                        float waterFactor = Mathf.clamp((coreWorst - 0.1f) / 0.2f, 0f, 1f);
                        // 只在接近 200% 熔毁线时收功率，正常高功率段不提前压棒。
                        float heatFactor = Mathf.clamp((zonedMeltTemp + 0.05f - heat) / 0.15f, 0f, 1f);
                        float effective = Math.max(0f, autoPower) * Math.min(steamFactor, Math.min(waterFactor, heatFactor));
                        float poison = Mathf.clamp(1f - xenon * 0.65f - boron * 1.2f, 0.05f, 1f);
                        float voidFactor = 1f + (1f - flow) * 1.2f;
                        float doppler = dopplerFactor(heat);
                        // 反算要同时考虑多普勒和棒尖曲线；再加 PI 修正消除稳态误差。
                        float feedRods = autoRodsFor(effective, poison, doppler, voidFactor);
                        float error = effective - flux;
                        if(tripLatched){
                            autoIntegral = 0f;
                        }else if(Math.abs(error) < 0.25f){
                            // 条件积分：大误差期间冻结积分，避免 999% 目标把积分顶到正饱和。
                            autoIntegral = Mathf.clamp(autoIntegral + error * dt * 0.05f, -0.35f, 0.35f);
                        }
                        common = Mathf.clamp(feedRods - error * 0.25f - autoIntegral, 0f, 1f);
                    }
                    if(scramLatch || tripLatched) common = 1f;
                    common = Math.max(common, protectRods);
                    for(int i = 0; i < 9; i++){
                        zoneTarget[i] = common;
                        float travel;
                        if(scramLatch){
                            travel = rodTravelScram;
                        }else if(common > zoneRods[i] && (controlMode == MODE_AUTO || protectRods > targetRods)){
                            travel = rodTravelAuto;
                        }else{
                            travel = rodTravelNormal;
                        }
                        if(!rodJammed) zoneRods[i] = Mathf.approach(zoneRods[i], common, rodStep(dt, travel));
                    }
                    // 半自动的 targetRods 是操作员目标，不能被保护量棘轮式抬高；只有全自动才写回计算值
                    if(controlMode == MODE_AUTO) targetRods = common;
                    float avg = 0f;
                    for(int i = 0; i < 9; i++) avg += zoneRods[i];
                    rods = avg / 9f;
                }

                // 干汽/湿汽都走公共汽包，但湿汽进汽轮机会造成水锤
                float steamBefore = liquids.get(IronworksLiquids.steam);
                if(steamBefore > 0.001f || liquids.get(IronworksLiquids.wetSteam) > 0.001f){
                    for(int d = 0; d < 4; d++){
                        if(d == waterSide) continue;
                        if(steamBefore > 0.001f) dumpLiquid(IronworksLiquids.steam, 2f, d);
                        if(liquids.get(IronworksLiquids.wetSteam) > 0.001f) dumpLiquid(IronworksLiquids.wetSteam, 2f, d);
                    }
                }
                pullFeedwater(dt);
                updateCorePump(dt);
                injectCoreWater(dt);
                syncZoneSteam();

                // —— 分区通量：棒尖 + 多普勒 + 毒物 + 空泡 ——
                float poison = Mathf.clamp(1f - xenon * 0.65f - boron * 1.2f, 0f, 1f);
                for(int i = 0; i < 9; i++){
                    float rodFactor = rodEffect(zoneRods[i]) * (hasFuel ? 1f : 0f);
                    float doppler = dopplerFactor(zoneHeat[i]);
                    float target = fluxMax * rodFactor * poison * doppler;
                    if(poisoned) target = 0f;
                    target *= 1f + (1f - zoneFlow[i]) * 1.2f;
                    // 不再按模式截成 100%/125%；热工和给水能力自己会限制它。
                    target = Math.max(0f, target);
                    zoneFlux[i] = Mathf.lerpDelta(zoneFlux[i], target, 0.008f);
                }

                // —— 分区水→汽：每区独立转化，不跨区搬汽；公共汽包只做管路出口 ——
                float globalSteamRoom = steamCap() - liquids.get(IronworksLiquids.steam);
                float globalWetRoom = Math.max(0f, wetSteamCap() - liquids.get(IronworksLiquids.wetSteam));
                float convertedTotal = 0f;
                float wetTotal = 0f;
                for(int i = 0; i < 9; i++){
                    // 理论冷却需求含蒸发加速；实际阀位限制可用流量。
                    zoneDemand[i] = Math.max(zoneFlux[i], 0f) * zonedWaterPerZone * evapFactor(i) * dt;
                    float roomSteam = Math.max(0f, zoneSteamCap[i] - zoneSteam[i]);
                    zoneDryness[i] = steamDryness(i);
                    // 先按干汽空间与湿汽总量粗分；后面再按公共湿汽包统一缩放。
                    float maxByDry = zoneDryness[i] > 0.01f ? roomSteam / zoneDryness[i] : roomSteam;
                    zoneConverted[i] = Math.max(0f, Math.min(zoneDemand[i] * zoneThrottleActual[i],
                        Math.min(zoneWater[i], maxByDry)));
                    convertedTotal += zoneConverted[i];
                    wetTotal += zoneConverted[i] * (1f - zoneDryness[i]);
                }
                if(wetTotal > globalWetRoom && wetTotal > 0.0001f){
                    float scale = globalWetRoom / wetTotal;
                    for(int i = 0; i < 9; i++) zoneConverted[i] *= scale;
                    convertedTotal *= scale;
                    wetTotal = globalWetRoom;
                }

                // 公共水罐/汽包仍可能有全局上限；按比例缩放，保证账面收支一致。
                if(convertedTotal > 0.0001f){
                    float scale = 1f;
                    // 分区堆用 zoneWater 自己记账；公共水罐已经和堆芯拆开，不能再拿罐里水位卡转化。
                    if(globalSteamRoom < convertedTotal * scale) scale = Math.min(scale, globalSteamRoom / convertedTotal);
                    // 分区水在 zoneWater 里，公共建筑总液体空间不能作为堆芯转化的硬上限。
                    // （水罐满时 LiquidKit.totalSpace 会归零，否则堆芯明明有水也蒸发不出来。）
                    scale = Mathf.clamp(scale, 0f, 1f);
                    if(scale < 0.9999f){
                        for(int i = 0; i < 9; i++) zoneConverted[i] *= scale;
                        convertedTotal *= scale;
                    }

                    float dryTotal = 0f;
                    for(int i = 0; i < 9; i++){
                        float c = zoneConverted[i];
                        if(c <= 0.00001f) continue;
                        float dry = c * zoneDryness[i];
                        float wet = c - dry;
                        zoneWater[i] = Math.max(0f, zoneWater[i] - c);
                        zoneSteam[i] = Math.min(zoneSteamCap[i], zoneSteam[i] + dry);
                        dryTotal += dry;
                        if(wet > 0.00001f) liquids.add(IronworksLiquids.wetSteam, wet);
                    }
                    if(dryTotal > 0.0001f){
                        // 堆芯水来自 zoneWater；公共水罐只通过注水泵补充，不能被二次扣除。
                        liquids.add(IronworksLiquids.steam, dryTotal);
                    }
                }else{
                    for(int i = 0; i < 9; i++) zoneConverted[i] = 0f;
                }

                // 真实流量 = 实际转化量 / 理论需求（都含水/汽/阀位限制），不再拿节流阀当流量。
                for(int i = 0; i < 9; i++){
                    boolean steamFull = zoneSteamCap[i] <= 0.0001f
                        || zoneSteam[i] >= zoneSteamCap[i] - 0.001f
                        || globalSteamRoom <= 0.001f
                        || liquids.get(IronworksLiquids.wetSteam) >= wetSteamCap() - 0.001f;
                    float flowTarget;
                    if(zoneWaterFrac(i) <= 0.001f && zoneWater[i] <= 0.01f){
                        flowTarget = 0f;
                    }else if(zoneDemand[i] > 0.00001f){
                        flowTarget = Mathf.clamp(zoneConverted[i] / zoneDemand[i], 0f, 1f);
                    }else{
                        flowTarget = steamFull ? 0f : zoneThrottleActual[i];
                    }
                    zoneFlow[i] = Mathf.lerpDelta(zoneFlow[i], flowTarget, 0.15f);
                }

                float avgFlux = 0f, avgFlow = 0f;
                for(int i = 0; i < 9; i++){
                    avgFlux += zoneFlux[i];
                    avgFlow += zoneFlow[i];
                }
                flux = avgFlux / 9f;
                flow = avgFlow / 9f;
                float instRate = dt <= 0.00001f ? 0f : convertedTotal / dt;
                steamRate = Mathf.lerpDelta(steamRate, instRate, 0.03f);
                wetSteamRate = Mathf.lerpDelta(wetSteamRate,
                    dt <= 0.00001f ? 0f : wetTotal / dt, 0.03f);

                // —— 衰变热：停堆后裂变产物仍以约 7% 额定功率放热 ——
                fluxHistory = Mathf.lerpDelta(fluxHistory, flux, 0.01f);
                if(flux < 0.01f) decayTimer += dt;
                else if(flux > 0.05f) decayTimer = 0f;
                decayHeat = decayHeatFraction * fluxHistory
                    * (float)Math.exp(-decayTimer / decayHeatTau);

                // —— 双节点热积分：燃料节点 → 冷却剂节点 → 冷却剂损失 ——
                for(int i = 0; i < 9; i++){
                    float zoneWaterFrac = zoneWaterFrac(i);
                    float heatGen = zoneFlux[i] * 0.14f + zonePrompt[i] + decayHeat / 9f;
                    // 三节点：燃料芯块 -> 包壳 -> 冷却剂。芯块热容大、包壳传热慢，
                    // 所以停堆后燃料温度仍会顶一段时间，而不是跟着通量瞬间掉下去。
                    float fuelToClad = fuelToCladK * Math.max(0f, zoneHeat[i] - zoneClad[i]);
                    float cladToCoolant = cladToCoolantK * zoneWaterFrac
                        * Math.max(0f, zoneClad[i] - zoneCoolant[i]);
                    float coolingLoss = zoneFlow[i] * zoneWaterFrac
                        * (0.06f + 0.09f * zoneCoolant[i]) + 0.01f;

                    zoneHeat[i] = Mathf.clamp(zoneHeat[i]
                        + (heatGen - fuelToClad) / fuelHeatCapacity * dt, 0f, 3f);
                    zoneClad[i] = Mathf.clamp(zoneClad[i]
                        + (fuelToClad - cladToCoolant) / cladHeatCapacity * dt, 0f, 2.5f);
                    zoneCoolant[i] = Mathf.clamp(zoneCoolant[i] + (cladToCoolant - coolingLoss) * dt, 0f, 1.5f);
                    zonePrompt[i] = Math.max(0f, zonePrompt[i] - dt * 0.22f);
                }
                // 任意一个分区熔毁 → 整堆熔毁；全局表显示最热燃料区
                heat = 0f;
                cladHeat = 0f;
                coolantHeat = 0f;
                for(int i = 0; i < 9; i++){
                    heat = Math.max(heat, zoneHeat[i]);
                    cladHeat = Math.max(cladHeat, zoneClad[i]);
                    coolantHeat = Math.max(coolantHeat, zoneCoolant[i]);
                }
                tempK = 280f + heat * 800f;
                coolantK = 280f + coolantHeat * 80f;

                // 逐区熔毁损伤：燃料温度 >= 2.0 才累积；损伤可恢复，任意一区到 1 即爆。
                boolean zoneMelted = false;
                for(int i = 0; i < 9; i++){
                    if(zoneHeat[i] >= zonedMeltTemp){
                        zoneMeltDamage[i] += (zoneHeat[i] - zonedMeltTemp + 0.10f) * meltRate * dt;
                    }else{
                        zoneMeltDamage[i] -= meltRecover * dt;
                    }
                    zoneMeltDamage[i] = Mathf.clamp(zoneMeltDamage[i], 0f, 1f);
                    if(zoneMeltDamage[i] >= 1f) zoneMelted = true;
                }
                if(zoneMelted && !melted) detonate();
                if(melted) return;

                // —— 一回路压力与水锤：空泡/低堆芯水位升压；冷水中击高温低水位堆芯会形成压力尖峰 ——
                float avgCore = 0f, steamFrac = Mathf.clamp(liquids.get(IronworksLiquids.steam) / steamCap(), 0f, 1f);
                for(int i = 0; i < 9; i++) avgCore += zoneWaterFrac(i);
                avgCore /= 9f;
                float pressureTarget = 0.45f + (1f - avgCore) * 0.80f + coolantHeat * 0.25f + steamFrac * 0.15f;
                loopPressure = Mathf.lerpDelta(loopPressure, pressureTarget, 0.03f);
                if(lastCoreInjection > 1f && heat > 1.0f && avgCore < 0.45f){
                    float shock = Mathf.clamp((heat - 0.8f) * (0.45f - avgCore)
                        * (lastCoreInjection / Math.max(ratedFlow(), 1f)) * 0.35f, 0f, 0.5f);
                    waterHammer = Mathf.clamp(waterHammer + shock, 0f, 1f);
                    pressureDamage = Mathf.clamp(pressureDamage + shock * 0.015f * dt * 60f, 0f, 1f);
                    loopPressure = Math.min(2.5f, loopPressure + shock * 0.35f);
                }
                waterHammer = Math.max(0f, waterHammer - dt * 0.02f);
                if(loopPressure > pressureLimit){
                    pressureDamage = Mathf.clamp(pressureDamage
                        + (loopPressure - pressureLimit) * 0.20f * dt, 0f, 1f);
                }
                if(pressureDamage >= 1f){
                    detonate();
                    return;
                }

                // 燃料/氙/硼沿用全局平均通量；分区只改变局部热与功率分布
                if(hasFuel && flux > 0.01f){
                    boolean rod = items.get(IronworksNuclear.fuelRod) > 0;
                    fuelUse += flux * (rod ? rodPerSecond : thoriumPerSecond) * dt;
                    while(fuelUse >= 1f){
                        fuelUse -= 1f;
                        if(items.get(IronworksNuclear.fuelRod) > 0){
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
                        }else break;
                    }
                    if(flux > 0.01f) rodBurn = Mathf.clamp(fuelUse);
                }
                iodine = Mathf.clamp(iodine + (flux * 0.02f - iodine * 0.011f) * dt, 0f, 1.5f);
                xenon = Mathf.clamp(xenon + (iodine * 0.012f - xenon * (0.0012f + flux * 0.06f)) * dt, 0f, 1.5f);
                if(boronArmed && liquids.get(IronworksLiquids.boricAcid) > 0.01f){
                    float take = Math.min(liquids.get(IronworksLiquids.boricAcid), 4f * dt);
                    liquids.remove(IronworksLiquids.boricAcid, take);
                    boron = Mathf.clamp(boron + take * 0.25f, 0f, 1f);
                    if(boron >= 1f) poisoned = true;
                }
                if(poisoned){
                    for(int i = 0; i < 9; i++){
                        zoneTarget[i] = 1f;
                        zoneRods[i] = Mathf.approach(zoneRods[i], 1f, rodStep(dt, rodTravelScram));
                        zoneFlux[i] = Mathf.approachDelta(zoneFlux[i], 0f, 0.02f);
                    }
                    targetRods = 1f;
                }

                // 压力保护：全部分区棒到底才满足 AZ-5 憋压
                boolean waterFull = liquids.get(Liquids.water) >= waterCap() * 0.98f;
                boolean steamStuck = liquids.get(IronworksLiquids.steam) >= steamCap() - 2f;
                boolean allBottom = true;
                for(int i = 0; i < 9; i++) allBottom &= zoneTarget[i] >= 0.995f && zoneRods[i] >= 0.995f;
                boolean live = hasFuel && (heat > 0.1f || flux > 0.02f);
                if(controlMode == MODE_MANUAL && waterFull && steamStuck && allBottom && live && !melted){
                    coreBoom += dt;
                    if(coreBoom >= pressureFuse) detonate();
                }else coreBoom = Math.max(0f, coreBoom - dt * 1.5f);
                if(melted) return;

                if(scramBoom > 0f){
                    scramBoom = Math.max(0f, scramBoom - dt);
                    if(!headless && Mathf.chanceDelta(0.25f)){
                        Fx.steam.at(x + Mathf.range(size * 4f), y + Mathf.range(size * 4f));
                    }
                }
                if(controlMode != MODE_MANUAL && heat > 1.8f) protectRods = 1f;

                // 旧 5×5 仍用原来的 125%/1 秒计时；RBMK-9 走上面的逐区损伤积分。
                if(!((RBMKReactor)block).zoned){
                    if(heat >= meltTemp && !melted){
                        meltTimer += second;
                        if(meltTimer >= 60f) detonate();
                    }else if(meltTimer > 0f){
                        meltTimer = Math.max(0f, meltTimer - second * 2f);
                    }
                }

                rodCeilingPct = rodEffect(rods) * 100f;
                productionEfficiency = flux;
                if(!headless && flux > 0.05f && Mathf.chanceDelta(flux * 0.15f)){
                    Fx.smoke.at(x + Mathf.range(size * 4f), y + Mathf.range(size * 4f));
                }
                if(!alertsMuted) IronworksRbmkAlerts.update(this, dt);
            }

            @Override
            public void updateTile(){
                float dt = delta() / 60f;
                float second = delta();

                // 静音开关只属于强制手动；自动/半自动始终恢复异常通知
                if(controlMode != MODE_MANUAL) alertsMuted = false;

                boolean hasFuel = items.get(IronworksNuclear.fuelRod) > 0 || items.get(Items.thorium) > 0;
                updateRodJam(dt, meltTemp - 0.05f);
                if(((RBMKReactor)block).zoned){
                    updateZoned(dt, second, hasFuel);
                    return;
                }

                // 半自动/全自动实时保护：先按上一 tick 的水位/温度/流量趋势算保护杆位
                updateProtection(dt, hasFuel);

                // —— 0. 控制棒：三种模式 ——
                if(controlMode == MODE_MANUAL){
                    // 强制手动：按操作目标移动，但控制棒有真实行程，不再瞬动。
                    if(!rodJammed) rods = Mathf.approach(rods, targetRods,
                        rodStep(dt, scramLatch ? rodTravelScram : rodTravelNormal));
                }else if(controlMode == MODE_AUTO){
                    // 全自动：目标功率 + 水位/蒸汽/温度三重保护
                    // 蒸汽憋到 50%~90% 时线性降功率，给水低于 10%~30% 时降功率，温度 0.95~1.15 时降功率；
                    // 任何一项到极限 → 目标功率 0（棒全插）；插棒用 1.5 秒行程，够快但不像手动那样瞬动。
                    float steamFrac = Mathf.clamp(liquids.get(IronworksLiquids.steam) / steamCap(), 0f, 1f);
                    float waterFrac = Mathf.clamp(liquids.get(Liquids.water) / waterCap(), 0f, 1f);
                    float steamFactor = Mathf.clamp(1f - (steamFrac - 0.5f) / 0.4f, 0f, 1f);
                    float waterFactor = Mathf.clamp((waterFrac - 0.1f) / 0.2f, 0f, 1f);
                    // 只在接近熔毁线时收功率；正常高功率不被“安全系数”提前压死。
                    float heatFactor = Mathf.clamp((meltTemp + 0.02f - heat) / 0.06f, 0f, 1f);
                    float effective = Math.max(0f, autoPower) * Math.min(steamFactor, Math.min(waterFactor, heatFactor));

                    float poison = Mathf.clamp(1f - xenon * 0.65f - boron * 1.2f, 0.05f, 1f);
                    // 失冷时空泡会把通量顶高，反算棒位时必须算进去，否则“降功率”降了个寂寞
                    float voidFactor = 1f + (1f - flow) * 1.2f;
                    float doppler = dopplerFactor(heat);
                    float feedRods = autoRodsFor(effective, poison, doppler, voidFactor);
                    // 前馈 + PI 修正：只靠反算曲线会在氙毒/空泡/棒尖变化时留下稳态误差。
                    float error = effective - flux;
                    if(tripLatched){
                        autoIntegral = 0f;
                    }else if(Math.abs(error) < 0.25f){
                        // 条件积分：大误差期间冻结积分，避免目标功率长期高于物理上限时积分饱和。
                        autoIntegral = Mathf.clamp(autoIntegral + error * dt * 0.05f, -0.35f, 0.35f);
                    }
                    targetRods = Mathf.clamp(feedRods - error * 0.25f - autoIntegral, 0f, 1f);
                    // 实时保护量是杆位下限；跳闸锁存后强制全插，不再追目标功率。
                    targetRods = Math.max(targetRods, protectRods);
                    if(scramLatch || tripLatched) targetRods = 1f;
                    if(!rodJammed) rods = Mathf.approach(rods, targetRods,
                        rodStep(dt, scramLatch ? rodTravelScram : rodTravelAuto));
                }else{
                    // 半自动：用户目标 + 实时保护下限；保护插棒用 1.5 秒行程，
                    // 正常提/插棒用 20 秒行程，SCRAM 才用 3 秒紧急行程。
                    float demand = (scramLatch || tripLatched) ? 1f : Math.max(targetRods, protectRods);
                    boolean inserting = demand > rods;
                    float travel = scramLatch ? rodTravelScram
                        : (inserting && protectRods > targetRods ? rodTravelAuto : rodTravelNormal);
                    if(!rodJammed) rods = Mathf.approach(rods, demand, rodStep(dt, travel));
                }

                // —— 1. 双水位线：设定值也有执行器行程，不能一拖滑条就瞬移 ——
                if(controlMode == MODE_MANUAL){
                    updateWaterSetpoints(dt, waterTarget, waterLowTarget);
                }else{
                    // 自动/半自动：高功率留更多水，但最高线始终 ≤90%；低线随高线上下浮动。
                    float autoHigh = Mathf.clamp(0.55f + 0.35f * Math.min(flux, 1f), 0.55f, 0.9f);
                    float autoLow = Mathf.clamp(autoHigh - 0.2f, 0.25f, autoHigh - 0.05f);
                    updateWaterSetpoints(dt, autoHigh, autoLow);
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
                // 自动/半自动的水汽通量硬上限是额定 980 L/s；手动模式保留 >980 的超通量通道。
                float processFlux = Math.max(flux, 0f);
                // 自动/半自动也不再截成 100%；功率和给水量由热工/泵能力自然限制。
                float demand = processFlux * ratedFlow() * dt;
                float water = liquids.get(Liquids.water);
                float steamRoom = steamCap() - liquids.get(IronworksLiquids.steam);
                float totalRoom = LiquidKit.totalSpace(this);
                float converted = Math.max(0f, Math.min(demand, Math.min(water, Math.min(steamRoom, totalRoom))));
                if(converted > 0.0001f){
                    liquids.remove(Liquids.water, converted);
                    liquids.add(IronworksLiquids.steam, converted);
                }
                float flowRaw = demand <= 0.0001f ? 1f : Mathf.clamp(converted / demand, 0f, 1f);
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
                // 空泡正反应性：没水/没汽路时上限被顶高，可越过棒位限制（RBMK 的致命点）。
                // 但自动/半自动只允许额定 100%（980 L/s）；手动模式才允许 125% 超通量。
                target *= 1f + (1f - flow) * 1.2f;
                // 不再对通量做 1.0/1.25 的硬截断；高温/空泡会让它自然跑高，直到熔毁或热平衡。
                target = Math.max(0f, target);

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

                // —— 7. 热：发热 ∝ 通量（+ AZ-5 闪蒸后的正反应性热冲击）；冷却 ∝ 给水流量 × 堆温 ——
                // 满通量 + 满给水：平衡温度 ≈ 79%；给水掉一半 → 平衡温度 >100%（会烧）；
                // 完全断水：约 9 秒从 0 升到 125% → 再加 1 秒熔毁确认。
                // promptHeat 是 SCRAM 闪蒸带来的额外发热率，几秒内衰减；它不会直接引爆，
                // 只是把「水没了 → 温度极速上升」这一段补上。
                float heatGen = flux * 0.14f + promptHeat;
                float cooling = flow * (0.06f + 0.09f * heat) + 0.01f;
                heat = Mathf.clamp(heat + (heatGen - cooling) * dt, 0f, 2f);
                promptHeat = Math.max(0f, promptHeat - dt * 0.22f);

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
                    rods = Mathf.approach(rods, 1f, rodStep(dt, rodTravelScram));
                    flux = Mathf.approachDelta(flux, 0f, 0.02f);
                }

                // —— 9. 汽路憋压引爆：罐满 + 蒸汽排不出去 + 控制棒插到底 ——
                // 真实 RBMK 的 AZ-5：堆芯被汽泡占据时把棒插到底，石墨补偿段先挤走水
                // → 正反应性尖峰。这里用 2 秒倒计时让你看到「憋压」警告条。
                checkPressureBoom(dt, hasFuel);
                if(melted) return;

                // SCRAM 闪蒸瞬态：只负责特效计时，绝不在这里 detonate()。
                // 水已经变成蒸汽、流量已经崩掉，接下来由第 7 步的温度积分决定生死。
                if(scramBoom > 0f){
                    scramBoom = Math.max(0f, scramBoom - dt);
                    if(!headless && Mathf.chanceDelta(0.25f)){
                        Fx.steam.at(x + Mathf.range(size * 4f), y + Mathf.range(size * 4f));
                    }
                }

                // —— 10. 最后防线：实时保护没压住时至少全插棒，不再自动触发 AZ-5 闪蒸 ——
                // AZ-5 闪蒸只保留给操作员手动按紧急停堆；自动系统应该在更早的水位/温度趋势阶段
                // 就把功率降下来，而不是等 105% 才插棒、再制造一次正反应性尖峰。
                if(controlMode != MODE_MANUAL && heat > meltTemp - 0.05f){
                    protectRods = 1f;
                    if(controlMode == MODE_AUTO) targetRods = Math.max(targetRods, 1f);
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

                if(!alertsMuted) IronworksRbmkAlerts.update(this, dt);
            }

            /**
             * 汽路堵死 + 水罐满 + 控制棒插到底：AZ-5 式的正反应性尖峰，2 秒后炸堆。
             *
             * <p>「水罐满」按**实际水罐 98%（9800 L）**算，不按滑条上限：
             * 自动/半自动最高线 ≤90%、水汽通量 ≤980 L/s，天然不会走到爆堆工况；
             * 手动把最高线拉到 100%（水罐 ≥9800 L）才可能触发，压力爆炸只属于手动模式。
             */
            void checkPressureBoom(float dt, boolean hasFuel){
                boolean waterFull = liquids.get(Liquids.water) >= waterCap() * 0.98f;
                boolean steamStuck = liquids.get(IronworksLiquids.steam) >= steamCap() - 2f;
                boolean rodsBottom = targetRods >= 0.995f && rods >= 0.995f;
                boolean live = hasFuel && (heat > 0.1f || flux > 0.02f);
                if(controlMode == MODE_MANUAL && waterFull && steamStuck && rodsBottom && live && !melted){
                    coreBoom += dt;
                    if(coreBoom >= pressureFuse) detonate();
                }else{
                    coreBoom = Math.max(0f, coreBoom - dt * 1.5f);
                }
            }

            /** 主泵惰转：启动约 2 秒，断电后按 45 秒时间常数指数衰减。 */
            void updatePumpSpeed(float dt, boolean running){
                float target = running ? 1f : 0f;
                float alpha = target > pumpSpeed
                    ? 1f - (float)Math.exp(-dt / 2f)
                    : 1f - (float)Math.exp(-dt / pumpCoastTau);
                pumpSpeed += (target - pumpSpeed) * Mathf.clamp(alpha, 0f, 1f);
            }

            /** 相邻建筑里有没有可抽的水；没有水源时泵不能再加速。 */
            boolean hasFeedSource(){
                for(Building other : proximity){
                    if(other == null || other == this || other.team != team || other.liquids == null) continue;
                    if(other.liquids.get(Liquids.water) > 0.001f) return true;
                }
                return false;
            }

            /** 从相邻管路/水罐主动抽水：低于最低线才启泵，补到最高线停止。 */
            void pullFeedwater(float dt){
                float high = waterLimit();
                float low = waterLowLevel * waterCap();
                float water = liquids.get(Liquids.water);
                if(water >= high - 0.5f){
                    feedwaterActive = false;
                    updatePumpSpeed(dt, false);
                    return;
                }
                // 低于低线才启泵；启泵后锁存到最高线，避免刚补过低线就停。
                if(water <= low + 0.5f && hasFeedSource()) feedwaterActive = true;
                updatePumpSpeed(dt, feedwaterActive);
                if(!feedwaterActive && pumpSpeed <= 0.001f) return;
                float need = high - water;
                float budget = Math.min(need, feedRate() * pumpSpeed * dt);
                if(budget <= 0.001f) return;

                boolean anySource = false;
                for(Building other : proximity){
                    if(budget <= 0.001f) break;
                    if(other == null || other == this || other.team != team || other.liquids == null) continue;
                    float avail = other.liquids.get(Liquids.water);
                    if(avail <= 0.001f) continue;
                    anySource = true;
                    float before = liquids.get(Liquids.water);
                    other.transferLiquid(this, Math.min(budget, avail), Liquids.water);
                    float moved = liquids.get(Liquids.water) - before;
                    if(moved > 0f) budget -= moved;
                }
                if(!anySource) updatePumpSpeed(dt, false);
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
                if(!headless && !alertsMuted){
                    IronworksRbmkAlerts.playAlertSound();
                    boolean zonedManual = ((RBMKReactor)block).zoned && controlMode == MODE_MANUAL;
                    if(zonedManual){
                        int hottest = 0;
                        for(int z = 1; z < 9; z++) if(zoneHeat[z] > zoneHeat[hottest]) hottest = z;
                        IronworksNotifications.warn(Core.bundle.format(
                            "ironworks.log.reactor.meltdown.zone", tileX(), tileY(), hottest + 1));
                    }else{
                        IronworksNotifications.warn(Core.bundle.format("ironworks.log.reactor.meltdown", tileX(), tileY()));
                    }
                }

                if(state.rules.reactorExplosions){
                    // 功率越高、堆芯越热，炸得越狠（冷停堆被拆只会小爆）
                    float scale = 0.45f + Math.min(flux, 1.4f) * 0.45f + Math.min(heat, 1.6f) * 0.35f;
                    float blastRadius = meltRadius * 8f * Math.min(scale, 1.6f) * blastRangeMultiplier;
                    // 第一次主爆炸固定 999999；元素/形态与二次爆燃一致：火 + 爆炸。
                    // 友军 100%，敌军 2%，单位/建筑都走同一套属性结算。
                    IronworksFissionFx.at(x, y, blastRadius, team);
                    Dmg.areaWeighted(team, x, y, blastRadius, meltDamage * IronworksFissionFx.testDamageScale,
                        DamageType.fire, DamageForm.EXPLOSIVE, 1f, 0.02f);
                    float falloutRadius = blastRadius * 1.25f;

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
                // 水：只接到「最高水位线」为止（手动=滑条，自动=算法 ≤90%），超过就不再收
                if(liquid == Liquids.water){
                    if(source != null){
                        int side = relativeTo(source);
                        if(side >= 0) waterSide = side;
                    }
                    return liquids.get(Liquids.water) < waterLimit() - 0.5f
                        && LiquidKit.totalSpace(this) > LiquidKit.EPS;
                }
                // 硼酸只有在「已武装注入」后才收（点击按钮之后才允许接液）
                if(boronArmed && liquid == IronworksLiquids.boricAcid) return LiquidKit.hasSpace(this, liquid);
                return false;
            }

            /**
             * 真正的进水硬闸：`transferLiquid` 只调用一次 `acceptLiquid` 判断布尔值，
             * 之后会把整批流量交给 `handleLiquid`。如果这里不按剩余容积截断，
             * 一次 5000 L 的批量进水就能冲过高线（实测会在 500↔5291 L 之间来回跳）。
             * 只截断水，不动蒸汽/硼酸；真正的 10000 L 物理上限仍由 waterCap() 保留。
             */
            @Override
            public void handleLiquid(Building source, Liquid liquid, float amount){
                if(liquid == Liquids.water){
                    float room = Math.max(0f, waterLimit() - liquids.get(Liquids.water));
                    if(room <= 0.001f) return;
                    amount = Math.min(amount, room);
                }
                super.handleLiquid(source, liquid, amount);
            }

            @Override
            public void draw(){
                super.draw();

                // 12×12 分区堆芯：3×3 网格 + 每区热负荷色块（只读视觉，不参与物理）
                if(((RBMKReactor)block).zoned){
                    float cell = size * 8f / 3f;
                    for(int i = 0; i < 9; i++){
                        int cx = i % 3, cy = i / 3;
                        float ox = x + (cx - 1) * cell;
                        float oy = y + (1 - cy) * cell;
                        Draw.color(Color.valueOf("ff6a3c"), 0.035f + Mathf.clamp(zoneHeat[i]) * 0.16f);
                        Draw.rect(Core.atlas.white(), ox, oy, cell - 1.5f, cell - 1.5f);
                    }
                    Draw.color(Color.white, 0.16f);
                    for(int k = 1; k < 3; k++){
                        Draw.rect(Core.atlas.white(), x, y - size * 4f + k * cell, size * 8f, 1.2f);
                        Draw.rect(Core.atlas.white(), x - size * 4f + k * cell, y, 1.2f, size * 8f);
                    }
                    Draw.color();
                }

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
                table.defaults().pad(4f);
                table.add(Core.bundle.get("ironworks.rbmk.panel.title")).color(Pal.accent)
                    .padBottom(4f).row();

                Label summary = new Label("");
                summary.update(() -> summary.setText(Core.bundle.format("ironworks.rbmk.win.short",
                    Core.bundle.get(controlMode == MODE_AUTO ? "ironworks.rbmk.mode.auto"
                        : controlMode == MODE_SEMI ? "ironworks.rbmk.mode.semi" : "ironworks.rbmk.mode.manual"),
                    Mathf.round(flux * 100f), Mathf.round(heat * 100f))));
                summary.setColor(Color.lightGray);
                table.add(summary).wrap().width(290f).row();

                table.button(Core.bundle.get("ironworks.rbmk.win.open"), Styles.defaultt,
                    () -> IronworksRbmkWindow.show(this)).size(290f, 48f).padTop(8f).row();
                table.add(Core.bundle.get("ironworks.rbmk.win.openHint")).wrap()
                    .width(290f).color(Color.lightGray).padTop(4f).row();

                // 点击方块直接弹出控制台；方块 config 只留摘要入口。
                if(!headless) Core.app.post(() -> IronworksRbmkWindow.show(this));
            }

            @Override
            public byte version(){
                return ((RBMKReactor)block).zoned ? (byte)16 : (byte)8;
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
                write.f(waterLowTarget);
                write.b(waterSide);
                write.bool(alertsMuted);
                if(((RBMKReactor)block).zoned){
                    write.i(selectedZone);
                    for(int i = 0; i < 9; i++){
                        write.f(zoneHeat[i]);
                        write.f(zoneFlux[i]);
                        write.f(zoneFlow[i]);
                        write.f(zoneRods[i]);
                        write.f(zoneTarget[i]);
                        write.f(zonePrompt[i]);
                    }
                    for(int i = 0; i < 9; i++) write.f(zoneThrottle[i]);
                    for(int i = 0; i < 9; i++) write.f(zoneThrottleActual[i]);
                    for(int i = 0; i < 9; i++) write.f(zoneCoolant[i]);
                    for(int i = 0; i < 9; i++) write.f(zoneMeltDamage[i]);
                    for(int i = 0; i < 9; i++) write.f(zoneWater[i]);
                    for(int i = 0; i < 9; i++) write.f(zoneSteam[i]);
                    write.f(fluxHistory);
                    write.f(decayTimer);
                    write.f(decayHeat);
                    for(int i = 0; i < 9; i++) write.f(zoneClad[i]);
                    for(int i = 0; i < 9; i++) write.f(zoneDryness[i]);
                    write.f(pumpSpeed);
                    write.f(loopPressure);
                    write.f(pressureDamage);
                    write.f(waterHammer);
                    write.bool(coreInjectEnabled);
                    write.f(coreInjectRate);
                    write.bool(rodJammed);
                    write.f(jamTimer);
                    write.bool(corePrimed);
                    write.f(corePumpSpeed);
                    write.bool(tripLatched);
                    write.i(tripReason);
                }
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
                    if(revision >= 7) waterLowTarget = read.f();
                    if(revision >= 6) waterSide = read.b();
                    if(revision >= 8) alertsMuted = read.bool();
                    if(revision >= 9 && ((RBMKReactor)block).zoned){
                        selectedZone = read.i();
                        for(int i = 0; i < 9; i++){
                            zoneHeat[i] = read.f();
                            zoneFlux[i] = read.f();
                            zoneFlow[i] = read.f();
                            zoneRods[i] = read.f();
                            zoneTarget[i] = read.f();
                            zonePrompt[i] = read.f();
                        }
                        for(int i = 0; i < 9; i++){
                            zoneThrottle[i] = revision >= 10 ? read.f() : 1f;
                        }
                        if(revision >= 13){
                            for(int i = 0; i < 9; i++) zoneThrottleActual[i] = read.f();
                        }else{
                            for(int i = 0; i < 9; i++) zoneThrottleActual[i] = zoneThrottle[i];
                        }
                        if(revision >= 11){
                            for(int i = 0; i < 9; i++) zoneCoolant[i] = read.f();
                            for(int i = 0; i < 9; i++) zoneMeltDamage[i] = read.f();
                            if(revision >= 12){
                                for(int i = 0; i < 9; i++) zoneWater[i] = read.f();
                                for(int i = 0; i < 9; i++) zoneSteam[i] = read.f();
                            }
                            fluxHistory = read.f();
                            decayTimer = read.f();
                            decayHeat = read.f();
                            if(revision >= 14){
                                for(int i = 0; i < 9; i++) zoneClad[i] = read.f();
                                for(int i = 0; i < 9; i++) zoneDryness[i] = read.f();
                                pumpSpeed = read.f();
                                loopPressure = read.f();
                                pressureDamage = read.f();
                                waterHammer = read.f();
                                coreInjectEnabled = read.bool();
                                coreInjectRate = read.f();
                                rodJammed = read.bool();
                                jamTimer = read.f();
                                if(revision >= 15) corePrimed = read.bool();
                                if(revision >= 16){
                                    corePumpSpeed = read.f();
                                    tripLatched = read.bool();
                                    tripReason = read.i();
                                }
                            }else{
                                for(int i = 0; i < 9; i++){
                                    zoneClad[i] = Math.max(0f, zoneHeat[i] * 0.9f);
                                    zoneDryness[i] = 1f;
                                }
                            }
                        }else{
                            for(int i = 0; i < 9; i++){
                                zoneCoolant[i] = Math.max(0f, zoneHeat[i] * 0.9f);
                                zoneMeltDamage[i] = 0f;
                            }
                            fluxHistory = flux;
                            decayTimer = 0f;
                            decayHeat = 0f;
                        }
                        zonesInit = true;
                        initZoneCaps();
                        syncZoneWater();
                        syncZoneSteam();
                    }
                }
                waterLowTarget = Math.min(waterLowTarget, waterTarget);
            }
        }
    }

    // ------------------------------------------------------------------
    // 汽轮机：热冷却液 → 电 + 回收水
    // ------------------------------------------------------------------

    public static class SteamTurbine extends PowerGenerator{
        /** 每台汽轮机的额定参数；旧 3×3 与 4×4 大型机各一份。 */
        public float steamPerSecond = turbineSteamPerSecond, waterReturn = turbineWaterReturn,
            powerOutput = turbinePower;

        public SteamTurbine(String name){
            this(name, 3, turbineSteamPerSecond, turbinePower);
        }

        public SteamTurbine(String name, int blockSize, float steamPerSecond, float powerOutput){
            super(name);
            this.steamPerSecond = steamPerSecond;
            this.powerOutput = powerOutput;
            size = blockSize;
            rotate = false;
            solid = true;
            update = true;
            hasLiquids = true;
            // 约 10 秒的蒸汽缓冲：旧机 1225 L，大型机 3600 L
            liquidCapacity = blockSize == 3 ? 1200f : Math.max(1200f, steamPerSecond * 10f);
            explosionDamage = 0;
            explosionRadius = 0;
            baseExplosiveness = 0f;
            powerProduction = powerOutput / 60f;
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
                    (int)powerOutput + StatUnit.powerSecond.localized(), Pal.accent);
                IronworksStats.specRow(table, IronworksLiquids.steam.uiIcon,
                    Core.bundle.get("ironworks.turbine.spec.steam"),
                    Strings.autoFixed(steamPerSecond, 1) + "/" + StatUnit.perSecond.localized(),
                    IronworksLiquids.steam.color);
                IronworksStats.specRow(table, Liquids.water.uiIcon,
                    Core.bundle.get("ironworks.turbine.spec.water"),
                    Strings.autoFixed(steamPerSecond * waterReturn, 1) + "/" + StatUnit.perSecond.localized(),
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
            /** 湿蒸汽流量与叶片水锤损伤 0..1；损伤满了会掉功率。 */
            public float wetFlow = 0f, bladeDamage = 0f, waterHammer = 0f;
            public float spin = 0f;
            /** 蒸汽从哪一侧进来（0=右 1=上 2=左 3=下）。温水不能从这一侧倒回蒸汽管。 */
            public int inputSide = -1;

            @Override
            public float getPowerProduction(){
                return enabled ? flow * powerProduction : 0f;
            }

            @Override
            public void handleLiquid(Building source, Liquid liquid, float amount){
                if((liquid == IronworksLiquids.steam || liquid == IronworksLiquids.wetSteam) && source != null){
                    int side = relativeTo(source);
                    if(side >= 0) inputSide = side;
                }
                super.handleLiquid(source, liquid, amount);
            }

            @Override
            public void updateTile(){
                float dt = delta() / 60f;

                // 先把温水排出去（冷却塔/管道）；排不出去就停机，不再吃蒸汽
                dumpWarmWater();
                float warmRoom = LiquidKit.space(this, IronworksLiquids.warmWater);
                if(warmRoom <= 0.01f){
                    flow = 0f;
                    productionEfficiency = 0f;
                    spin = Mathf.lerpDelta(spin, 0f, 0.05f);
                    return;
                }

                SteamTurbine type = (SteamTurbine)block;
                float need = type.steamPerSecond * dt;
                float dryHave = liquids.get(IronworksLiquids.steam);
                float wetHave = liquids.get(IronworksLiquids.wetSteam);

                // 湿蒸汽先走旁路：不发电、冷凝成温水，并按流量累积叶片水锤损伤。
                float wetUsed = Math.min(wetHave, need);
                if(type.waterReturn > 0f) wetUsed = Math.min(wetUsed, warmRoom / type.waterReturn);
                if(wetUsed > 0f){
                    liquids.remove(IronworksLiquids.wetSteam, wetUsed);
                    liquids.add(IronworksLiquids.warmWater, wetUsed * type.waterReturn);
                    wetFlow = Mathf.lerpDelta(wetFlow, wetUsed / Math.max(need, 0.0001f), 0.15f);
                    bladeDamage = Mathf.clamp(bladeDamage + wetUsed / Math.max(warmRoom + 1f, 1f) * dt * 0.8f, 0f, 1f);
                    waterHammer = Mathf.clamp(waterHammer + wetUsed / Math.max(need, 0.0001f) * dt * 0.25f, 0f, 1f);
                }else{
                    wetFlow = Mathf.lerpDelta(wetFlow, 0f, 0.1f);
                }

                float used = Math.min(need, dryHave);
                // 温水箱放不下这一 tick 的冷凝水就少发一点，绝不让产物被吞掉
                if(type.waterReturn > 0f) used = Math.min(used, warmRoom / type.waterReturn);
                if(used > 0f) liquids.remove(IronworksLiquids.steam, used);

                float bladeFactor = 1f - Math.min(bladeDamage, 1f) * 0.9f;
                flow = need < 0.0001f ? 0f : used / need * bladeFactor;
                productionEfficiency = flow;
                spin = Mathf.lerpDelta(spin, flow, 0.05f);

                if(used > 0f){
                    liquids.add(IronworksLiquids.warmWater, used * type.waterReturn);
                }
            }

            /** 只从「非进汽侧」排温水：否则蒸汽管一旦被抽空，温水会倒灌进去把导管变成温水，蒸汽线直接死掉（实测）。 */
            void dumpWarmWater(){
                if(liquids.get(IronworksLiquids.warmWater) <= 0.01f) return;
                for(int d = 0; d < 4; d++){
                    if(d == inputSide) continue;
                    dumpLiquid(IronworksLiquids.warmWater, 2f, d);
                }
            }

            // 同 reactor：不能走 super（没有消费者 → 过滤器全 false）
            @Override
            public boolean acceptLiquid(Building source, Liquid liquid){
                // 温水排不出去就不收蒸汽：否则蒸汽会在罐里憋着，反应堆那边看不见背压
                return (liquid == IronworksLiquids.steam || liquid == IronworksLiquids.wetSteam)
                    && LiquidKit.hasSpace(this, liquid)
                    && LiquidKit.hasSpace(this, IronworksLiquids.warmWater);
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
                return 2;
            }

            @Override
            public void write(Writes write){
                super.write(write);
                write.f(flow);
                write.f(bladeDamage);
                write.f(wetFlow);
            }

            @Override
            public void read(Reads read, byte revision){
                super.read(read, revision);
                if(revision >= 1) flow = read.f();
                if(revision >= 2){
                    bladeDamage = read.f();
                    wetFlow = read.f();
                }
            }
        }
    }
}
