package ironworks;

import ironworks.core.LiquidKit;
import mindustry.Vars;
import mindustry.content.Liquids;
import mindustry.gen.Building;
import mindustry.type.Liquid;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.ItemBridge;

/** 通液带桥：物品照常跨格传输，同时把液体沿链接送到另一端。 */
public class LiquidBeltBridge extends ItemBridge{

    public float liquidRate = 0.4f;
    public float liquidConsumeTicks = 48f * 60f;
    /** 高级款：接受任意冷却液。 */
    public boolean anyCoolant = false;

    public LiquidBeltBridge(String name){
        super(name);
        hasLiquids = true;
        liquidCapacity = 10f;
        // 关键：ItemBridge.transportTime 默认是 0，updateTransport 的 while 会死循环
        transportTime = 4f;
    }

    public class LiquidBeltBridgeBuild extends ItemBridgeBuild{
        float consumeTimer;
        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            boolean ok = liquid == Liquids.water || liquid == IronworksLiquids.chiller
                || (anyCoolant && (liquid == Liquids.cryofluid || liquid == IronworksLiquids.grapheneCoolant));
            return ok && LiquidKit.hasSpace(this, liquid);
        }

        @Override
        public void updateTile(){
            super.updateTile();

            Liquid cur = liquids.current();
            if(cur == null || liquids.get(cur) <= 0.001f) return;

            if((consumeTimer += delta()) >= liquidConsumeTicks){
                consumeTimer -= liquidConsumeTicks;
                liquids.remove(cur, 1f);
            }

            Tile lt = Vars.world.tile(link);
            Building dest = (lt != null && lt.build != null && lt.build != this) ? lt.build : null;

            if(dest != null && dest.acceptLiquid(this, cur)){
                // 只补到对岸容量为止，不能无限灌（否则会像之前那样冲到 20+）
                float space = dest.block.liquidCapacity - dest.liquids.get(cur);
                if(space > 0.0001f){
                    float amount = Math.min(Math.min(liquids.get(cur), liquidRate * delta()), space);
                    if(amount > 0f){
                        dest.handleLiquid(this, cur, amount);
                        liquids.remove(cur, amount);
                    }
                }
            }

            // 末端侧向输出：把罐里的液体尽量倒给相邻方块（传送带/管道/储罐）
            float remain = liquids.get(cur);
            if(remain <= 0.001f) return;
            for(Building other : proximity){
                if(remain <= 0.001f) break;
                if(other == this || !other.block.hasLiquids) continue;
                Building target = other.getLiquidDestination(this, cur);
                if(target == null || target == this || !target.block.hasLiquids) continue;
                if(!target.acceptLiquid(this, cur)) continue;
                float free = target.block.liquidCapacity - target.liquids.get(cur);
                if(free <= 0.001f) continue;
                float flow = Math.min(Math.min(remain, free), 240f * delta());
                target.handleLiquid(this, cur, flow);
                liquids.remove(cur, flow);
                remain -= flow;
            }
        }
    }
}
