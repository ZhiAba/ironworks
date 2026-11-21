package ironworks.unitmodule;

import arc.struct.ObjectMap;

/** One installed module instance. */
public class ModuleNode{
    public String instanceId;
    public String moduleId;
    public String parentId = "root";
    public String socketId;
    public String sourceId = "base";
    public float x, y;
    public float rotation;
    public float scale = 1f;
    public float z;
    public String regionOverride;
    public int tint = -1;
    public boolean mirror;
    /** Per-instance behavior state; not serialized in v1. */
    public final ObjectMap<String, Object> runtime = new ObjectMap<>();

    public ModuleNode(String instanceId, String moduleId){
        this.instanceId = instanceId;
        this.moduleId = moduleId;
    }

    public ModuleNode copy(){
        ModuleNode n = new ModuleNode(instanceId, moduleId);
        n.parentId = parentId;
        n.socketId = socketId;
        n.sourceId = sourceId;
        n.x = x; n.y = y; n.rotation = rotation;
        n.scale = scale; n.z = z;
        n.regionOverride = regionOverride;
        n.tint = tint; n.mirror = mirror;
        for(ObjectMap.Entry e : runtime){
            n.runtime.put((String)e.key, e.value);
        }
        return n;
    }
}