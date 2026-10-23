package ironworks.core;

import mindustry.gen.Building;
import mindustry.type.Liquid;

/**
 * 铁流核心组件库 · 液体层（纯工具，无状态）。
 *
 * <p>这个类只做**原子能力**：判断容量、按容量截断、安全交接、白名单接收。
 * 以前这些判断散落在 10 个文件里，各自写 `liquids.get(x) &lt; block.liquidCapacity - 0.001f`，
 * 一旦有人把 0.001 写成 0.01 就会出「倒灌 / 溢出 / 卡住」的怪 bug。
 *
 * <p>约定：
 * <ul>
 *   <li>所有方法都 **null 安全**（邻居可能是空罐/空路由器，会带 null 来问）；</li>
 *   <li>「空间」永远按**单种液体**算（与引擎 ConsumeLiquid 的判定一致）；</li>
 *   <li>返回值都是**实际发生**的量，不会返回"打算给"的量。</li>
 * </ul>
 */
public final class LiquidKit{

    /** 液体判定的统一容差。改这个值等于改全模组的液体行为，慎重。 */
    public static final float EPS = 0.001f;

    private LiquidKit(){
    }

    public static float amount(Building b, Liquid liquid){
        return (b == null || liquid == null) ? 0f : b.liquids.get(liquid);
    }

    /** 该液体在这种建筑里还能装多少。 */
    public static float space(Building b, Liquid liquid){
        if(b == null || liquid == null) return 0f;
        return Math.max(0f, b.block.liquidCapacity - b.liquids.get(liquid));
    }

    /** 罐内所有液体的总量（160.1 的 LiquidModule 没有 total()，用 each 累加）。 */
    public static float totalAmount(Building b){
        if(b == null || b.liquids == null) return 0f;
        float[] sum = {0f};
        b.liquids.each((liquid, amount) -> sum[0] += amount);
        return sum[0];
    }

    /** 罐子总剩余空间（多液体共用容量时用）。 */
    public static float totalSpace(Building b){
        if(b == null) return 0f;
        return Math.max(0f, b.block.liquidCapacity - totalAmount(b));
    }

    public static boolean hasSpace(Building b, Liquid liquid){
        return space(b, liquid) > EPS;
    }

    public static boolean hasSpace(Building b){
        return totalSpace(b) > EPS;
    }

    /** 加液体并**按容量截断**，返回真正加进去的量。 */
    public static float addCapped(Building b, Liquid liquid, float amount){
        if(b == null || liquid == null || amount <= 0f) return 0f;
        float add = Math.min(amount, space(b, liquid));
        if(add <= 0f) return 0f;
        b.liquids.add(liquid, add);
        return add;
    }

    /**
     * 按**罐子总容量**截断地加液体：多液体共用同一个池子时用这个，
     * 单纯按单液体的空间算会让水和热液一起把罐子撑爆。
     */
    public static float addCappedShared(Building b, Liquid liquid, float amount){
        if(b == null || liquid == null || amount <= 0f) return 0f;
        float add = Math.min(amount, Math.min(space(b, liquid), totalSpace(b)));
        if(add <= 0f) return 0f;
        b.liquids.add(liquid, add);
        return add;
    }

    public static void remove(Building b, Liquid liquid, float amount){
        if(b == null || liquid == null || amount <= 0f) return;
        b.liquids.remove(liquid, amount);
    }

    /** 液体是否在白名单里（白名单可为空 = 谁都不收）。 */
    public static boolean accepts(Liquid liquid, Liquid... allowed){
        if(liquid == null || allowed == null) return false;
        for(Liquid l : allowed){
            if(l == liquid) return true;
        }
        return false;
    }

    /** 白名单 + 有空间，两种判定一次到位（最常用的 acceptLiquid 组合）。 */
    public static boolean acceptOnly(Building self, Building source, Liquid liquid, Liquid... allowed){
        if(self == null || self.block == null || !self.block.hasLiquids) return false;
        return accepts(liquid, allowed) && hasSpace(self, liquid);
    }

    /**
     * 安全交接：目标能收且有空间才给，返回实际给出的量。
     * 用于"把热液/产物推给邻居"这类场景，避免把目标灌爆。
     */
    public static float give(Building from, Building to, Liquid liquid, float amount){
        if(from == null || to == null || to == from || liquid == null || amount <= 0f) return 0f;
        if(!to.block.hasLiquids || to.liquids == null) return 0f;
        if(!to.acceptLiquid(from, liquid)) return 0f;

        float give = Math.min(amount, space(to, liquid));
        if(give <= EPS) return 0f;
        to.handleLiquid(from, liquid, give);
        return give;
    }
}
