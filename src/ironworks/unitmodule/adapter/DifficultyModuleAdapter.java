package ironworks.unitmodule.adapter;

import ironworks.Difficulty;
import ironworks.unitmodule.ModuleModifierSource;

/** T7/difficulty revision adapter. Concrete weapon modifiers are content-owned. */
public class DifficultyModuleAdapter implements ModuleModifierSource{
    @Override
    public long revision(){
        long rev = Difficulty.tier();
        for(String id : Difficulty.modIds){
            rev = rev * 31L + Difficulty.modifierMask(id);
        }
        return rev;
    }
}