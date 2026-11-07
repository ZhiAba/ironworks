package ironworks;

import mindustry.world.blocks.production.GenericCrafter;

/**
 * 会被全局难度产能倍率影响的 GenericCrafter。
 * 目前只给磁选机使用；其余模组产线走 MultiCrafter 的进度倍率。
 */
public class DifficultyCrafter extends GenericCrafter{

    public DifficultyCrafter(String name){
        super(name);
        buildType = DifficultyCrafterBuild::new;
    }

    public class DifficultyCrafterBuild extends GenericCrafterBuild{
        @Override
        public float getProgressIncrease(float baseTime){
            return super.getProgressIncrease(baseTime) * Difficulty.productionMultiplier();
        }
    }
}
