package ironworks.unitmodule;

import arc.struct.ObjectMap;

/** Aggregated module stats. The core applies armor; other values are exposed for adapters. */
public class ModuleStatSet{
    public float armorAdd;
    public float healthMul = 1f;
    public float speedMul = 1f;
    public float damageMul = 1f;
    public float reloadMul = 1f;
    public float dragMul = 1f;
    public float buildSpeedMul = 1f;
    public final ObjectMap<String, Float> custom = new ObjectMap<>();

    public void add(ModuleStatSet other){
        armorAdd += other.armorAdd;
        healthMul *= other.healthMul;
        speedMul *= other.speedMul;
        damageMul *= other.damageMul;
        reloadMul *= other.reloadMul;
        dragMul *= other.dragMul;
        buildSpeedMul *= other.buildSpeedMul;
        for(ObjectMap.Entry e : other.custom){
            custom.put((String)e.key, custom.get((String)e.key, 0f) + (Float)e.value);
        }
    }

    public float custom(String key){
        return custom.get(key, 0f);
    }
}