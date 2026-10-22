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
 * 循环冷却液系统。
 *
 * 冷液（石墨烯冷却液）→ 设备吸热 → 热液 → 冷却工厂 → 冷液。
 * 冷/热是两种离散液体，不做连续温度仿真；转化率 1:1。
 */
public class IronworksCoolant{

    public static Block grapheneForge, fluidizer, coolantPlant;

    public static void load(){
        // —— 石墨烯炉：石墨 + 氢 + 电 → 石墨烯 ——
        grapheneForge = new MultiCrafter("graphene-forge"){{
            requirements(Category.crafting,
                with(Items.titanium, 100, Items.silicon, 60, Items.graphite, 80, Items.metaglass, 40));
            size = 2;
            itemCapacity = 20;
            liquidCapacity = 40f;
            recipes = new Recipe[]{
                new Recipe("graphene", 60f)
                    .items(with(Items.graphite, 2))
                    .liquids(new LiquidStack(Liquids.hydrogen, 4f))
                    .power(3f)
                    .output(with(IronworksMaterials.graphene, 1)),
                // 酸法：更快，但吃酸
                new Recipe("graphene-acid", 45f)
                    .items(with(Items.graphite, 2))
                    .liquids(new LiquidStack(IronworksLiquids.acid, 4f))
                    .power(2f)
                    .output(with(IronworksMaterials.graphene, 1))
            };
        }};

        // —— 石墨烯流化机：石墨烯 + 水 + 电 → 冷液 ——
        fluidizer = new MultiCrafter("fluidizer"){{
            requirements(Category.crafting,
                with(Items.titanium, 90, Items.silicon, 70, Items.metaglass, 50, Items.lead, 40));
            size = 2;
            itemCapacity = 20;
            liquidCapacity = 60f;
            recipes = new Recipe[]{
                new Recipe("graphene-coolant", 60f)
                    .items(with(IronworksMaterials.graphene, 1))
                    .liquids(new LiquidStack(Liquids.water, 6f))
                    .power(3f)
                    .outputLiquid(new LiquidStack(IronworksLiquids.grapheneCoolant, 6f))
            };
        }};

        // —— 冷却工厂：热液 + 水 + 电 → 冷液 ——
        coolantPlant = new MultiCrafter("coolant-plant"){{
            requirements(Category.crafting,
                with(Items.titanium, 110, Items.silicon, 80, Items.metaglass, 60, Items.lead, 50));
            size = 2;
            itemCapacity = 10;
            liquidCapacity = 60f;
            recipes = new Recipe[]{
                new Recipe("chiller", 60f)
                    .items(with(IronworksPetro.plastic, 1))
                    .liquids(new LiquidStack(Liquids.water, 10f))
                    .power(1.5f)
                    .outputLiquid(new LiquidStack(IronworksLiquids.chiller, 10f)),
                new Recipe("recool", 60f)
                    .liquids(new LiquidStack(IronworksLiquids.hotCoolant, 6f),
                             new LiquidStack(Liquids.water, 6f))
                    .power(2f)
                    .outputLiquid(new LiquidStack(IronworksLiquids.grapheneCoolant, 6f))
            };
        }};
    }
}
