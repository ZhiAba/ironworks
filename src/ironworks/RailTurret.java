package ironworks;

import arc.graphics.Blending;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.world.blocks.defense.turrets.PowerTurret;

/**
 * 磁轨炮：沿用雷光的 LaserBulletType 攻击方式。
 *
 * <p>弹体、音效、充能、命中特效都由原版 LaserBulletType/雷光字段提供；
 * 5 条束的散布由 {@link IronworksSilicon} 里的 ShootPattern 设置。
 * 本类只保留充能环绘制。</p>
 */
public class RailTurret extends PowerTurret{

    /** 一次开火发射几道束（雷光式激光）。 */
    public int beams = 5;
    /** 几道束总共覆盖的角度区间，单位度；5 道均匀分布在这个区间内。 */
    public float beamSpread = 4f;

    public RailTurret(String name){
        super(name);
        // 明确绑定自定义 Build；不能只依赖 Block.initBuilding() 的反射推断。
        buildType = RailTurretBuild::new;
    }

    public class RailTurretBuild extends PowerTurretBuild{

        /** 充能环：装填进度越满，环越亮；满环时补一圈亮边。 */
        @Override
        public void draw(){
            super.draw();

            float p = progress();
            if(p <= 0.03f || !isActive()) return;

            float rad = size * 10f + 4f;
            float t = Time.time * 90f;

            Draw.z(Layer.blockAdditive);
            Draw.blend(Blending.additive);

            Draw.color(Color.valueOf("6fa0ff"), 0.10f + 0.30f * p);
            Lines.stroke(2.4f);
            Lines.arc(x, y, rad, p * 0.98f, t);

            Draw.color(Color.valueOf("dfe9ff"), 0.22f * p);
            Lines.stroke(1.2f);
            Lines.arc(x, y, rad + 3.5f, p * 0.55f, -t * 1.4f);

            // 满环时再点一圈外光
            if(p > 0.92f){
                float pulse = (Mathf.sin(Time.time * 9f) * 0.5f + 0.5f);
                Draw.color(Color.valueOf("9fc4ff"), 0.18f * pulse);
                Lines.stroke(1.6f);
                Lines.circle(x, y, rad + 7f);
            }

            Draw.blend();
            Draw.color();

            Drawf.light(x, y, rad * 1.8f, Color.valueOf("8fb6ff"), 0.25f * p);
        }
    }
}
