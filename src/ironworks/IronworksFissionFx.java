package ironworks;

import arc.audio.Sound;
import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Fill;
import arc.math.Interp;
import arc.math.Mathf;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.game.Team;
import mindustry.gen.Sounds;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;

import static arc.graphics.g2d.Draw.*;
import static arc.graphics.g2d.Lines.*;
import static arc.math.Angles.*;

/**
 * RBMK 熔毁用的「裂变能量」爆炸特效。
 *
 * <p>参考了 Saturation-Firepower 裂变能量堆的分层思路：先是短促白闪，
 * 再叠多圈不同速度的冲击波，随后是等离子火球、放射状碎片和长时间余烬。
 * 二次爆燃每 12 tick（0.2 秒）生成一次，覆盖主爆炸的完整 960 tick 生命周期，
 * 让整段爆炸都保持密集的链式反应，而不是响一声后长时间安静。
 * 这里没有直接搬用对方的代码或素材，只按同样的视觉节奏重新实现。</p>
 *
 * <p>视觉半径由调用方传入，与伤害半径保持一致；主效果生命周期 960 tick，
 * 二次爆炸自身还会再延续 130 tick（扩散速度是旧版的 2 倍），并且每一颗都带范围伤害。</p>
 */
public class IronworksFissionFx{
    /** 主视觉效果时长：旧版约 480 tick，这里翻倍。 */
    public static final float lifetime = 960f;
    /** 视觉半径与伤害半径保持一致；外部已经应用了整体范围倍率。 */
    public static final float radiusScale = 1f;

    public static final Effect effect = new Effect(lifetime, 3400f, IronworksFissionFx::render)
        .layer(Layer.effect + 0.01f);

    /** 二次爆燃间隔：12 tick = 0.2 秒。 */
    public static final float secondaryInterval = 12f;
    /** 第一次二次爆燃的延迟，避免和主白闪糊在同一帧。 */
    public static final float secondaryFirstDelay = 12f;
    /** 覆盖整个主爆炸生命周期（960 tick）的二次爆燃次数。 */
    public static final int secondaryCount = (int)((lifetime - secondaryFirstDelay) / secondaryInterval) + 1;
    /** 每次二次爆燃的固定伤害。 */
    public static final float secondaryDamage = 15000f;
    /** 回归测试钩子：测试里关掉二次爆燃伤害，隔离 RBMK 物理；生产默认开启。 */
    public static boolean secondaryDamageEnabled = true;
    /** 回归测试钩子：按倍率缩小爆炸伤害，避免测试块被秒；生产默认 1。 */
    public static float testDamageScale = 1f;
    /** 二次爆燃可见橙晕相对传入半径的倍数；伤害半径与这层可见外晕对齐。 */
    public static final float secondaryVisualRadiusScale = 1.65f;
    /** 二次爆燃生命周期；旧版 260 tick，这里减半以把扩散速度翻倍。 */
    public static final float secondaryLifetime = 130f;

    /** 主爆炸之后的小型二次爆燃；每个都有自己的闪光、环、碎片和烟。 */
    public static final Effect secondary = new Effect(secondaryLifetime, 1800f, IronworksFissionFx::renderSecondary)
        .layer(Layer.effect + 0.02f);

    private static Sound secondaryBoom;
    private static boolean secondaryBoomTried;

    /** 客户端初始化时预加载，避免第一颗二次爆燃才解码 OGG 造成卡顿。 */
    public static void init(){
        if(!Vars.headless) ensureSecondaryBoom();
    }

    static void ensureSecondaryBoom(){
        if(secondaryBoomTried) return;
        secondaryBoomTried = true;
        try{
            if(Vars.tree != null && Vars.tree.get("sounds/ironworks-secondary-boom.ogg").exists()){
                secondaryBoom = Vars.tree.loadSound("ironworks-secondary-boom");
            }
        }catch(Throwable t){
            Log.err("Failed to load ironworks secondary boom sound", t);
        }
    }

    static void playSecondaryBoom(float x, float y, int idx){
        ensureSecondaryBoom();
        float pitch = 0.94f + (idx % 5) * 0.018f;
        // 按需求切回原版重击反应堆爆炸；自研 OGG 保留为资源缺失时的备用。
        if(Sounds.explosionReactor2 != null){
            Sounds.explosionReactor2.at(x, y, 1f, pitch);
        }else if(secondaryBoom != null){
            secondaryBoom.at(x, y, 1f, pitch);
        }else if(Sounds.explosion != null){
            Sounds.explosion.at(x, y, 1f, pitch);
        }
    }

    public static void at(float x, float y, float radius, Team team){
        effect.at(x, y, 0f, Color.white, radius);
        Effect.shake(20f, 210f, x, y);

        // 原版 ImpactReactor 的核心爆炸作第一层底子；外层冲击波和余烬由本类补足。
        if(!Vars.headless){
            Fx.impactReactorExplosion.at(x, y);
            Sounds.explosionReactor2.at(x, y);
        }

        // 每 0.2 秒一次，贯穿 960 tick 的主爆炸生命周期；链式反应不停顿。
        // 伤害调度不放进 headless 分支，专用服务器也必须吃到二次爆炸伤害。
        for(int i = 0; i < secondaryCount; i++){
            int idx = i;
            float delay = secondaryFirstDelay + i * secondaryInterval;
            float angle = Mathf.randomSeed(i * 53 + 7, 0f, 360f);
            float distance = radius * Mathf.randomSeed(i * 31 + 19, 0.10f, 0.62f);
            float subRadius = radius * Mathf.randomSeed(i * 71 + 23, 0.18f, 0.34f);

            Time.run(delay, () -> {
                float sx = x + trnsx(angle, distance);
                float sy = y + trnsy(angle, distance);
                secondary.at(sx, sy, 0f, Color.white, subRadius);

                // 每次后续爆燃固定 15000；火元素 + 爆炸形态，友军 100%、敌军 2%。
                if(secondaryDamageEnabled){
                    Dmg.areaWeighted(team, sx, sy, subRadius * secondaryVisualRadiusScale,
                        secondaryDamage * testDamageScale, DamageType.fire, DamageForm.EXPLOSIVE, 1f, 0.02f);
                }

                if(!Vars.headless){
                    playSecondaryBoom(sx, sy, idx);
                    if(idx % 4 == 0) Fx.massiveExplosion.at(sx, sy);
                    if(idx % 6 == 0) Effect.shake(3.2f, 24f, sx, sy);
                }
            });
        }
    }

    static void render(Effect.EffectContainer e){
        float radius = e.data instanceof Number n ? n.floatValue() : 480f;
        float t = e.time;
        float norm = Mathf.clamp(t / lifetime);

        // 0. 巨型光球：比普通火球更大，使用加法混合形成真正的“光球”而不是一张白饼。
        if(t < 320f){
            float p = Mathf.clamp(t / 320f);
            float orb = radius * (0.18f + 0.55f * Interp.pow3Out.apply(p));
            blend(Blending.additive);
            color(Color.white, Pal.lighterOrange, p);
            alpha(0.34f * (1f - p));
            Fill.circle(e.x, e.y, orb);
            color(Pal.lighterOrange, Pal.reactorPurple2, p);
            alpha(0.20f * (1f - p));
            Fill.circle(e.x, e.y, orb * 1.45f);
            blend();
            Drawf.light(e.x, e.y, orb * 3.1f, Pal.lighterOrange, 0.95f * (1f - p));
        }

        // 1. 白热闪光：给爆心一个短促、过曝的起点。
        if(t < 55f){
            float p = t / 55f;
            color(Color.white, Pal.reactorPurple, p);
            alpha(0.9f * (1f - p));
            Fill.circle(e.x, e.y, radius * (0.10f + 0.08f * p));
            Drawf.light(e.x, e.y, radius * 1.05f, Pal.reactorPurple, 0.9f * (1f - p));
        }

        // 2. 五圈错峰冲击波：前三圈是主环，后两圈把震波拖到中后段。
        for(int i = 0; i < 5; i++){
            float start = i < 3 ? i * 38f : 330f + (i - 3) * 110f;
            float duration = i < 3 ? 300f - i * 30f : 360f;
            if(t < start) continue;

            float q = Mathf.clamp((t - start) / duration);
            float eased = Interp.pow3Out.apply(q);
            float ringRadius = radius * (0.08f + 0.98f * eased);
            float fade = 1f - q;
            Color from = i == 0 ? Color.white : (i == 1 ? Pal.reactorPurple : Pal.reactorPurple2);
            if(i >= 3) from = Pal.lighterOrange;

            color(from, Pal.reactorPurple2, q);
            alpha(0.62f * fade);
            stroke((i == 0 ? 18f : (i >= 3 ? 7f : 10f)) * fade + 0.8f);
            circle(e.x, e.y, ringRadius);
            Drawf.light(e.x, e.y, ringRadius * 1.15f, i >= 3 ? Pal.lighterOrange : Pal.reactorPurple, 0.42f * fade);
        }

        // 3. 外圈橙色火球 + 内部白热核心，形成比单色更明显的层次。
        if(t < 440f){
            float q = Mathf.clamp(t / 440f);
            color(Pal.lighterOrange, Pal.reactorPurple2, q);
            alpha(0.22f * (1f - q));
            Fill.circle(e.x, e.y, radius * 0.40f * (1f - q * 0.52f));

            color(Color.white, Pal.lighterOrange, q);
            alpha(0.32f * (1f - q));
            Fill.circle(e.x, e.y, radius * 0.20f * (1f - q * 0.60f));
        }

        // 4. 等离子团：数量比旧版多一倍以上，并且外扩更远。
        if(t < 620f){
            float q = Mathf.clamp(t / 620f);
            color(Pal.lighterOrange, Pal.reactorPurple2, q);
            alpha(0.24f * (1f - q));
            Fill.circle(e.x, e.y, radius * 0.32f * (1f - q * 0.58f));

            randLenVectors(e.id + 2, (int)(52f * (1f - q) + 8f), radius * 0.72f * q, (x, y) -> {
                Fill.circle(e.x + x, e.y + y, (1f - q) * (7f + Mathf.randomSeed(e.id + 7, 0f, 16f)));
            });
        }

        // 5. 放射状碎片/电弧：更密、更快，先于余烬消失。
        if(t < 720f){
            float q = Mathf.clamp(t / 720f);
            color(Pal.lighterOrange, Color.gray, q);
            alpha(0.85f * (1f - q));
            stroke(4.2f * (1f - q) + 0.5f);
            randLenVectors(e.id + 1, 150, radius * 1.25f * Interp.pow2Out.apply(q), (x, y) -> {
                lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 4f + 22f * (1f - q));
            });
        }

        // 6. 大块残骸：比细线更重，弥补火球边缘的空洞。
        if(t < 760f){
            float q = Mathf.clamp(t / 760f);
            color(Pal.lighterOrange, Pal.reactorPurple2, q);
            alpha(0.55f * (1f - q));
            randLenVectors(e.id + 4, 36, radius * 1.05f * Interp.pow2Out.apply(q), (x, y) -> {
                Fill.circle(e.x + x, e.y + y, 2.5f + 8f * (1f - q));
            });
        }

        // 7. 余烬和烟尘：拖到最后一帧，制造长时间尾迹。
        if(t > 110f){
            float q = Mathf.clamp((t - 110f) / (lifetime - 110f));
            color(Pal.reactorPurple2, Color.gray, q);
            alpha(0.17f * (1f - q));
            randLenVectors(e.id + 3, 62, radius * 0.92f * Interp.pow2Out.apply(q), (x, y) -> {
                Fill.circle(e.x + x, e.y + y, 4f + 14f * (1f - q));
            });
        }

        // 8. 持续的紫色环境光，尾段自然衰减。
        if(t < 360f){
            Drawf.light(e.x, e.y, radius * 1.7f, Pal.reactorPurple, 0.24f * (1f - t / 360f));
        }
    }

    static void renderSecondary(Effect.EffectContainer e){
        float radius = e.data instanceof Number n ? n.floatValue() : 160f;
        float t = e.time;
        float q = Mathf.clamp(t / secondaryLifetime);

        // 巨型光球：和主爆炸一样用高不透明度的白热核心 + 橙色外晕，不再是一层淡光。
        if(t < secondaryLifetime){
            float p = Mathf.clamp(t / secondaryLifetime);
            float orb = radius * (0.55f + 0.85f * Interp.pow3Out.apply(p));
            blend(Blending.additive);

            // 真正的白热核心：接近不透明，保证每次爆燃都能在日光下“炸眼”。
            color(Color.white);
            alpha(1f * (1f - p));
            Fill.circle(e.x, e.y, orb * 0.62f);

            // 主体白光：比旧版更实、更亮。
            color(Color.white, Pal.lighterOrange, p);
            alpha(0.92f * (1f - p));
            Fill.circle(e.x, e.y, orb);

            // 橙色外晕，峰值正好落在伤害半径上，保证看到的范围就是打到的范围。
            color(Pal.lighterOrange, Pal.reactorPurple2, p);
            alpha(0.58f * (1f - p));
            Fill.circle(e.x, e.y, orb * (secondaryVisualRadiusScale / 1.4f));
            blend();

            // 光照拉满并扩大半径，让光球真的照到地面和周围方块。
            Drawf.light(e.x, e.y, orb * 4.2f, Pal.lighterOrange, 1f * (1f - p));
        }

        // 小型二次爆燃也有自己的白闪和橙环。
        if(t < 19f){
            float p = t / 19f;
            color(Color.white, Pal.lighterOrange, p);
            alpha(1f * (1f - p));
            Fill.circle(e.x, e.y, radius * (0.26f + 0.28f * p));
            Drawf.light(e.x, e.y, radius * 2.4f, Pal.lighterOrange, 1f * (1f - p));
        }

        if(t < 95f){
            float p = Mathf.clamp(t / 95f);
            color(Pal.lighterOrange, Pal.reactorPurple2, p);
            alpha(0.88f * (1f - p));
            stroke(7f * (1f - p) + 0.9f);
            circle(e.x, e.y, radius * (0.18f + 1.45f * Interp.pow2Out.apply(p)));
        }

        // 火球核心
        if(t < 110f){
            color(Color.white, Pal.lighterOrange, q);
            alpha(0.92f * (1f - q));
            Fill.circle(e.x, e.y, radius * 0.55f * (1f - q * 0.45f));
        }

        // 碎片、火星和烟；每颗二次爆炸都多于首批的普通粒子。
        randLenVectors(e.id + 1, 32, radius * 1.55f * Interp.pow2Out.apply(q), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, (1f - q) * (5f + Mathf.randomSeed(e.id + 9, 0f, 14f)));
        });

        if(t < 110f){
            color(Pal.lighterOrange, Color.gray, q);
            alpha(0.85f * (1f - q));
            stroke(3.2f * (1f - q) + 0.5f);
            randLenVectors(e.id + 2, 22, radius * 1.45f * Interp.pow2Out.apply(q), (x, y) -> {
                lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 3f + 16f * (1f - q));
            });
        }

        if(t > 22.5f){
            float s = Mathf.clamp((t - 22.5f) / (secondaryLifetime - 22.5f));
            color(Pal.reactorPurple2, Color.gray, s);
            alpha(0.18f * (1f - s));
            randLenVectors(e.id + 3, 16, radius * 1.1f * Interp.pow2Out.apply(s), (x, y) -> {
                Fill.circle(e.x + x, e.y + y, 3f + 9f * (1f - s));
            });
        }
    }
}
