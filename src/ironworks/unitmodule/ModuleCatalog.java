package ironworks.unitmodule;

import arc.struct.ObjectMap;
import arc.struct.Seq;

/** Runtime registry of module definitions. */
public class ModuleCatalog{
    public static final ObjectMap<String, ModuleSpec> all = new ObjectMap<>();

    public static void register(ModuleSpec spec){
        if(spec == null || spec.id == null || spec.id.length() == 0){
            throw new IllegalArgumentException("module spec id must not be empty");
        }
        all.put(spec.id, spec);
    }

    public static ModuleSpec get(String id){
        return id == null ? null : all.get(id);
    }

    public static boolean has(String id){
        return get(id) != null;
    }

    public static Seq<ModuleSpec> values(){
        Seq<ModuleSpec> out = new Seq<>();
        for(ModuleSpec spec : all.values()) out.add(spec);
        return out;
    }

    public static void clear(){
        all.clear();
    }
}