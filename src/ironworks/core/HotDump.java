package ironworks.core;

import arc.util.Time;
import mindustry.gen.Building;
import mindustry.type.Liquid;

/**
 * 铁流核心组件库 · 热液排放层。
 *
 * <p>所有「设备把热冷却液往外倒」的地方共用这一套：
 * 逐个邻居问 {@code getLiquidDestination → acceptLiquid → 剩余空间}，
 * 按单 tick 上限（{@link #PER_TICK_CAP} × Time.delta）截断，绝不灌爆邻居。
 *
 * <p>以前这段循环在过热炮塔里写了两遍（真实槽 / 虚拟槽各一遍），
 * 算力集群和反应堆又各自抄了一套变体。
 */
public final class HotDump{

    /** 单个邻居单 tick 的排放上限（乘 Time.delta）。 */
    public static final float PER_TICK_CAP = 240f;

    private HotDump(){
    }

    /** 从液体池里把 hot 往四邻倒，返回实际倒出的量。 */
    public static float drain(Building from, Liquid liquid){
        if(from == null || liquid == null) return 0f;
        float remain = from.liquids.get(liquid);
        if(remain <= LiquidKit.EPS) return 0f;

        float pushed = push(from, liquid, remain);
        if(pushed > 0f) from.liquids.remove(liquid, pushed);
        return pushed;
    }

    /** 把给定存量往四邻倒，返回**剩下的**存量（虚拟热槽用：不占液体池，只扣变量）。 */
    public static float spill(Building from, Liquid liquid, float stash){
        if(from == null || liquid == null || stash <= LiquidKit.EPS) return stash;
        return stash - push(from, liquid, stash);
    }

    /** 公共推液循环：每个邻居能收多少收多少，返回总共推出去的量。 */
    private static float push(Building from, Liquid liquid, float budget){
        float cap = Time.delta * PER_TICK_CAP;
        float pushed = 0f;
        float remain = budget;

        for(Building other : from.proximity){
            if(remain <= LiquidKit.EPS) break;

            Building dest = other.getLiquidDestination(from, liquid);
            if(dest == null || dest == from || !dest.block.hasLiquids) continue;
            if(!dest.acceptLiquid(from, liquid)) continue;

            float space = LiquidKit.space(dest, liquid);
            if(space <= LiquidKit.EPS) continue;

            float flow = Math.min(Math.min(remain, space), cap);
            if(flow <= LiquidKit.EPS) continue;

            dest.handleLiquid(from, liquid, flow);
            pushed += flow;
            remain -= flow;
        }
        return pushed;
    }
}
