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
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.Liquid;
import mindustry.ui.Bar;
import mindustry.world.blocks.power.PowerGenerator;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

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
    /** 满通量烧钍（/秒）。 */
    public static final float thoriumPerSecond = 0.05f;
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
            itemCapacity = 30;
            hasLiquids = true;
            liquidCapacity = 900f;
            // 熔毁自己算（带辐射），别让引擎再炸一次
            explosionDamage = 0;
            explosionRadius = 0;
            baseExplosiveness = 0f;
            // 动态发电，静态值只用来占位
            powerProduction = reactorPower / 60f;
            buildType = RBMKReactorBuild::new;

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
            stats.add(Stat.basePowerGeneration, reactorPower, StatUnit.powerSecond);
            stats.add(new Stat("ironworks-rbmk-coolant"), (int)waterPerSecond + "/秒");
            stats.add(new Stat("ironworks-rbmk-hot"), (int)(waterPerSecond * hotRatio) + "/秒");
            stats.add(new Stat("ironworks-rbmk-fuel"), thoriumPerSecond + "/秒");
            stats.add(new Stat("ironworks-rbmk-rods"), Core.bundle.get("ironworks.rbmk.rods.hint"));
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
                () -> Core.bundle.format("bar.ironworks-rbmk-fuel", b.items.get(Items.thorium), b.block.itemCapacity),
                () -> Items.thorium.color,
                () -> (float)b.items.get(Items.thorium) / b.block.itemCapacity));
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
            /** 控制棒插入深度 0..1（1=全插）。 */
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
                if(v < 0){
                    // SCRAM：全部插进去
                    rods = 1f;
                    return;
                }
                rods = Mathf.clamp(v / 100f, 0f, 1f);
            }

            @Override
            public void updateTile(){
                float dt = delta() / 60f;
                float second = delta();

                boolean hasFuel = items.get(Items.thorium) > 0;

                // —— 1. 冷却液 ——
                float need = flux * waterPerSecond * dt;
                float have = liquids.get(Liquids.water);
                float used = Math.min(need, have);
                if(used > 0f) liquids.remove(Liquids.water, used);
                flow = need < 0.0001f ? 1f : used / need;

                // —— 2. 反应性 → 通量 ——
                float reactivity = (1f - rods) * 0.012f * (hasFuel ? 1f : 0f)
                    - flux * 0.012f
                    - xenon * 0.006f
                    + (1f - flow) * flux * 0.012f;   // 空泡正反应性：没水反而更疯

                flux = Mathf.clamp(flux + reactivity * dt * 20f, 0f, fluxMax);
                productionEfficiency = flux;

                // —— 3. 燃料 ——
                if(hasFuel && flux > 0.01f){
                    fuelUse += flux * thoriumPerSecond * dt;
                    while(fuelUse >= 1f){
                        fuelUse -= 1f;
                        items.remove(Items.thorium, 1);
                        if(items.get(Items.thorium) <= 0) break;
                    }
                }

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
                iodine = Mathf.clamp(iodine + (flux * 0.02f - iodine * 0.02f) * dt, 0f, 1.5f);
                xenon = Mathf.clamp(xenon + (iodine * 0.01f - xenon * (0.004f + flux * 0.02f)) * dt, 0f, 1.5f);

                // —— 7. 自动 SCRAM：过热保护（保护也不一定救得回来，这才是 RBMK）——
                if(heat > 1.05f && rods < 0.999f){
                    rods = 1f;
                }

                // —— 8. 熔毁 ——
                if(heat >= meltTemp && !melted){
                    meltTimer += second;
                    if(meltTimer >= 60f) meltdown();
                }else if(meltTimer > 0f){
                    meltTimer = Math.max(0f, meltTimer - second * 2f);
                }

                // —— 9. 显示/视觉 ——
                tempK = 500f + heat * 900f;
                if(flux > 0.05f && Mathf.chanceDelta(flux * 0.15f)){
                    Fx.smoke.at(x + Mathf.range(size * 4f), y + Mathf.range(size * 4f));
                }
            }

            public void meltdown(){
                if(melted || dead) return;
                melted = true;

                if(state.rules.reactorExplosions){
                    Fx.reactorExplosion.at(x, y);
                    Fx.massiveExplosion.at(x, y);
                    Damage.damage(x, y, meltRadius * 8f, meltDamage);
                    //落尘范围比爆炸更大，站在爆心外也可能吃到辐射
                    for(Unit u : Groups.unit){
                        if(u.dst2(x, y) < falloutRadius * falloutRadius){
                            u.apply(IronworksStatus.radiation, radiationTime);
                        }
                    }
                }else{
                    Fx.dynamicExplosion.at(x, y);
                }

                kill();
            }

            // 只收水，热冷却液不被邻居倒灌
            @Override
            public boolean acceptItem(Building source, mindustry.type.Item item){
                return item == Items.thorium && items.get(item) < itemCapacity;
            }

            @Override
            public boolean acceptLiquid(Building source, Liquid liquid){
                return super.acceptLiquid(source, liquid) && LiquidKit.acceptOnly(this, source, liquid, Liquids.water);
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

                // 控制棒指示：插入越深，顶部亮条越短
                Draw.color(Pal.accent);
                float w = size * 8f * 0.7f;
                Draw.rect(Core.atlas.white(), x, y + size * 4f - 6f, w * (1f - rods * 0.8f), 3f);
                Draw.color();
            }

            // ---------------- UI ----------------

            @Override
            public void buildConfiguration(Table table){
                table.defaults().pad(3f);

                Label rodLabel = new Label("");
                rodLabel.update(() -> rodLabel.setText(
                    Core.bundle.format("ironworks.rbmk.rods.now", Mathf.round(rods * 100f))));
                table.add(rodLabel).row();

                Slider slider = new Slider(0f, 100f, 1f, false);
                slider.setValue(rods * 100f);
                slider.moved(v -> configure((int)v));
                table.add(slider).width(240f).row();

                table.add(new Label(Core.bundle.get("ironworks.rbmk.rodhint"))).wrap().width(240f).padTop(2f).row();

                Label readout = new Label("");
                readout.update(() -> readout.setText(Core.bundle.format("ironworks.rbmk.readout",
                    Mathf.round(flux * 100f), Mathf.round(heat * 100f),
                    Mathf.round(tempK), Mathf.round(xenon * 100f), Mathf.round(flow * 100f))));
                table.add(readout).padTop(6f).row();

                Label status = new Label("");
                status.update(() -> {
                    if(heat >= meltTemp) status.setText(Core.bundle.get("ironworks.rbmk.state.melt"));
                    else if(heat > 1.05f) status.setText(Core.bundle.get("ironworks.rbmk.state.danger"));
                    else if(flow < 0.9f) status.setText(Core.bundle.get("ironworks.rbmk.state.dry"));
                    else status.setText(Core.bundle.get("ironworks.rbmk.state.ok"));
                });
                table.add(status).padTop(2f).row();

                table.button(Core.bundle.get("ironworks.rbmk.scram"), () -> configure(-1))
                    .size(200f, 40f).padTop(6f).row();
            }

            @Override
            public byte version(){
                return 1;
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
            stats.add(Stat.basePowerGeneration, turbinePower, StatUnit.powerSecond);
            stats.add(new Stat("ironworks-rbmk-turbine-hot"), (int)turbineHotPerSecond + "/秒");
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
                    LiquidKit.addCappedShared(this, Liquids.water, used * turbineWaterReturn);
                    if(liquids.get(Liquids.water) > 0.01f) dumpLiquid(Liquids.water);
                }
            }

            @Override
            public boolean acceptLiquid(Building source, Liquid liquid){
                return liquid == IronworksLiquids.hotCoolant && super.acceptLiquid(source, liquid);
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
