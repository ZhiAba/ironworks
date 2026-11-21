package ironworks.unitmodule;

/**
 * A source of weapon modifiers with a monotonically comparable revision.
 * T7/difficulty adapters implement this and are polled by the runtime.
 */
public interface ModuleModifierSource{
    long revision();

    default void modify(mindustry.type.Weapon weapon, ModuleContext ctx){}
}