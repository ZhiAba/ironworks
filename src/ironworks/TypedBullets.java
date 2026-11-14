package ironworks;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.util.Tmp;
import mindustry.entities.Effect;
import mindustry.entities.bullet.*;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Hitboxc;
import mindustry.content.Fx;
import mindustry.content.StatusEffects;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
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
    @Override public float buildingDamage(Bullet b){ return 0f; }
    @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct){
        Dmg.bulletBuildingHit(this, b, build, x, y, initialHealth, direct);
    }

    final DamageType dtype;
    TypedBasicBulletType(DamageType dtype, float speed, float damage){
        super(speed, damage);
        this.dtype = dtype;
        armorMultiplier = 1f;
    }
    @Override public DamageType dtype(){ return dtype; }
    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){ Dmg.bulletHit(this, b, entity, health); }
    @Override public void createSplashDamage(Bullet b, float x, float y){
        Dmg.splash(this, b, x, y, dtype, () -> super.createSplashDamage(b, x, y));
    }
}

class TypedArtilleryBulletType extends ArtilleryBulletType implements TypedBullet{
    @Override public float buildingDamage(Bullet b){ return 0f; }
    @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct){
        Dmg.bulletBuildingHit(this, b, build, x, y, initialHealth, direct);
    }

    final DamageType dtype;
    TypedArtilleryBulletType(DamageType dtype, float speed, float damage){
        super(speed, damage);
        this.dtype = dtype;
        armorMultiplier = 1f;
    }
    @Override public DamageType dtype(){ return dtype; }
    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){ Dmg.bulletHit(this, b, entity, health); }
    @Override public void createSplashDamage(Bullet b, float x, float y){
        Dmg.splash(this, b, x, y, dtype, () -> super.createSplashDamage(b, x, y));
    }
}

/** 迫击炮专用炮弹：盲区内不结算伤害，并绘制 2D 伪 3D 抛物线。 */
class MortarShellBulletType extends TypedArtilleryBulletType{
    /** 抛物线最高点相对真实弹道的屏幕高度（世界单位）。 */
    public float arcHeight = 44f;

    MortarShellBulletType(DamageType dtype, float speed, float damage){
        super(dtype, speed, damage);
    }

    @Override public void hit(Bullet b, float x, float y, boolean createFrags){
        if(MortarDeadZone.inside(b, x, y)){
            // 盲区内只播放命中反馈，不生成破片/溅射/状态；否则近距离手动弹仍能造成伤害。
            hitEffect.at(x, y, b.rotation(), hitColor);
            if(hitSound != null) hitSound.at(x, y, hitSoundPitch + Mathf.range(hitSoundPitchRange), hitSoundVolume);
            if(hitShake > 0f) Effect.shake(hitShake, hitShake, b);
            return;
        }
        super.hit(b, x, y, createFrags);
    }

    @Override public void createSplashDamage(Bullet b, float x, float y){
        MortarTurret.MortarBuild owner = MortarDeadZone.owner(b);
        float damage = splashDamage;

        // 先让 TypedArtillery 的 vanilla 通道处理状态/点燃/溅射特效，但不让它再打一次范围伤害。
        splashDamage = 0f;
        try{
            super.createSplashDamage(b, x, y);
        }finally{
            splashDamage = damage;
        }

        if(damage <= 0f || b.absorbed) return;

        if(owner == null){
            Dmg.area(b.team, x, y, splashDamageRadius, damage * b.damageMultiplier(), dtype, false, true,
                Dmg.penOf(this), Dmg.formOf(this));
            return;
        }

        float min = owner.minRange();
        Dmg.areaFiltered(b.team, x, y, splashDamageRadius, damage * b.damageMultiplier(), dtype, false, true,
            Dmg.penOf(this), Dmg.formOf(this),
            target -> Mathf.dst(owner.x, owner.y, target.getX(), target.getY()) >= min);
    }

    @Override public void draw(Bullet b){
        float z = Draw.z();

        // 地面拖尾和影子留在真实弹道位置；弹体本身按抛物线高度抬起。
        drawTrail(b);
        float fin = Mathf.clamp(b.fin());
        float lift = Mathf.sin(fin * Mathf.pi) * arcHeight;

        Drawf.shadow(b.x, b.y, width * 0.72f, 0.30f);

        float shrink = shrinkInterp.apply(1f - fin);
        float drawWidth = width * ((1f - shrinkX) + shrinkX * shrink);
        float drawHeight = height * ((1f - shrinkY) + shrinkY * shrink);
        float offset = -90f + (spin != 0f ? Mathf.randomSeed(b.id, 360f) + b.time * spin : 0f) + rotationOffset;
        Color mix = Tmp.c1.set(mixColorFrom).lerp(mixColorTo, fin);
        Draw.mixcol(mix, mix.a);
        Draw.z(Layer.bullet);

        if(backRegion != null && backRegion.found()){
            Draw.color(backColor);
            Draw.rect(backRegion, b.x, b.y + lift, drawWidth, drawHeight, b.rotation() + offset);
        }
        if(frontRegion != null && frontRegion.found()){
            Draw.color(frontColor);
            Draw.rect(frontRegion, b.x, b.y + lift, drawWidth, drawHeight, b.rotation() + offset);
        }

        Draw.reset();
        Draw.z(z);
    }
}

/** 迫击炮破片也遵守盲区：爆炸中心在盲区外时，飞进盲区的破片不能造成伤害。 */
class MortarFragBulletType extends TypedBasicBulletType{
    MortarFragBulletType(DamageType dtype, float speed, float damage){
        super(dtype, speed, damage);
    }

    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){
        if(MortarDeadZone.inside(b, entity.getX(), entity.getY())) return;
        super.hitEntity(b, entity, health);
    }
}

final class MortarDeadZone{
    static MortarTurret.MortarBuild owner(Bullet b){
        return b.owner instanceof MortarTurret.MortarBuild build ? build : null;
    }

    static boolean inside(Bullet b, float x, float y){
        MortarTurret.MortarBuild owner = owner(b);
        return owner != null && Mathf.dst(owner.x, owner.y, x, y) < owner.minRange();
    }
}

class TypedFlakBulletType extends FlakBulletType implements TypedBullet{
    @Override public float buildingDamage(Bullet b){ return 0f; }
    @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct){
        Dmg.bulletBuildingHit(this, b, build, x, y, initialHealth, direct);
    }

    final DamageType dtype;
    TypedFlakBulletType(DamageType dtype){
        super(1f, 1f);
        this.dtype = dtype;
        armorMultiplier = 1f;
    }
    @Override public DamageType dtype(){ return dtype; }
    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){ Dmg.bulletHit(this, b, entity, health); }
    @Override public void createSplashDamage(Bullet b, float x, float y){
        Dmg.splash(this, b, x, y, dtype, () -> super.createSplashDamage(b, x, y));
    }
}

/** 原版激光弹（LaserBolt）：保留直线绘制和 laserBullet=true，命中走属性结算。 */
class TypedLaserBoltBulletType extends LaserBoltBulletType implements TypedBullet{
    @Override public float buildingDamage(Bullet b){ return 0f; }
    @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct){
        Dmg.bulletBuildingHit(this, b, build, x, y, initialHealth, direct);
    }

    final DamageType dtype;
    TypedLaserBoltBulletType(DamageType dtype, float speed, float damage){
        super(speed, damage);
        this.dtype = dtype;
        armorMultiplier = 1f;
    }
    @Override public DamageType dtype(){ return dtype; }
    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){ Dmg.bulletHit(this, b, entity, health); }
}

class TypedLaserBulletType extends LaserBulletType implements TypedBullet{
    @Override public float buildingDamage(Bullet b){ return 0f; }
    @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct){
        Dmg.bulletBuildingHit(this, b, build, x, y, initialHealth, direct);
    }

    final DamageType dtype;
    /** >0 时只固定绘制长度，不改变伤害穿透上限；磁轨炮用它做到“第 6 个目标挡不住特效但也不受伤”。 */
    float visualLength = -1f;

    TypedLaserBulletType(DamageType dtype, float damage){
        super(damage);
        this.dtype = dtype;
        armorMultiplier = 1f;
    }
    @Override public DamageType dtype(){ return dtype; }
    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){ Dmg.bulletHit(this, b, entity, health); }

    @Override
    public void init(Bullet b){
        super.init(b);
        // 伤害仍由 collideLaser 按 pierceCap 截断；只有磁轨炮（visualLength>0）
        // 把 fdata 单独恢复成完整射程，让第 6 个目标截不住特效。
        if(visualLength > 0f){
            b.fdata = visualLength;
            laserEffect.at(b.x, b.y, b.rotation(), visualLength * 0.75f);
        }
    }
}

class TypedLiquidBulletType extends LiquidBulletType implements TypedBullet{
    @Override public float buildingDamage(Bullet b){ return 0f; }
    @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct){
        Dmg.bulletBuildingHit(this, b, build, x, y, initialHealth, direct);
    }

    final DamageType dtype;
    TypedLiquidBulletType(DamageType dtype, Liquid liquid){
        super(liquid);
        this.dtype = dtype;
        armorMultiplier = 1f;
    }
    @Override public DamageType dtype(){ return dtype; }
    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){ Dmg.bulletHit(this, b, entity, health); }
}

class TypedBulletType extends BulletType implements TypedBullet{
    @Override public float buildingDamage(Bullet b){ return 0f; }
    @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct){
        Dmg.bulletBuildingHit(this, b, build, x, y, initialHealth, direct);
    }

    final DamageType dtype;
    TypedBulletType(DamageType dtype, float speed, float damage){
        super(speed, damage);
        this.dtype = dtype;
        armorMultiplier = 1f;
    }
    @Override public DamageType dtype(){ return dtype; }
    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){ Dmg.bulletHit(this, b, entity, health); }
    @Override public void createSplashDamage(Bullet b, float x, float y){
        Dmg.splash(this, b, x, y, dtype, () -> super.createSplashDamage(b, x, y));
    }
}

/** 原版电弧炮的闪电主弹：真正的伤害在 lightningType 的每一跳里。 */
class TypedLightningBulletType extends LightningBulletType implements TypedBullet{
    @Override public float buildingDamage(Bullet b){ return 0f; }
    @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct){
        Dmg.bulletBuildingHit(this, b, build, x, y, initialHealth, direct);
    }

    final DamageType dtype;
    TypedLightningBulletType(DamageType dtype, float damage){
        this.damage = damage;
        this.dtype = dtype;
        armorMultiplier = 1f;
    }
    @Override public DamageType dtype(){ return dtype; }
    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){ Dmg.bulletHit(this, b, entity, health); }
}

/** 原版熔化/连续激光：每 tick 的线段命中仍走自定义结算。 */
class TypedContinuousLaserBulletType extends ContinuousLaserBulletType implements TypedBullet{
    @Override public float buildingDamage(Bullet b){ return 0f; }
    @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct){
        Dmg.bulletBuildingHit(this, b, build, x, y, initialHealth, direct);
    }

    final DamageType dtype;
    TypedContinuousLaserBulletType(DamageType dtype, float damage){
        super(damage);
        this.dtype = dtype;
        armorMultiplier = 1f;
    }
    @Override public DamageType dtype(){ return dtype; }
    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){ Dmg.bulletHit(this, b, entity, health); }
}

/** 原版霰射/破片炮。 */
class TypedShrapnelBulletType extends ShrapnelBulletType implements TypedBullet{
    @Override public float buildingDamage(Bullet b){ return 0f; }
    @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct){
        Dmg.bulletBuildingHit(this, b, build, x, y, initialHealth, direct);
    }

    final DamageType dtype;
    TypedShrapnelBulletType(DamageType dtype){
        this.dtype = dtype;
        armorMultiplier = 1f;
    }
    @Override public DamageType dtype(){ return dtype; }
    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){ Dmg.bulletHit(this, b, entity, health); }
}

/** 原版轨道炮。 */
class TypedRailBulletType extends RailBulletType implements TypedBullet{
    @Override public float buildingDamage(Bullet b){ return 0f; }
    @Override public void hitTile(Bullet b, Building build, float x, float y, float initialHealth, boolean direct){
        Dmg.bulletBuildingHit(this, b, build, x, y, initialHealth, direct);
    }

    final DamageType dtype;
    TypedRailBulletType(DamageType dtype){
        this.dtype = dtype;
        armorMultiplier = 1f;
    }
    @Override public DamageType dtype(){ return dtype; }
    @Override public void hitEntity(Bullet b, Hitboxc entity, float health){ Dmg.bulletHit(this, b, entity, health); }
}
