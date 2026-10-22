package ironworks;

import arc.graphics.Color;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.consumers.ConsumePowerDynamic;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.type.ItemStack.with;

/**
 * 全域算力集群：区块级超速装置。
 *
 * 引擎限制：Mindustry 的方块只能是正方形（size 是单个数），所以 4×6 与 2×3 无法直接实现，
 * 这里用最接近的正方形替代：主集群 6×6、高级集群 3×3。
 *
 * 机制：
 * - 主集群固定消耗 32460 电/秒 + 每个挂载模块自身的功率；
 * - 需要石墨烯冷却液（2/刻 = 120/秒）；
 * - 基础超速 50%~250%（随供电/冷却效率线性增长）；
 * - 四个面相邻的算力模块各提供固定加成，总加成上限 +200%；
 * - 每 4 刻对全图建筑 applyBoost，时间倍率 = 基础 + 挂载加成。
 */
public class IronworksCompute{

    public static Block cluster, micro, basic, advanced, quantum;

    /** 主集群基础功耗（每秒） */
    public static final float basePower = 32460f;
    /** 挂载加成上限 */
    public static final float moduleCap = 2.0f;
    /** 冷却液消耗（每刻） */
    public static final float coolantUse = 1f;

    public static void load(){
        // —— 外挂模块：被动方块，功率计入主集群 ——
        micro = new Wall("compute-micro"){{
            requirements(Category.effect, with(IronworksSilicon.basicChip, 2, Items.silicon, 20, Items.metaglass, 10));
            size = 1;
            health = 200;
        }};

        basic = new Wall("compute-basic"){{
            requirements(Category.effect, with(IronworksSilicon.basicChip, 6, IronworksItems.steelPlate, 30,
                Items.silicon, 50, Items.metaglass, 30));
            size = 3;
            health = 900;
        }};

        advanced = new Wall("compute-advanced"){{
            requirements(Category.effect, with(IronworksSilicon.advancedChip, 4, IronworksItems.steelPlate, 80,
                IronworksMaterials.siliconCarbide, 30, Items.silicon, 100));
            size = 3;
            health = 2200;
        }};

        quantum = new Wall("compute-quantum"){{
            requirements(Category.effect, with(IronworksSilicon.quantumCore, 2, IronworksMaterials.siliconCarbide, 50,
                IronworksAluminum.aviationAluminum, 20, Items.silicon, 150));
            size = 2;
            health = 1500;
        }};

        // —— 主集群 ——
        cluster = new ComputeCluster("compute-cluster"){{
            requirements(Category.effect,
                with(IronworksSilicon.quantumCore, 10, IronworksMaterials.siliconCarbide, 200,
                     IronworksItems.steelPlate, 800, Items.silicon, 1200,
                     IronworksAluminum.aviationAluminum, 120));

            size = 6;
            health = 12000;
            armor = 12f;
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

    public static class ComputeCluster extends Block{

        public ComputeCluster(String name){
            super(name);
        }

        @Override
        public void setStats(){
            super.setStats();
            stats.add(new Stat("ironworks-compute-base"), "50% ~ 250%");
            stats.add(new Stat("ironworks-compute-modules"), "上限 +200%（相邻挂载）");
            stats.add(new Stat("ironworks-compute-coolant"), "石墨烯冷却液 60/秒");
        }

        @Override
        public void setBars(){
            super.setBars();
            addBar("compute-boost", (ComputeClusterBuild e) -> new Bar(
                () -> arc.Core.bundle.format("bar.ironworks-compute-boost", Mathf.round(e.appliedBoost * 100f)),
                () -> Pal.accent,
                () -> Mathf.clamp(e.appliedBoost / 4.5f)
            ));
            addBar("compute-modules", (ComputeClusterBuild e) -> new Bar(
                () -> arc.Core.bundle.format("bar.ironworks-compute-modules", e.moduleCount,
                    Mathf.round(e.moduleBonus * 100f)),
                () -> Color.valueOf("b6a8ff"),
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
                    if(b.block == micro){ count++; bonus += 0.0135f; power += 930f; }
                    else if(b.block == basic){ count++; bonus += 0.07f; power += 1950f; }
                    else if(b.block == advanced){ count++; bonus += 0.14f; power += 6825f; }
                    else if(b.block == quantum){ count++; bonus += 0.20f; power += 12420f; }
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
                // 没电/没冷却液时整机不工作，挂载加成也不生效
                appliedBoost = working ? base + moduleBonus : 0f;

                if(working && appliedBoost > 0.001f){
                    applyTimer += delta();
                    if(applyTimer >= 4f){
                        applyTimer = 0f;
                        float boost = appliedBoost;
                        for(Building b : mindustry.gen.Groups.build){
                            if(b == this || b.dead) continue;
                            if(b.block instanceof ComputeCluster) continue;
                            if(b.block == micro || b.block == basic || b.block == advanced || b.block == quantum) continue;
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
