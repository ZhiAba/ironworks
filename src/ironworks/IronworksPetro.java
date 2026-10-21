package ironworks;

import arc.graphics.Color;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.LiquidStack;
import mindustry.world.Block;
import ironworks.multicraft.MultiCrafter;
import ironworks.multicraft.Recipe;

import static mindustry.type.ItemStack.with;

/**
 * 石油基工业：原油从"直接烧掉"变成一条真正的化工线。
 *
 * 闭环点：
 * 1. 分馏出的重油去焦化 → 石油焦 + 汽油；石油焦进石墨化炉 → 石墨 → 电极 → 铝电解；
 * 2. 加氢裂化吃电解车间副产的氢气，把重油变成汽油；
 * 3. 汽油 + 硫（脱硫塔）→ 橡胶；汽油 + 氧化铝（催化剂）→ 塑料；
 * 4. 汽油装桶后送弹药组装机灌装凝固汽油弹 —— 汽油弹的"汽油"终于有提纯来源。
 */
public class IronworksPetro{

    public static Item rubber, plastic, petcoke;
    public static Block refinery, polymerWorks;

    public static void load(){
        rubber  = new Item("rubber",  Color.valueOf("4a4a4e")){{ cost = 2.8f; hardness = 2; }};
        plastic = new Item("plastic", Color.valueOf("dfe6ee")){{ cost = 3.0f; hardness = 2; }};
        petcoke = new Item("petcoke", Color.valueOf("2e2c2a")){{ cost = 1.4f; hardness = 2; }};

        // —— 炼油厂：常压分馏 / 延迟焦化 / 加氢裂化 ——
        refinery = new MultiCrafter("refinery"){{
            requirements(Category.crafting,
                with(IronworksItems.steelPlate, 100, Items.silicon, 120, Items.metaglass, 80,
                     IronworksMaterials.titaniumPlate, 40));
            size = 3;
            itemCapacity = 30;
            liquidCapacity = 60f;
            // 汽油走右、重油走左
            liquidOutputDirections = new int[]{0, 2};
            recipes = new Recipe[]{
                new Recipe("fractionation", 90f)
                    .liquids(new LiquidStack(Liquids.oil, 12f))
                    .power(4f)
                    .outputLiquid(new LiquidStack(IronworksLiquids.gasoline, 6f),
                                  new LiquidStack(IronworksLiquids.heavyOil, 5f)),
                new Recipe("coking", 120f)
                    .liquids(new LiquidStack(IronworksLiquids.heavyOil, 10f))
                    .power(5f)
                    .output(with(petcoke, 2))
                    .outputLiquid(new LiquidStack(IronworksLiquids.gasoline, 2f)),
                new Recipe("hydrocracking", 110f)
                    .liquids(new LiquidStack(IronworksLiquids.heavyOil, 8f),
                             new LiquidStack(Liquids.hydrogen, 4f))
                    .power(6f)
                    .outputLiquid(new LiquidStack(IronworksLiquids.gasoline, 7f))
            };
        }};

        // —— 聚合车间：橡胶 / 塑料 ——
        polymerWorks = new MultiCrafter("polymer-works"){{
            requirements(Category.crafting,
                with(IronworksItems.steelPlate, 80, Items.silicon, 100, Items.titanium, 60));
            size = 3;
            itemCapacity = 30;
            liquidCapacity = 60f;
            recipes = new Recipe[]{
                new Recipe("rubber", 90f)
                    .items(with(IronworksMaterials.sulfur, 1))
                    .liquids(new LiquidStack(IronworksLiquids.gasoline, 6f),
                             new LiquidStack(Liquids.water, 3f))
                    .power(3f)
                    .output(with(rubber, 2)),
                new Recipe("plastic", 90f)
                    .items(with(IronworksAluminum.alumina, 1))
                    .liquids(new LiquidStack(IronworksLiquids.gasoline, 8f))
                    .power(4f)
                    .output(with(plastic, 2))
            };
        }};
    }
}
