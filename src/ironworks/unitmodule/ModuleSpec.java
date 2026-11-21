package ironworks.unitmodule;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.type.ItemStack;

/** Declarative module definition. */
public class ModuleSpec{
    public final String id;
    public final String name;
    public final Seq<ModuleBehavior> behaviors = new Seq<>();
    public final Seq<ModuleVisual> visuals = new Seq<>();
    public final Seq<String> tags = new Seq<>();
    public ItemStack[] cost = ItemStack.empty;
    public float power;
    public String description = "";

    public ModuleSpec(String id, String name){
        this.id = id;
        this.name = name;
    }

    public ModuleSpec behavior(ModuleBehavior behavior){
        behaviors.add(behavior);
        return this;
    }

    public ModuleSpec visual(ModuleVisual visual){
        visuals.add(visual);
        return this;
    }

    public ModuleSpec tag(String... tags){
        for(String tag : tags){
            if(!this.tags.contains(tag)) this.tags.add(tag);
        }
        return this;
    }

    public ModuleSpec cost(ItemStack... cost){
        this.cost = cost;
        return this;
    }

    public ModuleSpec power(float power){
        this.power = power;
        return this;
    }

    public boolean hasTag(String tag){
        return tags.contains(tag);
    }

    public Seq<MountSpec> collectMounts(ModuleContext ctx){
        Seq<MountSpec> out = new Seq<>();
        for(ModuleBehavior behavior : behaviors) behavior.contributeMounts(out, ctx);
        return out;
    }

    public Seq<ModuleVisual> collectVisuals(ModuleContext ctx){
        Seq<ModuleVisual> out = new Seq<>();
        for(ModuleVisual visual : visuals) out.add(visual.copy());
        for(ModuleBehavior behavior : behaviors) behavior.contributeVisuals(out, ctx);
        return out;
    }

    public Seq<ModuleSocket> collectSockets(ModuleContext ctx){
        Seq<ModuleSocket> out = new Seq<>();
        for(ModuleBehavior behavior : behaviors) behavior.contributeSockets(out, ctx);
        return out;
    }

    public ModuleStatSet collectStats(ModuleContext ctx){
        ModuleStatSet out = new ModuleStatSet();
        for(ModuleBehavior behavior : behaviors) behavior.contributeStats(out, ctx);
        return out;
    }
}