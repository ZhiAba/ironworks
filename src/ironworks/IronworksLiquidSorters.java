package ironworks;

import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.world.Block;

import static mindustry.type.ItemStack.with;

/** 液体分类器：普通/反向两档，工作方式对应原版物品分类器。 */
public class IronworksLiquidSorters{
    public static Block sorter, inverted;

    public static void load(){
        sorter = new LiquidSorter("liquid-sorter"){{
            requirements(Category.distribution,
                with(Items.metaglass, 10, Items.graphite, 10, IronworksItems.steelPlate, 10));
        }};

        inverted = new LiquidSorter("liquid-inverted-sorter"){{
            requirements(Category.distribution,
                with(Items.metaglass, 10, Items.graphite, 10, IronworksItems.steelPlate, 10));
            invert = true;
        }};
    }
}
