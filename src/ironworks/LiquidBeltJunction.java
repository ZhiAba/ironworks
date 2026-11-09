package ironworks;

import ironworks.core.LiquidKit;
import mindustry.content.Liquids;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.meta.BlockGroup;

/**
 * 通液交叉器：物品与液体四向转发（路口式），保证一定能工作。
 * 物品按邻居轮询分配；液体按 liquidStep 倾倒。
 */
public class LiquidBeltJunction extends Block{

    public float liquidStep = 20f;
    public boolean anyCoolant = false;
    public LiquidBeltJunction(String name){
        super(name);
        update = true;
        solid = false;
        size = 1;
        hasItems = true;
        itemCapacity = 10;
        hasLiquids = true;
        liquidCapacity = 10f;
        group = BlockGroup.transportation;
        underBullets = true;
        noUpdateDisabled = true;
        unloadable = false;
    }

    public class LiquidBeltJunctionBuild extends Building{
        float timer;
        int counter;
        int liquidCounter;
        /** 每个进入的物品来自哪一侧；出口取它的对侧，保证直穿而不是向两边分。 */
        final arc.struct.IntSeq itemDirs = new arc.struct.IntSeq();

        @Override
        public void handleItem(Building source, Item item){
            if(source != null) itemDirs.add(dirTo(source));
            super.handleItem(source, item);
        }

        int dirTo(Building source){
            int dx = source.tileX() - tileX();
            int dy = source.tileY() - tileY();
            if(Math.abs(dx) >= Math.abs(dy)) return dx > 0 ? 0 : dx < 0 ? 2 : -1;
            return dy > 0 ? 1 : 3;
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            return items.total() < itemCapacity;
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            boolean ok = liquid == Liquids.water || liquid == IronworksLiquids.chiller
                || (anyCoolant && (liquid == Liquids.cryofluid || liquid == IronworksLiquids.grapheneCoolant));
            return ok && LiquidKit.hasSpace(this, liquid);
        }

        @Override
        public void updateTile(){
            // 交叉器只做“直穿”：每个物品从进入侧的对侧离开，绝不向左右两侧同时分流。
            while(items.total() > 0 && itemDirs.size > 0){
                int in = itemDirs.first();
                if(in < 0){
                    itemDirs.removeIndex(0);
                    continue;
                }
                Building target = nearby((in + 2) % 4);
                Item item = items.first();
                if(item == null || target == null || target == this || !target.acceptItem(this, item)) break;
                target.handleItem(this, item);
                items.remove(item, 1);
                itemDirs.removeIndex(0);
            }

            if((timer += delta()) >= liquidStep){
                timer = 0f;
                Liquid cur = liquids.current();
                if(cur != null && liquids.get(cur) > 0.001f){
                    dumpBalanced(cur);
                }
            }
        }

        void dumpBalanced(Liquid liquid){
            int count = 0;
            for(int i = 0; i < proximity.size; i++){
                Building other = proximity.get((i + liquidCounter) % proximity.size);
                Building target = other == this ? null : other.getLiquidDestination(this, liquid);
                if(target != null && target != this && target.acceptLiquid(this, liquid)
                    && LiquidKit.hasSpace(target, liquid)) count++;
            }
            if(count == 0) return;

            float remaining = liquids.get(liquid);
            float share = remaining / count;
            for(int i = 0; i < proximity.size && remaining > 0.001f; i++){
                Building other = proximity.get((i + liquidCounter) % proximity.size);
                Building target = other == this ? null : other.getLiquidDestination(this, liquid);
                if(target == null || target == this) continue;
                remaining -= LiquidKit.give(this, target, liquid, Math.min(share, remaining));
            }
            for(int i = 0; i < proximity.size && remaining > 0.001f; i++){
                Building other = proximity.get((i + liquidCounter) % proximity.size);
                Building target = other == this ? null : other.getLiquidDestination(this, liquid);
                if(target == null || target == this) continue;
                remaining -= LiquidKit.give(this, target, liquid, remaining);
            }
            liquidCounter++;
        }
    }
}
