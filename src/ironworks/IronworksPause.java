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
 * 多人暂停（内嵌自 utility-pack，包名/网络包名/设置键都已改名）。
 *
 * <p>原版多人里只有房主能暂停。这里允许其他玩家**请求**暂停/继续，服务端按模式放行：
 * <ul>
 *   <li>{@link #OFF}：只有房主/管理员（默认）；</li>
 *   <li>{@link #ADMINS}：所有玩家都能暂停（客户端按暂停键发请求）；</li>
 *   <li>{@link #CUSTOM}：只有白名单里的玩家（`!pause grant 名字`）。</li>
 * </ul>
 *
 * <p>注意：这是服务端行为，主机和客人**都要装本模组**才会生效（网络包要两端都注册）。
 */
public class IronworksPause{

    public static final int OFF = 0, ADMINS = 1, CUSTOM = 2;

    /** 暂停模式，见上方常量。 */
    public static int mode = OFF;
    /** 自定义白名单（玩家名）。 */
    public static final Seq<String> whitelist = new Seq<>();

    private static boolean registered;
    /** 已发出请求、还没收到服务端回执 */
    private static boolean pending;
    private static float lastRequestAt = -999f;

    private IronworksPause(){
    }

    public static void init(){
        //尽早上：主机/客人两端都要注册网络包（重复调用有 registered 兜底）
        register();
        Events.on(EventType.ClientLoadEvent.class, e -> register());
        Events.on(EventType.ServerLoadEvent.class, e -> register());

        //客户端：多人时按暂停键发请求；60 秒没回执就重发
        Events.run(EventType.Trigger.update, () -> {
            if(headless || !state.isGame()) return;
            if(!net.client() || !Core.settings.getBool("ironworks.pause.request", true)) return;

            if(Core.input.keyTap(Binding.pause)){
                request();
            }else if(pending && Time.time - lastRequestAt > 60f){
                request();
            }
        });

        //聊天命令 !pause ...
        Events.on(EventType.PlayerChatEvent.class, e -> {
            if(e.player == null || e.message == null || !e.message.startsWith("!pause")) return;
            handleCommand(e.player, e.message);
        });
    }

    static void register(){
        if(registered || netServer == null || netClient == null) return;
        registered = true;

        //暂停请求：服务端判定权限后直接切 state
        netServer.addPacketHandler("ironworks-pause", (p, time) -> {
            if(!canPause(p)) return;
            state.set(state.isPaused() ? GameState.State.playing : GameState.State.paused);
            Call.clientPacketReliable(p.con, "ironworks-paused", time);
        });
        netServer.addPacketHandler("ironworks-pause-mode", (p, data) -> {
            if(!isHost(p)) return;
            mode = Mathf.clamp(parse(data), OFF, CUSTOM);
        });
        netServer.addPacketHandler("ironworks-pause-grant", (p, data) -> {
            if(!isHost(p)) return;
            String target = data.trim();
            if(!target.isEmpty() && !whitelist.contains(target)) whitelist.add(target);
        });
        netServer.addPacketHandler("ironworks-pause-revoke", (p, data) -> {
            if(!isHost(p)) return;
            whitelist.remove(data.trim());
        });

        //回执：请求成功，别再重发
        netClient.addPacketHandler("ironworks-paused", s -> pending = false);
    }

    static int parse(String s){
        try{
            return Integer.parseInt(s.trim());
        }catch(Exception e){
            return OFF;
        }
    }

    static boolean isHost(Player p){
        return p.admin || p.name.equals(state.map.author());
    }

    static boolean canPause(Player p){
        if(isHost(p)) return true;
        return switch(mode){
            case ADMINS -> true;
            case CUSTOM -> whitelist.contains(p.name);
            default -> false;
        };
    }

    static void request(){
        Call.serverPacketReliable("ironworks-pause", String.valueOf((long)Time.time));
        pending = true;
        lastRequestAt = Time.time;
    }

    static void handleCommand(Player p, String msg){
        String[] parts = msg.split(" ");
        if(parts.length < 2) return;

        boolean host = isHost(p);
        String arg = parts.length >= 3 ? parts[2] : "";

        switch(parts[1]){
            case "on" -> {
                if(!host) return;
                mode = ADMINS;
                Call.infoMessage(p.con, Core.bundle.get("ironworks.pause.cmd.on"));
            }
            case "off" -> {
                if(!host) return;
                mode = OFF;
                Call.infoMessage(p.con, Core.bundle.get("ironworks.pause.cmd.off"));
            }
            case "custom" -> {
                if(!host) return;
                mode = CUSTOM;
                Call.infoMessage(p.con, Core.bundle.get("ironworks.pause.cmd.custom"));
            }
            case "grant" -> {
                if(!host || arg.isEmpty()) return;
                if(!whitelist.contains(arg)) whitelist.add(arg);
                Call.infoMessage(p.con, Core.bundle.format("ironworks.pause.cmd.grant", arg));
            }
            case "revoke" -> {
                if(!host || arg.isEmpty()) return;
                whitelist.remove(arg);
                Call.infoMessage(p.con, Core.bundle.format("ironworks.pause.cmd.revoke", arg));
            }
            case "list" -> {
                if(!host) return;
                String list = whitelist.isEmpty()
                    ? Core.bundle.get("ironworks.whitelist.empty")
                    : whitelist.toString(", ");
                Call.infoMessage(p.con, Core.bundle.format("ironworks.pause.cmd.list", list));
            }
            default -> {
            }
        }
    }

    /** 白名单管理对话框（设置里那个按钮）。 */
    public static void showWhitelistDialog(){
        BaseDialog dialog = new BaseDialog(Core.bundle.get("ironworks.whitelist.title"));
        dialog.cont.top();

        final Runnable[] rebuild = new Runnable[1];
        rebuild[0] = () -> {
            dialog.cont.clearChildren();
            dialog.cont.top();

            if(whitelist.isEmpty()){
                dialog.cont.add(Core.bundle.get("ironworks.whitelist.hint")).color(arc.graphics.Color.lightGray).pad(16f);
            }else{
                for(String name : whitelist){
                    dialog.cont.row();
                    dialog.cont.table(t -> {
                        t.add(name).growX().left();
                        t.button(Core.bundle.get("ironworks.whitelist.remove"), Styles.flatBordert, () -> {
                            whitelist.remove(name);
                            if(net.client()) Call.serverPacketReliable("ironworks-pause-revoke", name);
                            rebuild[0].run();
                        }).padLeft(8f);
                    }).fillX().pad(4f).padLeft(8f).padRight(8f);
                }
            }

            dialog.cont.row();
            dialog.cont.table(t -> {
                TextField field = t.field("", text -> {}).growX().pad(8f).get();
                field.setMessageText(Core.bundle.get("ironworks.whitelist.placeholder"));
                t.button(Core.bundle.get("ironworks.whitelist.add"), Styles.flatBordert, () -> {
                    String input = field.getText().trim();
                    if(!input.isEmpty() && !whitelist.contains(input)){
                        whitelist.add(input);
                        if(net.client()) Call.serverPacketReliable("ironworks-pause-grant", input);
                        field.clearText();
                        rebuild[0].run();
                    }
                }).padLeft(8f);
            }).fillX().pad(8f);
        };

        rebuild[0].run();
        dialog.closeOnBack();
        dialog.show();
    }
}
