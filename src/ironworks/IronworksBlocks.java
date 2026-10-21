package ironworks;

import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.world.Block;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.blocks.production.GenericCrafter;

import static mindustry.type.ItemStack.with;

/**
 * 按真实冶金顺序：
 *   磁选(物理) -> 电解水制氢 -> 氢还原炼铁 -> 转炉精炼成钢锭 -> 轧制成钢板
 */
public class IronworksBlocks{

    public static Block separator, reducer, steelWall;

    public static void load(){
        // 一、磁选机：物理分选铁砂，不消耗药剂
        separator = new GenericCrafter("separator"){{
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

        // 六、钢板墙
        steelWall = new Wall("steel-wall"){{
            // 对齐原版相织物墙(600)：原料是沙+水+电，可量产，不能给稀有矿墙强度
            health = 600;
            requirements(Category.defense, with(IronworksItems.steelPlate, 6, Items.copper, 10));
        }};
    }
}
