package ironworks;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Font;
import arc.graphics.g2d.GlyphLayout;
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
import mindustry.gen.Unit;
import mindustry.graphics.Layer;
import mindustry.ui.Fonts;

/**
 * 世界坐标漂浮伤害数字（v3.0 战斗体系）。
 *
 * <p>类别严格区分：跳弹（灰）、过穿（白）、护盾抵挡（蓝）、真实伤害（红）、
 * 普通/元素/Combo。使用对象池，同目标同类别在 1 秒内合并累加；同屏最多 64 条。</p>
 */
public class DamageNumbers{
    public static final String settingKey = "ironworks.damage.numbers";
    public static final float lifetime = 180f;
    public static final float mergeWindow = 60f;
    private static final int maxActive = 64;
    private static final GlyphLayout measure = new GlyphLayout();
    private static final Color COMBO_COLOR = Color.valueOf("ffd166");
    private static final Color DETAIL_COLOR = Color.valueOf("9aa0a6");

    public static boolean enabled = true;

    private static boolean initialized;
    private static final Seq<Floating> active = new Seq<>();
    private static final Pool<Floating> pool = Pools.get(Floating.class, Floating::new);

    private enum Kind{
        HIT, SHIELD, RICOCHET, OVERPEN, TRUE
    }

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
            f.target = null;
            f.text = null;
            f.parts.clear();
            pool.free(f);
        }
        active.clear();
    }

    /** 旧版兼容：按给定属性默认曲线计算护甲过滤量。 */
    public static float blocked(Healthc target, float amount, DamageType type){
        DamageType t = type == null ? DamageType.physical : type;
        return blocked(target, amount, t.pen);
    }

    public static float blocked(Healthc target, float amount, ArmorPen pen){
        if(target == null || amount <= 0f) return 0f;
        ArmorPen p = pen == null ? DamageType.physical.pen : pen;
        return p.blocked(amount, Dmg.armorOf(target));
    }

    /** 旧接口：普通/过穿显示。 */
    public static void show(Healthc target, float actual, float absorbed, float effectivePierce, float combo, DamageType type, boolean overpen){
        if(overpen) showOverpen(target, actual);
        else showHit(target, actual, absorbed, effectivePierce, combo, type, false);
    }

    /** 普通伤害（未跳弹/未过穿）。 */
    public static void showHit(Healthc target, float actual, float absorbed, float effectivePierce, float combo, DamageType type, boolean overpen){
        if(overpen){
            showOverpen(target, actual);
            return;
        }
        add(target, Kind.HIT, actual, absorbed, effectivePierce, combo, type);
    }

    /** 力场盾吸收量。 */
    public static void showShield(Healthc target, float absorbed){
        add(target, Kind.SHIELD, absorbed, 0f, 0f, 1f, null);
    }

    /** 跳弹：伤害归零。 */
    public static void showRicochet(Healthc target){
        add(target, Kind.RICOCHET, 0f, 0f, 0f, 1f, null);
    }

    /** 过穿：显示实际扣血量。 */
    public static void showOverpen(Healthc target, float actual){
        add(target, Kind.OVERPEN, actual, 0f, 0f, 1f, null);
    }

    /** 真实伤害：红色分类。 */
    public static void showTrue(Healthc target, float amount){
        add(target, Kind.TRUE, amount, 0f, 0f, 1f, null);
    }

    private static void add(Healthc target, Kind kind, float amount, float armor, float pierce, float combo, DamageType type){
        if(Vars.headless || !enabled || target == null) return;
        if(amount <= 0f && kind != Kind.RICOCHET) return;

        Floating merge = findMerge(target, kind, type);
        if(merge != null){
            merge.amount += Math.max(0f, amount);
            merge.armor += Math.max(0f, armor);
            merge.pierce = Math.max(merge.pierce, pierce);
            merge.combo = Math.max(merge.combo, combo);
            merge.count++;
            merge.lastHit = Time.time;
            merge.life = lifetime;
            rebuild(merge);
            return;
        }

        if(active.size >= maxActive){
            Floating old = active.remove(0);
            old.target = null;
            old.text = null;
            old.parts.clear();
            pool.free(old);
        }

        Floating f = pool.obtain();
        f.target = target;
        f.kind = kind;
        f.amount = Math.max(0f, amount);
        f.armor = Math.max(0f, armor);
        f.pierce = pierce;
        f.combo = combo;
        f.type = type;
        f.count = 1;
        f.x = target.x() + Mathf.range(7f);
        f.y = target.y() + targetHeight(target);
        f.maxLife = lifetime;
        f.life = lifetime;
        f.lastHit = Time.time;
        f.scale = kind == Kind.OVERPEN ? 1.15f : kind == Kind.RICOCHET ? 1.1f : combo > 1.001f ? 1.3f : 1f;
        Color color = colorFor(f);
        f.r = color.r;
        f.g = color.g;
        f.b = color.b;
        rebuild(f);
        active.add(f);
    }

    private static Floating findMerge(Healthc target, Kind kind, DamageType type){
        for(Floating f : active){
            if(f.target != target || f.kind != kind) continue;
            if(kind == Kind.HIT && f.type != type) continue;
            if(Time.time - f.lastHit > mergeWindow) continue;
            return f;
        }
        return null;
    }

    /** 带兜底的本地化格式：I18NBundle.format 不会把兜底文案当 fallback，必须自己先 get。 */
    private static String text(String key, String fallback, Object... args){
        return Core.bundle.formatString(Core.bundle.get(key, fallback), args);
    }

    /** 重建纯文本与彩色分段；合并伤害时两者都必须刷新。 */
    private static void rebuild(Floating f){
        f.parts.clear();
        f.text = null;
        int actual = Math.max(0, Math.round(f.amount));
        int armor = Math.max(0, Math.round(f.armor));
        int pierce = Math.max(0, Math.min(100, Math.round(f.pierce * 100f)));

        if(f.kind == Kind.RICOCHET){
            String t = f.count > 1
                ? text("ironworks.damage-number-ricochet-count", "跳弹×{0}", f.count)
                : Core.bundle.get("ironworks.damage-number-ricochet", "跳弹");
            addPart(f, t, Color.valueOf("9aa0a6"));
            return;
        }
        if(f.kind == Kind.SHIELD){
            addPart(f, text("ironworks.damage-number-shield", "护盾 -{0}", actual), Color.valueOf("5aa9ff"));
            return;
        }
        if(f.kind == Kind.OVERPEN){
            partText(f, "ironworks.damage-number-overpen", "过穿 {0}", Color.valueOf("f2f2f2"), actual);
            return;
        }
        if(f.kind == Kind.TRUE){
            partText(f, "ironworks.damage-number-true", "真伤 {0}", Color.valueOf("ff4d4d"), actual);
            return;
        }

        // 反应倍率与伤害数值分开着色：倍率黄色，数值使用属性自身颜色。
        if(f.combo > 1.001f){
            String prefix = Core.bundle.get("ironworks.damage-number-combo", "×{0}").replace("{0}", trim(f.combo));
            addPart(f, prefix + " ", COMBO_COLOR);
        }
        Color valueColor = f.type == null ? DamageType.physical.color() : f.type.color();
        if(actual > 0){
            addPart(f, String.valueOf(actual), valueColor);
        }
        if(armor > 0){
            String detail = text("ironworks.damage-number-armor-detail", "（{0}-{1}%）", armor, pierce);
            addPart(f, detail, DETAIL_COLOR);
        }
        if(f.parts.isEmpty()){
            addPart(f, String.valueOf(actual), valueColor);
        }
    }

    /** 单段文本（带本地化兜底）。 */
    private static void partText(Floating f, String key, String fallback, Color color, Object... args){
        addPart(f, text(key, fallback, args), color);
    }

    private static void addPart(Floating f, String value, Color color){
        Part p = new Part();
        p.text = value;
        p.r = color.r;
        p.g = color.g;
        p.b = color.b;
        f.parts.add(p);
        f.text = f.text == null ? value : f.text + value;
    }

    private static Color colorFor(Floating f){
        if(f.kind == Kind.RICOCHET) return Color.valueOf("9aa0a6");
        if(f.kind == Kind.SHIELD) return Color.valueOf("5aa9ff");
        if(f.kind == Kind.OVERPEN) return Color.valueOf("f2f2f2");
        if(f.kind == Kind.TRUE) return Color.valueOf("ff4d4d");
        if(f.combo > 1.001f) return Color.valueOf("ffd166");
        if(f.armor > 0f && f.amount <= f.armor) return Color.valueOf("9aa0a6");
        return f.type == null ? DamageType.physical.color() : f.type.color();
    }

    private static float targetHeight(Healthc target){
        if(target instanceof Unit unit) return unit.hitSize / 2f + 7f;
        if(target instanceof Building build && build.block != null) return build.block.size * 4f + 7f;
        return 7f;
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
                f.target = null;
                f.text = null;
                f.parts.clear();
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
            // 同一目标的多种伤害按行排布，避免不同来源在同一个坐标上完全重叠。
            int row = 0;
            for(Floating other : active){
                if(other == f) break;
                if(other.target == f.target) row++;
            }
            float y = f.y + (1f - f.life / f.maxLife) * 18f + row * 15f * f.scale;
            font.getData().setScale(0.25f * 1.6f / Scl.scl(1f) * f.scale);

            float total = 0f;
            for(Part part : f.parts){
                measure.setText(font, part.text);
                part.width = measure.width;
                total += part.width;
            }
            float cursor = f.x - total / 2f;
            for(Part part : f.parts){
                font.setColor(part.r, part.g, part.b, alpha);
                font.draw(part.text, cursor, y, Align.left | Align.center);
                cursor += part.width;
            }
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
        float armor = Dmg.armorOf(e.unit);
        float dealt = pen.damageAfterArmor(amount, armor);
        showHit(e.unit, dealt, amount - dealt, pen.effective(armor), 1f, type, false);
    }

    static class Floating{
        Healthc target;
        Kind kind;
        DamageType type;
        float x, y, life, maxLife, amount, armor, pierce, combo = 1f, lastHit;
        float r, g, b, scale = 1f;
        int count;
        String text;
        final Seq<Part> parts = new Seq<>();
    }

    static class Part{
        String text;
        float r, g, b, width;
    }
}
