package ironworks;

import arc.graphics.Color;
import mindustry.entities.Lightning;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.gen.Bullet;
import mindustry.gen.Hitboxc;

/** 点对点激光：命中后再向周围 2.5 格内最多 3 个目标连锁放电。 */
public class ChainLaserBulletType extends LaserBulletType implements TypedBullet{

    public float chainRadius = 2.5f * 8f;
    public int chainCount = 3;
    public float chainDamage = 35f;
    public Color chainColor = Color.valueOf("8a7bff");
    public DamageType dtype = DamageType.thunder;
    public DamageForm form = DamageForm.ENERGY;

    public ChainLaserBulletType(float damage){
        super(damage);
    }

    @Override public DamageType dtype(){ return dtype; }

    @Override
    public void hitEntity(Bullet b, Hitboxc entity, float health){
        Dmg.bulletHit(this, b, entity, health);
    }

    @Override
    public void hit(Bullet b, float x, float y){
        super.hit(b, x, y);
        if(b == null) return;

        int[] remaining = {chainCount};
        Units.nearbyEnemies(b.team, x, y, chainRadius, u -> {
            if(remaining[0] <= 0 || u.dead()) return;
            remaining[0]--;
            Dmg.hit(u, chainDamage, dtype, true, true, dtype.pen, true, form);
            Lightning.create(b.team, chainColor, x, y, u.x, u.y, 2);
        });
    }
}
