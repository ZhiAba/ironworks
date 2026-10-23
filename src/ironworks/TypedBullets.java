package ironworks;

import mindustry.entities.bullet.*;
import mindustry.gen.Bullet;
import mindustry.gen.Hitboxc;
import mindustry.type.Liquid;

/**
 * 带属性的弹体。文件里放多个包内类，避免为每个基类单独开文件。
 *
 * 约定：
 *  - 所有 hitEntity 都交给 Dmg.bulletHit，保证同一套结算（护甲、克制、状态）。
 *  - 有溅射的弹体额外接管 createSplashDamage，把溅射也换成属性伤害。
 *  - 新增基类时在这里补一个子类即可，Dmg.copy 会自动识别。
 */
interface TypedBullet{
    DamageType dtype();
}

class TypedBasicBulletType extends BasicBulletType implements TypedBullet{
    final DamageType dtype;
    TypedBasicBulletType(DamageType dtype, float speed, float damage){
        super(speed, damage);
        this.dtype = dtype;
        armorMultiplier = dtype.armorMult;
    }
    @Override public DamageType dtype(){ return dtype; }
    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){ Dmg.bulletHit(this, b, entity, health); }
    @Override public void createSplashDamage(Bullet b, float x, float y){
        Dmg.splash(this, b, x, y, dtype, () -> super.createSplashDamage(b, x, y));
    }
}

class TypedArtilleryBulletType extends ArtilleryBulletType implements TypedBullet{
    final DamageType dtype;
    TypedArtilleryBulletType(DamageType dtype, float speed, float damage){
        super(speed, damage);
        this.dtype = dtype;
        armorMultiplier = dtype.armorMult;
    }
    @Override public DamageType dtype(){ return dtype; }
    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){ Dmg.bulletHit(this, b, entity, health); }
    @Override public void createSplashDamage(Bullet b, float x, float y){
        Dmg.splash(this, b, x, y, dtype, () -> super.createSplashDamage(b, x, y));
    }
}

class TypedFlakBulletType extends FlakBulletType implements TypedBullet{
    final DamageType dtype;
    TypedFlakBulletType(DamageType dtype){
        super(1f, 1f);
        this.dtype = dtype;
        armorMultiplier = dtype.armorMult;
    }
    @Override public DamageType dtype(){ return dtype; }
    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){ Dmg.bulletHit(this, b, entity, health); }
    @Override public void createSplashDamage(Bullet b, float x, float y){
        Dmg.splash(this, b, x, y, dtype, () -> super.createSplashDamage(b, x, y));
    }
}

class TypedLaserBulletType extends LaserBulletType implements TypedBullet{
    final DamageType dtype;
    TypedLaserBulletType(DamageType dtype, float damage){
        super(damage);
        this.dtype = dtype;
        armorMultiplier = dtype.armorMult;
    }
    @Override public DamageType dtype(){ return dtype; }
    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){ Dmg.bulletHit(this, b, entity, health); }
}

class TypedLiquidBulletType extends LiquidBulletType implements TypedBullet{
    final DamageType dtype;
    TypedLiquidBulletType(DamageType dtype, Liquid liquid){
        super(liquid);
        this.dtype = dtype;
        armorMultiplier = dtype.armorMult;
    }
    @Override public DamageType dtype(){ return dtype; }
    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){ Dmg.bulletHit(this, b, entity, health); }
}

class TypedBulletType extends BulletType implements TypedBullet{
    final DamageType dtype;
    TypedBulletType(DamageType dtype, float speed, float damage){
        super(speed, damage);
        this.dtype = dtype;
        armorMultiplier = dtype.armorMult;
    }
    @Override public DamageType dtype(){ return dtype; }
    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){ Dmg.bulletHit(this, b, entity, health); }
    @Override public void createSplashDamage(Bullet b, float x, float y){
        Dmg.splash(this, b, x, y, dtype, () -> super.createSplashDamage(b, x, y));
    }
}
