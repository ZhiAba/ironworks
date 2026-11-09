package ironworks;

import arc.Core;
import arc.struct.ObjectMap;
import mindustry.gen.Building;
import mindustry.gen.Healthc;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

/**
 * 元素抗性与能量形态抗性（v3.0 防御管线）。
 *
 * <p>主人没有给具体单位的抗性数值，所以这里先提供可注册的通用表：
 * 默认 0，按单位/方块名登记；上限 0.90，避免出现 100% 免疫。</p>
 *
 * <p>能量抗性只在形态为 {@link DamageForm#ENERGY} 时参与乘区。</p>
 */
public final class Resistance{
    public static final float max = 0.90f;

    /** 名称 -> 6 个元素抗性，下标为 DamageType.ordinal()。 */
    private static final ObjectMap<String, float[]> elements = new ObjectMap<>();
    /** 名称 -> 能量形态抗性。 */
    private static final ObjectMap<String, Float> energy = new ObjectMap<>();

    private Resistance(){
    }

    public static float element(Healthc target, DamageType type){
        if(target == null || type == null) return 0f;
        float value = element(key(target), type);
        // 精英的“全元素抗性”只加在真正的元素上，物理伤害不吃这一项。
        if(type.elemental && Difficulty.isEnemyTarget(target)){
            value += Difficulty.enemyElementResistance();
        }
        return clamp(value);
    }

    public static float element(UnitType type, DamageType element){
        if(type == null || element == null) return 0f;
        return element(type.name, element);
    }

    public static float element(String key, DamageType type){
        if(key == null || type == null) return 0f;
        float[] values = elements.get(key);
        if(values == null || type.ordinal() >= values.length) return 0f;
        return clamp(values[type.ordinal()]);
    }

    public static float energy(Healthc target){
        if(target == null) return 0f;
        return energy(key(target));
    }

    public static float energy(UnitType type){
        return type == null ? 0f : energy(type.name);
    }

    public static float energy(String key){
        Float value = key == null ? null : energy.get(key);
        return value == null ? 0f : clamp(value);
    }

    /** 注册/覆盖一个元素抗性。 */
    public static void set(String key, DamageType type, float value){
        if(key == null || type == null) return;
        float[] values = elements.get(key);
        if(values == null){
            values = new float[DamageType.values().length];
            elements.put(key, values);
        }
        values[type.ordinal()] = clamp(value);
    }

    /** 注册/覆盖能量形态抗性。 */
    public static void setEnergy(String key, float value){
        if(key != null) energy.put(key, clamp(value));
    }

    /** 图鉴文案；没有登记时显示“无”。 */
    public static String summary(UnitType type){
        return type == null ? Core.bundle.get("ironworks.combat.no-resistance", "无") : summary(type.name);
    }

    /** 按内容名（单位/方块）生成基础抗性文案。 */
    public static String summary(String key){
        return summary(key, false);
    }

    /** 单位作为敌方目标时的实际抗性，包含精锐的“全元素抗性”惩罚。 */
    public static String summaryEnemy(UnitType type){
        return type == null ? Core.bundle.get("ironworks.combat.no-resistance", "无") : summary(type.name, true);
    }

    private static String summary(String key, boolean enemy){
        if(key == null) return Core.bundle.get("ironworks.combat.no-resistance", "无");
        StringBuilder sb = new StringBuilder();
        for(DamageType element : DamageType.values()){
            float value = element(key, element);
            if(enemy && element.elemental) value += Difficulty.enemyElementResistance();
            value = clamp(value);
            if(value <= 0.0001f) continue;
            if(sb.length() > 0) sb.append(" / ");
            sb.append(element.localizedName()).append(" ")
                .append(Math.round(value * 100f)).append("%");
        }
        float energyValue = energy(key);
        if(energyValue > 0.0001f){
            if(sb.length() > 0) sb.append(" / ");
            sb.append(Core.bundle.get("ironworks.combat.energy-resistance", "能量"))
                .append(" ").append(Math.round(energyValue * 100f)).append("%");
        }
        return sb.length() == 0 ? Core.bundle.get("ironworks.combat.no-resistance", "无") : sb.toString();
    }

    public static void clear(){
        elements.clear();
        energy.clear();
    }

    private static String key(Healthc target){
        if(target instanceof Unit unit) return unit.type == null ? null : unit.type.name;
        if(target instanceof Building building && building.block != null) return building.block.name;
        return null;
    }

    private static float clamp(float value){
        return Math.max(0f, Math.min(max, value));
    }
}
