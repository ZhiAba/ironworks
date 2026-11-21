package ironworks.unitmodule;

import arc.struct.ObjectMap;
import arc.struct.Seq;

/** Contributes stat modifiers. */
public class StatBehavior implements ModuleBehavior{
    public float armorAdd;
    public float healthMul = 1f, speedMul = 1f, damageMul = 1f;
    public float reloadMul = 1f, dragMul = 1f, buildSpeedMul = 1f;
    public final ObjectMap<String, Float> custom = new ObjectMap<>();

    public StatBehavior armor(float value){
        armorAdd += value;
        return this;
    }

    public StatBehavior health(float multiplier){
        healthMul *= multiplier;
        return this;
    }

    public StatBehavior speed(float multiplier){
        speedMul *= multiplier;
        return this;
    }

    public StatBehavior damage(float multiplier){
        damageMul *= multiplier;
        return this;
    }

    public StatBehavior reload(float multiplier){
        reloadMul *= multiplier;
        return this;
    }

    public StatBehavior drag(float multiplier){
        dragMul *= multiplier;
        return this;
    }

    public StatBehavior buildSpeed(float multiplier){
        buildSpeedMul *= multiplier;
        return this;
    }

    public StatBehavior custom(String key, float value){
        custom.put(key, custom.get(key, 0f) + value);
        return this;
    }

    @Override
    public void contributeStats(ModuleStatSet out, ModuleContext ctx){
        out.armorAdd += armorAdd;
        out.healthMul *= healthMul;
        out.speedMul *= speedMul;
        out.damageMul *= damageMul;
        out.reloadMul *= reloadMul;
        out.dragMul *= dragMul;
        out.buildSpeedMul *= buildSpeedMul;
        for(ObjectMap.Entry e : custom){
            out.custom.put((String)e.key, out.custom.get((String)e.key, 0f) + (Float)e.value);
        }
    }
}