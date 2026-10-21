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
 * 桶装液体物流。
 * 封装机与拆封机都用多配方：水/石油两套，共用同一个方块。
 */
public class IronworksBarrels{

    public static Block barrelFiller, barrelEmptier;

    public static void load(){
        // 封装：液体 → 桶（每桶消耗 30 单位液体）
        barrelFiller = new MultiCrafter("barrel-filler"){{
            requirements(Category.crafting, with(Items.copper, 50, Items.lead, 30, Items.metaglass, 20));
            size = 2;
            itemCapacity = 20;
            liquidCapacity = 60f;
            recipes = new Recipe[]{
                new Recipe("water-barrel", 45f)
                    .items(with(IronworksMaterials.copperPlate, 1))
                    .liquids(new LiquidStack(Liquids.water, 30f))
                    .power(1f)
                    .output(with(IronworksMaterials.waterBarrel, 1)),
                new Recipe("oil-barrel", 45f)
                    .items(with(IronworksMaterials.copperPlate, 1))
                    .liquids(new LiquidStack(Liquids.oil, 30f))
                    .power(1f)
                    .output(with(IronworksMaterials.oilBarrel, 1)),
                new Recipe("molten-iron-barrel", 60f)
                    .items(with(IronworksMaterials.ironPlate, 1))
                    .liquids(new LiquidStack(IronworksLiquids.moltenIron, 30f))
                    .power(2f)
                    .output(with(IronworksMaterials.moltenIronBarrel, 1)),
                new Recipe("molten-steel-barrel", 60f)
                    .items(with(IronworksItems.steelPlate, 1))
                    .liquids(new LiquidStack(IronworksLiquids.moltenSteel, 30f))
                    .power(2f)
                    .output(with(IronworksMaterials.moltenSteelBarrel, 1))
            };
        }};

        // 拆封：桶 → 液体（桶材销毁，只回收液体）
        barrelEmptier = new MultiCrafter("barrel-emptier"){{
            requirements(Category.crafting, with(Items.copper, 50, Items.lead, 30, Items.metaglass, 20));
            size = 2;
            itemCapacity = 20;
            liquidCapacity = 60f;
            recipes = new Recipe[]{
                new Recipe("water-barrel", 30f)
                    .items(with(IronworksMaterials.waterBarrel, 1))
                    .power(1f)
                    .outputLiquid(new LiquidStack(Liquids.water, 30f)),
                new Recipe("oil-barrel", 30f)
                    .items(with(IronworksMaterials.oilBarrel, 1))
                    .power(1f)
                    .outputLiquid(new LiquidStack(Liquids.oil, 30f)),
                new Recipe("molten-iron-barrel", 40f)
                    .items(with(IronworksMaterials.moltenIronBarrel, 1))
                    .power(2f)
                    .outputLiquid(new LiquidStack(IronworksLiquids.moltenIron, 30f)),
                new Recipe("molten-steel-barrel", 40f)
                    .items(with(IronworksMaterials.moltenSteelBarrel, 1))
                    .power(2f)
                    .outputLiquid(new LiquidStack(IronworksLiquids.moltenSteel, 30f))
            };
        }};
    }
}
