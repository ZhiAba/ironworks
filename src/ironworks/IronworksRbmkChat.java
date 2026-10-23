package ironworks;

import arc.Core;
import arc.Events;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Groups;

/**
 * 彩蛋：切伦诺贝利式「嘴炮引爆」。
 *
 * <p>任何玩家在 **15 秒内按顺序** 说出下面三句话：<br>
 * 1. 同时包含「核」和「轻而易」（例：核电，轻而易举啊）<br>
 * 2. 包含「坏」（例：坏了）<br>
 * 3. 至少包含两个「坏」（例：坏了坏了）<br>
 * → **当前地图上所有正在工作的反应堆**（通量 &gt; 0 且未熔毁）会集体熔毁。
 *
 * <p>判定窗口：每句话都必须在上一句之后 15 秒内出现，否则序列重置。
 * 与聊天前缀/颜色无关，只做子串匹配。
 */
public class IronworksRbmkChat{

    /** 两句话之间允许的最大间隔（秒）。 */
    public static final float window = 15f;

    static int step = 0;
    static float lastAt = -999f;
    static String lastMsg = "";
    static float lastMsgAt = -999f;

    public static void init(){
        // 别人发的消息（服务端/网络包）
        Events.on(EventType.PlayerChatEvent.class, e -> feed(e.message));
        // 自己发的消息：聊天框发送时会先发 ClientChatEvent（单机/多人都有）
        Events.on(EventType.ClientChatEvent.class, e -> feed(e.message));
    }

    /** 吃掉一条聊天消息，推进/重置/触发序列。测试也直接调这个。 */
    public static void feed(String msg){
        if(msg == null || msg.isEmpty()) return;

        float now = Time.time;

        // 同一条消息可能同时触发 ClientChatEvent 与 PlayerChatEvent（自己发时两个都来），去重
        if(msg.equals(lastMsg) && now - lastMsgAt < 1f) return;
        lastMsg = msg;
        lastMsgAt = now;
        if(step > 0 && now - lastAt > window){
            step = 0;   // 超时，序列作废
        }

        if(step == 0){
            if(msg.contains("核") && (msg.contains("轻而易") || msg.contains("轻易"))){
                step = 1;
                lastAt = now;
                Log.info("[IRONWORKS] meme step 1/3 matched"); IronworksLog.event("[MEME] step 1/3 matched msg=" + msg);
            }
            return;
        }

        if(step == 1){
            if(count(msg, "坏") >= 1){
                step = 2;
                lastAt = now;
                Log.info("[IRONWORKS] meme step 2/3 matched"); IronworksLog.event("[MEME] step 2/3 matched msg=" + msg);
            }
            return;
        }

        // step == 2：必须一口气两个「坏」
        if(count(msg, "坏") >= 2){
            lastAt = now;
            step = 0;
            Log.info("[IRONWORKS] meme step 3/3 matched -> detonate"); IronworksLog.event("[MEME] step 3/3 matched -> detonate");
            detonateAll();
        }
    }

    static int count(String s, String sub){
        int n = 0, i = 0;
        while((i = s.indexOf(sub, i)) >= 0){
            n++;
            i += sub.length();
        }
        return n;
    }

    /** 引爆当前地图所有正在工作的反应堆。 */
    static int detonateAll(){
        int n = IronworksRBMK.detonateAllWorking();
        Log.info("[IRONWORKS] meme detonation: " + n + " reactor(s)");
        IronworksLog.event("[MEME] detonated " + n + " reactor(s)");
        if(!Vars.headless && Vars.ui != null && Vars.ui.chatfrag != null){
            Vars.ui.chatfrag.addMessage(Core.bundle.get("ironworks.meme.boom"));
        }
        return n;
    }

    /** 测试用：查询/重置序列状态。 */
    public static int step(){
        return step;
    }

    public static void reset(){
        step = 0;
        lastAt = -999f;
    }
}
