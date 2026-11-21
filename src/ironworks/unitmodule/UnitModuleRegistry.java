package ironworks.unitmodule;

import arc.struct.IntMap;
import mindustry.gen.Unit;

/** Sidecar storage: any unit can own a module graph, no custom entity required. */
public class UnitModuleRegistry{
    private static final IntMap<UnitModuleState> states = new IntMap<>();
    private static final IntMap<Float> baseArmor = new IntMap<>();

    public static UnitModuleState get(Unit unit){
        return unit == null ? null : states.get(unit.id);
    }

    public static UnitModuleState ensure(Unit unit){
        UnitModuleState state = get(unit);
        if(state == null){
            state = new UnitModuleState();
            states.put(unit.id, state);
        }
        return state;
    }

    public static void put(Unit unit, UnitModuleState state){
        if(unit != null && state != null) states.put(unit.id, state);
    }

    public static void remove(Unit unit){
        if(unit == null) return;
        states.remove(unit.id);
        baseArmor.remove(unit.id);
    }

    public static void clear(){
        states.clear();
        baseArmor.clear();
    }

    public static boolean hasBaseArmor(Unit unit){
        return unit != null && baseArmor.containsKey(unit.id);
    }

    public static float baseArmor(Unit unit, float fallback){
        Float value = unit == null ? null : baseArmor.get(unit.id);
        return value == null ? fallback : value;
    }

    public static void setBaseArmor(Unit unit, float value){
        if(unit != null) baseArmor.put(unit.id, value);
    }

    public static void removeBaseArmor(Unit unit){
        if(unit != null) baseArmor.remove(unit.id);
    }
}