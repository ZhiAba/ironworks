package ironworks;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Healthc;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.world.blocks.defense.turrets.PowerTurret;

/** 磁轨炮：直接沿瞄准线结算，不发射子弹，避免光束画到射程尽头。 */
public class RailTurret extends PowerTurret{

    public float beamDamage = 240f;
    public int maxTargets = 5;
    public float hitWidth = 14f;
    /** 电磁重炮默认雷霆；以后换属性只改这一处。 */
    public DamageType damageType = DamageType.thunder;
    /** 磁轨炮是真实弹丸：雷霆 + 动能。 */
    public DamageForm form = DamageForm.KINETIC;

    /** 开火时的屏幕震动强度；0 = 关闭 */
    public float shakePower = 4f, shakeTime = 6f;

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

            if(target instanceof Healthc h) Dmg.hit(h, beamDamage, damageType, true, true, damageType.pen, true, form);

            int[] hits = {1};
            float midx = (x + tx) / 2f, midy = (y + ty) / 2f;

            Units.nearbyEnemies(team, midx, midy, len / 2f + 24f, u -> {
                if(hits[0] >= maxTargets || u.dead()) return;
                if(Mathf.dst(u.x, u.y, x, y) > len) return;
                if(pointLineDistance(u.x, u.y, x, y, tx, ty) > hitWidth) return;
                hits[0]++;
                Dmg.hit(u, beamDamage, damageType, true, true, damageType.pen, true, form);
            });

            IronworksFx.beam(x, y, ang, len);
            IronworksFx.muzzle.at(x, y, ang);
            IronworksFx.muzzle.at(tx, ty, ang);

            // —— 开火反馈：屏幕震动 + 命中点火花 ——
            if(shakePower > 0f) Effect.shake(shakePower, shakeTime, x, y);
            if(hits[0] > 1){
                Fx.hitBeam.at(tx, ty);
                Fx.hitLancer.at(tx, ty);
            }
        }

        /** 充能环：装填进度越满，环越亮；满环时补一圈亮边。 */
        @Override
        public void draw(){
            super.draw();

            float p = progress();
            if(p <= 0.03f || !isActive()) return;

            float rad = size * 10f + 4f;
            float t = Time.time * 90f;

            Draw.z(Layer.blockAdditive);
            Draw.blend(Blending.additive);

            Draw.color(Color.valueOf("6fa0ff"), 0.10f + 0.30f * p);
            Lines.stroke(2.4f);
            Lines.arc(x, y, rad, p * 0.98f, t);

            Draw.color(Color.valueOf("dfe9ff"), 0.22f * p);
            Lines.stroke(1.2f);
            Lines.arc(x, y, rad + 3.5f, p * 0.55f, -t * 1.4f);

            // 满环时再点一圈外光
            if(p > 0.92f){
                float pulse = (Mathf.sin(Time.time * 9f) * 0.5f + 0.5f);
                Draw.color(Color.valueOf("9fc4ff"), 0.18f * pulse);
                Lines.stroke(1.6f);
                Lines.circle(x, y, rad + 7f);
            }

            Draw.blend();
            Draw.color();

            Drawf.light(x, y, rad * 1.8f, Color.valueOf("8fb6ff"), 0.25f * p);
        }

        float pointLineDistance(float px, float py, float x1, float y1, float x2, float y2){
            float dx = x2 - x1, dy = y2 - y1;
            float len = Math.max((float)Math.sqrt(dx * dx + dy * dy), 0.001f);
            return Math.abs(dy * (px - x1) - dx * (py - y1)) / len;
        }
    }
}
