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
            for(int i = 0; i < proximity.size && items.total() > 0; i++){
                Building other = proximity.get((i + counter) % proximity.size);
                Item item = items.first();
                if(item != null && other != this && other.acceptItem(this, item)){
                    other.handleItem(this, item);
                    items.remove(item, 1);
                }
            }
            counter++;

            if((timer += delta()) >= liquidStep){
                timer = 0f;
                Liquid cur = liquids.current();
                if(cur != null && liquids.get(cur) > 0.001f){
                    dumpLiquid(cur, 2f);
                }
            }
        }
    }
}
