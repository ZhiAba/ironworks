package ironworks.core;

import arc.math.Mathf;
import mindustry.content.Blocks;
import mindustry.entities.Fires;
import mindustry.gen.Building;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.blocks.liquid.Conduit;
import mindustry.world.blocks.liquid.LiquidBridge;
import mindustry.world.blocks.liquid.LiquidJunction;
import mindustry.world.blocks.liquid.LiquidRouter;
import ironworks.IronworksLiquids;

/**
 * 铁流核心组件库 · 熔融侵蚀层。
 *
 * <p>规约（和以前完全一致，只是收进核心库）：
 * <ul>
 *   <li>{@link #isMolten}：哪些液体算熔融金属；</li>
 *   <li>{@link #burn}：熔融金属待在里面会「掉血 + 偶尔起火」，**不阻止**它进来；</li>
 *   <li>{@link #patchVanillaPipes}：给原版液体方块统一套一层每 tick 侵蚀。</li>
 * </ul>
 *
 * <p>以前 {@code patchVanillaPipes()} 里把同一个匿名 build 抄了 5 遍，
 * 现在只剩「把哪几类方块交给包装器」这张表。
 */
public final class Molten{

    private Molten(){
    }

    public static boolean isMolten(Liquid liquid){
        return liquid == IronworksLiquids.moltenIron || liquid == IronworksLiquids.moltenSteel;
    }

    /** 熔融金属进了普通管道会烧：掉血 + 起火（约 6 点/秒）。 */
    public static void burn(Building b){
        if(b == null || b.liquids == null) return;
        Liquid cur = b.liquids.current();
        if(cur == null || !isMolten(cur) || b.liquids.currentAmount() < 0.05f) return;

        b.damage(0.10f * b.delta());
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
                    if(isMolten(liquid)){
                        noSleep();          // 导管会休眠，熔融金属进来要唤醒
                        damage(0.05f);      // 流动中的熔融金属也会持续烧管壁
                    }
                }

                @Override public void updateTile(){
                    super.updateTile();
                    burn(this);
                }
            };
        }

        for(Block block : new Block[]{Blocks.liquidRouter, Blocks.liquidContainer, Blocks.liquidTank,
                Blocks.reinforcedLiquidRouter, Blocks.reinforcedLiquidContainer, Blocks.reinforcedLiquidTank}){
            LiquidRouter r = (LiquidRouter)block;
            r.buildType = () -> r.new LiquidRouterBuild(){
                @Override public void updateTile(){
                    super.updateTile();
                    burn(this);
                }
            };
        }

        for(Block block : new Block[]{Blocks.liquidJunction, Blocks.reinforcedLiquidJunction}){
            LiquidJunction j = (LiquidJunction)block;
            j.update = true; // 交叉器默认不更新，燃烧需要每 tick 处理
            j.buildType = () -> j.new LiquidJunctionBuild(){
                @Override public void updateTile(){
                    super.updateTile();
                    burn(this);
                }
            };
        }

        for(Block block : new Block[]{Blocks.bridgeConduit, Blocks.phaseConduit}){
            LiquidBridge b = (LiquidBridge)block;
            b.buildType = () -> b.new LiquidBridgeBuild(){
                @Override public void updateTile(){
                    super.updateTile();
                    burn(this);
                }
            };
        }
    }
}
