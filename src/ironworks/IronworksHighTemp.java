package ironworks;

import arc.math.Mathf;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.entities.Fires;
import mindustry.gen.Building;
import mindustry.type.Category;
import mindustry.type.Liquid;
import mindustry.type.LiquidStack;
import mindustry.world.Block;
import mindustry.world.blocks.liquid.Conduit;
import mindustry.world.blocks.liquid.LiquidBridge;
import mindustry.world.blocks.liquid.LiquidJunction;
import mindustry.world.blocks.liquid.LiquidRouter;
import ironworks.core.CoreBars;
import ironworks.core.Molten;
import ironworks.multicraft.MultiCrafter;
import ironworks.multicraft.Recipe;

import static mindustry.type.ItemStack.with;

/**
 * 高温与化工线：酸、碳化硅、石墨电极、耐高温管道与配件、石墨坩埚、熔融金属桶。
 */
public class IronworksHighTemp{

    public static Block acidPlant, hotPipe, coolantPipe, hotRouter, hotJunction, hotBridge, crucible;

    public static void load(){
        // —— 制酸车间：硫 + 水 + 电 → 酸 ——
        acidPlant = new MultiCrafter("acid-plant"){{
            requirements(Category.crafting,
                with(Items.titanium, 90, Items.silicon, 60, Items.metaglass, 50, Items.lead, 40));
            size = 2;
            itemCapacity = 20;
            liquidCapacity = 60f;
            recipes = new Recipe[]{
                new Recipe("acid", 60f)
                    .items(with(IronworksMaterials.sulfur, 2))
                    .liquids(new LiquidStack(Liquids.water, 6f))
                    .power(2f)
                    .outputLiquid(new LiquidStack(IronworksLiquids.acid, 6f))
            };
        }};

        // —— 耐高温管道：碳化硅内衬 + 石墨电极 ——
        hotPipe = new Conduit("hot-conduit"){{
            requirements(Category.liquid,
                with(IronworksMaterials.siliconCarbide, 6, IronworksMaterials.graphiteElectrode, 2, Items.graphite, 8));
            liquidCapacity = 40f;
        }};

        // —— 冷却循环管道：闭环专用，断头不泄漏（普通导管会在断头漏液，看起来像"蒸发"）——
        coolantPipe = new Conduit("coolant-conduit"){{
            requirements(Category.liquid,
                with(IronworksItems.steelPlate, 4, Items.silicon, 6, Items.metaglass, 4));
            liquidCapacity = 40f;
            leaks = false;
        }};

        // —— 耐高温路由器 / 交叉器 / 管道桥 ——
        hotRouter = new LiquidRouter("hot-router"){{
            requirements(Category.liquid,
                with(IronworksMaterials.siliconCarbide, 3, Items.graphite, 6));
            liquidCapacity = 40f;
        }};

        hotJunction = new LiquidJunction("hot-junction"){{
            requirements(Category.liquid,
                with(IronworksMaterials.siliconCarbide, 4, Items.graphite, 8));
            liquidCapacity = 40f;
        }};

        hotBridge = new LiquidBridge("hot-bridge"){{
            requirements(Category.liquid,
                with(IronworksMaterials.siliconCarbide, 6, Items.graphite, 10));
            range = 5;
            liquidCapacity = 40f;
        }};

        // —— 石墨坩埚：熔融金属储罐 ——
        crucible = new Crucible("graphite-crucible"){{
            requirements(Category.liquid,
                with(IronworksMaterials.siliconCarbide, 20, IronworksMaterials.graphiteElectrode, 6, Items.graphite, 40));
            size = 2;
            solid = true;
            liquidCapacity = 1500f;
        }};
    }

    /** 石墨坩埚：只收熔融金属，每种液体一条独立储量条。 */
    public static class Crucible extends LiquidRouter{
        public Crucible(String name){
            super(name);
        }

        @Override
        public void setBars(){
            super.setBars();
            // 动态条会显示 liquids.current()，来回跳 → 每种熔融金属各一条（核心库）
            CoreBars.perLiquid(this, IronworksLiquids.moltenIron, IronworksLiquids.moltenSteel);
        }

        public class CrucibleBuild extends LiquidRouterBuild{
            @Override
            public boolean acceptLiquid(Building source, Liquid liquid){
                return molten(liquid) && super.acceptLiquid(source, liquid);
            }
        }
    }

    /** 熔融金属判定（实现在核心库 {@link ironworks.core.Molten}）。 */
    public static boolean molten(Liquid liquid){
        return Molten.isMolten(liquid);
    }

    /** 熔融侵蚀：掉血 + 起火（核心库）。保留入口，老的调用点不用改。 */
    public static void burnMolten(Building b){
        Molten.burn(b);
    }

    /** 给原版液体方块统一套上熔融侵蚀（核心库）。 */
    public static void patchVanillaPipes(){
        Molten.patchVanillaPipes();
    }
}
