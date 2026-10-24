package ironworks;

import arc.Core;
import arc.math.Mathf;
import ironworks.IronworksRBMK.RBMKReactor.RBMKReactorBuild;
import mindustry.content.Liquids;

import static mindustry.Vars.*;

/**
 * RBMK 异常广播（v2.1.5.0）。
 *
 * 只在客户端写通知，按「条件从未触发 → 触发」边沿记录，避免每 tick 刷屏；
 * 条件恢复并稳定 3 秒后才重新武装，下一次异常会再次广播。
 * 坐标用方块所在 tile（tileX/tileY），和现有熔毁日志一致。
 */
public class IronworksRbmkAlerts{
    /** 堆芯温度安全阈值：100%（约 780°C）；熔毁阈值是 125%。 */
    public static final float heatThreshold = 1.0f;
    /** 冷却液实际满足率低于 90% 时告警。 */
    public static final float flowThreshold = 0.9f;
    /** 汽包达到 90% 时告警；满 2000 L 且水罐满则是 AZ-5 风险。 */
    public static final float steamThreshold = 0.9f;
    /** 水罐低于 20% 时告警。 */
    public static final float waterThreshold = 0.2f;
    /** 异常消失后持续 3 秒才重新武装，防止阈值附近抖动刷屏。 */
    private static final float quietSeconds = 3f;

    private static final int TEMP = 0, FLOW = 1, STEAM = 2, WATER = 3,
        BLOCKED = 4, PRESSURE = 5, SCRAM = 6, POISON = 7;

    private IronworksRbmkAlerts(){
    }

    public static void update(RBMKReactorBuild b, float dt){
        if(headless || b == null || b.melted || b.liquids == null) return;
        if(b.alertQuiet == null || b.alertQuiet.length < 8){
            b.alertQuiet = new float[8];
            b.alertMask = 0;
        }

        float steamFrac = Mathf.clamp(b.liquids.get(IronworksLiquids.steam) / IronworksRBMK.steamCapacity, 0f, 1f);
        float waterFrac = Mathf.clamp(b.liquids.get(Liquids.water) / IronworksRBMK.waterCapacity, 0f, 1f);
        boolean steamStuck = b.liquids.get(IronworksLiquids.steam) >= IronworksRBMK.steamCapacity - 2f;
        boolean waterFull = b.liquids.get(Liquids.water) >= IronworksRBMK.waterCapacity * 0.98f;
        boolean rodsBottom = b.targetRods >= 0.995f && b.rods >= 0.995f;

        if(b.heat >= heatThreshold){
            if(raise(b, TEMP)){
                warn(b, Core.bundle.format("ironworks.log.reactor.temp",
                    Mathf.round(heatThreshold * 100f), Mathf.round(b.heat * 100f), action(b)));
            }
        }else if(lower(b, TEMP, b.heat < heatThreshold - 0.03f, dt)){
            info(b, "ironworks.log.reactor.temp.ok");
        }

        if(b.flow < flowThreshold){
            if(raise(b, FLOW)){
                warn(b, Core.bundle.format("ironworks.log.reactor.flow",
                    Mathf.round(b.flow * 100f), action(b)));
            }
        }else if(lower(b, FLOW, b.flow > flowThreshold + 0.05f, dt)){
            info(b, "ironworks.log.reactor.flow.ok");
        }

        if(steamFrac >= steamThreshold){
            if(raise(b, STEAM)){
                warn(b, Core.bundle.format("ironworks.log.reactor.steam",
                    Mathf.round(steamFrac * 100f), action(b)));
            }
        }else if(lower(b, STEAM, steamFrac < steamThreshold - 0.08f, dt)){
            info(b, "ironworks.log.reactor.steam.ok");
        }

        if(waterFrac <= waterThreshold){
            if(raise(b, WATER)){
                warn(b, Core.bundle.format("ironworks.log.reactor.water",
                    Mathf.round(waterFrac * 100f), action(b)));
            }
        }else if(lower(b, WATER, waterFrac > waterThreshold + 0.05f, dt)){
            info(b, "ironworks.log.reactor.water.ok");
        }

        boolean blocked = steamStuck && waterFull && !rodsBottom;
        if(blocked){
            if(raise(b, BLOCKED)){
                warn(b, Core.bundle.get("ironworks.log.reactor.blocked"));
            }
        }else if(lower(b, BLOCKED, true, dt)){
            info(b, "ironworks.log.reactor.blocked.ok");
        }

        if(b.coreBoom > 0f){
            if(raise(b, PRESSURE)){
                warn(b, Core.bundle.get("ironworks.log.reactor.pressure"));
            }
        }else if(lower(b, PRESSURE, true, dt)){
            info(b, "ironworks.log.reactor.pressure.ok");
        }

        if(b.scramBoom > 0f && raise(b, SCRAM)){
            warn(b, Core.bundle.get("ironworks.log.reactor.scram"));
        }

        if(b.poisoned && raise(b, POISON)){
            warn(b, Core.bundle.get("ironworks.log.reactor.poisoned"));
        }
    }

    private static boolean raise(RBMKReactorBuild b, int index){
        b.alertQuiet[index] = 0f;
        int bit = 1 << index;
        if((b.alertMask & bit) != 0) return false;
        b.alertMask |= bit;
        return true;
    }

    private static boolean lower(RBMKReactorBuild b, int index, boolean quietCondition, float dt){
        int bit = 1 << index;
        if((b.alertMask & bit) == 0) return false;
        if(!quietCondition){
            b.alertQuiet[index] = 0f;
            return false;
        }
        b.alertQuiet[index] += dt;
        if(b.alertQuiet[index] < quietSeconds) return false;
        b.alertMask &= ~bit;
        b.alertQuiet[index] = 0f;
        return true;
    }

    private static String action(RBMKReactorBuild b){
        boolean scrammed = b.targetRods >= 0.995f;
        if(b.controlMode == IronworksRBMK.MODE_AUTO){
            return Core.bundle.get(scrammed
                ? "ironworks.log.reactor.action.auto.scram"
                : "ironworks.log.reactor.action.auto.reduce");
        }
        if(b.controlMode == IronworksRBMK.MODE_SEMI){
            return Core.bundle.get(scrammed
                ? "ironworks.log.reactor.action.semi.scram"
                : "ironworks.log.reactor.action.semi.manual");
        }
        return Core.bundle.get("ironworks.log.reactor.action.manual");
    }

    private static String at(RBMKReactorBuild b, String detail){
        return Core.bundle.format("ironworks.log.reactor.at", b.tileX(), b.tileY(), detail);
    }

    private static void warn(RBMKReactorBuild b, String detail){
        IronworksNotifications.warn(at(b, detail));
    }

    private static void info(RBMKReactorBuild b, String key){
        IronworksNotifications.info(Core.bundle.format(key, b.tileX(), b.tileY()));
    }
}
