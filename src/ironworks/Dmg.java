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
import mindustry.entities.Damage;
import mindustry.entities.Units;
import mindustry.entities.bullet.*;
import mindustry.game.EventType.UnitBulletDestroyEvent;
import mindustry.game.Team;
import mindustry.game.EventType.UnitDamageEvent;
import mindustry.gen.*;
import mindustry.type.*;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.*;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * 属性伤害统一入口（v2.6 战斗体系）。
 *
 * <p>结算顺序：Combo 乘区 → 有效穿甲率 → 护甲减免 → 10% 保底 → 挂新状态。
 * 穿甲曲线由 {@link ArmorPen} 管理，具体弹种可在 {@link #load()} 里覆盖。</p>
 *
 * <p>所有模组自己结算伤害的地方走这里。原版弹体只做一次反射拷贝，
 * 不改变原版类的全局行为。</p>
 */
public class Dmg{

    /** 内容 -> 属性。Block / BulletType / 其它 Content 都放这里。 */
    public static final ObjectMap<Object, DamageType> registry = new ObjectMap<>();
    /** 具体弹种的穿甲覆盖；没有覆盖时用属性默认曲线。 */
    public static final ObjectMap<BulletType, ArmorPen> bulletPens = new ObjectMap<>();
    /** 内容 -> 伤害形态覆盖；没有覆盖时用属性默认形态。 */
    public static final ObjectMap<Object, DamageForm> formRegistry = new ObjectMap<>();

    /** 最近一次 hit 的结算细节，供飘字/UI 读取；游戏逻辑单线程调用。 */
    public static float lastEffectivePierce, lastArmorAbsorbed, lastDealt, lastComboMultiplier = 1f;
    public static String lastComboName;
    /** 上一次命中是否触发过穿。 */
    public static boolean lastOverpen;
    /** 过穿后的伤害上限：目标最大生命的 30%。 */
    public static final float overpenMaxFraction = 0.30f;
    /** 标记在 Bullet.data 上，供 splash 判定“本次命中过穿，禁止溅射/状态”。 */
    private static final Object overpenMarker = new Object();

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

    /** 弹种形态覆盖；没有覆盖时回退到属性默认形态。 */
    public static DamageForm formOf(BulletType type){
        if(type == null) return DamageType.physical.defaultForm;
        DamageForm form = formRegistry.get(type);
        return form != null ? form : typeOf(type).defaultForm;
    }

    public static DamageForm formOf(Object key){
        if(key == null) return DamageType.physical.defaultForm;
        DamageForm form = formRegistry.get(key);
        return form != null ? form : typeOf(key).defaultForm;
    }

    /** 详情页用：弹体自身形态优先，其次炮塔形态，最后属性默认。 */
    public static DamageForm formOf(BulletType bullet, Turret turret){
        if(bullet != null){
            DamageForm form = formRegistry.get(bullet);
            if(form != null) return form;
        }
        if(turret != null){
            DamageForm form = formRegistry.get(turret);
            if(form != null) return form;
        }
        return formOf(bullet);
    }

    /** 详情页用：弹体穿甲覆盖优先，其次炮塔属性默认。 */
    public static ArmorPen penOf(BulletType bullet, Turret turret){
        if(bullet != null){
            ArmorPen pen = bulletPens.get(bullet);
            if(pen != null) return pen;
        }
        if(bullet instanceof TypedBullet tb) return tb.dtype().pen;
        return typeOf(bullet, turret).pen;
    }

    /** 给某个内容/弹种设置形态。 */
    public static void form(Object key, DamageForm form){
        if(key != null && form != null) formRegistry.put(key, form);
    }

    /** 弹种穿甲覆盖；没有覆盖时回退到属性默认曲线。 */
    public static ArmorPen penOf(BulletType type){
        if(type == null) return DamageType.physical.pen;
        ArmorPen pen = bulletPens.get(type);
        if(pen != null) return pen;
        return type instanceof TypedBullet tb ? tb.dtype().pen : typeOf(type).pen;
    }

    /** 给某个弹种设置穿甲曲线；返回这个弹种，方便链式注册。 */
    public static BulletType pen(BulletType type, float rated, float threshold, float decay){
        if(type != null) bulletPens.put(type, new ArmorPen(rated, threshold, decay));
        return type;
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
        return hit(target, amount, type, true, true, type == null ? DamageType.physical.pen : type.pen, true, type == null ? DamageType.physical.defaultForm : type.defaultForm);
    }

    public static float hit(Healthc target, float amount, DamageType type, boolean applyStatus){
        return hit(target, amount, type, applyStatus, true, type == null ? DamageType.physical.pen : type.pen, true, type == null ? DamageType.physical.defaultForm : type.defaultForm);
    }

    public static float hit(Healthc target, float amount, DamageType type, boolean applyStatus, boolean showNumber){
        return hit(target, amount, type, applyStatus, showNumber, type == null ? DamageType.physical.pen : type.pen);
    }

    /** 持续场/熔穿等高频伤害用 silent，避免每 tick 刷屏。 */
    public static float hitSilent(Healthc target, float amount, DamageType type){
        return hit(target, amount, type, true, false, type == null ? DamageType.physical.pen : type.pen, false, type == null ? DamageType.physical.defaultForm : type.defaultForm);
    }

    public static float hitSilent(Healthc target, float amount, DamageType type, DamageForm form){
        return hit(target, amount, type, true, false, type == null ? DamageType.physical.pen : type.pen, false,
            form == null ? (type == null ? DamageType.physical.defaultForm : type.defaultForm) : form);
    }

    public static float hit(Healthc target, float amount, DamageType type, boolean applyStatus, boolean showNumber, ArmorPen pen){
        return hit(target, amount, type, applyStatus, showNumber, pen, true);
    }

    /**
     * @param allowOverpen 允许过穿：真实单发弹体为 true；范围/溅射/持续伤害为 false。
     */
    public static float hit(Healthc target, float amount, DamageType type, boolean applyStatus, boolean showNumber, ArmorPen pen, boolean allowOverpen){
        return hit(target, amount, type, applyStatus, showNumber, pen, allowOverpen, type == null ? DamageType.physical.defaultForm : type.defaultForm);
    }

    public static float hit(Healthc target, float amount, DamageType type, boolean applyStatus, boolean showNumber, ArmorPen pen, boolean allowOverpen, DamageForm form){
        if(target == null || amount <= 0f || target.dead()) return 0f;

        DamageType t = type == null ? DamageType.physical : type;
        ArmorPen p = pen == null ? t.pen : pen;
        DamageForm f = form == null ? t.defaultForm : form;

        // 0. 过穿：只有动能形态、软体目标、且这一发会打掉超过剩余生命时才触发。
        //    不再用「伤害 >= 最大生命 × 3」这种生命上限比例当触发器；那会让
        //    同一把枪对满血和残血软体的表现突然跳变。这里看的是当前这一发是否
        //    已经“过量”，所以低伤机枪打满血软体不会误触发。
        float armor = armorOf(target);
        float uncomboed = p.apply(amount, armor);
        boolean overpen = allowOverpen && f.kinetic
            && TargetMaterial.of(target) == TargetMaterial.SOFT
            && uncomboed > target.health();
        lastOverpen = overpen;

        // 1. Combo：先查旧附着，再决定这次伤害的乘区；过穿没有 Combo
        float combo = overpen ? 1f : ElementCombo.evaluate(target, t);
        float boosted = amount * combo;

        // 2. 穿甲：有效穿甲率随目标护甲线性衰减
        float effectivePierce = p.effective(armor);
        float dealt = p.apply(boosted, armor);
        if(overpen){
            dealt = Math.min(dealt, target.maxHealth() * overpenMaxFraction);
        }
        lastEffectivePierce = effectivePierce;
        lastArmorAbsorbed = Math.max(0f, boosted - dealt);
        lastDealt = dealt;
        lastComboMultiplier = combo;
        lastComboName = overpen ? null : ElementCombo.lastName;

        boolean numbers = showNumber && !Vars.headless;
        float healthBefore = target.health();
        float shieldBefore = target instanceof Shieldc s ? s.shield() : 0f;

        // 3. 已经自己算过护甲，必须走 pierce 入口，避免引擎再扣一次。
        // 能量形态额外绕过物理护盾：临时移除护盾，打完再放回去（护盾本身不消耗）。
        if(f.bypassShields && target instanceof Shieldc shieldTarget && shieldTarget.shield() > 0f){
            float shield = shieldTarget.shield();
            shieldTarget.shield(0f);
            target.damagePierce(dealt);
            shieldTarget.shield(shield);
        }else{
            target.damagePierce(dealt);
        }

        if(numbers){
            float shieldAfter = target instanceof Shieldc s ? s.shield() : 0f;
            float actual = Math.max(0f, (healthBefore + shieldBefore) - (target.health() + shieldAfter));
            DamageNumbers.show(target, actual, lastArmorAbsorbed, effectivePierce, combo, t, overpen);
        }

        // 4. 消耗旧附着，再挂本属性新状态；过穿不触发也不挂附着
        if(applyStatus && !overpen){
            if(target instanceof Unit unit){
                ElementCombo.consume(unit);
                ElementCombo.apply(unit, t);
            }
        }
        return dealt;
    }

    /** UI 用：该属性默认形态是否动能。 */
    public static boolean isKinetic(DamageType type){
        return type != null && type.defaultForm.kinetic;
    }

    /** 真实伤害入口（持续伤害等），不触发护甲/Combo/状态。 */
    public static float pierce(Healthc target, float amount, DamageType type){
        if(target == null || amount <= 0f || target.dead()) return 0f;
        target.damagePierce(amount);
        return amount;
    }

    public static float armorOf(Healthc target){
        if(target instanceof Unit unit){
            return Math.max(0f, unit.armorOverride() >= 0f ? unit.armorOverride() : unit.armor());
        }
        if(target instanceof Building build && build.block != null){
            return Math.max(0f, build.block.armor);
        }
        return 0f;
    }

    /** 范围属性伤害：逐目标结算护甲/Combo，而不是用原版统一 armorMult。 */
    public static void area(Team team, float x, float y, float radius, float amount, DamageType type, boolean complete){
        area(team, x, y, radius, amount, type, complete, false);
    }

    public static void area(Team team, float x, float y, float radius, float amount, DamageType type, boolean complete, boolean showNumbers){
        area(team, x, y, radius, amount, type, complete, showNumbers, type == null ? DamageType.physical.pen : type.pen);
    }

    public static void area(Team team, float x, float y, float radius, float amount, DamageType type, boolean complete, DamageForm form){
        area(team, x, y, radius, amount, type, complete, false, type == null ? DamageType.physical.pen : type.pen, form);
    }

    public static void area(Team team, float x, float y, float radius, float amount, DamageType type, boolean complete, boolean showNumbers, ArmorPen pen){
        area(team, x, y, radius, amount, type, complete, showNumbers, pen, type == null ? DamageType.physical.defaultForm : type.defaultForm);
    }

    public static void area(Team team, float x, float y, float radius, float amount, DamageType type, boolean complete, boolean showNumbers, ArmorPen pen, DamageForm form){
        DamageType t = type == null ? DamageType.physical : type;
        ArmorPen p = pen == null ? t.pen : pen;
        DamageForm f = form == null ? t.defaultForm : form;
        if(amount <= 0f || radius < 0f) return;

        // 单位：保留原版的范围衰减（中心 100%，边缘 40%）
        float rr = radius <= 0.00001f ? 0f : radius;
        arc.func.Cons<Unit> unitHit = u -> {
            if(!u.hittable() || u.dead() || !u.within(x, y, radius + u.hitSize / 2f)) return;
            float scaled = scaled(amount, u.dst(x, y) - u.hitSize / 2f, rr);
            hit(u, scaled, t, true, showNumbers, p, false, f);
        };
        if(team == null){
            Units.nearby(null, x, y, radius, unitHit);
        }else{
            Units.nearbyEnemies(team, x, y, radius, unitHit);
        }

        // 建筑：逐目标结算，避免多格建筑被按格重复打
        if(Vars.indexer != null){
            // team 只用于敌我筛选；查询索引必须用 null 才能拿到全阵营建筑，
            // 传攻击方 team 会只遍历该阵营的 buildingTree，墙/敌人一个都打不到。
            Vars.indexer.eachBlock(null, x, y, radius, b -> b != null && !b.dead() && (team == null || b.team != team), b -> {
                float scaled = scaled(amount, b.dst(x, y), rr);
                hit(b, scaled, t, true, showNumbers, p, false, f);
            });
        }
    }

    private static float scaled(float amount, float distance, float radius){
        if(radius <= 0.00001f) return amount;
        float falloff = Mathf.lerp(1f - Math.min(Math.max(distance, 0f) / radius, 1f), 1f, 0.4f);
        return amount * falloff;
    }

    /**
     * 弹体溅射接管：
     * 先把原版 splashDamage 临时清零，让 super.createSplashDamage 只做状态/点燃/治疗，
     * 再用属性入口补一次范围伤害。
     */
    public static void splash(BulletType type, Bullet bullet, float x, float y, DamageType dtype, Runnable vanilla){
        // 过穿弹不炸：连原版 splash 附带的状态/点燃也不执行
        if(bullet.data == overpenMarker){
            return;
        }
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
            area(bullet.team, x, y, type.splashDamageRadius, damage * bullet.damageMultiplier(), dtype, false, true, penOf(type), formOf(type));
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

            DamageForm form = formOf(type);
            hit(target, amount, type instanceof TypedBullet tb ? tb.dtype() : typeOf(bullet), true, true,
                penOf(type), form.kinetic, form);
            if(lastOverpen){
                bullet.data = overpenMarker;
            }else if(bullet.data == overpenMarker){
                bullet.data = null;
            }
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

    public static void register(Object key, DamageType type, DamageForm form){
        register(key, type);
        form(key, form);
    }

    /** 把任意弹体复制成带属性的同类弹体；未知基类原样返回。 */
    public static BulletType copy(BulletType source, DamageType type){
        return copy(source, type, null);
    }

    /** 复制时一并继承伤害形态，包含 fragBullet / intervalBullet / spawnBullets。 */
    public static BulletType copy(BulletType source, DamageType type, DamageForm form){
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
        // 建筑碰撞也由 Dmg 自己结算，不能再让引擎套一层旧 armorMult
        target.armorMultiplier = 1f;
        if(form != null) formRegistry.put(target, form);

        target.fragBullet = retype(source.fragBullet, type, form);
        target.intervalBullet = retype(source.intervalBullet, type, form);

        if(source.spawnBullets != null && source.spawnBullets.size > 0){
            target.spawnBullets = new Seq<>();
            for(BulletType bullet : source.spawnBullets){
                target.spawnBullets.add(retype(bullet, type, form));
            }
        }
        return target;
    }

    /** 只复制已知行为一致的子类；LightningBulletType 这类特殊弹体保持原样。 */
    private static BulletType retype(BulletType source, DamageType type){
        return retype(source, type, null);
    }

    private static BulletType retype(BulletType source, DamageType type, DamageForm form){
        if(source == null || source instanceof TypedBullet) return source;

        boolean known = source instanceof FlakBulletType
            || source instanceof ArtilleryBulletType
            || source instanceof LiquidBulletType
            || source instanceof LaserBulletType
            || source instanceof BasicBulletType;

        return known ? copy(source, type, form) : source;
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
        }
        ElementCombo.load();

        // 2. 模组炮塔归属
        register(IronworksTurrets.rapidCannon, DamageType.physical);
        register(IronworksTurrets.piercer, DamageType.physical);
        register(IronworksShells.mortar, DamageType.physical);
        register(IronworksSilicon.arcTower, DamageType.thunder, DamageForm.ENERGY);
        register(IronworksSilicon.railTower, DamageType.thunder, DamageForm.KINETIC);
        register(IronworksSilicon.singularityTower, DamageType.physical, DamageForm.ENERGY);
        register(IronworksTianfa.tianfa, DamageType.thunder, DamageForm.ENERGY);

        // 3. 原版炮塔归属（弹体属性优先，这里兜底详情页与逻辑查询）
        register(Blocks.tsunami, DamageType.water);
        register(Blocks.wave, DamageType.water);
        register(Blocks.lancer, DamageType.thunder, DamageForm.ENERGY);
        register(Blocks.arc, DamageType.thunder, DamageForm.ENERGY);
        register(Blocks.meltdown, DamageType.physical, DamageForm.ENERGY);
        register(Blocks.malign, DamageType.physical, DamageForm.ENERGY);
        register(Blocks.duo, DamageType.physical);

        // 4. 原版液体炮弹：水 / 冰 / 火（渣）按弹种分开
        retypeLiquid((LiquidTurret) Blocks.tsunami, Liquids.water, DamageType.water, DamageForm.ENERGY);
        retypeLiquid((LiquidTurret) Blocks.tsunami, Liquids.cryofluid, DamageType.ice, DamageForm.ENERGY);
        retypeLiquid((LiquidTurret) Blocks.tsunami, Liquids.slag, DamageType.fire, DamageForm.EXPLOSIVE);
        retypeLiquid((LiquidTurret) Blocks.wave, Liquids.water, DamageType.water, DamageForm.ENERGY);
        retypeLiquid((LiquidTurret) Blocks.wave, Liquids.cryofluid, DamageType.ice, DamageForm.ENERGY);
        retypeLiquid((LiquidTurret) Blocks.wave, Liquids.slag, DamageType.fire, DamageForm.EXPLOSIVE);
        register(((LiquidTurret) Blocks.tsunami).ammoTypes.get(Liquids.oil), DamageType.physical);
        register(((LiquidTurret) Blocks.wave).ammoTypes.get(Liquids.oil), DamageType.physical);

        // 5. 原版直射/激光/防空弹
        retypeItems((ItemTurret) Blocks.duo, DamageType.physical, DamageForm.KINETIC);
        retypePower((PowerTurret) Blocks.lancer, DamageType.thunder, DamageForm.ENERGY);
        retypePower((PowerTurret) Blocks.malign, DamageType.physical, DamageForm.ENERGY);
        retypePower((PowerTurret) Blocks.meltdown, DamageType.physical, DamageForm.ENERGY);

        // 6. 具体弹种的穿甲曲线：速度/弹重/用途不同，不能全用属性默认值
        pen(IronworksTurrets.copperBullet, 0.55f, 15f, 0.025f);
        pen(IronworksTurrets.heavyBullet, 0.70f, 20f, 0.020f);
        pen(IronworksTurrets.leadCoreBullet, 0.75f, 18f, 0.020f);
        pen(IronworksTurrets.tiPierceBullet, 0.90f, 25f, 0.015f);
        pen(IronworksTurrets.tiOverpressureBullet, 0.60f, 20f, 0.020f);
        pen(IronworksTurrets.napalmBullet, 0.25f, 10f, 0.025f);
        pen(IronworksTurrets.corrosiveBullet, 0.80f, 20f, 0.015f);
        form(IronworksTurrets.copperBullet, DamageForm.KINETIC);
        form(IronworksTurrets.heavyBullet, DamageForm.KINETIC);
        form(IronworksTurrets.leadCoreBullet, DamageForm.KINETIC);
        form(IronworksTurrets.tiPierceBullet, DamageForm.KINETIC);
        form(IronworksTurrets.tiOverpressureBullet, DamageForm.EXPLOSIVE);
        form(IronworksTurrets.napalmBullet, DamageForm.EXPLOSIVE);
        form(IronworksTurrets.corrosiveBullet, DamageForm.KINETIC);
        pen(IronworksShells.heBullet, 0.55f, 18f, 0.025f);
        pen(IronworksShells.apBullet, 0.90f, 25f, 0.015f);
        pen(IronworksShells.incendiaryBullet, 0.20f, 8f, 0.030f);
        pen(IronworksShells.fragBullet, 0.45f, 12f, 0.025f);
        form(IronworksShells.heBullet, DamageForm.EXPLOSIVE);
        form(IronworksShells.apBullet, DamageForm.KINETIC);
        form(IronworksShells.incendiaryBullet, DamageForm.EXPLOSIVE);
        form(IronworksShells.fragBullet, DamageForm.EXPLOSIVE);
        if(IronworksShells.fragBullet.fragBullet != null) form(IronworksShells.fragBullet.fragBullet, DamageForm.EXPLOSIVE);

        // 7. 让属性状态也能吃到原版溅射逻辑（status 由 BulletType.createSplashDamage 统一施加）
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

    private static void retypeItems(ItemTurret turret, DamageType type, DamageForm form){
        if(turret == null || turret.ammoTypes == null) return;
        Seq<Item> keys = turret.ammoTypes.keys().toSeq();
        for(Item key : keys){
            BulletType old = turret.ammoTypes.get(key);
            if(old != null && !(old instanceof TypedBullet)){
                BulletType copied = copy(old, type, form);
                turret.ammoTypes.put(key, copied);
            }
        }
    }

    private static void retypeLiquid(LiquidTurret turret, Liquid liquid, DamageType type, DamageForm form){
        if(turret == null || turret.ammoTypes == null) return;
        BulletType old = turret.ammoTypes.get(liquid);
        if(old != null && !(old instanceof TypedBullet)){
            BulletType copied = copy(old, type, form);
            turret.ammoTypes.put(liquid, copied);
        }
    }

    private static void retypePower(PowerTurret turret, DamageType type, DamageForm form){
        if(turret != null && turret.shootType != null && !(turret.shootType instanceof TypedBullet)){
            turret.shootType = copy(turret.shootType, type, form);
        }
    }
}
