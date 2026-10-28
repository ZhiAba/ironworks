package ironworks;

import arc.graphics.Color;
import arc.graphics.g2d.Fill;
import arc.math.Interp;
import arc.math.Mathf;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.gen.Sounds;
import mindustry.entities.Effect;
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
 * 再叠三圈不同速度的冲击波，随后是等离子火球、放射状碎片和长时间余烬。
 * 这里没有直接搬用对方的代码或素材，只按同样的视觉节奏重新实现。</p>
 *
 * <p>视觉半径由调用方传入；当前在 RBMK 中传入伤害半径的 2 倍，
 * 总生命周期 960 tick，约为旧版 reactorExplosion 的两倍。</p>
 */
public class IronworksFissionFx{
    /** 总视觉时长：旧版约 480 tick，这里翻倍。 */
    public static final float lifetime = 960f;
    /** 视觉半径相对伤害半径的倍率。 */
    public static final float radiusScale = 2f;

    public static final Effect effect = new Effect(lifetime, 3200f, IronworksFissionFx::render)
        .layer(Layer.effect + 0.01f);

    public static void at(float x, float y, float radius){
        effect.at(x, y, 0f, Color.white, radius);
        Effect.shake(18f, 180f, x, y);
        if(!Vars.headless){
            // 原版 ImpactReactor 的核心爆炸作第一层底子；外层冲击波和余烬由本类补足。
            Fx.impactReactorExplosion.at(x, y);
            Sounds.explosionReactor2.at(x, y);
        }
    }

    static void render(Effect.EffectContainer e){
        float radius = e.data instanceof Number n ? n.floatValue() : 480f;
        float t = e.time;
        float norm = Mathf.clamp(t / lifetime);

        // 1. 白热闪光：给爆心一个短促、过曝的起点。
        if(t < 55f){
            float p = t / 55f;
            color(Color.white, Pal.reactorPurple, p);
            alpha(0.85f * (1f - p));
            Fill.circle(e.x, e.y, radius * (0.10f + 0.06f * p));
            Drawf.light(e.x, e.y, radius * 0.9f, Pal.reactorPurple, 0.8f * (1f - p));
        }

        // 2. 三层错峰冲击波：内圈快、外圈慢，形成层次。
        for(int i = 0; i < 3; i++){
            float start = i * 38f;
            float duration = 300f - i * 30f;
            if(t < start) continue;

            float q = Mathf.clamp((t - start) / duration);
            float eased = Interp.pow3Out.apply(q);
            float ringRadius = radius * (0.08f + 0.98f * eased);
            float fade = 1f - q;

            color(i == 0 ? Color.white : (i == 1 ? Pal.reactorPurple : Pal.reactorPurple2), Pal.reactorPurple2, q);
            alpha(0.65f * fade);
            stroke((i == 0 ? 18f : 10f) * fade + 0.8f);
            circle(e.x, e.y, ringRadius);
            Drawf.light(e.x, e.y, ringRadius * 1.15f, Pal.reactorPurple, 0.45f * fade);
        }

        // 3. 等离子火球：一边收缩一边向外抛射发光团。
        if(t < 520f){
            float q = Mathf.clamp(t / 520f);
            color(Pal.lighterOrange, Pal.reactorPurple2, q);
            alpha(0.18f * (1f - q));
            Fill.circle(e.x, e.y, radius * 0.35f * (1f - q * 0.55f));

            randLenVectors(e.id + 2, (int)(26f * (1f - q) + 4f), radius * 0.55f * q, (x, y) -> {
                Fill.circle(e.x + x, e.y + y, (1f - q) * (8f + Mathf.randomSeed(e.id + 7, 0f, 12f)));
            });
        }

        // 4. 放射状碎片/电弧：速度更快，先于余烬消失。
        if(t < 700f){
            float q = Mathf.clamp(t / 700f);
            color(Pal.lighterOrange, Color.gray, q);
            alpha(0.8f * (1f - q));
            stroke(3.5f * (1f - q) + 0.4f);
            randLenVectors(e.id + 1, 72, radius * 1.1f * Interp.pow2Out.apply(q), (x, y) -> {
                lineAngle(e.x + x, e.y + y, Mathf.angle(x, y), 4f + 16f * (1f - q));
            });
        }

        // 5. 余烬和烟尘：拖到最后一帧，制造长时间尾迹。
        if(t > 120f){
            float q = Mathf.clamp((t - 120f) / (lifetime - 120f));
            color(Pal.reactorPurple2, Color.gray, q);
            alpha(0.12f * (1f - q));
            randLenVectors(e.id + 3, 28, radius * 0.8f * Interp.pow2Out.apply(q), (x, y) -> {
                Fill.circle(e.x + x, e.y + y, 4f + 10f * (1f - q));
            });
        }

        // 6. 持续的紫色环境光，尾段自然衰减。
        if(t < 300f){
            Drawf.light(e.x, e.y, radius * 1.5f, Pal.reactorPurple, 0.2f * (1f - t / 300f));
        }
    }
}
