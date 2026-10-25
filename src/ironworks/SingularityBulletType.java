package ironworks;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Bullet;

/** 引力奇点：缓慢前进，持续拉扯 10 格内敌人，5 秒后坍缩爆炸。 */
public class SingularityBulletType extends BulletType{

    public float pullRadius = 10f * 8f;
    public float pullDamage = 12f;      // 每秒真实伤害
    public float collapseDamage = 420f;
    public float collapseRadius = 12f * 8f;
    public DamageType damageType = DamageType.physical;
    public DamageForm form = DamageForm.ENERGY;
    public Color color = Color.valueOf("a06bff");

    public SingularityBulletType(){
        super(0.001f, 0f);
        speed = 1.2f;
        lifetime = 300f;
        collides = false;
        hittable = false;
        keepVelocity = false;
        despawnEffect = Fx.none;
        hitEffect = Fx.none;
    }

    @Override
    public void update(Bullet b){
        super.update(b);

        // 持续涡流伤害（单位 + 建筑）
        Dmg.area(b.team, b.x, b.y, pullRadius * 0.6f, 10f * Time.delta / 60f, damageType, true, form);

        if(Mathf.chanceDelta(0.35f)){
            Fx.smoke.at(b.x + Mathf.range(18f), b.y + Mathf.range(18f));
            IronworksFx.singularityRing.at(b.x, b.y);
        }

        Units.nearbyEnemies(b.team, b.x, b.y, pullRadius, u -> {
            if(u.dead()) return;
            float dx = b.x - u.x, dy = b.y - u.y;
            float len = Math.max((float)Math.sqrt(dx * dx + dy * dy), 1f);
            float force = Mathf.clamp(1f - len / pullRadius) * 2.2f;
            u.vel.add(dx / len * force, dy / len * force);
            Dmg.hitSilent(u, pullDamage * Time.delta / 60f, damageType, form);
        });
    }

    @Override
    public void draw(Bullet b){
        float f = Mathf.clamp(b.fin());
        Draw.blend(arc.graphics.Blending.additive);
        Draw.color(color);
        Fill.circle(b.x, b.y, 7f + Mathf.sin(Time.time * 6f + b.id) * 1.5f);
        Lines.stroke(2.2f);
        Lines.circle(b.x, b.y, 14f + f * 6f);
        Draw.reset();
        Draw.blend();
    }

    @Override
    public void despawned(Bullet b){
        super.despawned(b);

        Dmg.area(b.team, b.x, b.y, collapseRadius, collapseDamage, damageType, true, form);
        Fx.shockwave.at(b.x, b.y);
        Fx.explosion.at(b.x, b.y);
        IronworksFx.muzzle.at(b.x, b.y, 0f);
        IronworksFx.singularityRing.at(b.x, b.y);

        // 大块碎片四散
        for(int i = 0; i < 12; i++){
            float ang = Mathf.random(360f), dist = Mathf.random(20f, 90f);
            Fx.explosion.at(b.x + Mathf.cosDeg(ang) * dist, b.y + Mathf.sinDeg(ang) * dist);
        }
    }
}
