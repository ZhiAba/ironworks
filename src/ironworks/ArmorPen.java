package ironworks;

import arc.math.Mathf;

/**
 * 穿甲曲线（v2.6 战斗体系）。
 *
 * <p>不是固定百分比：目标护甲低于阈值时享受额定穿甲率；超过阈值后每点护甲
 * 线性扣减穿甲率，直到 0%。有效护甲 = 护甲 × (1 - 有效穿甲率)，最终伤害
 * 仍保留至少 10%（Mindustry 的防刮痧底线）。</p>
 */
public final class ArmorPen{
    /** 无论护甲多高，至少保留的伤害比例。 */
    public static final float minDamageFraction = 0.1f;
    /** 完全无穿甲。 */
    public static final ArmorPen none = new ArmorPen(0f, 0f, 0f);

    /** 额定穿甲率 0..1（目标护甲 ≤ 阈值时生效）。 */
    public final float rated;
    /** 穿透阈值（护甲点）。 */
    public final float threshold;
    /** 超出阈值后每 1 点护甲扣减的穿甲率（0.025 = 每点 2.5 个百分点）。 */
    public final float decay;

    public ArmorPen(float rated, float threshold, float decay){
        this.rated = Mathf.clamp(rated, 0f, 1f);
        this.threshold = Math.max(0f, threshold);
        this.decay = Math.max(0f, decay);
    }

    /** 对给定护甲的有效穿甲率 0..1。 */
    public float effective(float armor){
        if(armor <= 0f) return rated;
        if(armor <= threshold) return rated;
        return Mathf.clamp(rated - (armor - threshold) * decay, 0f, 1f);
    }

    /** 穿透后仍然生效的护甲值。 */
    public float remainingArmor(float armor){
        return Math.max(0f, armor) * (1f - effective(armor));
    }

    /** 结算护甲后的伤害；保留至少 10%。 */
    public float apply(float damage, float armor){
        if(damage <= 0f) return 0f;
        return Math.max(damage - remainingArmor(armor), damage * minDamageFraction);
    }

    /** 被护甲抵消的伤害（不含 10% 保底产生的虚构成分）。 */
    public float blocked(float damage, float armor){
        return Math.max(0f, damage - apply(damage, armor));
    }

    public String percent(){
        return Mathf.round(rated * 100f) + "%";
    }
}
