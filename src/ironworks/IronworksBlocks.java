package ironworks;

import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.world.Block;

import static mindustry.type.ItemStack.with;

/**
 * 按真实冶金顺序：
 *   磁选(物理) -> 电解水制氢 -> 氢还原炼铁 -> 转炉精炼成钢锭 -> 轧制成钢板
 */
public class IronworksBlocks{

    public static Block separator, reducer,
        steelWall, graphiteWall, leadWall, ceramicWall, compositeWall, phaseWall, tungstenWall,
        copperPlateWall, ironPlateWall, aluminumPlateWall, titaniumPlateWall, surgePlateWall,
        steelWallLarge, graphiteWallLarge, leadWallLarge, ceramicWallLarge,
        compositeWallLarge, phaseWallLarge, tungstenWallLarge,
        copperPlateWallLarge, ironPlateWallLarge, aluminumPlateWallLarge,
        titaniumPlateWallLarge, surgePlateWallLarge;

    public static void load(){
        // 一、磁选机：物理分选铁砂，不消耗药剂
        separator = new DifficultyCrafter("separator"){{
            requirements(Category.crafting, with(Items.copper, 50, Items.lead, 30));

            craftEffect = Fx.pulverizeMedium;
            outputItem = new ItemStack(IronworksItems.ironConcentrate, 1);
            craftTime = 45f;
            size = 2;
            itemCapacity = 20;

            consumeItems(with(Items.sand, 3));
        }};

        // 三、氢还原炉：Fe2O3 + 3H2 -> 2Fe + 3H2O
        // 用 MultiCrafter 而不是 GenericCrafter：它只接受当前配方要用的液体，
        // 否则邻居会把氧气/铁水倒进来把罐子占满。
        reducer = new ironworks.multicraft.MultiCrafter("reducer"){{
            requirements(Category.crafting,
                with(Items.titanium, 90, Items.metaglass, 50, Items.silicon, 30));
            size = 2;
            itemCapacity = 20;
            liquidCapacity = 40f;
            recipes = new ironworks.multicraft.Recipe[]{
                new ironworks.multicraft.Recipe("reduce-iron", 90f)
                    .items(with(IronworksItems.ironConcentrate, 1))
                    .liquids(new LiquidStack(Liquids.hydrogen, 9f))
                    .output(with(IronworksItems.ironIngot, 1))
                    .outputLiquid(new LiquidStack(Liquids.water, 3f))
            };
        }};

        // 六、墙体系：同一血量/造价区间内做「装甲、抗性、造价、建造时间」的取舍。
        // 原版墙的参考：thorium 800/0 甲、tungsten 720/14 甲、phase 600/0 甲。
        steelWall = new ResistWall("steel-wall"){{
            // 基准墙：便宜、建造快，无特殊抗性
            health = 600;
            armor = 0;
            requirements(Category.defense, with(IronworksItems.steelPlate, 6, Items.copper, 10));
        }};

        graphiteWall = new ResistWall("graphite-wall"){{
            // 早期耐火墙：便宜、快建，血量低；防火但不防动能
            health = 360;
            armor = 0;
            buildTime = 32f / 6f;
            requirements(Category.defense, with(Items.graphite, 6, Items.copper, 4));
        }};

        leadWall = new ResistWall("lead-wall"){{
            // 铅衬墙：抗水/腐蚀/能量，血量中等；怕火，造价偏高
            health = 720;
            armor = 5f;
            buildTime = 70f / 6f;
            requirements(Category.defense, with(IronworksMaterials.leadPlate, 6, IronworksItems.steelPlate, 2));
        }};

        ceramicWall = new ResistWall("ceramic-wall"){{
            // 陶瓷复合墙：高耐火、抗雷/冰；能量和重动能是弱点
            health = 680;
            armor = 12f;
            buildTime = 96f / 6f;
            requirements(Category.defense,
                with(IronworksMaterials.siliconCarbide, 5, Items.metaglass, 4, Items.graphite, 2));
        }};

        compositeWall = new ResistWall("composite-wall"){{
            // 复合装甲：高护甲、高血量，专吃动能；能量武器完全绕过护甲
            health = 880;
            armor = 30f;
            buildTime = 150f / 6f;
            requirements(Category.defense,
                with(IronworksItems.steelPlate, 8, IronworksMaterials.siliconCarbide, 4,
                     IronworksAluminum.aluminumSheet, 3));
        }};

        phaseWall = new ResistWall("phase-wall"){{
            // 相位屏障：高能量抗性、轻甲、低血量；纯动能是明确弱点
            health = 460;
            armor = 0f;
            buildTime = 120f / 6f;
            requirements(Category.defense,
                with(Items.phaseFabric, 4, Items.silicon, 20, IronworksItems.steelPlate, 2));
        }};

        tungstenWall = new ResistWall("tungsten-wall"){{
            // 重钨墙：最高血量 + 高护甲，造价和建造时间也最高；怕能量
            health = 980;
            armor = 24f;
            buildTime = 180f / 6f;
            requirements(Category.defense, with(Items.tungsten, 6, IronworksItems.steelPlate, 4));
        }};

        // ── 2×2 大型墙：按原版 large wall 规则（4 倍血量/造价、同甲、同抗性）──
        // 原版大型墙对照：tungsten-large 2880/14、carbide-large 4320/16、
        // phase-large 2400/0、plastanium-large 2000/0；这里保留模组小墙的「同材料增强」比例。
        steelWallLarge = new ResistWall("steel-wall-large"){{
            health = 2400;      // 600 × 4
            armor = 0;
            size = 2;
            buildTime = 336f / 6f;
            requirements(Category.defense,
                with(IronworksItems.steelPlate, 24, Items.copper, 40));
        }};

        graphiteWallLarge = new ResistWall("graphite-wall-large"){{
            health = 1440;      // 360 × 4
            armor = 0;
            size = 2;
            buildTime = 128f / 6f;
            requirements(Category.defense, with(Items.graphite, 24, Items.copper, 16));
        }};

        leadWallLarge = new ResistWall("lead-wall-large"){{
            health = 2880;      // 720 × 4
            armor = 5f;
            size = 2;
            buildTime = 280f / 6f;
            requirements(Category.defense,
                with(IronworksMaterials.leadPlate, 24, IronworksItems.steelPlate, 8));
        }};

        ceramicWallLarge = new ResistWall("ceramic-wall-large"){{
            health = 2720;      // 680 × 4
            armor = 12f;
            size = 2;
            buildTime = 384f / 6f;
            requirements(Category.defense,
                with(IronworksMaterials.siliconCarbide, 20, Items.metaglass, 16, Items.graphite, 8));
        }};

        compositeWallLarge = new ResistWall("composite-wall-large"){{
            health = 3520;      // 880 × 4
            armor = 30f;
            size = 2;
            buildTime = 600f / 6f;
            requirements(Category.defense,
                with(IronworksItems.steelPlate, 32, IronworksMaterials.siliconCarbide, 16,
                     IronworksAluminum.aluminumSheet, 12));
        }};

        phaseWallLarge = new ResistWall("phase-wall-large"){{
            health = 1840;      // 460 × 4
            armor = 0f;
            size = 2;
            buildTime = 480f / 6f;
            requirements(Category.defense,
                with(Items.phaseFabric, 16, Items.silicon, 80, IronworksItems.steelPlate, 8));
        }};

        tungstenWallLarge = new ResistWall("tungsten-wall-large"){{
            health = 3920;      // 980 × 4
            armor = 24f;
            size = 2;
            buildTime = 720f / 6f;
            requirements(Category.defense,
                with(Items.tungsten, 24, IronworksItems.steelPlate, 16));
        }};

        // ── 基础板材墙：参考钢板墙，不做元素特化，只按材料做血量/护甲/造价梯度 ──
        // 覆盖铜板、铁板、铝板、钛板、巨浪合金板；钢板墙和铅衬防蚀墙已存在，
        // 不再重复造同定位的铅板墙。
        copperPlateWall = new ResistWall("copper-plate-wall"){{
            health = 400;
            armor = 0;
            buildTime = 56f / 6f;
            requirements(Category.defense, with(IronworksMaterials.copperPlate, 6, Items.copper, 6));
        }};

        ironPlateWall = new ResistWall("iron-plate-wall"){{
            health = 520;
            armor = 0;
            buildTime = 72f / 6f;
            requirements(Category.defense, with(IronworksMaterials.ironPlate, 8, Items.copper, 6));
        }};

        aluminumPlateWall = new ResistWall("aluminum-plate-wall"){{
            // 轻质快建，但铝板本身贵，血量和护甲不占优
            health = 620;
            armor = 1f;
            buildTime = 64f / 6f;
            requirements(Category.defense,
                with(IronworksAluminum.aluminumSheet, 8, IronworksItems.steelPlate, 2));
        }};

        titaniumPlateWall = new ResistWall("titanium-plate-wall"){{
            health = 840;
            armor = 6f;
            buildTime = 120f / 6f;
            requirements(Category.defense,
                with(IronworksMaterials.titaniumPlate, 8, IronworksItems.steelPlate, 2));
        }};

        surgePlateWall = new ResistWall("surge-plate-wall"){{
            // 巨浪合金板墙：高血量/护甲，保留原版巨浪墙的受击闪电
            health = 960;
            armor = 8f;
            buildTime = 150f / 6f;
            lightningChance = 0.05f;
            requirements(Category.defense,
                with(Items.surgeAlloy, 6, IronworksItems.steelPlate, 4));
        }};

        // ── 基础板材墙的 2×2 版本：4 倍血量/造价，同甲 ──
        copperPlateWallLarge = new ResistWall("copper-plate-wall-large"){{
            health = 1600;
            armor = 0;
            size = 2;
            buildTime = 224f / 6f;
            requirements(Category.defense, with(IronworksMaterials.copperPlate, 24, Items.copper, 24));
        }};

        ironPlateWallLarge = new ResistWall("iron-plate-wall-large"){{
            health = 2080;
            armor = 0;
            size = 2;
            buildTime = 288f / 6f;
            requirements(Category.defense, with(IronworksMaterials.ironPlate, 32, Items.copper, 24));
        }};

        aluminumPlateWallLarge = new ResistWall("aluminum-plate-wall-large"){{
            health = 2480;
            armor = 1f;
            size = 2;
            buildTime = 256f / 6f;
            requirements(Category.defense,
                with(IronworksAluminum.aluminumSheet, 32, IronworksItems.steelPlate, 8));
        }};

        titaniumPlateWallLarge = new ResistWall("titanium-plate-wall-large"){{
            health = 3360;
            armor = 6f;
            size = 2;
            buildTime = 480f / 6f;
            requirements(Category.defense,
                with(IronworksMaterials.titaniumPlate, 32, IronworksItems.steelPlate, 8));
        }};

        surgePlateWallLarge = new ResistWall("surge-plate-wall-large"){{
            health = 3840;
            armor = 8f;
            size = 2;
            buildTime = 600f / 6f;
            lightningChance = 0.05f;
            requirements(Category.defense,
                with(Items.surgeAlloy, 24, IronworksItems.steelPlate, 16));
        }};

        // —— 抗性表：只登记“额外减伤”，护甲仍走原版 armor 字段 ——
        Resistance.set(graphiteWall.name, DamageType.fire, 0.55f);

        Resistance.set(leadWall.name, DamageType.water, 0.45f);
        Resistance.set(leadWall.name, DamageType.corrosive, 0.55f);
        Resistance.setEnergy(leadWall.name, 0.20f);

        Resistance.set(ceramicWall.name, DamageType.fire, 0.65f);
        Resistance.set(ceramicWall.name, DamageType.thunder, 0.20f);
        Resistance.set(ceramicWall.name, DamageType.ice, 0.15f);
        Resistance.set(ceramicWall.name, DamageType.physical, 0.10f);

        Resistance.set(compositeWall.name, DamageType.physical, 0.18f);
        Resistance.set(compositeWall.name, DamageType.fire, 0.10f);

        Resistance.set(phaseWall.name, DamageType.thunder, 0.15f);
        Resistance.set(phaseWall.name, DamageType.fire, 0.20f);
        Resistance.setEnergy(phaseWall.name, 0.70f);

        Resistance.set(tungstenWall.name, DamageType.physical, 0.12f);
        Resistance.set(tungstenWall.name, DamageType.fire, 0.18f);

        // 大型墙继承小墙的完整抗性表（元素 + 能量），护甲则在定义里同值。
        copyResistance(steelWall.name, steelWallLarge.name);
        copyResistance(graphiteWall.name, graphiteWallLarge.name);
        copyResistance(leadWall.name, leadWallLarge.name);
        copyResistance(ceramicWall.name, ceramicWallLarge.name);
        copyResistance(compositeWall.name, compositeWallLarge.name);
        copyResistance(phaseWall.name, phaseWallLarge.name);
        copyResistance(tungstenWall.name, tungstenWallLarge.name);
    }

    /** 大型墙直接继承小墙的元素/能量抗性，避免两处数值漂移。 */
    private static void copyResistance(String from, String to){
        for(DamageType type : DamageType.values()){
            float value = Resistance.element(from, type);
            if(value > 0f) Resistance.set(to, type, value);
        }
        Resistance.setEnergy(to, Resistance.energy(from));
    }
}
