package ironworks;

import arc.Core;
import arc.audio.Sound;
import arc.math.Mathf;
import arc.util.Log;
import arc.util.Time;
import ironworks.IronworksRBMK.RBMKReactor;
import ironworks.IronworksRBMK.RBMKReactor.RBMKReactorBuild;
import mindustry.content.Liquids;
import mindustry.gen.Sounds;

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
    /** 警告音最短间隔：同一 tick 多个条件同时触发时只响一次。 */
    private static final long alertSoundCooldownMillis = 1200L;

    /** 懒加载的自定义警告音；加载失败时退回原版 uiNotify。 */
    private static Sound alertSound;
    private static boolean alertSoundTried;
    private static long lastAlertSoundAt = -alertSoundCooldownMillis;

    private static final int TEMP = 0, FLOW = 1, STEAM = 2, WATER = 3,
        BLOCKED = 4, PRESSURE = 5, SCRAM = 6, POISON = 7, JAM = 8, HAMMER = 9, WET = 10, CORELOW = 11;

    private IronworksRbmkAlerts(){
    }

    public static void update(RBMKReactorBuild b, float dt){
        if(headless || b == null || b.alertsMuted || b.melted || b.liquids == null) return;
        if(b.alertQuiet == null || b.alertQuiet.length < 12){
            b.alertQuiet = new float[12];
            b.alertMask = 0;
        }

        float steamFrac = Mathf.clamp(b.liquids.get(IronworksLiquids.steam) / b.steamCap(), 0f, 1f);
        float waterFrac = Mathf.clamp(b.liquids.get(Liquids.water) / b.waterCap(), 0f, 1f);
        boolean steamStuck = b.liquids.get(IronworksLiquids.steam) >= b.steamCap() - 2f;
        boolean waterFull = b.liquids.get(Liquids.water) >= b.waterCap() * 0.98f;
        boolean rodsBottom = b.targetRods >= 0.995f && b.rods >= 0.995f;

        int hotZone = hottestZone(b);
        int coldZone = driestZone(b);
        float heatThreshold = b.block instanceof RBMKReactor r && r.zoned
            ? 1.55f : IronworksRbmkAlerts.heatThreshold;

        if(b.heat >= heatThreshold){
            if(raise(b, TEMP)){
                warn(b, zoneTag(b, hotZone, "ironworks.log.reactor.zone.hot")
                    + Core.bundle.format("ironworks.log.reactor.temp",
                        Mathf.round(heatThreshold * 100f), Mathf.round(b.heat * 100f), action(b)));
            }
        }else if(lower(b, TEMP, b.heat < heatThreshold - 0.05f, dt)){
            info(b, "ironworks.log.reactor.temp.ok");
        }

        if(b.flow < flowThreshold){
            if(raise(b, FLOW)){
                warn(b, zoneTag(b, coldZone, "ironworks.log.reactor.zone.cold")
                    + Core.bundle.format("ironworks.log.reactor.flow",
                        Mathf.round(b.flow * 100f), action(b)));
            }
        }else if(lower(b, FLOW, b.flow > flowThreshold + 0.05f, dt)){
            info(b, "ironworks.log.reactor.flow.ok");
        }

        if(steamFrac >= steamThreshold){
            if(raise(b, STEAM)){
                warn(b, zoneTag(b, hotZone, "ironworks.log.reactor.zone.hot")
                    + Core.bundle.format("ironworks.log.reactor.steam",
                        Mathf.round(steamFrac * 100f), action(b)));
            }
        }else if(lower(b, STEAM, steamFrac < steamThreshold - 0.08f, dt)){
            info(b, "ironworks.log.reactor.steam.ok");
        }

        if(waterFrac <= waterThreshold){
            if(raise(b, WATER)){
                warn(b, zoneTag(b, hotZone, "ironworks.log.reactor.zone.hot")
                    + Core.bundle.format("ironworks.log.reactor.water",
                        Mathf.round(waterFrac * 100f), action(b)));
            }
        }else if(lower(b, WATER, waterFrac > waterThreshold + 0.05f, dt)){
            info(b, "ironworks.log.reactor.water.ok");
        }

        boolean blocked = steamStuck && waterFull && !rodsBottom;
        if(blocked){
            if(raise(b, BLOCKED)){
                warn(b, zoneTag(b, hotZone, "ironworks.log.reactor.zone.hot")
                    + Core.bundle.get("ironworks.log.reactor.blocked"));
            }
        }else if(lower(b, BLOCKED, true, dt)){
            info(b, "ironworks.log.reactor.blocked.ok");
        }

        if(b.coreBoom > 0f){
            if(raise(b, PRESSURE)){
                warn(b, zoneTag(b, hotZone, "ironworks.log.reactor.zone.hot")
                    + Core.bundle.get("ironworks.log.reactor.pressure"));
            }
        }else if(lower(b, PRESSURE, true, dt)){
            info(b, "ironworks.log.reactor.pressure.ok");
        }

        if(b.scramBoom > 0f && raise(b, SCRAM)){
            warn(b, zoneTag(b, -2, "ironworks.log.reactor.zone.all")
                + Core.bundle.get("ironworks.log.reactor.scram"));
        }

        if(b.poisoned && raise(b, POISON)){
            warn(b, zoneTag(b, -3, "ironworks.log.reactor.zone.allcore")
                + Core.bundle.get("ironworks.log.reactor.poisoned"));
        }

        if(b.rodJammed){
            if(raise(b, JAM)){
                warn(b, Core.bundle.get("ironworks.log.reactor.jam"));
            }
        }else{
            lower(b, JAM, true, dt);
        }

        if(b.waterHammer > 0.20f){
            if(raise(b, HAMMER)){
                warn(b, Core.bundle.get("ironworks.log.reactor.hammer"));
            }
        }else{
            lower(b, HAMMER, b.waterHammer < 0.10f, dt);
        }

        boolean zoned = b.block instanceof RBMKReactor r && r.zoned;
        if(zoned){
            float dry = 0f, core = 0f;
            for(int i = 0; i < 9; i++){
                dry += b.zoneDryness[i];
                core += b.zoneWater[i];
            }
            dry /= 9f;
            core /= Math.max(1f, b.waterTotalCap());
            if(dry < 0.90f && b.wetSteamRate > 0.5f){
                if(raise(b, WET)){
                    warn(b, Core.bundle.format("ironworks.log.reactor.wet", Mathf.round(dry * 100f)));
                }
            }else{
                lower(b, WET, dry >= 0.90f || b.wetSteamRate <= 0.1f, dt);
            }
            if(core < 0.15f && b.flux > 0.05f && !b.melted){
                if(raise(b, CORELOW)){
                    warn(b, Core.bundle.format("ironworks.log.reactor.corelow", Mathf.round(core * 100f),
                        Mathf.round(b.lastCoreInjection)));
                }
            }else{
                lower(b, CORELOW, core > 0.20f, dt);
            }
        }
    }

    /** 只有「12×12 分区堆 + 强制手动」才带区位；自动/半自动保持原句。 */
    private static boolean zonedManual(RBMKReactorBuild b){
        return b.block instanceof RBMKReactor r && r.zoned
            && b.controlMode == IronworksRBMK.MODE_MANUAL;
    }

    private static int hottestZone(RBMKReactorBuild b){
        if(!(b.block instanceof RBMKReactor r) || !r.zoned) return -1;
        int best = 0;
        for(int i = 1; i < 9; i++){
            if(b.zoneHeat[i] > b.zoneHeat[best]) best = i;
        }
        return best;
    }

    private static int driestZone(RBMKReactorBuild b){
        if(!(b.block instanceof RBMKReactor r) || !r.zoned) return -1;
        int best = 0;
        for(int i = 1; i < 9; i++){
            if(b.zoneFlow[i] < b.zoneFlow[best]) best = i;
        }
        return best;
    }

    /** key 为 zone.hot/cold/all/allcore；非手动或非分区堆返回空串。 */
    private static String zoneTag(RBMKReactorBuild b, int zone, String key){
        if(!zonedManual(b)) return "";
        String label = zone >= 0
            ? Core.bundle.format(key, zone + 1)
            : Core.bundle.get(key);
        return label + "：";
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

    /** 客户端启动时预加载，避免第一条警告处于异步加载窗口而静默。 */
    public static void init(){
        if(headless) return;
        ensureAlertSound();
        // 0 音量触发一次异步解码；真正警告时直接播放已加载的 handle。
        if(alertSound != null && Core.audio != null){
            alertSound.play(0f);
        }
    }

    private static void ensureAlertSound(){
        if(alertSoundTried) return;
        alertSoundTried = true;
        try{
            if(tree != null && tree.get("sounds/ironworks-reactor-alert.ogg").exists()){
                alertSound = tree.loadSound("ironworks-reactor-alert");
            }
        }catch(Throwable t){
            Log.err("Failed to load reactor alert sound", t);
        }
    }

    /**
     * 反应堆警告边沿音效。自定义 OGG 在 assets/sounds/ 下；缺失或加载失败
     * 时退回原版 uiNotify，保证不会静默。冷却 1.2 秒，避免多条件同时触发时叠音。
     */
    public static void playAlertSound(){
        if(headless) return;
        ensureAlertSound();

        long now = Time.millis();
        if(now - lastAlertSoundAt < alertSoundCooldownMillis) return;
        lastAlertSoundAt = now;

        if(alertSound != null){
            alertSound.play(0.8f);
        }else if(Sounds.uiNotify != null){
            Sounds.uiNotify.play(0.55f);
        }
    }

    private static void warn(RBMKReactorBuild b, String detail){
        playAlertSound();
        IronworksNotifications.warn(at(b, detail));
    }

    private static void info(RBMKReactorBuild b, String key){
        IronworksNotifications.info(Core.bundle.format(key, b.tileX(), b.tileY()));
    }
}
