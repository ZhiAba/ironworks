package ironworks;

import arc.math.Mathf;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Healthc;
import mindustry.world.blocks.defense.turrets.PowerTurret;

/** 磁轨炮：直接沿瞄准线结算，不发射子弹，避免光束画到射程尽头。 */
public class RailTurret extends PowerTurret{

    public float beamDamage = 240f;
    public int maxTargets = 5;
    public float hitWidth = 14f;

    public RailTurret(String name){
        super(name);
    }

    public class RailTurretBuild extends PowerTurretBuild{
        @Override
        protected void shoot(BulletType type){
            float tx, ty;
            if(target != null){
                tx = target.getX(); ty = target.getY();
            }else if(controlled() || logicControlled() || alwaysShooting){
                float ddx = targetPos.x - x, ddy = targetPos.y - y;
                if((float)Math.sqrt(ddx * ddx + ddy * ddy) > range) return;
                tx = targetPos.x; ty = targetPos.y;
            }else{
                return;
            }

            float ang = Mathf.angle(tx - x, ty - y);
            float len = Mathf.dst(x, y, tx, ty);

            Steel:
            {
                if(target instanceof Healthc h) h.damage(beamDamage);
            }

            int[] hits = {1};
            float midx = (x + tx) / 2f, midy = (y + ty) / 2f;

            Units.nearbyEnemies(team, midx, midy, len / 2f + 24f, u -> {
                if(hits[0] >= maxTargets || u.dead()) return;
                if(Mathf.dst(u.x, u.y, x, y) > len) return;
                if(pointLineDistance(u.x, u.y, x, y, tx, ty) > hitWidth) return;
                hits[0]++;
                u.damage(beamDamage);
                u.apply(mindustry.content.StatusEffects.shocked, 30f);
            });

            IronworksFx.beam(x, y, ang, len);
            IronworksFx.muzzle.at(x, y, ang);
            IronworksFx.muzzle.at(tx, ty, ang);
        }

        float pointLineDistance(float px, float py, float x1, float y1, float x2, float y2){
            float dx = x2 - x1, dy = y2 - y1;
            float len = Math.max((float)Math.sqrt(dx * dx + dy * dy), 0.001f);
            return Math.abs(dy * (px - x1) - dx * (py - y1)) / len;
        }
    }
}
