package ironworks.unitmodule.adapter;

import arc.struct.Seq;
import ironworks.unitmodule.ModuleEditSession;
import ironworks.unitmodule.ModulePlacement;
import mindustry.gen.Unit;

/** Maps an arsenal/loadout request to one atomic module edit session. */
public class ArsenalModuleAdapter{
    public static class Entry{
        public final String moduleId;
        public final ModulePlacement placement;

        public Entry(String moduleId, ModulePlacement placement){
            this.moduleId = moduleId;
            this.placement = placement == null ? new ModulePlacement() : placement;
        }
    }

    public static boolean apply(Unit unit, Seq<Entry> entries){
        if(unit == null || entries == null) return false;
        ModuleEditSession session = new ModuleEditSession(unit);
        for(Entry entry : entries){
            session.install(entry.moduleId, entry.placement);
        }
        return session.commit();
    }
}
