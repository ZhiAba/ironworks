package ironworks;

import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.type.Category;
import mindustry.type.LiquidStack;
import mindustry.world.Block;
import ironworks.multicraft.MultiCrafter;
import ironworks.multicraft.Recipe;

import static mindustry.type.ItemStack.with;

/**
 * 原版材料闭环：把原版那批「有矿就能造」的材料，改走本模组的工业线。
 *
 * 这里不是简单复刻原版工厂，而是给每一条链补上闭环：
 * - 硅 / 玻璃：吃沙与煤，产线复用钢铁线的电与粉料；
 * - 石墨：焦化路线，比原版石墨压机省煤但吃电；
 * - 塑钢 / 相织物：吃石油与钍，副产品回流化工线；
 * - 合金：四元合金，吃本线的硅与钛，产出量翻倍但极慢；
 * - 硫铁矿 / 爆炸物：与弹药线的黑火药共用硫与煤；
 * - 废料再生：原版废料在这里拆回铜/铅/钛/钍，是整张图的回收闭环；
 *   铝线的赤泥也进这里提钍，两条线在这里合流。
 */
public class IronworksVanillaLoops{

    public static Block siliconFurnace, glassKiln, graphitizer, polymerizer,
        phaseWeaver, alloyFurnace, pyroWorks, scrapWorks;

    public static void load(){
        // —— 硅晶炉：沙 + 煤 电还原 ——
        siliconFurnace = new MultiCrafter("silicon-furnace"){{
            requirements(Category.crafting,
                with(Items.copper, 60, Items.lead, 50, Items.silicon, 30, Items.metaglass, 20));
            size = 2;
            itemCapacity = 20;
            liquidCapacity = 20f;
            recipes = new Recipe[]{
                new Recipe("silicon-smelt", 60f)
                    .items(with(Items.sand, 4, Items.coal, 2))
                    .power(2f)
                    .output(with(Items.silicon, 2))
            };
        }};

        // —— 玻璃窑：沙 + 铅 ——
        glassKiln = new MultiCrafter("glass-kiln"){{
            requirements(Category.crafting,
                with(Items.copper, 50, Items.lead, 60, Items.graphite, 20));
            size = 2;
            itemCapacity = 20;
            liquidCapacity = 20f;
            recipes = new Recipe[]{
                new Recipe("metaglass", 60f)
                    .items(with(Items.sand, 3, Items.lead, 2))
                    .power(1.5f)
                    .output(with(Items.metaglass, 2))
            };
        }};

        // —— 石墨化炉：煤焦化 ——
        graphitizer = new MultiCrafter("graphitizer"){{
            requirements(Category.crafting,
                with(IronworksItems.steelPlate, 60, Items.copper, 100, Items.graphite, 40, Items.silicon, 40));
            size = 3;
            itemCapacity = 30;
            liquidCapacity = 20f;
            recipes = new Recipe[]{
                new Recipe("graphite", 90f)
                    .items(with(Items.coal, 4))
                    .power(3f)
                    .output(with(Items.graphite, 3))
            };
        }};

        // —— 聚合釜：钛 + 石油 → 塑钢 ——
        polymerizer = new MultiCrafter("polymerizer"){{
            requirements(Category.crafting,
                with(Items.titanium, 120, Items.silicon, 100, Items.metaglass, 80));
            size = 3;
            itemCapacity = 30;
            liquidCapacity = 50f;
            recipes = new Recipe[]{
                new Recipe("plastanium", 120f)
                    .items(with(Items.titanium, 2))
                    .liquids(new LiquidStack(Liquids.oil, 12f))
                    .power(6f)
                    .output(with(Items.plastanium, 1))
            };
        }};

        // —— 相织机：钍 + 沙 → 相织物 ——
        phaseWeaver = new MultiCrafter("phase-weaver"){{
            requirements(Category.crafting,
                with(Items.titanium, 200, Items.silicon, 200, Items.thorium, 100));
            size = 3;
            itemCapacity = 30;
            liquidCapacity = 20f;
            recipes = new Recipe[]{
                new Recipe("phase-fabric", 150f)
                    .items(with(Items.thorium, 4, Items.sand, 8))
                    .power(10f)
                    .output(with(Items.phaseFabric, 1))
            };
        }};

        // —— 合金炉：四元合金 ——
        alloyFurnace = new MultiCrafter("alloy-furnace"){{
            requirements(Category.crafting,
                with(IronworksItems.steelPlate, 100, Items.silicon, 120, Items.titanium, 100));
            size = 3;
            itemCapacity = 40;
            liquidCapacity = 20f;
            recipes = new Recipe[]{
                new Recipe("surge-alloy", 150f)
                    .items(with(Items.copper, 2, Items.lead, 3, Items.titanium, 2, Items.silicon, 4))
                    .power(12f)
                    .output(with(Items.surgeAlloy, 2))
            };
        }};

        // —— 火工车间：硫铁矿 + 爆炸混合物 ——
        pyroWorks = new MultiCrafter("pyro-works"){{
            requirements(Category.crafting,
                with(Items.copper, 80, Items.lead, 80, Items.graphite, 40));
            size = 2;
            itemCapacity = 20;
            liquidCapacity = 40f;
            recipes = new Recipe[]{
                new Recipe("pyratite", 60f)
                    .items(with(Items.coal, 2, Items.lead, 2, Items.sand, 2))
                    .power(1.5f)
                    .output(with(Items.pyratite, 2)),
                new Recipe("blast-compound", 90f)
                    .items(with(Items.sporePod, 2, Items.pyratite, 2))
                    .liquids(new LiquidStack(Liquids.oil, 4f))
                    .power(3f)
                    .output(with(Items.blastCompound, 1))
            };
        }};

        // —— 废料分选厂：原版废料拆回金属（回收闭环） ——
        scrapWorks = new MultiCrafter("scrap-works"){{
            requirements(Category.crafting,
                with(IronworksItems.steelPlate, 80, Items.silicon, 150, Items.metaglass, 60,
                     IronworksAluminum.aluminumSheet, 60));
            size = 3;
            itemCapacity = 30;
            liquidCapacity = 60f;
            recipes = new Recipe[]{
                new Recipe("scrap-copper", 60f)
                    .items(with(Items.scrap, 5))
                    .power(2f)
                    .output(with(Items.copper, 2)),
                new Recipe("scrap-lead", 60f)
                    .items(with(Items.scrap, 5))
                    .power(2f)
                    .output(with(Items.lead, 2)),
                new Recipe("scrap-titanium", 90f)
                    .items(with(Items.scrap, 8))
                    .liquids(new LiquidStack(IronworksLiquids.acid, 4f))
                    .power(3f)
                    .output(with(Items.titanium, 1)),
                // 赤泥提钍：与铝线合流的闭环点
                new Recipe("scrap-thorium", 120f)
                    .items(with(Items.scrap, 10, IronworksAluminum.redMud, 2))
                    .liquids(new LiquidStack(IronworksLiquids.acid, 6f))
                    .power(4f)
                    .output(with(Items.thorium, 1))
            };
        }};
    }
}
