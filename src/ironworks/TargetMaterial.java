package ironworks;

import arc.Core;
import mindustry.gen.Building;
import mindustry.gen.Healthc;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

/**
 * 目标材质（v2.6 过穿体系）。
 *
 * <p>目前是保守的分类：建筑=结构；单位护甲 ≥6 或最大生命 >800=装甲；
 * 其余低阶单位=软体。以后要给具体单位单独贴标签，可以在 {@link #of(UnitType)}
 * 里加特例，不影响结算代码。</p>
 */
public enum TargetMaterial{
    SOFT,
    ARMORED,
    STRUCTURE;

    public static TargetMaterial of(Healthc target){
        if(target instanceof Building) return STRUCTURE;
        if(target instanceof Unit unit) return of(unit.type);
        return STRUCTURE;
    }

    public static TargetMaterial of(UnitType type){
        if(type == null) return SOFT;
        if(type.armor >= 6f || type.health > 800f) return ARMORED;
        return SOFT;
    }

    public static String name(Healthc target){
        return name(of(target));
    }

    public static String name(UnitType type){
        return name(of(type));
    }

    private static String name(TargetMaterial material){
        String key = switch(material){
            case SOFT -> "ironworks.material.soft";
            case ARMORED -> "ironworks.material.armored";
            case STRUCTURE -> "ironworks.material.structure";
        };
        return Core.bundle.get(key, switch(material){
            case SOFT -> "软体";
            case ARMORED -> "装甲";
            case STRUCTURE -> "结构";
        });
    }
}
