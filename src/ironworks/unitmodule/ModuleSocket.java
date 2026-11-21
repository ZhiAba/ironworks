package ironworks.unitmodule;

import arc.struct.Seq;

/** A socket exposed by a module or by the unit root. */
public class ModuleSocket{
    public final String id;
    public float x, y;
    public float rotation;
    public float scale = 1f;
    public int capacity = 1;
    public final Seq<String> accepts = new Seq<>();

    public ModuleSocket(String id, float x, float y){
        this.id = id;
        this.x = x;
        this.y = y;
    }

    public ModuleSocket accepts(String... tags){
        for(String tag : tags){
            if(!accepts.contains(tag)) accepts.add(tag);
        }
        return this;
    }

    public ModuleSocket capacity(int capacity){
        this.capacity = Math.max(1, capacity);
        return this;
    }

    public boolean accepts(ModuleSpec spec){
        if(accepts.isEmpty()) return true;
        for(String tag : accepts){
            if(spec.hasTag(tag)) return true;
        }
        return false;
    }
}