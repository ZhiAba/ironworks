package ironworks;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Fill;
import arc.math.Interp;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
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
 * 另外在 34–850 tick 之间错峰生成一批小爆炸，让主火球后面持续有二次爆燃。
 * 这里没有直接搬用对方的代码或素材，只按同样的视觉节奏重新实现。</p>
 *
 * <p>视觉半径由调用方传入；当前在 RBMK 中传入伤害半径的 2 倍，
 * 主效果生命周期 960 tick；二次爆炸会继续延续到约 1100 tick。</p>
 */
public class IronworksFissionFx{
    /** 主视觉效果时长：旧版约 480 tick，这里翻倍。 */
    public static final float lifetime = 960f;
    /** 视觉半径相对伤害半径的倍率。 */
    public static final float radiusScale = 2f;

    public static final Effect effect = new Effect(lifetime, 3400f, IronworksFissionFx::render)
        .layer(Layer.effect + 0.01f);

    /** 主爆炸之后的小型二次爆燃；每个都有自己的闪光、环、碎片和烟。 */
    public static final Effect secondary = new Effect(260f, 1600f, IronworksFissionFx::renderSecondary)
        .layer(Layer.effect + 0.02f);

    public static void at(float x, float y, float radius){
        effect.at(x, y, 0f, Color.white, radius);
        Effect.shake(20f, 210f, x, y);

        if(!Vars.headless){
            // 原版 ImpactReactor 的核心爆炸作第一层底子；外层冲击波和余烬由本类补足。
            Fx.impactReactorExplosion.at(x, y);
            Sounds.explosionReactor2.at(x, y);

            // 18 次错峰二次爆炸，覆盖 34–850 tick；比单次爆点更像持续的链式反应。
            for(int i = 0; i < 18; i++){
                int idx = i;
                float delay = 34f + i * 46f + Mathf.randomSeed(i * 97 + 11, 0f, 20f);
                float angle = Mathf.randomSeed(i * 53 + 7, 0f, 360f);
                float distance = radius * Mathf.randomSeed(i * 31 + 19, 0.10f, 0.62f);
                float subRadius = radius * Mathf.randomSeed(i * 71 + 23, 0.16f, 0.31f);

                Time.run(delay, () -> {
                    float sx = x + trnsx(angle, distance);
                    float sy = y + trnsy(angle, distance);
                    secondary.at(sx, sy, 0f, Color.white, subRadius);
                    Sounds.explosion.at(sx, sy, 0.52f + (idx % 3) * 0.05f);

                    if(idx % 3 == 0) Fx.massiveExplosion.at(sx, sy);
                    if(idx % 4 == 0) Effect.shake(3.2f, 24f, sx, sy);
                });
            }
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
        float q = Mathf.clamp(t / 260f);

        // 巨型光球：每次后续爆炸都要有一个能照亮地面的白色/橙色光核。
        if(t < 260f){
            float p = Mathf.clamp(t / 260f);
            float orb = radius * (0.55f + 0.85f * Interp.pow3Out.apply(p));
            blend(Blending.additive);
            color(Color.white, Pal.lighterOrange, p);
            alpha(0.42f * (1f - p));
            Fill.circle(e.x, e.y, orb);
            color(Pal.lighterOrange, Pal.reactorPurple2, p);
            alpha(0.23f * (1f - p));
            Fill.circle(e.x, e.y, orb * 1.55f);
            blend();
            Drawf.light(e.x, e.y, orb * 2.7f, Pal.lighterOrange, 0.95f * (1f - p));
        }

        // 小型二次爆燃也有自己的白闪和橙环。
        if(t < 38f){
            float p = t / 38f;
            color(Color.white, Pal.lighterOrange, p);
            alpha(0.85f * (1f - p));
            Fill.circle(e.x, e.y, radius * (0.22f + 0.20f * p));
            Drawf.light(e.x, e.y, radius * 1.5f, Pal.lighterOrange, 0.8f * (1f - p));
        }

        if(t < 190f){
            float p = Mathf.clamp(t / 190f);
            color(Pal.lighterOrange, Pal.reactorPurple2, p);
            alpha(0.62f * (1f - p));
            stroke(5f * (1f - p) + 0.6f);
            circle(e.x, e.y, radius * (0.18f + 1.45f * Interp.pow2Out.apply(p)));
        }

        // 火球核心
        if(t < 220f){
            color(Color.white, Pal.lighterOrange, q);
            alpha(0.55f * (1f - q));
            Fill.circle(e.x, e.y, radius * 0.55f * (1f - q * 0.45f));
        }

        // 碎片、火星和烟；每颗二次爆炸都多于首批的普通粒子。
        randLenVectors(e.id + 1, 40, radius * 1.45f * Interp.pow2Out.apply(q), (x, y) -> {
            Fill.circle(e.x + x, e.y + y, (1f - q) * (4f + Mathf.randomSeed(e.id + 9, 0f, 12f)));
        });

        if(t < 220f){
            color(Pal.lighterOrange, Color.gray, q);
            alpha(0.72f * (1f - q));
            stroke(2.8f * (1f - q) + 0.4f);
            randLenVectors(e.id + 2, 24, radius * 1.35f * Interp.pow2Out.apply(q), (x, y) -> {
                lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 3f + 14f * (1f - q));
            });
        }

        if(t > 45f){
            float s = Mathf.clamp((t - 45f) / 215f);
            color(Pal.reactorPurple2, Color.gray, s);
            alpha(0.12f * (1f - s));
            randLenVectors(e.id + 3, 18, radius * 1.1f * Interp.pow2Out.apply(s), (x, y) -> {
                Fill.circle(e.x + x, e.y + y, 3f + 9f * (1f - s));
            });
        }
    }
}
