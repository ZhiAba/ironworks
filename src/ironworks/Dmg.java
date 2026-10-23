package ironworks;

import arc.Events;
import arc.math.Mathf;
import arc.struct.FloatSeq;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Liquids;
import mindustry.content.StatusEffects;
import mindustry.entities.Damage;
import mindustry.entities.Units;
import mindustry.entities.bullet.*;
import mindustry.game.EventType.UnitBulletDestroyEvent;
import mindustry.game.EventType.UnitDamageEvent;
import mindustry.game.Team;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * 属性伤害统一入口（v2.0.0.0）。
 *
 * 所有模组自己结算伤害的地方都走这里：
 *   Dmg.hit(unit, 100f, DamageType.thunder);
 *   Dmg.area(team, x, y, radius, 200f, DamageType.fire, false);
 *   new TypedBasicBulletType(DamageType.fire, 4f, 38f){{ ... }}   // 弹体自带属性
 *
 * 规则：
 *  - 单位：armorMult 直接乘在护甲上（0 = 真伤，0.5 = 半甲）。
 *  - 建筑：Building 的 armor 不在 damage() 里结算，所以这里显式算一次。
 *  - 状态：命中后按属性附加；火/腐蚀等状态型属性因此不需要在每个弹体里重复写。
 *  - 克制：目标身上有 bonusStatus 时伤害乘 bonusMult（雷霆打潮湿、冰打潮湿、能量打电击）。
 *
 * 设计约束：
 *  - 每发子弹只做 O(1) 的字段/注册表查询，不 new 对象、不遍历。
 *  - 原版弹体不复制整类，只给需要改结算的弹种做一次反射拷贝（读条时一次性开销）。
 *  - 没有命中任何已知归属时默认物理，保证“所有伤害都有类型”。
 */
public class Dmg{

    /** 内容 -> 属性。Block / BulletType / 其它 Content 都放这里。 */
    public static final ObjectMap<Object, DamageType> registry = new ObjectMap<>();
    private static final UnitDamageEvent damageEvent = new UnitDamageEvent();
    private static boolean loaded;

    // ────────────────────────── 查询 ──────────────────────────

    public static DamageType typeOf(Object key){
        return key == null ? DamageType.physical : registry.get(key, DamageType.physical);
    }

    public static DamageType typeOf(BulletType type){
        if(type == null) return DamageType.physical;
        return type instanceof TypedBullet tb ? tb.dtype() : registry.get(type, DamageType.physical);
    }

    public static DamageType typeOf(Bullet bullet){
        if(bullet == null || bullet.type == null) return DamageType.physical;
        if(bullet.type instanceof TypedBullet tb) return tb.dtype();
        DamageType type = registry.get(bullet.type);
        if(type != null) return type;
        if(bullet.owner instanceof Building build && build.block != null) return typeOf(build.block);
        return DamageType.physical;
    }

    /** 详情页用：先看弹体自己的属性，再看炮塔方块归属。 */
    public static DamageType typeOf(BulletType bullet, Turret turret){
        if(bullet == null) return turret == null ? DamageType.physical : typeOf(turret);
        if(bullet instanceof TypedBullet tb) return tb.dtype();
        DamageType type = registry.get(bullet);
        if(type != null) return type;
        return turret == null ? DamageType.physical : typeOf(turret);
    }

    /** 按详情页真实显示顺序返回弹药列表（原版 StatValues.ammo 也按这个顺序排）。 */
    public static Seq<BulletType> ammoTypes(Turret turret){
        Seq<BulletType> out = new Seq<>();
        if(turret == null) return out;

        if(turret instanceof ItemTurret item){
            Seq<Item> keys = item.ammoTypes.keys().toSeq();
            keys.sort();
            for(Item key : keys) out.add(item.ammoTypes.get(key));
        }else if(turret instanceof LiquidTurret liquid){
            Seq<Liquid> keys = liquid.ammoTypes.keys().toSeq();
            keys.sort();
            for(Liquid key : keys) out.add(liquid.ammoTypes.get(key));
        }else if(turret instanceof PowerTurret power && power.shootType != null){
            out.add(power.shootType);
        }
        return out;
    }

    // ────────────────────────── 结算 ──────────────────────────

    public static float hit(Healthc target, float amount, DamageType type){
        return hit(target, amount, type, true, true);
    }

    public static float hit(Healthc target, float amount, DamageType type, boolean applyStatus){
        return hit(target, amount, type, applyStatus, true);
    }

    /** 持续场/熔穿等高频伤害用 silent，避免每 tick 刷屏。 */
    public static float hitSilent(Healthc target, float amount, DamageType type){
        return hit(target, amount, type, true, false);
    }

    public static float hit(Healthc target, float amount, DamageType type, boolean applyStatus, boolean showNumber){
        if(target == null || amount <= 0f || target.dead()) return 0f;

        DamageType t = type == null ? DamageType.physical : type;

        // 克制：先看目标当前状态，再结算
        if(applyStatus && t.bonusStatus != null && target instanceof Unit unit && unit.hasEffect(t.bonusStatus)){
            amount *= t.bonusMult;
        }

        boolean numbers = showNumber && !Vars.headless;
        float healthBefore = target.health();
        float shieldBefore = target instanceof Shieldc s ? s.shield() : 0f;
        float blocked = numbers ? DamageNumbers.blocked(target, amount, t) : 0f;

        if(target instanceof Building build){
            // Building.damage() 本身不结算 block.armor，这里补上
            float armor = build.block == null ? 0f : build.block.armor * t.armorMult;
            target.damage(Damage.applyArmor(amount, armor));
        }else if(t.armorMult <= 0f){
            target.damagePierce(amount);
        }else if(t.armorMult != 1f){
            target.damageArmorMult(amount, t.armorMult);
        }else{
            target.damage(amount);
        }

        if(numbers){
            float shieldAfter = target instanceof Shieldc s ? s.shield() : 0f;
            DamageNumbers.show(target, Math.max(0f, (healthBefore + shieldBefore) - (target.health() + shieldAfter)), blocked, t);
        }

        if(applyStatus && t.status != null && target instanceof Unit unit && Mathf.chance(t.statusChance)){
            unit.apply(t.status, t.statusDuration);
        }
        return amount;
    }

    /** 真实伤害入口（持续伤害等），同样带属性语义，但不触发状态。 */
    public static float pierce(Healthc target, float amount, DamageType type){
        if(target == null || amount <= 0f || target.dead()) return 0f;
        target.damagePierce(amount);
        return amount;
    }

    /** 范围属性伤害。complete=true 时连地形/完整建筑一起结算（原版 Damage.damage 的 complete）。 */
    public static void area(Team team, float x, float y, float radius, float amount, DamageType type, boolean complete){
        area(team, x, y, radius, amount, type, complete, false);
    }

    /** showNumbers 仅给弹体溅射用；持续场/熔毁不刷屏。 */
    public static void area(Team team, float x, float y, float radius, float amount, DamageType type, boolean complete, boolean showNumbers){
        DamageType t = type == null ? DamageType.physical : type;
        if(amount <= 0f) return;

        boolean numbers = showNumbers && !Vars.headless;
        if(numbers) captureArea(team, x, y, radius);
        Damage.damage(team, x, y, radius, amount, complete, true, true, false, null, t.armorMult);
        if(numbers) showArea(x, y, radius, amount, t);

        if(t.status != null){
            Damage.status(team, x, y, radius, t.status, t.statusDuration, true, true, t.statusChance);
        }
    }

    private static final Seq<Unit> areaUnits = new Seq<>();
    private static final FloatSeq areaHealth = new FloatSeq(), areaShield = new FloatSeq();

    private static void captureArea(Team team, float x, float y, float radius){
        areaUnits.clear();
        areaHealth.clear();
        areaShield.clear();
        if(team != null){
            Units.nearbyEnemies(team, x, y, radius, u -> addAreaUnit(u, x, y, radius));
        }else{
            Units.nearby(null, x, y, radius, u -> addAreaUnit(u, x, y, radius));
        }
    }

    private static void addAreaUnit(Unit unit, float x, float y, float radius){
        if(!unit.hittable() || !unit.checkTarget(true, true) || !unit.within(x, y, radius)) return;
        areaUnits.add(unit);
        areaHealth.add(unit.health());
        areaShield.add(unit.shield());
    }

    private static void showArea(float x, float y, float radius, float amount, DamageType type){
        for(int i = 0; i < areaUnits.size; i++){
            Unit unit = areaUnits.get(i);
            float scaled = radius <= 0.00001f ? amount
                : amount * Mathf.lerp(1f - Math.min(unit.dst(x, y) / radius, 1f), 1f, 0.4f);
            float blocked = DamageNumbers.blocked(unit, scaled, type);
            float dealt = (areaHealth.get(i) + areaShield.get(i)) - (unit.health() + unit.shield());
            DamageNumbers.show(unit, Math.max(0f, dealt), blocked, type);
        }
        areaUnits.clear();
        areaHealth.clear();
        areaShield.clear();
    }

    /**
     * 弹体溅射接管：
     * 先把原版 splashDamage 临时清零，让 super.createSplashDamage 只做状态/点燃/治疗，
     * 再用属性入口补一次范围伤害。
     */
    public static void splash(BulletType type, Bullet bullet, float x, float y, DamageType dtype, Runnable vanilla){
        if(type.splashDamageRadius <= 0f || bullet.absorbed){
            vanilla.run();
            return;
        }

        float damage = type.splashDamage;
        type.splashDamage = 0f;
        try{
            vanilla.run();
        }finally{
            type.splashDamage = damage;
        }

        if(damage > 0f){
            // 溅射不是 complete 伤害；splashDamagePierce 只影响护甲，这里保持普通范围结算
            area(bullet.team, x, y, type.splashDamageRadius, damage * bullet.damageMultiplier(), dtype, false, true);
        }
    }

    /** 与 BulletType.hitEntity 等价，只把 h.damage(...) 换成属性结算。 */
    public static void bulletHit(BulletType type, Bullet bullet, Hitboxc entity, float health){
        boolean wasDead = entity instanceof Unit unit && unit.dead;

        if(entity instanceof Healthc target){
            float amount = bullet.damage;
            float shield = entity instanceof Shieldc s ? Math.max(s.shield(), 0f) : 0f;

            if(type.maxDamageFraction > 0){
                float cap = target.maxHealth() * type.maxDamageFraction + shield;
                amount = Math.min(amount, cap);
                health = Math.min(health, cap);
            }else{
                health += shield;
            }

            if(type.lifesteal > 0f && bullet.owner instanceof Healthc owner){
                float result = Math.max(Math.min(target.health(), amount), 0f);
                owner.heal(result * type.lifesteal);
            }

            hit(target, amount, type instanceof TypedBullet tb ? tb.dtype() : typeOf(bullet));
        }

        if(entity instanceof Unit unit){
            if(unit.type.knockbackMultiplier > 0f){
                Tmp.v3.set(unit).sub(bullet).nor().scl(type.knockback * 80f * unit.type.knockbackMultiplier);
                if(type.impact) Tmp.v3.setAngle(bullet.rotation() + (type.knockback < 0 ? 180f : 0f));
                unit.impulse(Tmp.v3);
            }

            Events.fire(damageEvent.set(unit, bullet));
        }

        if(!wasDead && entity instanceof Unit unit && unit.dead){
            Events.fire(new UnitBulletDestroyEvent(unit, bullet));
        }

        type.handlePierce(bullet, health, entity.x(), entity.y());
    }

    // ────────────────────────── 注册 ──────────────────────────

    public static void register(Object key, DamageType type){
        if(key != null && type != null) registry.put(key, type);
    }

    /** 把任意弹体复制成带属性的同类弹体；未知基类原样返回。 */
    public static BulletType copy(BulletType source, DamageType type){
        if(source == null || source instanceof TypedBullet) return source;

        BulletType target;
        if(source instanceof FlakBulletType){
            target = new TypedFlakBulletType(type);
        }else if(source instanceof ArtilleryBulletType){
            target = new TypedArtilleryBulletType(type, source.speed, source.damage);
        }else if(source instanceof LiquidBulletType liquid){
            target = new TypedLiquidBulletType(type, liquid.liquid);
        }else if(source instanceof LaserBulletType){
            target = new TypedLaserBulletType(type, source.damage);
        }else if(source instanceof BasicBulletType){
            target = new TypedBasicBulletType(type, source.speed, source.damage);
        }else{
            target = new TypedBulletType(type, source.speed, source.damage);
        }

        copyFields(source, target);
        // 建筑碰撞走原版 armorMultiplier，这里同步成属性护甲倍率
        target.armorMultiplier = type.armorMult;

        target.fragBullet = retype(source.fragBullet, type);
        target.intervalBullet = retype(source.intervalBullet, type);

        if(source.spawnBullets != null && source.spawnBullets.size > 0){
            target.spawnBullets = new Seq<>();
            for(BulletType bullet : source.spawnBullets){
                target.spawnBullets.add(retype(bullet, type));
            }
        }
        return target;
    }

    /** 只复制已知行为一致的子类；LightningBulletType 这类特殊弹体保持原样。 */
    private static BulletType retype(BulletType source, DamageType type){
        if(source == null || source instanceof TypedBullet) return source;

        boolean known = source instanceof FlakBulletType
            || source instanceof ArtilleryBulletType
            || source instanceof LiquidBulletType
            || source instanceof LaserBulletType
            || source instanceof BasicBulletType;

        return known ? copy(source, type) : source;
    }

    private static void copyFields(BulletType source, BulletType target){
        for(Class<?> c = source.getClass(); c != null && c != Object.class; c = c.getSuperclass()){
            for(Field field : c.getDeclaredFields()){
                if(Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) continue;
                String name = field.getName();
                // id 是内容注册表分配的，不能覆盖；dtype 是目标自己的字段
                if(name.equals("id") || name.equals("dtype")) continue;
                try{
                    field.setAccessible(true);
                    field.set(target, field.get(source));
                }catch(Throwable ignored){
                }
            }
        }
    }

    // ────────────────────────── 加载 ──────────────────────────

    public static void load(){
        if(loaded) return;
        loaded = true;

        // 1. 解析状态引用（枚举常量创建时不能碰 StatusEffects）
        for(DamageType type : DamageType.values()){
            if(type.statusName != null) type.status = Vars.content.statusEffect(type.statusName);
            if(type.bonusStatusName != null) type.bonusStatus = Vars.content.statusEffect(type.bonusStatusName);
        }

        // 2. 模组炮塔归属
        register(IronworksTurrets.rapidCannon, DamageType.physical);
        register(IronworksTurrets.piercer, DamageType.physical);
        register(IronworksShells.mortar, DamageType.physical);
        register(IronworksSilicon.arcTower, DamageType.thunder);
        register(IronworksSilicon.railTower, DamageType.thunder);
        register(IronworksSilicon.singularityTower, DamageType.energy);
        register(IronworksTianfa.tianfa, DamageType.thunder);

        // 3. 原版炮塔归属（弹体属性优先，这里兜底详情页与逻辑查询）
        register(Blocks.tsunami, DamageType.water);
        register(Blocks.wave, DamageType.water);
        register(Blocks.lancer, DamageType.thunder);
        register(Blocks.arc, DamageType.thunder);
        register(Blocks.meltdown, DamageType.energy);
        register(Blocks.malign, DamageType.energy);
        register(Blocks.duo, DamageType.physical);

        // 4. 原版液体炮弹：水 / 冰 / 火（渣）按弹种分开
        retypeLiquid((LiquidTurret) Blocks.tsunami, Liquids.water, DamageType.water);
        retypeLiquid((LiquidTurret) Blocks.tsunami, Liquids.cryofluid, DamageType.ice);
        retypeLiquid((LiquidTurret) Blocks.tsunami, Liquids.slag, DamageType.fire);
        retypeLiquid((LiquidTurret) Blocks.wave, Liquids.water, DamageType.water);
        retypeLiquid((LiquidTurret) Blocks.wave, Liquids.cryofluid, DamageType.ice);
        retypeLiquid((LiquidTurret) Blocks.wave, Liquids.slag, DamageType.fire);
        // 油弹不是水：保持原版物理结算，只登记归属，避免详情页被炮塔的“水”兜底
        register(((LiquidTurret) Blocks.tsunami).ammoTypes.get(Liquids.oil), DamageType.physical);
        register(((LiquidTurret) Blocks.wave).ammoTypes.get(Liquids.oil), DamageType.physical);

        // 双管炮：用户点名的物理基准。换成 typed physical 后直伤也走 Dmg.bulletHit（伤害数字）
        retypeItems((ItemTurret) Blocks.duo, DamageType.physical);

        // 5. 原版直射/激光/防空弹
        retypePower((PowerTurret) Blocks.lancer, DamageType.thunder);
        retypePower((PowerTurret) Blocks.malign, DamageType.energy);

        // 6. 让属性状态也能吃到原版溅射逻辑（status 由 BulletType.createSplashDamage 统一施加）
        if(Vars.content != null && Vars.content.bullets() != null){
            for(BulletType bullet : Vars.content.bullets()){
                if(!(bullet instanceof TypedBullet typed)) continue;
                DamageType type = typed.dtype();
                if(type.status != null){
                    bullet.status = type.status;
                    bullet.statusDuration = type.statusDuration;
                    bullet.statusChance = type.statusChance;
                }
            }
        }

        if(!Vars.headless) DamageNumbers.init();
    }

    private static void retypeItems(ItemTurret turret, DamageType type){
        if(turret == null || turret.ammoTypes == null) return;
        Seq<Item> keys = turret.ammoTypes.keys().toSeq();
        for(Item key : keys){
            BulletType old = turret.ammoTypes.get(key);
            if(old != null && !(old instanceof TypedBullet)){
                turret.ammoTypes.put(key, copy(old, type));
            }
        }
    }

    private static void retypeLiquid(LiquidTurret turret, Liquid liquid, DamageType type){
        if(turret == null || turret.ammoTypes == null) return;
        BulletType old = turret.ammoTypes.get(liquid);
        if(old != null && !(old instanceof TypedBullet)){
            turret.ammoTypes.put(liquid, copy(old, type));
        }
    }

    private static void retypePower(PowerTurret turret, DamageType type){
        if(turret != null && turret.shootType != null && !(turret.shootType instanceof TypedBullet)){
            turret.shootType = copy(turret.shootType, type);
        }
    }
}
