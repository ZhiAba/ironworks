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
 * 世界坐标漂浮伤害数字（v2.6 战斗体系）。
 *
 * <p>直伤由 {@link Dmg} 传入「实际伤害 / 护甲抵消 / 有效穿甲率 / Combo 倍率」；
 * 显示格式例：{@code 5（5-50%）}，Combo 时变成金色 {@code ×1.5 5（5-50%）}。
 * 高护甲硬挡时数字转灰。原版未走 Dmg 的普通子弹由 UnitDamageEvent 兜底。</p>
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

    /** 旧版兼容：按给定属性默认曲线计算护甲抵消。 */
    public static float blocked(Healthc target, float amount, DamageType type){
        DamageType t = type == null ? DamageType.physical : type;
        return blocked(target, amount, t.pen);
    }

    public static float blocked(Healthc target, float amount, ArmorPen pen){
        if(target == null || amount <= 0f) return 0f;
        ArmorPen p = pen == null ? DamageType.physical.pen : pen;
        return p.blocked(amount, Dmg.armorOf(target));
    }

    /** Dmg.hit 结算后调用。actual 是实际掉血（含护盾吸收），absorbed 是护甲抵消。 */
    public static void show(Healthc target, float actual, float absorbed, float effectivePierce, float combo, DamageType type, boolean overpen){
        if(Vars.headless || !enabled || target == null) return;

        int dealt = Math.max(0, Math.round(actual));
        int armor = Math.max(0, Math.round(absorbed));
        int pierce = Math.max(0, Math.min(100, Math.round(effectivePierce * 100f)));
        if(dealt <= 0 && armor <= 0) return;

        DamageType t = type == null ? DamageType.physical : type;
        boolean armorText = armor > 0;
        boolean isCombo = combo > 1.001f;

        String text;
        if(overpen){
            text = Core.bundle.get("ironworks.damage-number-overpen", "过穿 {0}")
                .replace("{0}", String.valueOf(dealt));
        }else if(armorText){
            String format = Core.bundle.get("ironworks.damage-number-armor", "{0}（{1}-{2}%）");
            text = format.replace("{0}", String.valueOf(dealt))
                .replace("{1}", String.valueOf(armor))
                .replace("{2}", String.valueOf(pierce));
        }else{
            String format = Core.bundle.get("ironworks.damage-number", "{0}");
            text = format.replace("{0}", String.valueOf(dealt));
        }
        if(isCombo && !overpen){
            String prefix = Core.bundle.get("ironworks.damage-number-combo", "×{0} ");
            text = prefix.replace("{0}", trim(combo)) + " " + text;
        }

        Color color = overpen ? Color.valueOf("c9a86a")
            : isCombo ? Color.valueOf("ffd166")
            : (armor > 0 && dealt <= armor) ? Color.valueOf("9aa0a6") : t.color();

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
        f.scale = overpen ? 1.1f : isCombo ? 1.35f : 1f;
        f.text = text;
        active.add(f);
    }

    private static String trim(float value){
        return value == (int)value ? String.valueOf((int)value) : String.format(java.util.Locale.US, "%.2f", value);
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

        Draw.z(Layer.playerName + 1f);
        for(Floating f : active){
            float alpha = Mathf.clamp(f.life / 60f);
            float y = f.y + (1f - f.life / f.maxLife) * 18f;
            font.getData().setScale(0.25f * 1.6f / Scl.scl(1f) * f.scale);
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
        if(e.bullet.type.splashDamageRadius > 0f) return;

        float amount = e.bullet.damage;
        if(amount <= 0f) return;

        DamageType type = Dmg.typeOf(e.bullet);
        ArmorPen pen = Dmg.penOf(e.bullet.type);
        float dealt = pen.apply(amount, Dmg.armorOf(e.unit));
        show(e.unit, dealt, amount - dealt, pen.effective(Dmg.armorOf(e.unit)), 1f, type, false);
    }

    static class Floating{
        float x, y, life, maxLife, r, g, b, scale = 1f;
        String text;
    }
}
