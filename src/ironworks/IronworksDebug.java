package ironworks;

import mindustry.world.Block;

/** 调试内容（不进科技树，不影响正常内容）。 */
public class IronworksDebug{
    public static Block liquidMonitor, verboseLogger;

    public static void load(){
        liquidMonitor = new LiquidMonitor("liquid-monitor");
        verboseLogger = new VerboseLogger("verbose-logger");

        registerEvents();
    }

    static void registerEvents(){
        arc.util.Log.info("[IRONWORKS] registering verbose logger events");
        arc.Events.on(mindustry.game.EventType.TapEvent.class, e -> IronworksLog.event("tap " + (e.tile == null ? "-" : e.tile.x + "," + e.tile.y)));
        arc.Events.on(mindustry.game.EventType.BlockBuildBeginEvent.class, e -> IronworksLog.event("buildBegin " + (e.tile == null ? "-" : e.tile.x + "," + e.tile.y) + " break=" + e.breaking));
        arc.Events.on(mindustry.game.EventType.BlockBuildEndEvent.class, e -> IronworksLog.event("buildEnd " + (e.tile == null ? "-" : e.tile.x + "," + e.tile.y) + " break=" + e.breaking));
        arc.Events.on(mindustry.game.EventType.BlockDestroyEvent.class, e -> IronworksLog.event("blockDestroyed " + (e.tile == null ? "-" : e.tile.x + "," + e.tile.y)));
        arc.Events.on(mindustry.game.EventType.ConfigEvent.class, e -> IronworksLog.event("config " + (e.player == null ? "-" : e.player.name) + " value=" + e.value));
        arc.Events.on(mindustry.game.EventType.DepositEvent.class, e -> IronworksLog.event("deposit item=" + e.item.name + " amount=" + e.amount));
        arc.Events.on(mindustry.game.EventType.UnitDestroyEvent.class, e -> IronworksLog.event("unitDestroyed " + e.unit.type.name));
        arc.Events.on(mindustry.game.EventType.WorldLoadEvent.class, e -> IronworksLog.event("worldLoad"));
    }
}