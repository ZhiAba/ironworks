package ironworks;

import arc.math.Mathf;

/**
 * 穿透白值与平方衰减模型（v3.0 战斗体系）。
 *
 * <p>字段含义：
 * <ul>
 *   <li>{@link #rate}：基础穿透率 P_rate，目标护甲不超过白值时保持。</li>
 *   <li>{@link #flat}：穿透白值 P_flat，绝对破防门槛。</li>
 *   <li>{@link #decay}：相对比例平方衰减常数 C_decay，建议 0.2~0.5。</li>
 *   <li>{@link #ricochet}：跳弹增长系数 C_ricochet。</li>
 * </ul>
 *
 * <p>公式：A &gt; P_flat 时
 * {@code P_actual = max(0, rate - decay * ((A - flat) / flat)^2)}；
 * 有效护甲 {@code A_eff = A * (1 - P_actual)}；
 * 百分比减伤 {@code A_eff / (A_eff + K)}，K=100。
 * 不再保留旧版的 10% 刮痧保底。</p>
 */
public final class ArmorPen{
    /** 百分比减伤常数 K。 */
    public static final float armorConstant = 100f;
    /** 跳弹率硬上限。 */
    public static final float maxRicochet = 0.90f;
    /** 完全无穿透/无跳弹的兜底。 */
    public static final ArmorPen none = new ArmorPen(0f, 0f, 0f, 0f);

    /** 基础穿透率 0..1（P_rate）。 */
    public final float rate;
    /** 穿透白值（P_flat），单位是护甲点。 */
    public final float flat;
    /** 平方衰减常数（C_decay）。 */
    public final float decay;
    /** 跳弹增长系数（C_ricochet）。 */
    public final float ricochet;

    public ArmorPen(float rate, float flat, float decay, float ricochet){
        this.rate = Mathf.clamp(rate, 0f, 1f);
        this.flat = Math.max(0f, flat);
        this.decay = Math.max(0f, decay);
        this.ricochet = Math.max(0f, ricochet);
    }

    /** 兼容旧三参数调用：跳弹系数使用保守默认值。 */
    public ArmorPen(float rate, float flat, float decay){
        this(rate, flat, decay, 0.02f);
    }

    /** 兼容旧字段名；新代码统一用 {@link #rate}。 */
    public float rated(){
        return rate;
    }

    /** 兼容旧字段名；新代码统一用 {@link #flat}。 */
    public float threshold(){
        return flat;
    }

    /** 对给定护甲的有效穿透率 0..1。 */
    public float effective(float armor){
        float a = Math.max(0f, armor);
        if(a <= flat) return rate;
        if(flat <= 0.0001f) return 0f;
        float over = (a - flat) / flat;
        return Mathf.clamp(rate - decay * over * over, 0f, rate);
    }

    /** 穿透后仍然生效的护甲值 A_eff。 */
    public float effectiveArmor(float armor){
        return Math.max(0f, armor) * (1f - effective(armor));
    }

    /** 百分比减伤率 A_eff / (A_eff + K)。 */
    public float armorReduction(float armor){
        float eff = effectiveArmor(armor);
        return eff / (eff + armorConstant);
    }

    /** 护甲过滤后的伤害（不含抗性/Combo/保底）。 */
    public float damageAfterArmor(float damage, float armor){
        if(damage <= 0f) return 0f;
        return damage * (1f - armorReduction(armor));
    }

    /** 旧调用兼容：没有 10% 保底。 */
    public float apply(float damage, float armor){
        return damageAfterArmor(damage, armor);
    }

    /** 被护甲过滤掉的伤害。 */
    public float blocked(float damage, float armor){
        return Math.max(0f, damage - damageAfterArmor(damage, armor));
    }

    /** 跳弹率；护甲超过白值才增长，封顶 90%。 */
    public float ricochetChance(float armor){
        float a = Math.max(0f, armor);
        if(a <= flat || ricochet <= 0f) return 0f;
        return Math.min(maxRicochet, (a - flat) * ricochet);
    }

    /** 动能过穿条件：白值至少是当前护甲的 2 倍。 */
    public boolean canOverpen(float armor){
        return flat >= Math.max(0f, armor) * 2f;
    }

    /**
     * 动态过穿保留率：比例刚好 2 倍时 30%，每多 1 倍扣 10 个百分点；
     * 最低 10%。目标护甲 <=0 时直接取最低保底。
     */
    public float overpenKeepRate(float armor){
        if(armor <= 0f) return 0.10f;
        float ratio = flat / armor;
        return Mathf.clamp(0.30f - (ratio - 2f) * 0.10f, 0.10f, 0.30f);
    }

    /** 过穿伤害：基础伤害 × 动态保留率。 */
    public float overpenDamage(float damage, float armor){
        return Math.max(0f, damage) * overpenKeepRate(armor);
    }

    public String percent(){
        return Mathf.round(rate * 100f) + "%";
    }

    public String summary(){
        return "rate=" + Mathf.round(rate * 100f) + "% flat=" + trim(flat)
            + " decay=" + trim(decay) + " ric=" + trim(ricochet);
    }

    private static String trim(float value){
        return value == (int)value ? String.valueOf((int)value) : String.format(java.util.Locale.US, "%.2f", value);
    }
}
