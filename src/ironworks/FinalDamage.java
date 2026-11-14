package ironworks;

import arc.struct.ObjectMap;
import mindustry.gen.Building;
import mindustry.gen.Healthc;
import mindustry.gen.Unit;

/**
 * 全单位最终伤害修正（v3.9）。
 *
 * <p>“受到最终伤害降低”作用在目标，“造成最终伤害降低”作用在攻击者。
 * 两者都在完整伤害公式（含保底）之后乘算，默认 0；真实伤害走
 * {@link Dmg#pierce}，不读取这里。湮灭伤害只读取目标的“受到”项。</p>
 */
public final class FinalDamage{
    /** 数值为 0..1 的减伤比例；按单位/方块内容名登记。 */
    private static final ObjectMap<String, Float> received = new ObjectMap<>();
    private static final ObjectMap<String, Float> dealt = new ObjectMap<>();

    private FinalDamage(){
    }

    public static float received(Healthc target){
        return get(received, key(target));
    }

    public static float dealt(Healthc source){
        return get(dealt, key(source));
    }

    public static float received(mindustry.type.UnitType type){
        return type == null ? 0f : get(received, type.name);
    }

    public static float received(String key){
        return get(received, key);
    }

    public static float dealt(mindustry.type.UnitType type){
        return type == null ? 0f : get(dealt, type.name);
    }

    public static float dealt(String key){
        return get(dealt, key);
    }

    public static void setReceived(String key, float value){
        put(received, key, value);
    }

    public static void setDealt(String key, float value){
        put(dealt, key, value);
    }

    public static void set(Healthc target, float receivedValue, float dealtValue){
        String key = key(target);
        if(key == null) return;
        setReceived(key, receivedValue);
        setDealt(key, dealtValue);
    }

    public static void clear(){
        received.clear();
        dealt.clear();
    }

    /** 单位用类型名，建筑用方块名；与 Resistance 的归属方式一致。 */
    public static String key(Healthc target){
        if(target instanceof Unit unit) return unit.type == null ? null : unit.type.name;
        if(target instanceof Building build) return build.block == null ? null : build.block.name;
        return null;
    }

    private static float get(ObjectMap<String, Float> map, String key){
        if(key == null) return 0f;
        Float value = map.get(key);
        return value == null ? 0f : clamp(value);
    }

    private static void put(ObjectMap<String, Float> map, String key, float value){
        if(key != null) map.put(key, clamp(value));
    }

    private static float clamp(float value){
        return Math.max(0f, Math.min(1f, value));
    }
}
