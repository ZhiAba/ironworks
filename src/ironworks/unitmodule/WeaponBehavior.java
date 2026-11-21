package ironworks.unitmodule;

import arc.struct.Seq;

/** Contributes one or more weapon mounts. */
public class WeaponBehavior implements ModuleBehavior{
    public final Seq<MountSpec> mounts = new Seq<>();

    public WeaponBehavior add(MountSpec mount){
        mounts.add(mount);
        return this;
    }

    @Override
    public void contributeMounts(Seq<MountSpec> out, ModuleContext ctx){
        out.addAll(mounts);
    }
}