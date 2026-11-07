package ironworks;

import arc.struct.ObjectMap;
import mindustry.Vars;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.Turret;

/**
 * 把难度词条的炮塔弹药/射速/精度修正写到 Turret 方块字段。
 * 伤害与穿甲在 Dmg.bulletHit 中按“玩家炮塔弹体”动态结算。
 */
public final class IronworksTurretStats{
    private static final ObjectMap<Turret, Base> bases = new ObjectMap<>();
    private static boolean captured;

    private IronworksTurretStats(){
    }

    public static void apply(){
        if(Vars.content == null) return;
        if(!captured || bases.size == 0){
            bases.clear();
            for(Block block : Vars.content.blocks()){
                if(block instanceof Turret turret){
                    bases.put(turret, new Base(turret.reload, turret.inaccuracy, turret.ammoPerShot));
                }
            }
            captured = bases.size > 0;
        }

        float fireRate = Difficulty.turretFireRateMultiplier();
        float inaccuracy = Difficulty.turretInaccuracyBonus();
        float ammo = Difficulty.turretAmmoMultiplier();
        for(ObjectMap.Entry<Turret, Base> entry : bases){
            Turret turret = entry.key;
            Base base = entry.value;
            turret.reload = Math.max(0.01f, base.reload / Math.max(0.10f, fireRate));
            turret.inaccuracy = base.inaccuracy + inaccuracy;
            turret.ammoPerShot = Math.max(1, (int)Math.ceil(base.ammoPerShot * ammo - 0.0001f));
        }
    }

    private static final class Base{
        final float reload, inaccuracy;
        final int ammoPerShot;

        Base(float reload, float inaccuracy, int ammoPerShot){
            this.reload = Math.max(0.01f, reload);
            this.inaccuracy = Math.max(0f, inaccuracy);
            this.ammoPerShot = Math.max(1, ammoPerShot);
        }
    }
}
