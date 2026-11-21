package ironworks.unitmodule;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.gen.Unit;

/** Runtime context passed to behaviors. */
public class ModuleContext{
    public final Unit unit;
    public final UnitModuleState state;
    public final ModuleNode node;
    public final ModuleSpec spec;

    public ModuleContext(Unit unit, UnitModuleState state, ModuleNode node, ModuleSpec spec){
        this.unit = unit;
        this.state = state;
        this.node = node;
        this.spec = spec;
    }

    public float[] local(float visualX, float visualY){
        float[] t = ModuleTransforms.local(state, node);
        float cos = arc.math.Mathf.cosDeg(t[2]), sin = arc.math.Mathf.sinDeg(t[2]);
        return new float[]{t[0] + visualX * cos - visualY * sin, t[1] + visualX * sin + visualY * cos};
    }
}