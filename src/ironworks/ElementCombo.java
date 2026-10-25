package ironworks;

import arc.Core;
import arc.struct.ObjectMap;
import arc.util.Time;
import mindustry.gen.Healthc;
import mindustry.gen.Unit;
import mindustry.type.StatusEffect;
import mindustry.type.UnitType;

/**
 * 属性附着与 Combo（v2.6 战斗体系）。
 *
 * <p>不使用原版「状态自动反应」判定。每个单位只保留一层自己的元素附着
 * （{@link Aura}），命中时按「本属性 + 旧附着」查表乘倍率；触发 Combo 后
 * 消耗旧附着，再写入本次属性。原版 StatusEffect 只负责可见图标与减速等
 * 基础效果，不参与倍率计算。</p>
 */
public final class ElementCombo{
    public static final float baseMultiplier = 1f;
    /** 附着基础持续时间（秒）；至少保留到下一次攻击。 */
    public static final float auraSeconds = 4f;
    /** 内部按 tick 计时（Mindustry 的 Time.time 是 tick，不是秒）。 */
    public static final float auraTicks = auraSeconds * 60f;

    private static final Combo[] combos = {
        new Combo(DamageType.thunder, DamageType.water, 1.50f, "感电", true),
        new Combo(DamageType.fire, DamageType.ice, 1.50f, "融化", true),
        new Combo(DamageType.water, DamageType.fire, 1.50f, "蒸发", true),
        new Combo(DamageType.thunder, DamageType.ice, 1.35f, "超导", true),
        new Combo(DamageType.ice, DamageType.water, 1.25f, "冻结", true),
        new Combo(DamageType.physical, DamageType.ice, 1.25f, "碎冰", true),
        new Combo(DamageType.corrosive, DamageType.water, 1.25f, "腐蚀扩散", true)
    };

    /** 最近一次 evaluate 的结果，供伤害飘字读取；游戏逻辑单线程调用。 */
    public static String lastName;
    public static float lastMultiplier = 1f;
    private static boolean lastConsume;

    private static final ObjectMap<Unit, Aura> auras = new ObjectMap<>();
    private static final ObjectMap<String, DamageType> weaknesses = new ObjectMap<>();
    private static int cleanupCounter;

    private ElementCombo(){
    }

    /** 必须在内容加载完成后调用一次。 */
    public static void load(){
        // 只登记有明确设计意图的图鉴弱点；未登记的单位显示“无明确弱点”。
        weakness("corvus", DamageType.thunder);
        weakness("toxopid", DamageType.fire);
        weakness("eclipse", DamageType.ice);
        weakness("oct", DamageType.thunder);
        weakness("scepter", DamageType.corrosive);
        weakness("reign", DamageType.thunder);
        weakness("vela", DamageType.ice);
        weakness("atrax", DamageType.fire);
        weakness("spiroct", DamageType.fire);
        weakness("arkyid", DamageType.ice);
        weakness("latum", DamageType.corrosive);
        weakness("vanquish", DamageType.corrosive);
    }

    private static void weakness(String unitName, DamageType type){
        weaknesses.put(unitName, type);
    }

    /**
     * 结算目标当前附着能触发的 Combo；结果写入 last* 字段。
     * 非单位（建筑）不挂元素，返回 1。
     */
    public static float evaluate(Healthc target, DamageType type){
        lastName = null;
        lastMultiplier = baseMultiplier;
        lastConsume = false;
        if(!(target instanceof Unit unit) || type == null) return baseMultiplier;

        cleanup();
        Aura aura = auras.get(unit);
        if(aura == null) return baseMultiplier;
        if(unit.dead() || aura.expire <= Time.time){
            auras.remove(unit);
            return baseMultiplier;
        }

        for(Combo combo : combos){
            if(combo.type != type || combo.requires != aura.type) continue;
            lastName = combo.label;
            lastMultiplier = combo.multiplier;
            lastConsume = combo.consume;
            return lastMultiplier;
        }
        return baseMultiplier;
    }

    /** 伤害结算后消耗旧附着；调用方随后再挂本属性的新状态。 */
    public static void consume(Unit unit){
        if(unit != null && lastConsume){
            auras.remove(unit);
            lastConsume = false;
        }
    }

    /** 写入本次属性附着，并挂对应可见状态。物理不覆盖已有元素附着。 */
    public static void apply(Unit unit, DamageType type){
        if(unit == null || type == null || type == DamageType.physical) return;
        auras.put(unit, new Aura(type, Time.time + auraTicks));
        if(type.status != null){
            unit.apply(type.status, type.statusDuration);
        }
    }

    /** 清空所有附着（世界重置/调试用）。 */
    public static void clear(){
        auras.clear();
    }

    /** 某属性可触发的 Combo 文案（详情页/设置页用）。 */
    public static String summary(DamageType type){
        StringBuilder sb = new StringBuilder();
        for(Combo combo : combos){
            if(combo.type != type) continue;
            if(sb.length() > 0) sb.append("；");
            sb.append(combo.requires.localizedName()).append("→")
                .append(combo.label).append(" ×").append(trim(combo.multiplier));
        }
        return sb.length() == 0 ? Core.bundle.get("ironworks.combat.no-combo", "无") : sb.toString();
    }

    /** 某单位图鉴弱点；没有登记时返回 null。 */
    public static DamageType weakness(UnitType type){
        return type == null ? null : weaknesses.get(type.name);
    }

    public static String weaknessName(UnitType type){
        DamageType weak = weakness(type);
        if(weak != null) return weak.coloredName();
        if(type != null && type.armor >= 20f){
            return Core.bundle.get("ironworks.combat.heavy-weakness", "腐蚀（高护甲通用）");
        }
        return Core.bundle.get("ironworks.combat.no-weakness", "无明确弱点");
    }

    /** 调试/测试：当前附着元素名。 */
    public static String auraName(Unit unit){
        Aura aura = unit == null ? null : auras.get(unit);
        return aura == null ? "null" : aura.type.id;
    }

    private static void cleanup(){
        if(++cleanupCounter < 256) return;
        cleanupCounter = 0;
        for(ObjectMap.Entry<Unit, Aura> entry : auras){
            if(entry.key == null || entry.key.dead() || entry.value.expire <= Time.time){
                auras.remove(entry.key);
            }
        }
    }

    private static String trim(float value){
        return value == (int)value ? String.valueOf((int)value) : String.valueOf(value);
    }

    private static final class Aura{
        final DamageType type;
        final float expire;

        Aura(DamageType type, float expire){
            this.type = type;
            this.expire = expire;
        }
    }

    private static final class Combo{
        final DamageType type;
        final DamageType requires;
        final float multiplier;
        final String label;
        final boolean consume;

        Combo(DamageType type, DamageType requires, float multiplier, String label, boolean consume){
            this.type = type;
            this.requires = requires;
            this.multiplier = multiplier;
            this.label = label;
            this.consume = consume;
        }
    }
}
