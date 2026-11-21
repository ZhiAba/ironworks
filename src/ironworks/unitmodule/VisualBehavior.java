package ironworks.unitmodule;

import arc.struct.Seq;

/** Contributes extra visual layers. */
public class VisualBehavior implements ModuleBehavior{
    public final Seq<ModuleVisual> visuals = new Seq<>();

    public VisualBehavior add(ModuleVisual visual){
        visuals.add(visual);
        return this;
    }

    @Override
    public void contributeVisuals(Seq<ModuleVisual> out, ModuleContext ctx){
        for(ModuleVisual visual : visuals) out.add(visual.copy());
    }
}