package ironworks.unitmodule;

import arc.struct.Seq;

/** A named origin of modules (base loadout, arsenal, attachment, affix). */
public class ModuleSource{
    public final String id;
    public final String kind;
    public final int priority;
    public final boolean persistent;
    public int sourceUnitId = -1;
    public final Seq<String> nodeIds = new Seq<>();

    public ModuleSource(String id, String kind, int priority, boolean persistent){
        this.id = id;
        this.kind = kind;
        this.priority = priority;
        this.persistent = persistent;
    }

    public ModuleSource unit(int unitId){
        this.sourceUnitId = unitId;
        return this;
    }

    public ModuleSource copy(){
        ModuleSource s = new ModuleSource(id, kind, priority, persistent);
        s.sourceUnitId = sourceUnitId;
        s.nodeIds.addAll(nodeIds);
        return s;
    }
}