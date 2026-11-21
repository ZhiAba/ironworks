package ironworks.unitmodule;

import arc.math.Angles;
import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.gen.Unit;

/** Composes parent/child local transforms into unit/world space. */
public class ModuleTransforms{
    public static float[] local(UnitModuleState state, ModuleNode node){
        Seq<ModuleNode> chain = new Seq<>();
        ModuleNode cur = node;
        int guard = 0;
        while(cur != null && guard++ < 32){
            chain.add(cur);
            cur = state.node(cur.parentId);
        }
        chain.reverse();

        float x = 0f, y = 0f, rot = 0f;
        for(ModuleNode n : chain){
            float cos = Mathf.cosDeg(rot), sin = Mathf.sinDeg(rot);
            float nx = x + n.x * cos - n.y * sin;
            float ny = y + n.x * sin + n.y * cos;
            x = nx;
            y = ny;
            rot += n.rotation;
        }
        return new float[]{x, y, rot};
    }

    public static float scale(UnitModuleState state, ModuleNode node){
        float scale = 1f;
        ModuleNode cur = node;
        int guard = 0;
        while(cur != null && guard++ < 32){
            scale *= cur.scale;
            cur = state.node(cur.parentId);
        }
        return scale;
    }

    /** Unit-local point (node transform + visual offset) to world coordinates. */
    public static float[] world(Unit unit, UnitModuleState state, ModuleNode node, float visualX, float visualY){
        float[] t = local(state, node);
        float cos = Mathf.cosDeg(t[2]), sin = Mathf.sinDeg(t[2]);
        float lx = t[0] + visualX * cos - visualY * sin;
        float ly = t[1] + visualX * sin + visualY * cos;
        return new float[]{
            unit.x + Angles.trnsx(unit.rotation - 90f, lx, ly),
            unit.y + Angles.trnsy(unit.rotation - 90f, lx, ly)
        };
    }

    public static float worldRotation(Unit unit, UnitModuleState state, ModuleNode node){
        return unit.rotation - 90f + local(state, node)[2];
    }
}