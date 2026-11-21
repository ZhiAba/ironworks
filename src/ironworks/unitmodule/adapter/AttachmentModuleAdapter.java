package ironworks.unitmodule.adapter;

import ironworks.unitmodule.ModuleNode;
import ironworks.unitmodule.ModulePlacement;
import ironworks.unitmodule.ModuleSource;
import ironworks.unitmodule.UnitModuleRuntime;
import mindustry.gen.Unit;

/** Attaches modules supplied by another unit/system. */
public class AttachmentModuleAdapter{
    private static long nextId = 1;

    public static ModuleSource attach(Unit host, Unit sourceUnit, String moduleId, ModulePlacement placement){
        String sourceId = "attach:" + (sourceUnit == null ? "system" : sourceUnit.id) + ":" + (nextId++);
        return UnitModuleRuntime.attachSource(host, sourceUnit, sourceId, moduleId, placement);
    }

    public static ModuleNode attachNode(Unit host, Unit sourceUnit, String moduleId, ModulePlacement placement){
        ModuleSource source = attach(host, sourceUnit, moduleId, placement);
        if(source == null) return null;
        for(String id : source.nodeIds){
            ModuleNode node = UnitModuleRuntime.stateOf(host).node(id);
            if(node != null) return node;
        }
        return null;
    }

    public static boolean detach(Unit host, String sourceId){
        return UnitModuleRuntime.detachSource(host, sourceId);
    }
}