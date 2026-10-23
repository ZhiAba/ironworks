package ironworks;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.world.blocks.defense.turrets.PowerTurret;

/**
 * 引力奇点：不发射子弹，直接在世界坐标上维持一个 5 秒的引力场，然后坍缩。
 * （比自定义 Bullet 更可控：一定会在目标点生效）
 */
public class SingularityTurret extends PowerTurret{

    /** 引力/坍缩属于能量属性。 */
    public DamageType damageType = DamageType.energy;
    public float fieldSeconds = 14f;
    public float pullRadius = 15f * 8f;
    public float pullDps = 360f;
    public float collapseRadius = 24f * 8f;
    public float collapseDamage = 680f;
    /** 视觉半径倍率 */
    public float fxScale = 2f;

    public SingularityTurret(String name){
        super(name);
    }

    public class SingularityBuild extends PowerTurretBuild{
        final arc.struct.Seq<Shard> shards = new arc.struct.Seq<>();

        public class Shard{
            float x, y, vx, vy, life, rot, size;
        }
        public float fx, fy, fieldTimer;
        public boolean active;

        @Override
        protected void shoot(BulletType type){
            if(target == null){ super.shoot(type); return; }

            fx = target.getX();
            fy = target.getY();
            fieldTimer = fieldSeconds * 60f;
            active = true;
            IronworksFx.singularityRing.at(fx, fy);
            Fx.shockwave.at(fx, fy);
        }

        @Override
        public void updateTile(){
            for(int i = shards.size - 1; i >= 0; i--){
                Shard sh = shards.get(i);
                sh.x += sh.vx * Time.delta;
                sh.y += sh.vy * Time.delta;
                sh.rot += Time.delta * 2.5f;
                sh.life -= Time.delta;
                if(sh.life <= 0f) shards.remove(i);
            }

            if(active){
                fieldTimer -= Time.delta;

                Dmg.area(team, fx, fy, pullRadius * 0.7f, pullDps * Time.delta / 60f, damageType, true);

                Units.nearbyEnemies(team, fx, fy, pullRadius, u -> {
                    if(u.dead()) return;
                    float dx = fx - u.x, dy = fy - u.y;
                    float len = Math.max((float)Math.sqrt(dx * dx + dy * dy), 1f);
                    float force = Mathf.clamp(1f - len / pullRadius) * 2.4f;
                    u.vel.add(dx / len * force, dy / len * force);
                    Dmg.hitSilent(u, pullDps * Time.delta / 60f, damageType);
                    u.apply(StatusEffects.slow, 20f);
                });

                if(Mathf.chanceDelta(0.5f)){
                    Fx.smoke.at(fx + Mathf.range(120f), fy + Mathf.range(120f));
                    IronworksFx.singularityRing.at(fx, fy);
                }

                if(fieldTimer <= 0f){
                    active = false;
                    collapse();
                }
            }

            super.updateTile();
        }

        void collapse(){
            Dmg.area(team, fx, fy, collapseRadius, collapseDamage, damageType, true);
            Fx.shockwave.at(fx, fy);
            Fx.explosion.at(fx, fy);
            IronworksFx.muzzle.at(fx, fy, 0f);
            IronworksFx.singularityRing.at(fx, fy);

            // 20 块发光碎片：缓慢向外飘、滞留 6 秒、逐渐变淡
            for(int i = 0; i < 20; i++){
                float ang = Mathf.random(360f);
                float speed = Mathf.random(0.35f, 0.9f);
                Shard sh = new Shard();
                sh.x = fx + Mathf.rand.random(0f, 28f) * Mathf.cosDeg(ang);
                sh.y = fy + Mathf.rand.random(0f, 28f) * Mathf.sinDeg(ang);
                sh.vx = Mathf.cosDeg(ang) * speed;
                sh.vy = Mathf.sinDeg(ang) * speed;
                sh.life = 6f * 60f;
                sh.rot = Mathf.random(360f);
                sh.size = Mathf.random(5f, 11f);
                shards.add(sh);

                Dmg.area(team, sh.x, sh.y, 16f, 230f, damageType, true);
                Fx.explosion.at(sh.x, sh.y);
            }
        }

        @Override
        public void draw(){
            super.draw();
            for(Shard sh : shards){
                float f = Mathf.clamp(sh.life / (6f * 60f));
                Draw.blend(Blending.additive);
                Draw.color(Color.valueOf("c9a2ff"), f);
                float r = sh.size;
                for(int i = 0; i < 4; i++){
                    float a = sh.rot + i * 90f;
                    float a2 = sh.rot + (i + 1) * 90f;
                    Fill.tri(sh.x, sh.y,
                        sh.x + Mathf.cosDeg(a) * r, sh.y + Mathf.sinDeg(a) * r,
                        sh.x + Mathf.cosDeg(a2) * r, sh.y + Mathf.sinDeg(a2) * r);
                }
                Draw.color(Color.white, f * 0.8f);
                Fill.circle(sh.x, sh.y, r * 0.35f);
                Draw.blend();
                Draw.reset();
            }

            if(active){
                Draw.blend(Blending.additive);
                Draw.color(Color.valueOf("a06bff"));
                Fill.circle(fx, fy, (6f + Mathf.sin(Time.time * 6f) * 2f) * fxScale);
                Lines.stroke(2f);
                Lines.circle(fx, fy, (10f + (fieldTimer % 30f)) * fxScale);
                Draw.blend();
                Draw.reset();
            }
        }
    }
}
