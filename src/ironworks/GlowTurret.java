package ironworks;

import mindustry.entities.Effect;
import mindustry.entities.bullet.BulletType;
import mindustry.world.blocks.defense.turrets.PowerTurret;

/** 开火时附带一个自定义光束/枪口特效的电炮。 */
public class GlowTurret extends PowerTurret{
    public Effect beamEffect, muzzleEffect;

    public GlowTurret(String name){
        super(name);
    }

    public class GlowTurretBuild extends PowerTurretBuild{
        @Override
        protected void shoot(BulletType type){
            super.shoot(type);
            if(muzzleEffect != null) muzzleEffect.at(x, y, rotation);
            if(beamEffect != null) beamEffect.at(x, y, rotation);
            IronworksFx.muzzle.at(x, y, rotation);
        }
    }
}
