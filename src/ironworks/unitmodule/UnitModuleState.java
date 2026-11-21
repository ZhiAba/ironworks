package ironworks.unitmodule;

import arc.struct.Seq;

/** The per-unit module graph. A unit root is implicit; nodes may nest via sockets. */
public class UnitModuleState{
    public String rootId = "root";
    public long revision;
    public final Seq<ModuleNode> nodes = new Seq<>();
    public final Seq<ModuleSource> sources = new Seq<>();

    public ModuleNode node(String instanceId){
        if(instanceId == null || "root".equals(instanceId)) return null;
        for(ModuleNode node : nodes){
            if(node.instanceId.equals(instanceId)) return node;
        }
        return null;
    }

    public Seq<ModuleNode> children(String parentId){
        Seq<ModuleNode> out = new Seq<>();
        for(ModuleNode node : nodes){
            if(node.parentId.equals(parentId)) out.add(node);
        }
        return out;
    }

    public ModuleSource source(String id){
        for(ModuleSource source : sources){
            if(source.id.equals(id)) return source;
        }
        return null;
    }

    public void touch(){
        revision++;
    }

    public UnitModuleState copy(){
        UnitModuleState s = new UnitModuleState();
        s.rootId = rootId;
        s.revision = revision;
        for(ModuleNode node : nodes) s.nodes.add(node.copy());
        for(ModuleSource source : sources) s.sources.add(source.copy());
        return s;
    }
}