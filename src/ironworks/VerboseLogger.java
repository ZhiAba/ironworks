package ironworks;

import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.type.Category;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.meta.BuildVisibility;

/**
 * 全量日志器（调试）：每 tick 扫描全图，记录玩家状态、单位/建筑数量、
 * 所有模组炮塔的液体与弹药、冷热液体总量及突变，并记录点击/建造/拆除/配置等操作事件。
 *
 * 输出：/sdcard/ironworks-tick.log（写不进去时退回游戏数据目录）
 */
public class VerboseLogger extends Block{

    public VerboseLogger(String name){
        super(name);
        update = true;
        solid = false;
        size = 1;
        alwaysUnlocked = true;
        buildVisibility = BuildVisibility.shown;
        requirements(Category.effect, ItemStack.empty);
    }

    public class VerboseLoggerBuild extends Building{
        float lastCold = -1f, lastHot = -1f;
        float flushTimer;

        @Override
        public void updateTile(){
            IronworksLog.active = true;
            noSleep();

            Liquid cold = IronworksLiquids.grapheneCoolant;
            Liquid hot = IronworksLiquids.hotCoolant;
            Liquid chill = IronworksLiquids.chiller;

            float coldTotal = 0f, hotTotal = 0f, chillTotal = 0f;
            int modBuilds = 0;
            StringBuilder turrets = new StringBuilder();
            StringBuilder mods = new StringBuilder();

            for(Building b : Groups.build){
                if(!b.block.name.startsWith("ironworks-")) continue;
                modBuilds++;

                if(b.liquids != null){
                    coldTotal += b.liquids.get(cold);
                    hotTotal += b.liquids.get(hot);
                    chillTotal += b.liquids.get(chill);
                }

                if(b instanceof Turret.TurretBuild t){
                    turrets.append(b.block.name).append('@').append(b.tileX()).append(',').append(b.tileY())
                        .append(" ammo=").append(t.totalAmmo)
                        .append(" C=").append(IronworksLog.fmt(b.liquids == null ? 0f : b.liquids.get(cold)))
                        .append(" H=").append(IronworksLog.fmt(b.liquids == null ? 0f : b.liquids.get(hot)))
                        .append(" L=").append(IronworksLog.fmt(b.liquids == null ? 0f : b.liquids.get(chill)))
                        .append(" tgt=").append(t.target != null)
                        .append(" heat=").append(t.isShooting() ? 1 : 0)
                        .append(" | ");
                }else if(b instanceof IronworksRBMK.RBMKReactor.RBMKReactorBuild r){
                    mods.append("[RBMK]@").append(b.tileX()).append(',').append(b.tileY())
                        .append(" flux=").append(Math.round(r.flux * 100)).append('%')
                        .append(" heat=").append(Math.round(r.heat * 100)).append('%')
                        .append(" xenon=").append(Math.round(r.xenon * 100)).append('%')
                        .append(" rods=").append(Math.round(r.rods * 100)).append('%')
                        .append(" mode=").append(r.controlMode)
                        .append(" fuel=").append(r.items == null ? 0 : r.items.get(IronworksNuclear.fuelRod))
                        .append(" spent=").append(r.items == null ? 0 : r.items.get(IronworksNuclear.depletedRod))
                        .append(" steam=").append(IronworksLog.fmt(b.liquids == null ? 0f : b.liquids.get(IronworksLiquids.steam)))
                        .append(" water=").append(IronworksLog.fmt(b.liquids == null ? 0f : b.liquids.get(mindustry.content.Liquids.water)))
                        .append(" wlvl=").append(Math.round(r.waterLevel * 100)).append('%')
                        .append(" wtarget=").append(Math.round(r.waterTarget * 100)).append('%')
                        .append(" flow=").append(Math.round(r.flow * 100)).append('%')
                        .append(" rate=").append(Math.round(r.steamRate)).append("/s")
                        .append(" boom=").append(IronworksLog.fmt(r.coreBoom))
                        .append(" melted=").append(r.melted)
                        .append(" | ");
                }else if(b.liquids != null){
                    mods.append(b.block.name).append('@').append(b.tileX()).append(',').append(b.tileY())
                        .append('=').append(IronworksLog.fmt(b.liquids.get(cold))).append('/').append(IronworksLog.fmt(b.liquids.get(hot)))
                        .append(' ');
                }
            }

            String player = "-";
            if(Vars.player != null && Vars.player.unit() != null){
                var u = Vars.player.unit();
                player = Iround(u.x) + "," + Iround(u.y) + " hp=" + Iround(u.health);
            }

            float total = coldTotal + hotTotal + chillTotal;
            String anomaly = "";
            if(lastCold >= 0f){
                float d = total - (lastCold + lastHot);
                if(Math.abs(d) > 0.01f){
                    anomaly = " [DELTA " + IronworksLog.fmt(d) + "]";
                }
            }
            lastCold = coldTotal;
            lastHot = hotTotal;

            IronworksLog.line("tick=" + Vars.state.tick
                + " dt=" + IronworksLog.fmt(arc.util.Time.delta)
                + " ms=" + System.currentTimeMillis()
                + " units=" + Groups.unit.size() + " builds=" + Groups.build.size() + " mod=" + modBuilds
                + " player=" + player
                + " cold=" + IronworksLog.fmt(coldTotal) + " hot=" + IronworksLog.fmt(hotTotal)
                + " chill=" + IronworksLog.fmt(chillTotal)
                + " total=" + IronworksLog.fmt(total) + anomaly
                + " | " + turrets + mods);

            // 每 tick 落盘，保证不丢事件
            IronworksLog.flush();
        }

        String Iround(float v){
            return String.valueOf(Math.round(v));
        }

        @Override
        public void onDestroyed(){
            IronworksLog.line("[EVENT] logger destroyed");
            IronworksLog.flush();
            super.onDestroyed();
        }
    }
}
