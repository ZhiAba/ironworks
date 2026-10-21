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
            removeBar("liquid"); // 动态条会显示 liquids.current()，来回跳
            addLiquidBar(IronworksLiquids.moltenIron);
            addLiquidBar(IronworksLiquids.moltenSteel);
        }

        public class CrucibleBuild extends LiquidRouterBuild{
            @Override
            public boolean acceptLiquid(Building source, Liquid liquid){
                return molten(liquid) && super.acceptLiquid(source, liquid);
            }
        }
    }

    /** 熔融金属：只有耐高温管道能安全输送。 */
    public static boolean molten(Liquid liquid){
        return liquid == IronworksLiquids.moltenIron
            || liquid == IronworksLiquids.moltenSteel;
    }

    /** 熔融金属进了普通管道会烧：掉血 + 起火，但**不阻止**它进来。 */
    public static void burnMolten(Building b){
        Liquid cur = b.liquids.current();
        if(cur == null || !molten(cur) || b.liquids.currentAmount() < 0.05f) return;

        b.damage(0.10f * b.delta());   // 约 6 点/秒
        if(b.tile != null && Mathf.chanceDelta(0.001f)){
            Fires.create(b.tile);
        }
    }

    /**
     * 所有原版液体方块：**能接收**熔融金属，但会掉血、起火。
     * 覆盖普通/脉冲/电镀/强化导管、路由器、容器、储罐、交叉器、液体桥、相位导管。
     */
    public static void patchVanillaPipes(){
        for(Block block : new Block[]{Blocks.conduit, Blocks.pulseConduit, Blocks.platedConduit, Blocks.reinforcedConduit}){
            Conduit c = (Conduit)block;
            c.buildType = () -> c.new ConduitBuild(){
                @Override public void handleLiquid(Building source, Liquid liquid, float amount){
                    super.handleLiquid(source, liquid, amount);
                    if(molten(liquid)){
                        noSleep();          // 导管会休眠，熔融金属进来要唤醒
                        damage(0.05f);      // 流动中的熔融金属也会持续烧管壁
                    }
                }

                @Override public void updateTile(){
                    super.updateTile();
                    burnMolten(this);
                }
            };
        }

        for(Block block : new Block[]{Blocks.liquidRouter, Blocks.liquidContainer, Blocks.liquidTank,
                Blocks.reinforcedLiquidRouter, Blocks.reinforcedLiquidContainer, Blocks.reinforcedLiquidTank}){
            LiquidRouter r = (LiquidRouter)block;
            r.buildType = () -> r.new LiquidRouterBuild(){
                @Override public void updateTile(){
                    super.updateTile();
                    burnMolten(this);
                }
            };
        }

        for(Block block : new Block[]{Blocks.liquidJunction, Blocks.reinforcedLiquidJunction}){
            LiquidJunction j = (LiquidJunction)block;
            j.update = true; // 交叉器默认不更新，燃烧需要每 tick 处理
            j.buildType = () -> j.new LiquidJunctionBuild(){
                @Override public void updateTile(){
                    super.updateTile();
                    burnMolten(this);
                }
            };
        }

        for(Block block : new Block[]{Blocks.bridgeConduit, Blocks.phaseConduit}){
            LiquidBridge b = (LiquidBridge)block;
            b.buildType = () -> b.new LiquidBridgeBuild(){
                @Override public void updateTile(){
                    super.updateTile();
                    burnMolten(this);
                }
            };
        }
    }
}
