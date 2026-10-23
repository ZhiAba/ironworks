package ironworks.core;

import mindustry.gen.Building;
import mindustry.type.Liquid;

/**
 * 铁流核心组件库 · 冷却循环层。
 *
 * <p>「耗冷液、产热液」这套东西以前在炮塔、算力集群、反应堆里各写一遍，
 * 每处都要自己算「热侧还剩多少空间、装不下就少消耗、绝不溢出」。
 * 现在统一成这个组件：谁要循环冷却，就在自己的 build 里挂一个实例，调 {@link #convert} 即可。
 *
 * <p>两种槽位都支持：
 * <ul>
 *   <li>真实槽（默认）：热液进同一个液体池，受 {@code liquidCapacity} 限制；</li>
 *   <li>虚拟槽（{@link #virtualHot}）：热液只记账不占池子，容量由 {@link #hotCapacity} 定
 *       （过热炮塔那种"热槽满就停机"的玩法）。</li>
 * </ul>
 *
 * <p>注意：**不会**帮你把冷液从邻居抽进来，也不管在什么条件下该工作——
 * 那是方块自己的事，组件只负责"一次转换"。
 */
public class CoolantLoop{

    /** 冷液（输入）。 */
    public Liquid cold;
    /** 热液（输出），可为 null（只耗冷液、不产热液）。 */
    public Liquid hot;
    /** 每 tick 消耗的冷液量（每秒 = ×60）。 */
    public float usePerTick = 0.04f;
    /** 冷 → 热 转化比。 */
    public float ratio = 1f;
    /** 热液走虚拟槽（不占液体池）。 */
    public boolean virtualHot;
    /** 虚拟槽容量。 */
    public float hotCapacity = 40f;
    /** 虚拟槽存量（仅 virtualHot 时有效）。 */
    public float stored;

    public CoolantLoop(){
    }

    public CoolantLoop(Liquid cold, Liquid hot){
        this.cold = cold;
        this.hot = hot;
    }

    public float coldAmount(Building b){
        return LiquidKit.amount(b, cold);
    }

    public float hotAmount(Building b){
        if(virtualHot) return stored;
        return LiquidKit.amount(b, hot);
    }

    /** 热侧还能装多少（hot 为 null 视为无限）。 */
    public float hotSpace(Building b){
        if(hot == null) return Float.MAX_VALUE;
        if(virtualHot) return Math.max(0f, hotCapacity - stored);
        return LiquidKit.space(b, hot);
    }

    public boolean hotFull(Building b){
        return hotSpace(b) <= LiquidKit.EPS;
    }

    /** acceptLiquid 的冷液侧判定：只收冷液 + 有空间。 */
    public boolean acceptCold(Building self, Building source, Liquid liquid){
        return liquid != null && liquid == cold && LiquidKit.hasSpace(self, cold);
    }

    /**
     * 一次转换：消耗冷液并生成热液。
     *
     * @param active 本 tick 是否该工作（开火中 / 有目标 / 正在运行时……由方块自己判断）
     * @return 实际消耗的冷液量（0 表示没工作或冷液/空间不足）
     */
    public float convert(Building b, boolean active){
        if(!active || b == null || cold == null) return 0f;

        float want = Math.min(usePerTick * b.delta(), coldAmount(b));
        if(want <= LiquidKit.EPS) return 0f;

        // 热侧装不下就少消耗：宁可停机，也不让热液溢出上限
        want = Math.min(want, hotSpace(b) / Math.max(ratio, 0.0001f));
        if(want <= LiquidKit.EPS) return 0f;

        LiquidKit.remove(b, cold, want);
        float produced = want * ratio;

        if(hot != null){
            if(virtualHot){
                stored = Math.min(hotCapacity, stored + produced);
            }else{
                LiquidKit.addCapped(b, hot, produced);
            }
        }
        return want;
    }
}
