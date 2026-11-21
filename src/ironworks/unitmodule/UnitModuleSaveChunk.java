package ironworks.unitmodule;

import arc.struct.ObjectMap;
import arc.util.Log;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.io.SaveFileReader;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/** Save chunk for sidecar module graphs. */
public class UnitModuleSaveChunk implements SaveFileReader.CustomChunk{
    @Override
    public void write(DataOutput out) throws IOException{
        Writes w = new Writes(out);
        ObjectMap<Integer, UnitModuleState> states = new ObjectMap<>();
        for(Unit unit : Groups.unit){
            UnitModuleState state = UnitModuleRegistry.get(unit);
            if(state != null && !state.nodes.isEmpty()) states.put(unit.id, state);
        }
        w.i(states.size);
        for(ObjectMap.Entry<Integer, UnitModuleState> e : states){
            w.i(e.key);
            UnitModuleIO.write(w, e.value);
        }
    }

    @Override
    public void read(DataInput in) throws IOException{
        Reads r = new Reads(in);
        int count = r.i();
        for(int i = 0; i < count; i++){
            int unitId = r.i();
            UnitModuleState state = UnitModuleIO.read(r);
            sanitize(state);
            Unit unit = Groups.unit.getByID(unitId);
            if(unit == null){
                Log.warn("unit-module: save state for missing unit @", unitId);
                continue;
            }
            UnitModuleRuntime.bumpNodeId(state);
            UnitModuleRegistry.put(unit, state);
            MountManager.captureBase(unit);
            UnitModuleRuntime.rebuildNow(unit);
        }
    }

    @Override
    public boolean writeNet(){
        return true;
    }

    private void sanitize(UnitModuleState state){
        ObjectMap<String, ModuleNode> kept = new ObjectMap<>();
        arc.struct.Seq<ModuleNode> ordered = new arc.struct.Seq<>();
        for(ModuleNode node : state.nodes){
            if(!ModuleCatalog.has(node.moduleId)){
                Log.warn("unit-module: dropping unknown module '@' from save", node.moduleId);
                continue;
            }
            if(!"root".equals(node.parentId) && !kept.containsKey(node.parentId)){
                Log.warn("unit-module: dropping orphan module '@'", node.moduleId);
                continue;
            }
            kept.put(node.instanceId, node);
            ordered.add(node);
        }
        state.nodes.clear();
        state.nodes.addAll(ordered);
        for(ModuleSource source : state.sources){
            for(int i = source.nodeIds.size - 1; i >= 0; i--){
                if(!kept.containsKey(source.nodeIds.get(i))) source.nodeIds.remove(i);
            }
        }
    }
}