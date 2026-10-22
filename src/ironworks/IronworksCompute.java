package ironworks;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.consumers.ConsumePowerDynamic;
import mindustry.world.meta.Stat;

import static mindustry.type.ItemStack.with;

/**
 * 全域算力集群：区块级超速装置。
 *
 * 引擎限制：Mindustry 的方块只能是正方形（size 是单个数），4×6 与 2×3 无法在纯原版实现，
 * 主集群用 6×6、高级模块用 3×3 替代。
 *
 * 挂载规则（0.61.0 起）：
 * - 模块必须有**至少一条完整的边**贴合主集群（3×3 模块要 3 格连续贴边，只贴 1 格不算）；
 * - 贴合后模块换成 "名称-2" 变体贴图，并按贴合方向自动旋转（0=上 1=右 2=下 3=左）；
 * - 加成与功率由主集群统计，加成总上限 +200%。
 */
public class IronworksCompute{

    public static Block cluster;
    public static ComputeModule micro, basic, advanced, quantum;

    public static final float basePower = 32460f;
    public static final float moduleCap = 2.0f;
    public static final float coolantUse = 1f;

    public static void load(){
        micro = new ComputeModule("compute-micro", 1, 0.0135f, 930f);
        micro.requirements(Category.effect, with(IronworksSilicon.basicChip, 2, Items.silicon, 20, Items.metaglass, 10));
        micro.health = 200;

        basic = new ComputeModule("compute-basic", 3, 0.07f, 1950f);
        basic.requirements(Category.effect, with(IronworksSilicon.basicChip, 6, IronworksItems.steelPlate, 30,
            Items.silicon, 50, Items.metaglass, 30));
        basic.health = 900;

        advanced = new ComputeModule("compute-advanced", 3, 0.14f, 6825f);
        advanced.requirements(Category.effect, with(IronworksSilicon.advancedChip, 4, IronworksItems.steelPlate, 80,
            IronworksMaterials.siliconCarbide, 30, Items.silicon, 100));
        advanced.health = 2200;

        quantum = new ComputeModule("compute-quantum", 2, 0.20f, 12420f);
        quantum.requirements(Category.effect, with(IronworksSilicon.quantumCore, 2, IronworksMaterials.siliconCarbide, 50,
            IronworksAluminum.aviationAluminum, 20, Items.silicon, 150));
        quantum.health = 1500;

        cluster = new ComputeCluster("compute-cluster"){{
            requirements(Category.effect,
                with(IronworksSilicon.quantumCore, 10, IronworksMaterials.siliconCarbide, 200,
                     IronworksItems.steelPlate, 800, Items.silicon, 1200,
                     IronworksAluminum.aviationAluminum, 120));

            size = 6;
            health = 12000;
            armor = 12f;
            solid = true;
            update = true;
            hasLiquids = true;
            liquidCapacity = 480f;
            hasPower = true;
            consumeLiquid(IronworksLiquids.grapheneCoolant, coolantUse);
            consume(new ConsumePowerDynamic(b -> {
                ComputeClusterBuild c = (ComputeClusterBuild)b;
                return (basePower + c.modulePower) / 60f;
            }));
        }};
    }

    /**
     * 返回模块相对主集群的贴合方向（0=上 1=右 2=下 3=左），-1 表示没有整边贴合。
     * 规则：模块的一条完整边必须与主集群的一条边重合且完全落在主集群范围内。
     */
    public static int contactDir(Building host, int hostSize, Building mod, int modSize){
        int hx1 = host.tileX() - (hostSize - 1) / 2, hy1 = host.tileY() - (hostSize - 1) / 2;
        int hx2 = hx1 + hostSize - 1, hy2 = hy1 + hostSize - 1;
        int mx1 = mod.tileX() - (modSize - 1) / 2, my1 = mod.tileY() - (modSize - 1) / 2;
        int mx2 = mx1 + modSize - 1, my2 = my1 + modSize - 1;

        // 模块在右 → 主集群在模块左边（3）
        if(mx1 == hx2 + 1 && my1 >= hy1 && my2 <= hy2) return 3;
        // 模块在左 → 主集群在右（1）
        if(mx2 == hx1 - 1 && my1 >= hy1 && my2 <= hy2) return 1;
        // 模块在下 → 主集群在上（0）
        if(my1 == hy2 + 1 && mx1 >= hx1 && mx2 <= hx2) return 0;
        // 模块在上 → 主集群在下（2）
        if(my2 == hy1 - 1 && mx1 >= hx1 && mx2 <= hx2) return 2;
        return -1;
    }

    public static class ComputeModule extends Wall{
        public float bonus, power;
        public TextureRegion variant;

        public ComputeModule(String name, int size, float bonus, float power){
            super(name);
            this.size = size;
            this.bonus = bonus;
            this.power = power;
            update = true;
            buildType = ComputeModuleBuild::new;
        }

        @Override
        public void load(){
            super.load();
            variant = Core.atlas.find(name + "-2");
        }

        public class ComputeModuleBuild extends WallBuild{
            public boolean attached;
            public int dir = -1;
            float checkTimer;

            @Override
            public void updateTile(){
                checkTimer += delta();
                if(checkTimer < 10f) return;
                checkTimer = 0f;

                attached = false;
                dir = -1;
                for(Building b : proximity){
                    if(b.block == cluster){
                        int d = contactDir(b, cluster.size, this, block.size);
                        if(d >= 0){ attached = true; dir = d; break; }
                    }
                }
            }

            @Override
            public void draw(){
                if(attached && variant != null && variant.found()){
                    float ang = -90f * dir;
                    Draw.rect(variant, x, y, variant.width / 4f, variant.height / 4f, ang);
                }else{
                    super.draw();
                }
            }
        }
    }

    public static class ComputeCluster extends Block{

        public ComputeCluster(String name){
            super(name);
            buildType = ComputeClusterBuild::new;
        }

        @Override
        public void setStats(){
            super.setStats();
            stats.add(new Stat("ironworks-compute-base"), "50% ~ 250%");
            stats.add(new Stat("ironworks-compute-modules"), "上限 +200%（整边贴合）");
            stats.add(new Stat("ironworks-compute-coolant"), "石墨烯冷却液 60/秒");
        }

        @Override
        public void setBars(){
            super.setBars();
            addBar("compute-boost", (ComputeClusterBuild e) -> new Bar(
                () -> Core.bundle.format("bar.ironworks-compute-boost", Mathf.round(e.appliedBoost * 100f)),
                () -> Pal.accent,
                () -> Mathf.clamp(e.appliedBoost / 4.5f)
            ));
            addBar("compute-modules", (ComputeClusterBuild e) -> new Bar(
                () -> Core.bundle.format("bar.ironworks-compute-modules", e.moduleCount,
                    Mathf.round(e.moduleBonus * 100f)),
                () -> arc.graphics.Color.valueOf("b6a8ff"),
                () -> Mathf.clamp(e.moduleBonus / moduleCap)
            ));
        }

        public class ComputeClusterBuild extends Building{
            public int moduleCount;
            public float moduleBonus, modulePower;
            public float appliedBoost;
            float applyTimer;

            void recalcModules(){
                int count = 0;
                float bonus = 0f, power = 0f;
                for(int i = 0; i < proximity.size; i++){
                    Building b = proximity.get(i);
                    if(!(b.block instanceof ComputeModule)) continue;
                    if(contactDir(this, block.size, b, b.block.size) < 0) continue;
                    ComputeModule m = (ComputeModule)b.block;
                    count++;
                    bonus += m.bonus;
                    power += m.power;
                }
                moduleCount = count;
                moduleBonus = Math.min(bonus, moduleCap);
                modulePower = power;
            }

            @Override
            public void updateTile(){
                recalcModules();

                boolean working = efficiency > 0.001f;
                float base = working ? 0.5f + 2.0f * Mathf.clamp(efficiency, 0f, 1f) : 0f;
                appliedBoost = working ? base + moduleBonus : 0f;

                if(working && appliedBoost > 0.001f){
                    applyTimer += delta();
                    if(applyTimer >= 4f){
                        applyTimer = 0f;
                        float boost = appliedBoost;
                        for(Building b : mindustry.gen.Groups.build){
                            if(b == this || b.dead) continue;
                            if(b.block instanceof ComputeCluster || b.block instanceof ComputeModule) continue;
                            b.applyBoost(boost, 8f);
                        }
                    }
                }
            }

            @Override
            public byte version(){
                return 1;
            }

            @Override
            public void write(arc.util.io.Writes write){
                super.write(write);
                write.f(moduleBonus);
            }

            @Override
            public void read(arc.util.io.Reads read, byte revision){
                super.read(read, revision);
                if(revision >= 1) moduleBonus = read.f();
            }
        }
    }
}
