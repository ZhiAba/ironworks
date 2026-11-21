package ironworks.unitmodule;

import arc.struct.Seq;

/**
 * Open SPI for unit modules.
 *
 * <p>A module is a composition of behaviors; the library does not hardcode
 * weapon/armor kinds. New functionality is added by implementing this
 * interface and registering it on a {@link ModuleSpec}.</p>
 */
public interface ModuleBehavior{
    default void onInstall(ModuleContext ctx){}
    default void onUninstall(ModuleContext ctx){}
    default void update(ModuleContext ctx){}

    default void contributeMounts(Seq<MountSpec> out, ModuleContext ctx){}
    default void contributeStats(ModuleStatSet out, ModuleContext ctx){}
    default void contributeVisuals(Seq<ModuleVisual> out, ModuleContext ctx){}
    default void contributeSockets(Seq<ModuleSocket> out, ModuleContext ctx){}
}