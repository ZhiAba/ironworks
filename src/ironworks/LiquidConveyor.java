package ironworks;

import arc.Core;
import arc.math.Mathf;
import mindustry.content.Liquids;
import mindustry.entities.Fires;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.world.blocks.distribution.Conveyor;
import mindustry.world.meta.Stat;

/**
 * 传送带通液。
 *
 * 设计：继承原版 Conveyor（保留物品移动的全部逻辑），同时打开液体模块：
 * - 液体沿传送带前进方向流动，断头会泄漏（和导管一致）；
 * - 液体容量极小、流动很慢（每 6 tick 推一次），所以不能替代管道；
 * - 带上有液体时物品移动加速；
 * - 被摧毁时带内液体淬灭火焰。
 */
public class LiquidConveyor extends Conveyor{

    /** 是否接受任意冷却液（高级款）；false = 只收水。 */
    public boolean anyCoolant = false;
    /** 各种液体对应的速度倍率。 */
    public float waterBoost = 1.15f, cryoBoost = 1.25f, grapheneBoost = 1.55f;
    /** 液体每多少 tick 前进一步（越大越慢）。 */
    public float liquidStep = 20f;
    /** 每格每多少 tick 消耗 1 单位液体（默认 48 秒）。 */
    public float liquidConsumeTicks = 48f * 60f;

    public LiquidConveyor(String name){
        super(name);
        hasLiquids = true;
        liquidCapacity = 10f;
    }

    @Override
    public void setStats(){
        super.setStats();
        if(anyCoolant){
            stats.add(Stat.booster, IronworksStats.liquidBoosts("stat.ironworks-action-belt",
                Liquids.water, waterBoost, -1f,
                Liquids.cryofluid, cryoBoost, -1f,
                IronworksLiquids.grapheneCoolant, grapheneBoost, -1f));
        }else{
            stats.add(Stat.booster, IronworksStats.liquidBoosts("stat.ironworks-action-belt",
                Liquids.water, waterBoost, -1f));
        }
    }

    public float boostFor(Liquid liquid){
        if(liquid == null) return 1f;
        if(liquid == Liquids.water) return waterBoost;
        if(liquid == Liquids.cryofluid) return cryoBoost;
        if(liquid == IronworksLiquids.grapheneCoolant) return grapheneBoost;
        return 1f;
    }

    public boolean accepts(Liquid liquid){
        if(liquid == Liquids.water) return true;
        return anyCoolant && (liquid == Liquids.cryofluid || liquid == IronworksLiquids.grapheneCoolant);
    }

    public class LiquidConveyorBuild extends ConveyorBuild{
        public float liquidTimer;
        public float consumeTimer;

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            return accepts(liquid) && liquids.get(liquid) < block.liquidCapacity - 0.001f;
        }

        @Override
        public void handleLiquid(Building source, Liquid liquid, float amount){
            super.handleLiquid(source, liquid, amount);
            noSleep();
        }

        @Override
        public void sleep(){
            // 带上有液体时不能睡，否则液体不再流动
            if(liquids != null && liquids.currentAmount() > 0.001f) return;
            super.sleep();
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            // 原版传送带侧向进料要求 minitem > 0.7，实际很难喂进去；
            // 通液带直接放开：任意方向、有空位就收，方便做"带桥路由"网络。
            return len < 3 && items.total() < itemCapacity;
        }

        @Override
        public float edelta(){
            Liquid cur = liquids == null ? null : liquids.current();
            boolean wet = cur != null && liquids.currentAmount() > 0.001f;
            return super.edelta() * (wet ? boostFor(cur) : 1f);
        }

        @Override
        public void updateTile(){
            super.updateTile();

            Liquid cur = liquids.current();
            if(cur != null && liquids.get(cur) > 0.001f){
                noSleep();

                // 带内液体不是永久的：每格每 48 秒蒸耗 1 单位
                if((consumeTimer += delta()) >= liquidConsumeTicks){
                    consumeTimer -= liquidConsumeTicks;
                    liquids.remove(cur, 1f);
                }

                // 极慢的液体推进：每 liquidStep 帧走一格
                if((liquidTimer += delta()) >= liquidStep){
                    liquidTimer = 0f;
                    moveLiquidForward(true, cur);
                }
            }
        }

        @Override
        public void onDestroyed(){
            if(liquids != null && liquids.currentAmount() > 0.01f && tile != null){
                Fires.extinguish(tile, 100f);
            }
            super.onDestroyed();
        }
    }
}
