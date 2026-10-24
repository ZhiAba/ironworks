package ironworks;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.struct.Seq;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.mod.Mods;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static mindustry.Vars.*;

/**
 * 左侧长条的「日志」数据源（v2.1.3.0）。
 *
 * 只保留最近 100 条，进程内存储；不写文件、不联网。
 * 由 IronworksLogPanel 渲染，侧栏长条点击即可打开。
 */
public class IronworksNotifications{
    public static final int maxEntries = 100;
    private static final Seq<Entry> entries = new Seq<>();
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);
    private static boolean initialized;
    /** 数据版本号；打开中的日志面板据此刷新，不需要每帧比较整张表。 */
    private static int revision;
    /** 新通知监听器；弹出通知条只挂客户端，headless 下不会注册。 */
    private static final Seq<Cons<Entry>> listeners = new Seq<>();

    private IronworksNotifications(){
    }

    public static void init(){
        if(initialized || headless) return;
        initialized = true;

        String version = "?";
        try{
            Mods.LoadedMod mod = mods.getMod(IronworksMod.class);
            if(mod != null && mod.meta != null) version = mod.meta.version;
        }catch(Throwable ignored){
        }
        info(Core.bundle.format("ironworks.log.loaded", version));

        Events.on(WorldLoadEvent.class, e -> {
            if(state.map != null){
                info(Core.bundle.format("ironworks.log.world", state.map.name()));
            }
        });
    }

    public static void info(String text){
        add(false, text);
    }

    public static void warn(String text){
        add(true, text);
    }

    private static void add(boolean warning, String text){
        if(headless || text == null || text.isEmpty()) return;
        if(entries.size >= maxEntries){
            entries.remove(0);
        }
        Entry entry = new Entry(System.currentTimeMillis(), text, warning);
        entries.add(entry);
        revision++;
        for(int i = 0; i < listeners.size; i++){
            listeners.get(i).get(entry);
        }
    }

    public static Seq<Entry> entries(){
        return entries;
    }

    public static int revision(){
        return revision;
    }

    public static void addListener(Cons<Entry> listener){
        if(listener != null) listeners.add(listener);
    }

    public static void remove(Entry entry){
        if(entries.remove(entry)) revision++;
    }

    public static void clear(){
        if(entries.size > 0){
            entries.clear();
            revision++;
        }
    }

    public static String time(Entry entry){
        return dateFormat.format(new Date(entry.millis));
    }

    public static class Entry{
        public final long millis;
        public final String text;
        public final boolean warning;

        Entry(long millis, String text, boolean warning){
            this.millis = millis;
            this.text = text;
            this.warning = warning;
        }
    }
}
