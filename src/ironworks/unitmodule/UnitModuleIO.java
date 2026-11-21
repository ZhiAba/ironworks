package ironworks.unitmodule;

import arc.util.io.Reads;
import arc.util.io.Writes;

/** Shared save/network encoding for a module graph. */
public class UnitModuleIO{
    public static final int VERSION = 1;

    public static void write(Writes w, UnitModuleState state){
        w.b(VERSION);
        w.str(state.rootId == null ? "root" : state.rootId);
        w.l(state.revision);
        w.i(state.nodes.size);
        for(ModuleNode node : state.nodes){
            w.str(node.instanceId);
            w.str(node.moduleId);
            w.str(node.parentId == null ? "root" : node.parentId);
            w.str(node.socketId == null ? "" : node.socketId);
            w.str(node.sourceId == null ? "base" : node.sourceId);
            w.f(node.x);
            w.f(node.y);
            w.f(node.rotation);
            w.f(node.scale);
            w.f(node.z);
            w.str(node.regionOverride == null ? "" : node.regionOverride);
            w.i(node.tint);
            w.bool(node.mirror);
        }
        w.i(state.sources.size);
        for(ModuleSource source : state.sources){
            w.str(source.id);
            w.str(source.kind);
            w.i(source.priority);
            w.bool(source.persistent);
            w.i(source.sourceUnitId);
            w.i(source.nodeIds.size);
            for(String id : source.nodeIds) w.str(id);
        }
    }

    public static UnitModuleState read(Reads r){
        UnitModuleState state = new UnitModuleState();
        int version = r.b();
        if(version < 1) throw new IllegalArgumentException("unknown unit module save version " + version);
        state.rootId = r.str();
        state.revision = r.l();
        int nodes = r.i();
        for(int i = 0; i < nodes; i++){
            ModuleNode node = new ModuleNode(r.str(), r.str());
            node.parentId = r.str();
            node.socketId = emptyToNull(r.str());
            node.sourceId = r.str();
            node.x = r.f();
            node.y = r.f();
            node.rotation = r.f();
            node.scale = r.f();
            node.z = r.f();
            node.regionOverride = emptyToNull(r.str());
            node.tint = r.i();
            node.mirror = r.bool();
            state.nodes.add(node);
        }
        int sources = r.i();
        for(int i = 0; i < sources; i++){
            ModuleSource source = new ModuleSource(r.str(), r.str(), r.i(), r.bool());
            source.sourceUnitId = r.i();
            int ids = r.i();
            for(int j = 0; j < ids; j++) source.nodeIds.add(r.str());
            state.sources.add(source);
        }
        return state;
    }

    private static String emptyToNull(String value){
        return value == null || value.length() == 0 ? null : value;
    }
}