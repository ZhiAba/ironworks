package ironworks;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.entities.Effect;
import mindustry.entities.Lightning;
import mindustry.game.Team;
import mindustry.graphics.Pal;

/** 光污染特效用到的 Effect 集合：全部走加色混合。 */
public class IronworksFx{

    /** 枪口蓄能闪光 */
    public static final Effect muzzle = new Effect(16f, e -> {
        Draw.blend(Blending.additive);
        Draw.color(Color.valueOf("8a7bff"), e.fout() * 0.30f);
        Fill.circle(e.x, e.y, 4f + 8f * e.fin());
        Draw.color(Color.white, e.fout() * 0.15f);
        Fill.circle(e.x, e.y, 2f + 3f * e.fin());
        Lines.stroke(1.2f * e.fout() * 0.6f);
        Lines.circle(e.x, e.y, 8f + 16f * e.fin());
        Draw.blend();
        Draw.reset();
    });

    /** 磁轨束：粗光柱 + 白色核心 + 边缘辉光 */
    public static final Effect railBeam = new Effect(20f, 600f, e -> {
        Draw.blend(Blending.additive);
        Draw.color(Color.valueOf("6fa0ff"), e.fout() * 0.45f);
        Lines.stroke(14f * e.fout());
        Lines.lineAngle(e.x, e.y, e.rotation, 420f);
        Draw.color(Color.valueOf("dfe9ff"), e.fout() * 0.55f);
        Lines.stroke(6f * e.fout());
        Lines.lineAngle(e.x, e.y, e.rotation, 420f);
        Draw.color(Color.white, e.fout());
        Lines.stroke(2.5f * e.fout());
        Lines.lineAngle(e.x, e.y, e.rotation, 420f);
        Draw.blend();
        Draw.reset();
    });

    /** 奇点吸积环 */
    public static final Effect singularityRing = new Effect(30f, e -> {
        Draw.blend(Blending.additive);
        for(int i = 0; i < 3; i++){
            float f = (e.fin() + i / 3f) % 1f;
            Draw.color(Color.valueOf("a06bff"), 1f - f);
            Lines.stroke(2.5f * (1f - f));
            Lines.circle(e.x, e.y, 6f + f * 40f);
        }
        Draw.blend();
        Draw.reset();
    });

    /** 动态长度的磁轨光柱（长度按实际目标距离） */
    public static void beam(float x, float y, float angle, float len){
        new Effect(18f, e -> {
            Draw.blend(Blending.additive);
            Draw.color(Color.valueOf("6fa0ff"), e.fout() * 0.9f);
            Lines.stroke(14f * e.fout());
            Lines.lineAngle(e.x, e.y, angle, len);
            Draw.color(Color.valueOf("dfe9ff"), e.fout());
            Lines.stroke(6f * e.fout());
            Lines.lineAngle(e.x, e.y, angle, len);
            Draw.color(Color.white, e.fout() * 0.45f);
            Lines.stroke(2f * e.fout());
            Lines.lineAngle(e.x, e.y, angle, len);
            Draw.blend();
            Draw.reset();
        }).at(x, y, angle);
    }

    /** 点对点粗雷电：5 根强抖动折线，宽度接近 1~2 格。 */
    public static void arcBolt(Team team, float x1, float y1, float x2, float y2){
        arcBolt(team, x1, y1, x2, y2, 1f);
    }

    /** scale < 1 时闪电更细（天罚用 0.6）。 */
    public static void arcBolt(Team team, float x1, float y1, float x2, float y2, float scale){
        float dx = x2 - x1, dy = y2 - y1;
        float len = Math.max((float)Math.sqrt(dx * dx + dy * dy), 1f);
        float ang = Mathf.angle(dx, dy);

        Color[] cols = {Color.valueOf("8a7bff"), Color.valueOf("b6a8ff"), Color.valueOf("ffffff")};
        float[] widths = {9f * scale, 5f * scale, 2f * scale};

        for(int b = 0; b < 4; b++){
            final float seed = Mathf.random(10000f);
            final float width = widths[b % widths.length];
            final Color col = b == 3 ? Color.white : cols[b % 2];
            final float alpha = b == 3 ? 0.28f : 0.45f;
            final float off = Mathf.range(3f);

            new Effect(14f, e -> {
                Draw.blend(Blending.additive);
                arc.graphics.g2d.Lines.stroke(width * e.fout());
                Draw.color(col, e.fout() * alpha);

                int seg = 16;
                float step = len / seg;
                float nx = Mathf.cosDeg(ang), ny = Mathf.sinDeg(ang);
                float px2 = -ny, py2 = nx;

                float cx = e.x + px2 * off, cy = e.y + py2 * off;
                float lx = cx, ly = cy;

                for(int i = 1; i <= seg; i++){
                    float t = i * step;
                    // 中间抖动最大，两端收敛
                    float amp = len * 0.14f * Mathf.sin((float)i / seg * Mathf.PI);
                    float dev = (Mathf.randomSeed((long)(seed + i * 7), 1000) / 1000f - 0.5f) * 2f * amp;
                    float rx = e.x + nx * t + px2 * (off + dev);
                    float ry = e.y + ny * t + py2 * (off + dev);
                    Draw.blend(Blending.additive);
                    arc.graphics.g2d.Lines.stroke(width * e.fout());
                    Draw.color(col, e.fout() * alpha);
                    arc.graphics.g2d.Lines.line(lx, ly, rx, ry);
                    lx = rx; ly = ry;
                }

                Draw.blend();
                Draw.reset();
            }).at(x1, y1, ang);
        }

        muzzle.at(x1, y1);
        muzzle.at(x2, y2);
    }
}
