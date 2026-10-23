package ironworks;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Font;
import arc.math.Mathf;
import arc.scene.ui.layout.Scl;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Time;
import arc.util.pooling.Pool;
import arc.util.pooling.Pools;
import mindustry.Vars;
import mindustry.entities.Damage;
import mindustry.entities.bullet.ContinuousBulletType;
import mindustry.entities.bullet.LightningBulletType;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitDamageEvent;
import mindustry.gen.Building;
import mindustry.gen.Healthc;
import mindustry.gen.Shieldc;
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.ui.Fonts;

/**
 * 世界坐标漂浮伤害数字（v2.1.0.0）。
 *
 * Dmg.hit 在结算前后取实际掉血，并按属性护甲倍率算出被护甲抵消的部分；
 * 原版未走 Dmg 的普通子弹由 UnitDamageEvent 兜底（溅射/持续激光/连锁闪电跳过，
 * 避免把直伤数值显示到溅射目标身上）。
 *
 * 渲染用 Fonts.outline，颜色取属性专属色；180 tick 生命周期，最后 60 tick 渐隐并上浮。
 * 仅客户端初始化；无头端 show() 直接返回。
 */
public class DamageNumbers{
    public static final String settingKey = "ironworks.damage.numbers";
    public static final float lifetime = 180f;
    private static final int maxActive = 64;

    public static boolean enabled = true;

    private static boolean initialized;
    private static final Seq<Floating> active = new Seq<>();
    private static final Pool<Floating> pool = Pools.get(Floating.class, Floating::new);

    public static void init(){
        if(initialized || Vars.headless) return;
        initialized = true;
        enabled = Core.settings.getBool(settingKey, true);

        Events.run(Trigger.update, DamageNumbers::update);
        Events.run(Trigger.draw, DamageNumbers::draw);
        Events.on(ResetEvent.class, e -> clear());
        Events.on(UnitDamageEvent.class, DamageNumbers::fallback);
    }

    public static void clear(){
        for(Floating f : active){
            f.text = null;
            pool.free(f);
        }
        active.clear();
    }

    /** 被护甲抵消的数值（按属性护甲倍率计算）。 */
    public static float blocked(Healthc target, float amount, DamageType type){
        if(target == null || amount <= 0f) return 0f;
        DamageType t = type == null ? DamageType.physical : type;
        if(t.armorMult <= 0f) return 0f;

        float armor = 0f;
        if(target instanceof Unit unit){
            armor = unit.armorOverride() >= 0f ? unit.armorOverride() : unit.armor();
        }else if(target instanceof Building build && build.block != null){
            armor = build.block.armor;
        }
        if(armor <= 0f) return 0f;

        float applied = Damage.applyArmor(amount, armor * t.armorMult);
        return Math.max(0f, amount - applied);
    }

    /** Dmg.hit 结算后调用：amount 是实际掉血（含护盾吸收），blocked 是护甲抵消。 */
    public static void show(Healthc target, float amount, float blocked, DamageType type){
        if(Vars.headless || !enabled || target == null) return;

        int dealt = Math.max(0, Math.round(amount));
        int armor = Math.max(0, Math.round(blocked));
        if(dealt <= 0 && armor <= 0) return;

        DamageType t = type == null ? DamageType.physical : type;
        Color color = t.color();

        String format = Core.bundle.get("ironworks.damage-number", "{0}（-{1}）");
        String text = format.replace("{0}", String.valueOf(dealt)).replace("{1}", String.valueOf(armor));

        float x = target.x() + Mathf.range(3f);
        float y = target.y();
        if(target instanceof Unit unit){
            y += unit.hitSize / 2f + 7f;
        }else if(target instanceof Building build && build.block != null){
            y += build.block.size * 4f + 7f;
        }

        if(active.size >= maxActive){
            Floating old = active.remove(0);
            old.text = null;
            pool.free(old);
        }

        Floating f = pool.obtain();
        f.x = x;
        f.y = y;
        f.maxLife = lifetime;
        f.life = lifetime;
        f.r = color.r;
        f.g = color.g;
        f.b = color.b;
        f.text = text;
        active.add(f);
    }

    private static void update(){
        if(Vars.headless) return;
        enabled = Core.settings.getBool(settingKey, true);
        if(!Vars.state.isGame()) return;
        if(!enabled){
            if(active.size > 0) clear();
            return;
        }
        for(int i = active.size - 1; i >= 0; i--){
            Floating f = active.get(i);
            f.life -= Time.delta;
            if(f.life <= 0f){
                active.remove(i);
                f.text = null;
                pool.free(f);
            }
        }
    }

    private static void draw(){
        if(Vars.headless || !Vars.state.isGame() || active.isEmpty()) return;

        Font font = Fonts.outline;
        boolean ints = font.usesIntegerPositions();
        font.setUseIntegerPositions(false);
        font.getData().setScale(0.25f * 1.6f / Scl.scl(1f));

        Draw.z(Layer.playerName + 1f);
        for(Floating f : active){
            float alpha = Mathf.clamp(f.life / 60f);
            float y = f.y + (1f - f.life / f.maxLife) * 18f;
            font.setColor(f.r, f.g, f.b, alpha);
            font.draw(f.text, f.x, y, Align.center);
        }

        font.getData().setScale(1f);
        font.setColor(Color.white);
        font.setUseIntegerPositions(ints);
        Draw.reset();
    }

    /** 原版无属性子弹的兜底；typed bullet 已经由 Dmg.bulletHit 显示过。 */
    private static void fallback(UnitDamageEvent e){
        if(Vars.headless || !enabled || e.unit == null || e.bullet == null) return;
        if(e.bullet.type instanceof TypedBullet) return;
        if(e.bullet.type instanceof ContinuousBulletType || e.bullet.type instanceof LightningBulletType) return;
        // 溅射命中的事件无法区分直伤/溅射，跳过，宁可少显示不显示错
        if(e.bullet.type.splashDamageRadius > 0f) return;

        float amount = e.bullet.damage;
        if(amount <= 0f) return;

        DamageType type = Dmg.typeOf(e.bullet);
        float armor = e.unit.armorOverride() >= 0f ? e.unit.armorOverride() : e.unit.armor();
        float applied = e.bullet.type.pierceArmor
            ? amount
            : Damage.applyArmor(amount, armor * type.armorMult * e.bullet.type.armorMultiplier);

        show(e.unit, applied, Math.max(0f, amount - applied), type);
    }

    static class Floating{
        float x, y, life, maxLife, r, g, b;
        String text;
    }
}
