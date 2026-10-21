package ironworks;

import arc.math.Mathf;
import arc.struct.Seq;
import mindustry.entities.Units;
import mindustry.gen.Building;
import mindustry.gen.Posc;
import mindustry.gen.TimedKillc;
import mindustry.gen.Unit;

/**
 * 迫击炮：有最小射程盲区，且索敌优先选**最密集的敌群**。
 *
 * 两个和原版不同的地方：
 * 1. 原版 Turret.minRange 只钳子弹寿命，不阻止炮塔选近处目标；这里在索敌时按距离过滤。
 * 2. 原版 bestTarget 只挑「最近/最强」的单体；对曲射溅射炮来说，应该挑周围敌人最多的一点，
 *    让一发炮弹的溅射覆盖尽可能多的目标。
 */
public class MortarTurret extends CoolantTurret{

    public MortarTurret(String name){
        super(name);
    }

    public class MortarBuild extends CoolantTurretBuild{
        private final Seq<Posc> candidates = new Seq<>();
        private final Seq<Posc> nearby = new Seq<>();

        @Override
        protected Posc findEnemy(float range){
            float min = minRange();
            var ammo = peekAmmo();
            float splashR = ammo != null && ammo.splashDamageRadius > 0 ? ammo.splashDamageRadius : 24f;
            boolean buildings = targetGround && targetBlocks && (ammo == null || ammo.targetBlocks);
            boolean missiles = ammo == null || ammo.targetMissiles;

            candidates.clear();
            nearby.clear();

            // 候选：射程内、盲区外、符合过滤条件的敌方单位
            Units.nearbyEnemies(team, x, y, range, u -> {
                if(u.dead() || !u.isValid() || !unitFilter.get(u)) return;
                if(u.isGrounded() ? !targetGround : !targetAir) return;
                if(!missiles && u instanceof TimedKillc) return;
                if(Mathf.dst(x, y, u.x, u.y) < min) return;
                candidates.add(u);
            });

            // 候选：敌方建筑（溅射同样吃建筑）
            if(buildings){
                Units.nearbyBuildings(x, y, range, b -> {
                    if(b.team == team || b.dead() || !buildingFilter.get(b)) return;
                    if(Mathf.dst(x, y, b.x, b.y) < min) return;
                    candidates.add(b);
                });
            }

            if(candidates.isEmpty()) return null;

            // 统计用：所有候选（含盲区外的全部），用于给每个候选打分
            nearby.addAll(candidates);

            Posc best = null;
            int bestCount = -1;
            float bestDist = Float.MAX_VALUE;

            for(int i = 0; i < candidates.size; i++){
                Posc c = candidates.get(i);
                int count = 0;
                for(int j = 0; j < nearby.size; j++){
                    Posc o = nearby.get(j);
                    if(Mathf.dst(c.getX(), c.getY(), o.getX(), o.getY()) <= splashR) count++;
                }
                float dist = Mathf.dst(x, y, c.getX(), c.getY());

                // 密集优先；同密度取更近的
                if(count > bestCount || (count == bestCount && dist < bestDist)){
                    bestCount = count;
                    bestDist = dist;
                    best = c;
                }
            }

            return best;
        }
    }
}
