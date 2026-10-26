package ironworks;

import arc.struct.ObjectMap;
import arc.util.Time;
import mindustry.content.StatusEffects;
import mindustry.gen.Healthc;
import mindustry.gen.Unit;

/**
 * 永久腐蚀减甲（v3.0）。
 *
 * <p>腐蚀不参与 Combo。命中后进入腐蚀激活期，每 tick 永久削减目标基础护甲的
 * 0.01%（0.0001 × A_max）；上限普通 15%，处于潮湿/酸蚀时速率减半、上限 8%。
 * 削减量在目标死亡前不会恢复，即使腐蚀 Aura 过期也保留。</p>
 *
 * <p>激活期沿用 4 秒 Aura 时长；重复命中刷新激活期，不重置已经削减的量。</p>
 */
public final class Corrosion{
    public static final float normalRatePerTick = 0.0001f;
    public static final float acidRatePerTick = 0.00005f;
    public static final float normalCap = 0.15f;
    public static final float acidCap = 0.08f;
    public static final float activeTicks = 240f;

    private static final ObjectMap<Healthc, State> states = new ObjectMap<>();
    private static int cleanupCounter;

    private Corrosion(){
    }

    /** 命中腐蚀元素时调用；不会清空已有削减量。 */
    public static void apply(Healthc target){
        if(target == null || target.dead()) return;
        State state = states.get(target);
        if(state == null){
            float base = Dmg.baseArmorOf(target);
            state = new State(base);
            states.put(target, state);
        }
        state.activeUntil = Time.time + activeTicks;
    }

    /** 当前永久削减的护甲值。 */
    public static float reduction(Healthc target){
        State state = target == null ? null : states.get(target);
        return state == null ? 0f : state.reduced;
    }

    /** 当前有效护甲（基础护甲 - 永久腐蚀）。 */
    public static float currentArmor(Healthc target){
        return Math.max(0f, Dmg.baseArmorOf(target) - reduction(target));
    }

    /** 调试/事件入口：推进一 tick。 */
    public static void update(){
        cleanupCounter++;
        for(ObjectMap.Entry<Healthc, State> entry : states){
            Healthc target = entry.key;
            State state = entry.value;
            if(target == null || target.dead()){
                states.remove(target);
                continue;
            }
            if(Time.time > state.activeUntil) continue;

            boolean acid = isWet(target);
            float cap = state.baseArmor * (acid ? acidCap : normalCap);
            if(state.reduced >= cap) continue;
            float rate = state.baseArmor * (acid ? acidRatePerTick : normalRatePerTick);
            state.reduced = Math.min(cap, state.reduced + rate);
        }

        if(cleanupCounter >= 600){
            cleanupCounter = 0;
            for(ObjectMap.Entry<Healthc, State> entry : states){
                if(entry.key == null || entry.key.dead()) states.remove(entry.key);
            }
        }
    }

    public static boolean isWet(Healthc target){
        if(target instanceof Unit unit){
            if(unit.hasEffect(StatusEffects.wet)) return true;
            return ElementCombo.hasAura(unit, DamageType.water);
        }
        return false;
    }

    public static void clear(){
        states.clear();
    }

    private static final class State{
        final float baseArmor;
        float reduced;
        float activeUntil;

        State(float baseArmor){
            this.baseArmor = Math.max(0f, baseArmor);
        }
    }
}
