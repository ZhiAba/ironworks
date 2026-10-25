package ironworks;

import arc.Core;
import arc.Events;
import arc.math.Mathf;
import arc.scene.ui.TextField;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.core.GameState;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Player;
import mindustry.input.Binding;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import static mindustry.Vars.*;

/**
 * 多人暂停（独立实现，不依赖 utility-pack 的代码）。
 *
 * <p>原版多人只有房主能暂停。本实现允许客人向主机发“暂停/继续”请求，
 * 主机按策略决定是否放行。主机和客人都要安装本模组，网络包才会注册。
 */
public class IronworksPause{

    public static final int OFF = 0, ADMINS = 1, CUSTOM = 2;

    /** 主机策略：0 仅房主/管理员，1 所有玩家，2 仅白名单。 */
    public static int mode = OFF;
    /** 自定义白名单（玩家名）。 */
    public static final Seq<String> whitelist = new Seq<>();

    private static boolean wired;
    private static boolean waiting;
    private static float lastRequestAt;

    private IronworksPause(){
    }

    public static void init(){
        wire();
        Events.on(EventType.ClientLoadEvent.class, e -> wire());
        Events.on(EventType.ServerLoadEvent.class, e -> wire());

        // 客人按暂停键 → 发请求；60 秒没回执就重发，避免丢包后永远卡住。
        Events.run(EventType.Trigger.update, () -> {
            if(headless || !state.isGame() || !net.client()) return;
            if(!Core.settings.getBool("ironworks.pause.request", true)) return;

            if(Core.input.keyTap(Binding.pause)){
                sendRequest();
            }else if(waiting && Time.millis() - lastRequestAt > 60_000f){
                sendRequest();
            }
        });

        Events.on(EventType.PlayerChatEvent.class, e -> {
            if(e.player != null && e.message != null && e.message.startsWith("!pause")){
                command(e.player, e.message);
            }
        });
    }

    private static void wire(){
        if(wired || netServer == null || netClient == null) return;
        wired = true;

        netServer.addPacketHandler("ironworks-pause-request", (p, payload) -> {
            if(!mayPause(p)){
                reply(p, "denied");
                return;
            }
            boolean next = !state.isPaused();
            state.set(next ? GameState.State.paused : GameState.State.playing);
            reply(p, next ? "paused" : "playing");
        });

        netServer.addPacketHandler("ironworks-pause-policy", (p, payload) -> {
            if(isHost(p)) mode = Mathf.clamp(parse(payload), OFF, CUSTOM);
        });

        netServer.addPacketHandler("ironworks-pause-allow", (p, payload) -> {
            if(!isHost(p)) return;
            String name = payload == null ? "" : payload.trim();
            if(!name.isEmpty() && !whitelist.contains(name)) whitelist.add(name);
        });

        netServer.addPacketHandler("ironworks-pause-deny", (p, payload) -> {
            if(!isHost(p)) return;
            whitelist.remove(payload == null ? "" : payload.trim());
        });

        netClient.addPacketHandler("ironworks-pause-reply", payload -> waiting = false);
    }

    private static int parse(String value){
        try{
            return Integer.parseInt(value.trim());
        }catch(Exception e){
            return OFF;
        }
    }

    private static boolean isHost(Player player){
        return player != null && (player.admin || player.name.equals(state.map.author()));
    }

    private static boolean mayPause(Player player){
        if(isHost(player)) return true;
        if(mode == ADMINS) return true;
        return mode == CUSTOM && whitelist.contains(player.name);
    }

    private static void sendRequest(){
        Call.serverPacketReliable("ironworks-pause-request", String.valueOf((long)Time.millis()));
        waiting = true;
        lastRequestAt = Time.millis();
    }

    private static void reply(Player player, String result){
        Call.clientPacketReliable(player.con, "ironworks-pause-reply", result);
    }

    private static void command(Player player, String raw){
        String[] parts = raw.trim().split("\\s+");
        if(parts.length == 1){
            if(mayPause(player)){
                boolean next = !state.isPaused();
                state.set(next ? GameState.State.paused : GameState.State.playing);
                Call.infoMessage(player.con, Core.bundle.get(next
                    ? "ironworks.pause.cmd.paused" : "ironworks.pause.cmd.playing"));
            }
            return;
        }

        String sub = parts[1].toLowerCase();
        boolean host = isHost(player);

        switch(sub){
            case "on" -> {
                if(!host) return;
                mode = ADMINS;
                Call.infoMessage(player.con, Core.bundle.get("ironworks.pause.cmd.on"));
            }
            case "off" -> {
                if(!host) return;
                mode = OFF;
                Call.infoMessage(player.con, Core.bundle.get("ironworks.pause.cmd.off"));
            }
            case "custom" -> {
                if(!host) return;
                mode = CUSTOM;
                Call.infoMessage(player.con, Core.bundle.get("ironworks.pause.cmd.custom"));
            }
            case "grant" -> {
                if(!host || parts.length < 3) return;
                String name = parts[2];
                if(!whitelist.contains(name)) whitelist.add(name);
                Call.infoMessage(player.con, Core.bundle.format("ironworks.pause.cmd.grant", name));
            }
            case "revoke" -> {
                if(!host || parts.length < 3) return;
                String name = parts[2];
                whitelist.remove(name);
                Call.infoMessage(player.con, Core.bundle.format("ironworks.pause.cmd.revoke", name));
            }
            case "list" -> {
                if(!host) return;
                String list = whitelist.isEmpty()
                    ? Core.bundle.get("ironworks.whitelist.empty")
                    : whitelist.toString(", ");
                Call.infoMessage(player.con, Core.bundle.format("ironworks.pause.cmd.list", list));
            }
            default -> {
                // 未知子命令不广播，避免刷屏。
            }
        }
    }

    /** 设置界面里的白名单管理。 */
    public static void showWhitelistDialog(){
        BaseDialog dialog = new BaseDialog(Core.bundle.get("ironworks.whitelist.title"));
        dialog.cont.top();

        if(whitelist.isEmpty()){
            dialog.cont.add(Core.bundle.get("ironworks.whitelist.hint"))
                .color(arc.graphics.Color.lightGray).pad(16f).row();
        }else{
            for(String name : whitelist.copy()){
                dialog.cont.table(row -> {
                    row.left();
                    row.add(name).growX().left();
                    row.button(Core.bundle.get("ironworks.whitelist.remove"), Styles.flatBordert, () -> {
                        whitelist.remove(name);
                        if(net.client()) Call.serverPacketReliable("ironworks-pause-deny", name);
                        dialog.hide();
                        showWhitelistDialog();
                    }).padLeft(8f);
                }).fillX().pad(4f).padLeft(8f).padRight(8f).row();
            }
        }

        dialog.cont.table(row -> {
            TextField input = row.field("", text -> {}).growX().pad(8f).get();
            input.setMessageText(Core.bundle.get("ironworks.whitelist.placeholder"));
            row.button(Core.bundle.get("ironworks.whitelist.add"), Styles.flatBordert, () -> {
                String name = input.getText().trim();
                if(name.isEmpty() || whitelist.contains(name)) return;
                whitelist.add(name);
                if(net.client()) Call.serverPacketReliable("ironworks-pause-allow", name);
                input.clearText();
                dialog.hide();
                showWhitelistDialog();
            }).padLeft(8f);
        }).fillX().pad(8f).row();

        dialog.closeOnBack();
        dialog.show();
    }
}
