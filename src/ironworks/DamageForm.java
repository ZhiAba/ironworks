package ironworks;

import arc.Core;

/**
 * 伤害形态（v2.6 战斗体系）。
 *
 * <p>形态与元素正交：雷霆可以是动能轨道炮，也可以是能量电弧。
 * 形态决定过穿、护盾交互与（后续）材质克制，不再由元素或生命上限决定。</p>
 */
public enum DamageForm{
    KINETIC("kinetic", "动能", true, false),
    ENERGY("energy", "能量", false, true),
    EXPLOSIVE("explosive", "爆炸", false, false),
    SLASHING("slashing", "切割", false, false);

    public final String id;
    public final String cn;
    /** 是否可能触发过穿。 */
    public final boolean kinetic;
    /** 是否无视物理护盾。 */
    public final boolean bypassShields;

    DamageForm(String id, String cn, boolean kinetic, boolean bypassShields){
        this.id = id;
        this.cn = cn;
        this.kinetic = kinetic;
        this.bypassShields = bypassShields;
    }

    public String localizedName(){
        return Core.bundle == null ? cn : Core.bundle.get("damage-form." + id + ".name", cn);
    }
}
