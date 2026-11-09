package ironworks;

/**
 * 词条等级的**唯一加算器**。
 *
 * <p>用户要求：同一词条的多级加成先做绝对和，再作为一次基础倍率作用到游戏数据；
 * 不允许把 I/II/III 逐级乘起来。这里集中提供“求和 / 加 / 乘 / 减 / 缩”五个原子操作，
 * 具体哪些词条走哪条路径仍由 {@link Difficulty} 的档位表决定，避免出现第二套公式。</p>
 */
public final class ModifierMath{
    private ModifierMath(){
    }

    /** 已启用等级对应系数的绝对和；mask 的位序与 perLevel 一致。 */
    public static float sum(int mask, float[] perLevel){
        if(perLevel == null || mask == 0) return 0f;
        float sum = 0f;
        for(int i = 0; i < perLevel.length; i++){
            if((mask & (1 << i)) != 0) sum += perLevel[i];
        }
        return sum;
    }

    /** 基础值 + 绝对加成和（生命/护甲/造价等“加算”路径）。 */
    public static float add(float base, int mask, float[] perLevel){
        return base + sum(mask, perLevel);
    }

    /** 基础值 × (1 + 绝对加成和)（建造速度/伤害等“乘算一次”路径）。 */
    public static float scale(float base, int mask, float[] perLevel){
        return base * (1f + sum(mask, perLevel));
    }

    /** 基础值 × (1 - 绝对减益和)（建筑生命上限等“按比例扣减”路径）。 */
    public static float reduce(float base, int mask, float[] perLevel){
        return base * (1f - sum(mask, perLevel));
    }

    /** 基础值 - 绝对减益和（护甲等“减算”路径）。 */
    public static float subtract(float base, int mask, float[] perLevel){
        return base - sum(mask, perLevel);
    }
}
