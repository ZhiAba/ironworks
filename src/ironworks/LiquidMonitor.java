package ironworks;

import arc.Core;
import arc.files.Fi;
import arc.util.Log;
import arc.util.Time;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.type.Category;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.meta.BuildVisibility;

/**
 * 调试用液体计量器：每秒扫描全图，把所有方块里的冷/热冷却液精确总量写进日志文件。
 *
 * 写入两个位置（哪个能写就写哪个）：
 *   1. /sdcard/ironworks-liquid.log （外部存储，方便导出）
 *   2. 游戏数据目录/ironworks-liquid.log
 */
public class LiquidMonitor extends Block{

    public LiquidMonitor(String name){
        super(name);
        update = true;
        solid = false;
        size = 1;
        alwaysUnlocked = true;
        buildVisibility = BuildVisibility.shown;
        requirements(Category.effect, mindustry.type.ItemStack.empty);
    }

    public class LiquidMonitorBuild extends Building{
        float timer;

        @Override
        public void updateTile(){
            timer += Time.delta;
            if(timer >= 60f){
                timer = 0f;
                scan();
            }
        }

        void scan(){
            Liquid cold = IronworksLiquids.grapheneCoolant;
            Liquid hot = IronworksLiquids.hotCoolant;

            float[] totals = new float[2];
            StringBuilder detail = new StringBuilder();

            Groups.build.each(b -> {
                if(b.liquids == null) return;
                float c = b.liquids.get(cold);
                float h = b.liquids.get(hot);
                if(c > 0.0001f || h > 0.0001f){
                    totals[0] += c;
                    totals[1] += h;
                    detail.append(b.block.name)
                          .append('@').append(b.tileX()).append(',').append(b.tileY())
                          .append('=').append(fmt(c)).append('/').append(fmt(h)).append(' ');
                }
            });

            String line = "time=" + String.format("%.1f", Time.time / 60f)
                + " cold=" + fmt(totals[0])
                + " hot=" + fmt(totals[1])
                + " total=" + fmt(totals[0] + totals[1])
                + " | " + detail;

            Log.info("[LIQMON] " + line);

            write("/sdcard/ironworks-liquid.log", line);
            try{
                write(Core.settings.getDataDirectory().child("ironworks-liquid.log").absolutePath(), line);
            }catch(Throwable ignored){
            }
        }

        void write(String path, String line){
            try{
                Fi file = Core.files.absolute(path);
                file.writeString(line + "\n", true);
            }catch(Throwable t){
                Log.err("[LIQMON] write failed: " + path + " / " + t);
            }
        }

        String fmt(float f){
            return String.format("%.4f", f);
        }
    }
}
