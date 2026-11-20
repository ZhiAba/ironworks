package ironworks;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.math.Mathf;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Units;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.type.Category;
import mindustry.world.Block;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import mindustry.graphics.Drawf;


/**
 * 辐射净化舱：消耗电力，缓慢清除范围内友军单位的永久结构损伤。
 *
 * <p>永久损伤不是状态时间，而是单位的 maxHealth 上限；净化只降低
 * {@link IronworksRadiation#scar(Unit)}，单位离开后上限仍保持已恢复的量。</p>
 */
public class IronworksDecontaminator{
    public static Block decontaminator;

    public static void load(){
        decontaminator = new Decontaminator("decontaminator");
    }

    public static class Decontaminator extends Block{
        /** 作用半径（世界单位）。 */
        public float range = 104f;
        /** 每秒恢复的永久损伤比例；0.008 = 1.25 秒恢复 1%。 */
        public float purifyRate = 0.008f;
        /** 电力消耗。 */
        public float powerUse = 2.5f;

        public Decontaminator(String name){
            super(name);
            size = 3;
            solid = true;
            update = true;
            hasPower = true;
            hasLiquids = false;
            category = Category.effect;
            health = 720;
            liquidCapacity = 0f;

            // 净化舱自身有微弱的绿色工作灯；真正的地面光由 build.drawLight 补。
            emitLight = true;
            lightColor = Color.valueOf("7fe3d0").a(0.35f);
            lightRadius = 64f;
            lightClipSize = 180f;

            consumePower(powerUse);
        }

        @Override
        public void setStats(){
            super.setStats();
            stats.add(Stat.range, range / Vars.tilesize, StatUnit.blocks);
            stats.add(Stat.powerUse, powerUse * 60f, StatUnit.powerSecond);
        }

        public class DecontaminatorBuild extends Building{
            float tick;

            @Override
            public void updateTile(){
                if(efficiency <= 0f) return;
                tick += delta();
                if(tick < 10f) return;
                tick = 0f;

                float amount = purifyRate * 10f / 60f;
                Units.nearby(team, x, y, range, unit -> {
                    if(unit == null || unit.dead()) return;
                    if(IronworksRadiation.scar(unit) <= 0.0001f
                        && IronworksRadiation.pollution(unit) <= 0.01f) return;

                    IronworksRadiation.purify(unit, amount);
                    if(!Vars.headless && Mathf.chanceDelta(0.12f)){
                        Fx.heal.at(unit.x, unit.y);
                    }
                });
            }

            @Override
            public void drawLight(){
                super.drawLight();
                float pulse = 0.8f + Mathf.absin(Time.time, 10f, 0.2f);
                Drawf.light(x, y, range * 0.55f * pulse, Color.valueOf("7fe3d0"), 0.28f * pulse);
            }

            @Override
            public void draw(){
                super.draw();
                Draw.color(Color.valueOf("7fe3d0"), 0.10f + 0.06f * Mathf.absin(Time.time, 8f, 1f));
                Draw.rect(arc.Core.atlas.white(), x, y, size * 8f * 0.7f, size * 8f * 0.7f);
                Draw.color();
                Drawf.dashCircle(x, y, range, Color.valueOf("7fe3d0"));
            }
        }
    }
}
