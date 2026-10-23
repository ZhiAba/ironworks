package ironworks;

import ironworks.core.LiquidKit;
import mindustry.content.Liquids;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.meta.BlockGroup;

/** 通液传送带的路由器：分配物品，同时把带内液体倒给邻居。 */
public class LiquidBeltRouter extends Block{

    public float liquidStep = 20f;
    public float liquidConsumeTicks = 48f * 60f;
    /** 高级款：接受任意冷却液；普通款只收水。 */
    public boolean anyCoolant = false;

    public LiquidBeltRouter(String name){
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

    public class LiquidBeltRouterBuild extends Building{
        float timer;
        int counter;
        float consumeTimer;

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

            if((consumeTimer += delta()) >= liquidConsumeTicks){
                consumeTimer -= liquidConsumeTicks;
                Liquid c2 = liquids.current();
                if(c2 != null) liquids.remove(c2, 1f);
            }

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
