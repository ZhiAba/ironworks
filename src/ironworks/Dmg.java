package ironworks;

import arc.Events;
import arc.math.Mathf;
import arc.struct.FloatSeq;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Tmp;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
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
 * <p>v3.0 结算顺序：力场盾 → 跳弹 → 过穿 → 平方衰减穿透/护甲减伤 → 抗性 → Combo → 保底 → 扣血/附着。</p>
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
    public static float lastShieldAbsorbed, lastBodyDamage, lastArmorReduction;
    public static float lastElementResistance, lastEnergyResistance, lastRicochetChance;
    public static String lastComboName;
    /** 上一次命中是否触发跳弹/过穿。 */
    public static boolean lastRicochet, lastOverpen, lastShieldBlocked;
    /** 最近一次过穿的动态保留率。 */
    public static float lastOverpenKeepRate;
    /** 测试钩子：>=0 时覆盖跳弹随机 roll，生产环境保持 -1。 */
    public static float ricochetRoll = -1f;
    /** 标记在 Bullet.data 上，供 splash 判定“本次命中禁止溅射/状态”。 */
    private static final Object overpenMarker = new Object();
    private static final Object ricochetMarker = new Object();
    private static final Object shieldMarker = new Object();

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

    /** 给某个弹种设置穿透曲线；返回这个弹种，方便链式注册。 */
    public static BulletType pen(BulletType type, float rate, float flat, float decay, float ricochet){
        if(type != null) bulletPens.put(type, new ArmorPen(rate, flat, decay, ricochet));
        return type;
    }

    public static BulletType pen(BulletType type, float rate, float flat, float decay){
        return pen(type, rate, flat, decay, 0.02f);
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

    /** 持续场/熔穿等高频伤害用 silent，避免每 tick 刷屏；不触发跳弹/过穿。 */
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
     * @param allowExtremes 是否允许真实弹体的跳弹/过穿；范围/溅射/持续伤害传 false。
     */
    public static float hit(Healthc target, float amount, DamageType type, boolean applyStatus, boolean showNumber, ArmorPen pen, boolean allowExtremes){
        return hit(target, amount, type, applyStatus, showNumber, pen, allowExtremes, type == null ? DamageType.physical.defaultForm : type.defaultForm);
    }

    public static float hit(Healthc target, float amount, DamageType type, boolean applyStatus, boolean showNumber, ArmorPen pen, boolean allowExtremes, DamageForm form){
        if(target == null || amount <= 0f || target.dead()){
            resetLast();
            return 0f;
        }

        DamageType t = type == null ? DamageType.physical : type;
        ArmorPen p = pen == null ? t.pen : pen;
        DamageForm f = form == null ? t.defaultForm : form;
        resetLast();

        boolean numbers = showNumber && !Vars.headless;
        float shieldBefore = target instanceof Shieldc s ? Math.max(s.shield(), 0f) : 0f;
        float incoming = amount;

        // 1. 力场盾：非能量形态先扣盾，溢出部分才进入后续管线；能量形态直接穿盾。
        if(shieldBefore > 0f && !f.bypassShields){
            float absorbed = Math.min(shieldBefore, incoming);
            setShield(target, shieldBefore - absorbed);
            incoming -= absorbed;
            lastShieldAbsorbed = absorbed;
            if(numbers) DamageNumbers.showShield(target, absorbed);
            if(incoming <= 0.0001f){
                lastShieldBlocked = true;
                return 0f;
            }
        }

        float shieldRemaining = target instanceof Shieldc s ? Math.max(s.shield(), 0f) : 0f;
        float armor = armorOf(target);

        // 2. 跳弹：护甲超过白值才按概率触发；能量形态不吃跳弹。
        if(allowExtremes && !f.bypassShields){
            lastRicochetChance = p.ricochetChance(armor);
            if(lastRicochetChance > 0f){
                float roll = ricochetRoll >= 0f ? ricochetRoll : Mathf.random();
                if(roll < lastRicochetChance){
                    lastRicochet = true;
                    if(numbers) DamageNumbers.showRicochet(target);
                    return 0f;
                }
            }
        }

        // 3. 过穿：动能形态且白值 >= 当前护甲 × 2；按动态保留率结算，跳过抗性/Combo/附着。
        if(allowExtremes && f.kinetic && p.canOverpen(armor)){
            lastOverpen = true;
            lastOverpenKeepRate = p.overpenKeepRate(armor);
            float dealt = incoming * lastOverpenKeepRate;
            float actual = applyBody(target, dealt, shieldRemaining);
            lastDealt = dealt;
            lastBodyDamage = actual;
            if(numbers) DamageNumbers.showOverpen(target, actual > 0f ? actual : dealt);
            return dealt;
        }

        // 4. 穿透率/有效护甲/百分比减伤（不再使用旧版 10% 刮痧保底）。
        //    能量形态无视单位/建筑护甲：不是“穿透率 100%”，而是整段护甲乘区不参与结算。
        float effectivePierce;
        float afterArmor;
        if(f == DamageForm.ENERGY){
            effectivePierce = 1f;
            afterArmor = incoming;
            lastArmorAbsorbed = 0f;
            lastArmorReduction = 0f;
        }else{
            effectivePierce = p.effective(armor);
            afterArmor = p.damageAfterArmor(incoming, armor);
            lastArmorAbsorbed = Math.max(0f, incoming - afterArmor);
            lastArmorReduction = p.armorReduction(armor);
        }
        lastEffectivePierce = effectivePierce;

        // 5. 抗性乘区：元素抗性；能量形态同时乘能量抗性。能量形态不再有护甲项。
        float elementRes = Resistance.element(target, t);
        float energyRes = f.bypassShields ? Resistance.energy(target) : 0f;
        float afterResist = afterArmor * (1f - elementRes) * (1f - energyRes);
        lastElementResistance = elementRes;
        lastEnergyResistance = energyRes;

        // 6. Combo 在抗性之后结算；过穿/跳弹已经在上面提前结束。
        float combo = 1f;
        if(applyStatus){
            combo = ElementCombo.evaluate(target, t);
        }
        float finalDamage = afterResist * combo;

        // 防浮点归零：未跳弹的最终伤害至少 1 或基础伤害的 5%，不再是 10% 刮痧。
        float floor = Math.max(1f, amount * 0.05f);
        finalDamage = Math.max(finalDamage, floor);
        lastComboMultiplier = combo;
        lastComboName = ElementCombo.lastName;

        float actual = applyBody(target, finalDamage, shieldRemaining);
        lastDealt = finalDamage;
        lastBodyDamage = actual;

        if(numbers){
            // 某些单位/护盾/瞬时状态会让“结算前后生命差”为 0；用最终伤害兜底，
            // 避免正常伤害完全不显示。过穿/跳弹/护盾有各自分类，不走这里。
            float shown = actual > 0.01f ? actual : finalDamage;
            DamageNumbers.showHit(target, shown, lastArmorAbsorbed, effectivePierce, combo, t, false);
        }

        // 7. 扣血后再消耗旧 Aura、挂新状态；腐蚀另行登记永久减甲。
        if(applyStatus && target instanceof Unit unit){
            ElementCombo.consume(unit);
            ElementCombo.apply(unit, t);
        }
        if(applyStatus && t == DamageType.corrosive){
            Corrosion.apply(target);
        }
        return finalDamage;
    }

    /** 把最终伤害打到本体，绕开已经单独处理的力场盾；返回实际生命损失。 */
    private static float applyBody(Healthc target, float amount, float shieldToRestore){
        if(target == null || amount <= 0f) return 0f;
        float before = target.health();

        if(shieldToRestore > 0f && target instanceof Shieldc shieldTarget){
            shieldTarget.shield(0f);
            target.damagePierce(amount);
            shieldTarget.shield(shieldToRestore);
        }else{
            target.damagePierce(amount);
        }

        return Math.max(0f, before - target.health());
    }

    private static void setShield(Healthc target, float value){
        if(target instanceof Shieldc shield) shield.shield(Math.max(0f, value));
    }

    private static void resetLast(){
        lastEffectivePierce = 0f;
        lastArmorAbsorbed = 0f;
        lastDealt = 0f;
        lastBodyDamage = 0f;
        lastArmorReduction = 0f;
        lastShieldAbsorbed = 0f;
        lastElementResistance = 0f;
        lastEnergyResistance = 0f;
        lastRicochetChance = 0f;
        lastComboMultiplier = 1f;
        lastComboName = null;
        lastRicochet = false;
        lastOverpen = false;
        lastShieldBlocked = false;
        lastOverpenKeepRate = 0f;
    }

    /** UI 用：该属性默认形态是否动能。 */
    public static boolean isKinetic(DamageType type){
        return type != null && type.defaultForm.kinetic;
    }

    /** 真实伤害入口（持续伤害等），不触发护甲/Combo/状态；飘字用红色真伤分类。 */
    public static float pierce(Healthc target, float amount, DamageType type){
        if(target == null || amount <= 0f || target.dead()) return 0f;
        target.damagePierce(amount);
        if(!Vars.headless) DamageNumbers.showTrue(target, amount);
        return amount;
    }

    /** 基础护甲（不含永久腐蚀）。 */
    public static float baseArmorOf(Healthc target){
        if(target instanceof Unit unit){
            return Math.max(0f, unit.armorOverride() >= 0f ? unit.armorOverride() : unit.armor());
        }
        if(target instanceof Building build && build.block != null){
            return Math.max(0f, build.block.armor);
        }
        return 0f;
    }

    /** 当前有效护甲：基础护甲扣除永久腐蚀。 */
    public static float armorOf(Healthc target){
        return Math.max(0f, baseArmorOf(target) - Corrosion.reduction(target));
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

    /**
     * 反应堆范围伤害：同一爆炸里对友军和敌军使用不同倍率。
     *
     * <p>RBMK 熔毁要求友军吃 100%、敌军只吃 2%，但两边都要走完整的护甲/抗性/Combo/状态结算，
     * 所以不能简单调两次 {@link #area}（会重复命中友军或漏掉敌军）。</p>
     */
    public static void areaWeighted(Team sourceTeam, float x, float y, float radius, float amount,
                                    DamageType type, DamageForm form,
                                    float friendlyMultiplier, float enemyMultiplier){
        DamageType t = type == null ? DamageType.physical : type;
        ArmorPen p = t.pen;
        DamageForm f = form == null ? t.defaultForm : form;
        if(amount <= 0f || radius < 0f) return;

        float rr = radius <= 0.00001f ? 0f : radius;
        arc.func.Cons<Unit> unitHit = u -> {
            if(!u.hittable() || u.dead() || !u.within(x, y, radius + u.hitSize / 2f)) return;
            float mult = sourceTeam == null || u.team == sourceTeam ? friendlyMultiplier : enemyMultiplier;
            float scaled = scaled(amount * mult, u.dst(x, y) - u.hitSize / 2f, rr);
            hit(u, scaled, t, true, false, p, false, f);
        };
        // null = 查全部单位，而不是只查敌军；倍率在 unitHit 里按 team 结算。
        Units.nearby(null, x, y, radius, unitHit);

        if(Vars.indexer != null){
            Vars.indexer.eachBlock(null, x, y, radius, b -> b != null && !b.dead(), b -> {
                float mult = sourceTeam == null || b.team == sourceTeam ? friendlyMultiplier : enemyMultiplier;
                float scaled = scaled(amount * mult, b.dst(x, y), rr);
                hit(b, scaled, t, true, false, p, false, f);
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
        if(bullet.data == overpenMarker || bullet.data == ricochetMarker || bullet.data == shieldMarker){
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
                penOf(type), true, form);
            if(lastOverpen){
                bullet.data = overpenMarker;
            }else if(lastRicochet){
                bullet.data = ricochetMarker;
            }else if(lastShieldBlocked){
                bullet.data = shieldMarker;
            }else if(bullet.data == overpenMarker || bullet.data == ricochetMarker || bullet.data == shieldMarker){
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
        }else if(source instanceof LightningBulletType){
            target = new TypedLightningBulletType(type, source.damage);
        }else if(source instanceof ContinuousLaserBulletType){
            target = new TypedContinuousLaserBulletType(type, source.damage);
        }else if(source instanceof ShrapnelBulletType){
            target = new TypedShrapnelBulletType(type);
        }else if(source instanceof RailBulletType){
            target = new TypedRailBulletType(type);
        }else if(source instanceof LaserBulletType){
            target = new TypedLaserBulletType(type, source.damage);
        }else if(source instanceof BasicBulletType){
            target = new TypedBasicBulletType(type, source.speed, source.damage);
        }else{
            // 未知基类（连续激光/闪电/轨道弹等）不要伪装成普通子弹，否则会破坏原行为。
            return source;
        }

        copyFields(source, target);
        // 建筑碰撞也由 Dmg 自己结算，不能再让引擎套一层旧 armorMult
        target.armorMultiplier = 1f;
        // 能量形态必须不被原版力场投影器吸收；否则伤害还没进 Dmg 就被力场墙拦掉了。
        if((form == null ? type.defaultForm : form) == DamageForm.ENERGY) target.absorbable = false;
        if(form != null) formRegistry.put(target, form);

        if(target instanceof TypedLightningBulletType lightning){
            BulletType chunk = new TypedBulletType(type, 0.0001f, 0f);
            chunk.hittable = false;
            chunk.lifetime = Fx.lightning.lifetime;
            chunk.hitEffect = Fx.hitLancer;
            chunk.despawnEffect = Fx.none;
            chunk.status = StatusEffects.shocked;
            chunk.statusDuration = 10f;
            chunk.armorMultiplier = 1f;
            if((form == null ? type.defaultForm : form) == DamageForm.ENERGY) chunk.absorbable = false;
            formRegistry.put(chunk, form == null ? type.defaultForm : form);
            lightning.lightningType = chunk;
        }
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

    /** 复制 frag/spawn 中已知的弹体；未知特殊基类原样保留。 */
    private static BulletType retype(BulletType source, DamageType type){
        return retype(source, type, null);
    }

    private static BulletType retype(BulletType source, DamageType type, DamageForm form){
        if(source == null || source instanceof TypedBullet) return source;

        return canCopy(source) ? copy(source, type, form) : source;
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
        Events.run(mindustry.game.EventType.Trigger.update, Corrosion::update);
        Events.on(mindustry.game.EventType.ResetEvent.class, e -> Corrosion.clear());

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

        // 3b. 所有原版物品/液体/电力炮塔统一接管，避免只覆盖少数硬编码炮塔
        integrateVanillaTurrets();

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
        pen(IronworksTurrets.copperBullet, 0.55f, 15f, 0.35f, 0.04f);
        pen(IronworksTurrets.heavyBullet, 0.70f, 20f, 0.30f, 0.03f);
        pen(IronworksTurrets.leadCoreBullet, 0.75f, 18f, 0.28f, 0.025f);
        pen(IronworksTurrets.tiPierceBullet, 0.90f, 25f, 0.18f, 0.015f);
        pen(IronworksTurrets.tiOverpressureBullet, 0.60f, 20f, 0.30f, 0.02f);
        pen(IronworksTurrets.napalmBullet, 0.25f, 10f, 0.45f, 0.01f);
        pen(IronworksTurrets.corrosiveBullet, 0.80f, 20f, 0.20f, 0.015f);
        form(IronworksTurrets.copperBullet, DamageForm.KINETIC);
        form(IronworksTurrets.heavyBullet, DamageForm.KINETIC);
        form(IronworksTurrets.leadCoreBullet, DamageForm.KINETIC);
        form(IronworksTurrets.tiPierceBullet, DamageForm.KINETIC);
        form(IronworksTurrets.tiOverpressureBullet, DamageForm.EXPLOSIVE);
        form(IronworksTurrets.napalmBullet, DamageForm.EXPLOSIVE);
        form(IronworksTurrets.corrosiveBullet, DamageForm.KINETIC);
        pen(IronworksShells.heBullet, 0.55f, 18f, 0.30f, 0.02f);
        pen(IronworksShells.apBullet, 0.90f, 25f, 0.15f, 0.01f);
        pen(IronworksShells.incendiaryBullet, 0.20f, 8f, 0.45f, 0.01f);
        pen(IronworksShells.fragBullet, 0.45f, 12f, 0.35f, 0.02f);
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

        // 已存在的带属性弹体也同步力场墙穿透标记（包括本模组直接创建的弹体）。
        if(Vars.content != null && Vars.content.bullets() != null){
            for(BulletType bullet : Vars.content.bullets()){
                if(bullet instanceof TypedBullet && formOf(bullet) == DamageForm.ENERGY){
                    bullet.absorbable = false;
                }
            }
        }

        if(!Vars.headless) DamageNumbers.init();
    }

    /** 原版炮塔自动接管：跳过模组方块，按弹种/液体映射元素与形态。 */
    private static void integrateVanillaTurrets(){
        if(Vars.content == null || Vars.content.blocks() == null) return;

        for(Block block : Vars.content.blocks()){
            if(block == null || (block.name != null && block.name.startsWith("ironworks-"))
                || (block.minfo != null && block.minfo.mod != null)) continue;

            if(block instanceof ItemTurret turret){
                registerIfAbsent(turret, DamageType.physical, DamageForm.KINETIC);
                if(turret.ammoTypes == null) continue;
                for(Item item : turret.ammoTypes.keys().toSeq()){
                    BulletType old = turret.ammoTypes.get(item);
                    if(old == null || old instanceof TypedBullet || !canCopy(old)) continue;
                    DamageType type = itemType(item);
                    DamageForm form = itemForm(item);
                    BulletType copied = copy(old, type, form);
                    if(copied != old) turret.ammoTypes.put(item, copied);
                }
            }else if(block instanceof LiquidTurret turret){
                registerIfAbsent(turret, DamageType.physical, DamageForm.KINETIC);
                if(turret.ammoTypes == null) continue;
                for(Liquid liquid : turret.ammoTypes.keys().toSeq()){
                    BulletType old = turret.ammoTypes.get(liquid);
                    if(old == null || old instanceof TypedBullet || !canCopy(old)) continue;
                    DamageType type = liquidType(liquid);
                    DamageForm form = liquidForm(liquid);
                    BulletType copied = copy(old, type, form);
                    if(copied != old) turret.ammoTypes.put(liquid, copied);
                }
            }else if(block instanceof PowerTurret turret){
                DamageType type = powerType(block.name);
                DamageForm form = DamageForm.ENERGY;
                registerIfAbsent(turret, type, form);
                if(turret.shootType != null && !(turret.shootType instanceof TypedBullet) && canCopy(turret.shootType)){
                    BulletType copied = copy(turret.shootType, type, form);
                    if(copied != turret.shootType) turret.shootType = copied;
                }
            }
        }
    }

    private static void registerIfAbsent(Object key, DamageType type, DamageForm form){
        if(!registry.containsKey(key)){
            registry.put(key, type);
            formRegistry.put(key, form);
        }
    }

    private static boolean canCopy(BulletType type){
        return type instanceof FlakBulletType
            || type instanceof ArtilleryBulletType
            || type instanceof LiquidBulletType
            || type instanceof LaserBulletType
            || type instanceof LightningBulletType
            || type instanceof ContinuousLaserBulletType
            || type instanceof ShrapnelBulletType
            || type instanceof RailBulletType
            || type instanceof BasicBulletType;
    }

    private static DamageType itemType(Item item){
        if(item == Items.pyratite) return DamageType.fire;
        return DamageType.physical;
    }

    private static DamageForm itemForm(Item item){
        if(item == Items.pyratite || item == Items.blastCompound || item == Items.sporePod) return DamageForm.EXPLOSIVE;
        return DamageForm.KINETIC;
    }

    private static DamageType liquidType(Liquid liquid){
        if(liquid == Liquids.water) return DamageType.water;
        if(liquid == Liquids.cryofluid) return DamageType.ice;
        if(liquid == Liquids.slag) return DamageType.fire;
        return DamageType.physical;
    }

    private static DamageForm liquidForm(Liquid liquid){
        if(liquid == Liquids.water || liquid == Liquids.cryofluid) return DamageForm.ENERGY;
        if(liquid == Liquids.slag) return DamageForm.EXPLOSIVE;
        return DamageForm.KINETIC;
    }

    private static DamageType powerType(String name){
        if(name == null) return DamageType.physical;
        if(name.equals("lancer") || name.equals("arc")) return DamageType.thunder;
        return DamageType.physical;
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
