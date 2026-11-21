package ironworks.unitmodule;

/** Events fired after a unit module graph changes. */
public class UnitModuleEvents{
    public static class ModuleChangedEvent{
        public final mindustry.gen.Unit unit;
        public final UnitModuleState state;
        public final long revision;

        public ModuleChangedEvent(mindustry.gen.Unit unit, UnitModuleState state, long revision){
            this.unit = unit;
            this.state = state;
            this.revision = revision;
        }
    }
}