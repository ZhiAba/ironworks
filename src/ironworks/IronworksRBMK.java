package ironworks;

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
 *   <li>冷却液闭环全部复用老的：反应堆吐 {@link IronworksLiquids#hotCoolant}，
 *       汽轮机把它换成电 + 回收水，老「冷却工厂」也能吃热液再生产石墨烯冷却液；</li>
 *   <li>燃料直接用原版 {@link Items#thorium}，不新增物品；</li>
 *   <li>造价复用现有材料（碳化硅 / 钢板 / 量子核心 / 航空铝）；</li>
 *   <li>熔毁用引擎现成的 {@link Fx#reactorExplosion} + {@link Damage#damage}，
 *       辐射用我们自己的状态框架 {@link IronworksStatus#radiation}；</li>
 *   <li>UI 沿用 MCT 节点的做法：{@code config(Class, Cons2)} + {@code buildConfiguration(Table)}。</li>
 * </ul>
 *
 * <p>核心机制：
 * <ul>
 *   <li>控制棒（0=全提 / 100=全插）决定目标通量；</li>
 *   <li>通量决定发热与发电；水流量不够 → 发热压不住；</li>
 *   <li>**空泡正反应性**：冷却液不足时通量反而上涨（RBMK 的经典缺陷）；</li>
 *   <li>氙毒：降功率后碘衰变成氙，短时间内压着反应性（碘坑）；</li>
 *   <li>堆芯温度 &gt; 125% 持续 1 秒 → 熔毁：炸场 + 大范围辐射。</li>
 * </ul>
 */
public class IronworksRBMK{

    public static RBMKReactor reactor;
    public static SteamTurbine turbine;

    // ---- 反应堆参数 ----
    /** 满通量发电（/秒）。 */
    public static final float reactorPower = 45000f;
    /** 满通量耗水（/秒）。 */
    public static final float waterPerSecond = 40f;
    /** 满通量吐热冷却液（/秒，按实际用水量的 85%）。 */
    public static final float hotRatio = 0.85f;
    /** 满通量烧钍（/秒）：1 根 = 200 秒。 */
    public static final float thoriumPerSecond = 0.05f;
    /** 满通量烧燃料棒（/秒）：1 根 = 900 秒（加工过的燃料耐烧得多）。 */
    public static final float rodPerSecond = 1f / 900f;
    /** 硼酸注入的负反应性权重与消散时间（秒）。 */
    public static final float boronReactivity = 0.05f, boronDecaySeconds = 45f;
    /** 控制棒行程速度（每 tick 的插入比例）：正常 ~2000 tick 走完全程，急停 ~180 tick。 */
    public static final float rodSpeedNormal = 1f / 2000f, rodSpeedScram = 1f / 180f;
    /** 通量上限。 */
    public static final float fluxMax = 1.25f;
    /** 熔毁温度阈值（归一化）。 */
    public static final float meltTemp = 1.25f;
    /** 熔毁后爆炸半径（tile）与伤害。 */
    public static final int meltRadius = 26;
    public static final float meltDamage = 12000f;
    /** 辐射持续时间（tick）。 */
    public static final float radiationTime = 900f;
    /** 辐射沉降半径（比爆炸大：落尘范围更广）。 */
    public static final float falloutRadius = meltRadius * 8f * 1.6f;

    // ---- 汽轮机参数 ----
    /** 满流发电（/秒）。 */
    public static final float turbinePower = 18000f;
    /** 满流吃热冷却液（/秒）。 */
    public static final float turbineHotPerSecond = 20f;
    /** 回收水的比例。 */
    public static final float turbineWaterReturn = 0.9f;

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
            liquidCapacity = 900f;
            // 熔毁自己算（带辐射），别让引擎再炸一次
            explosionDamage = 0;
            explosionRadius = 0;
            baseExplosiveness = 0f;
            // 动态发电，静态值只用来占位
            powerProduction = reactorPower / 60f;
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
                IronworksStats.specRow(table, Icon.power,
                    Core.bundle.get("ironworks.rbmk.spec.power"),
                    (int)reactorPower + StatUnit.powerSecond.localized(), Pal.accent);
                IronworksStats.specRow(table, Liquids.water.uiIcon,
                    Core.bundle.get("ironworks.rbmk.spec.water"),
                    (int)waterPerSecond + "/" + StatUnit.perSecond.localized(), Liquids.water.color);
                IronworksStats.specRow(table, IronworksLiquids.hotCoolant.uiIcon,
                    Core.bundle.get("ironworks.rbmk.spec.hot"),
                    (int)(waterPerSecond * hotRatio) + "/" + StatUnit.perSecond.localized(),
                    IronworksLiquids.hotCoolant.color);
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
                () -> b.flow < 0.9f ? Pal.remove : IronworksLiquids.grapheneCoolant.color,
                () -> Mathf.clamp(b.flow)));

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
            /** 中子通量 0..1.25。 */
            public float flux = 0f;
            /** 堆芯温度（归一化，>1.25 熔毁）。 */
            public float heat = 0f;
            /** 氙毒 0..1.5。 */
            public float xenon = 0f, iodine = 0f;
            /** 实际冷却液满足率 0..1。 */
            public float flow = 1f;
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
                return enabled ? flux * powerProduction : 0f;
            }

            @Override
            public float warmup(){
                return Mathf.clamp(heat);
            }

            public void applyConfig(int v){
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
                if(v < 0) return;
                targetRods = Mathf.clamp(v / 100f, 0f, 1f);
            }

            @Override
            public void updateTile(){
                float dt = delta() / 60f;
                float second = delta();

                boolean hasFuel = items.get(IronworksNuclear.fuelRod) > 0 || items.get(Items.thorium) > 0;

                // —— 0. 控制棒行程：真实棒要几十秒才走完全程，急停也只是「快一点」 ——
                float rodSpeed = targetRods > rods ? rodSpeedScram : rodSpeedNormal;
                rods = Mathf.approachDelta(rods, targetRods, rodSpeed);

                // —— 1. 冷却液：流量有惯性（泵与管路不会瞬间归零/拉满） ——
                float need = flux * waterPerSecond * dt;
                float have = liquids.get(Liquids.water);
                float used = Math.min(need, have);
                if(used > 0f) liquids.remove(Liquids.water, used);
                float flowRaw = need < 0.0001f ? 1f : used / need;
                flow = Mathf.lerpDelta(flow, flowRaw, 0.06f);

                // —— 2. 棒位 = 功率上限（拟真核心）——
                // 之前用「反应性积分」驱动通量，只要净反应性为正就会一路顶到上限；
                // 现在改成：每根棒的插入深度直接决定**稳态上限**，通量只朝这个上限收敛。
                //   rods = 0   → 上限 100%（满功率）
                //   rods = 50% → 上限 50%
                //   rods = 90% → 上限 10%
                // 氙毒/硼酸按比例压低上限；失冷（空泡正反应性）**顶高**上限 → 才会失控。
                float rodCeiling = (1f - rods) * (hasFuel ? 1f : 0f);
                float poisonFactor = Mathf.clamp(1f - xenon * 0.65f - boron * 1.2f, 0f, 1f);
                if(poisoned) poisonFactor = 0f;

                float target = fluxMax * rodCeiling * poisonFactor;
                // 空泡正反应性：没水时上限被顶到 2.2 倍，可越过棒位限制（RBMK 的致命点）
                target *= 1f + (1f - flow) * 1.2f;
                target = Mathf.clamp(target, 0f, fluxMax);

                // 一阶滞后（中子动力学）：插棒/提棒后通量慢慢追上限
                flux = Mathf.lerpDelta(flux, target, 0.008f);
                productionEfficiency = flux;
                rodCeilingPct = rodCeiling * 100f;

                // —— 3. 燃料：燃料棒优先（耐烧 + 吐乏燃料），钍是应急替代 ——
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

                // —— 4. 热 ——
                float heatGen = flux * 0.50f;
                float cooling = flow * 0.60f * heat + 0.05f;
                heat = Mathf.clamp(heat + (heatGen - cooling) * 0.04f * dt, 0f, 2f);

                // —— 5. 热冷却液（用水量的 85%），罐满=背压升温 ——
                float hotOut = used * hotRatio;
                if(hotOut > 0f){
                    float stored = LiquidKit.addCappedShared(this, IronworksLiquids.hotCoolant, hotOut);
                    if(hotOut > stored + 0.0001f){
                        // 排不出去 = 热也带走不出去
                        heat = Mathf.clamp(heat + (hotOut - stored) * 0.9f * dt, 0f, 2f);
                    }
                }
                if(liquids.get(IronworksLiquids.hotCoolant) > 0.01f){
                    dumpLiquid(IronworksLiquids.hotCoolant);
                }

                // —— 6. 氙毒（碘 → 氙，氙被通量烧掉）——
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

                // SCRAM 引爆倒计时
                if(scramBoom > 0f){
                    scramBoom -= dt * 60f;
                    if(scramBoom <= 0f) detonate();
                }

                // —— 7. 自动 SCRAM：过热保护（保护也不一定救得回来，这才是 RBMK）——
                if(heat > 1.05f && targetRods < 0.999f){
                    targetRods = 1f;
                }

                // —— 8. 熔毁 ——
                if(heat >= meltTemp && !melted){
                    meltTimer += second;
                    if(meltTimer >= 60f) detonate();
                }else if(meltTimer > 0f){
                    meltTimer = Math.max(0f, meltTimer - second * 2f);
                }

                // —— 9. 显示/视觉 ——
                // 冷却剂出口温度（°C）：名义工况 ~280°C，熔毁前逼近 900°C
                tempK = 280f + heat * 500f;
                if(flux > 0.05f && Mathf.chanceDelta(flux * 0.15f)){
                    Fx.smoke.at(x + Mathf.range(size * 4f), y + Mathf.range(size * 4f));
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
                explodeIfLive();
                super.onDeconstructed(unit);
            }

            public void detonate(){
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

                if(!dead) kill();
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
                if(liquid == Liquids.water) return LiquidKit.hasSpace(this, liquid);
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

                // —— 控制棒 ——
                table.table(Styles.grayPanel, c -> {
                    c.defaults().pad(5f);
                    Label rodLabel = new Label("");
                    rodLabel.update(() -> rodLabel.setText(Core.bundle.format(
                        "ironworks.rbmk.rods.now", Mathf.round(targetRods * 100f), Mathf.round(rods * 100f))));
                    c.add(rodLabel).left().growX().row();

                    Slider slider = new Slider(0f, 100f, 1f, false);
                    slider.setValue(rods * 100f);
                    slider.moved(v -> configure((int)v));
                    c.add(slider).width(230f).row();

                    c.add(Core.bundle.get("ironworks.rbmk.rodhint")).wrap()
                        .width(230f).color(Color.lightGray).left().row();
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
                    if(heat >= meltTemp){
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
                return 3;
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
            liquidCapacity = 240f;
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
                IronworksStats.specRow(table, IronworksLiquids.hotCoolant.uiIcon,
                    Core.bundle.get("ironworks.turbine.spec.hot"),
                    (int)turbineHotPerSecond + "/" + StatUnit.perSecond.localized(),
                    IronworksLiquids.hotCoolant.color);
                IronworksStats.specRow(table, Liquids.water.uiIcon,
                    Core.bundle.get("ironworks.turbine.spec.water"),
                    (int)(turbineHotPerSecond * turbineWaterReturn) + "/" + StatUnit.perSecond.localized(),
                    Liquids.water.color);
            });
        }

        @Override
        public void setBars(){
            super.setBars();
            addBar("turbine-flow", (SteamTurbineBuild b) -> new Bar(
                () -> Core.bundle.format("bar.ironworks-rbmk-turbine", Mathf.round(b.flow * 100f)),
                () -> IronworksLiquids.hotCoolant.color,
                () -> Mathf.clamp(b.flow)));
        }

        public class SteamTurbineBuild extends PowerGenerator.GeneratorBuild{
            public float flow = 0f;
            public float spin = 0f;

            @Override
            public float getPowerProduction(){
                return enabled ? flow * powerProduction : 0f;
            }

            @Override
            public void updateTile(){
                float dt = delta() / 60f;

                float need = turbineHotPerSecond * dt;
                float have = liquids.get(IronworksLiquids.hotCoolant);
                float used = Math.min(need, have);
                if(used > 0f) liquids.remove(IronworksLiquids.hotCoolant, used);

                flow = need < 0.0001f ? 0f : used / need;
                productionEfficiency = flow;
                spin = Mathf.lerp(spin, flow, 0.05f * delta());

                // 冷凝水回收
                if(used > 0f){
                    // 做完功的冷凝水是「温水」，要过冷却塔才能回反应堆
                    LiquidKit.addCappedShared(this, IronworksLiquids.hotWater, used * turbineWaterReturn);
                    if(liquids.get(IronworksLiquids.hotWater) > 0.01f) dumpLiquid(IronworksLiquids.hotWater);
                }
            }

            // 同 reactor：不能走 super（没有消费者 → 过滤器全 false）
            @Override
            public boolean acceptLiquid(Building source, Liquid liquid){
                return liquid == IronworksLiquids.hotCoolant && LiquidKit.hasSpace(this, liquid);
            }

            @Override
            public void draw(){
                super.draw();
                if(spin > 0.05f){
                    float a = Mathf.clamp(spin) * Mathf.absin(Time.time, 3f, 0.5f) + 0.1f;
                    Draw.color(IronworksLiquids.hotCoolant.color, a);
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
