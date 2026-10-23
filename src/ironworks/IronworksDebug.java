package ironworks;

import mindustry.content.Blocks;
import mindustry.content.Liquids;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.world.Block;

/** 调试内容（不进科技树，不影响正常内容）。 */
public class IronworksDebug{
    public static Block liquidMonitor, verboseLogger;

    public static void load(){
        liquidMonitor = new LiquidMonitor("liquid-monitor");
        verboseLogger = new VerboseLogger("verbose-logger");

        registerEvents();
    }

    /** 属性伤害回归：查询归属 + 护甲/克制/状态三条实时结算。 */
    public static String damageTest(Building at){
        StringBuilder sb = new StringBuilder();
        sb.append("tsun=").append(Dmg.typeOf(Blocks.tsunami).id);
        sb.append(" liquid=").append(Dmg.typeOf(((mindustry.world.blocks.defense.turrets.LiquidTurret) Blocks.tsunami).ammoTypes.get(Liquids.water)).id);
        sb.append(" lancer=").append(Dmg.typeOf(((mindustry.world.blocks.defense.turrets.PowerTurret) Blocks.lancer).shootType).id);
        sb.append(" malign=").append(Dmg.typeOf(((mindustry.world.blocks.defense.turrets.PowerTurret) Blocks.malign).shootType).id);
        sb.append(" napalm=").append(Dmg.typeOf(IronworksTurrets.napalmBullet).id);
        sb.append(" corr=").append(Dmg.typeOf(IronworksTurrets.corrosiveBullet).id);
        sb.append(" tianfa=").append(Dmg.typeOf(IronworksTianfa.tianfa).id);

        float x = at.x, y = at.y;
        Unit a = UnitTypes.fortress.spawn(Team.crux, x + 40f, y);
        Unit b = UnitTypes.fortress.spawn(Team.crux, x + 72f, y);
        Unit c = UnitTypes.fortress.spawn(Team.crux, x + 104f, y);
        Unit d = UnitTypes.dagger.spawn(Team.crux, x + 136f, y);

        float ha = a.health;
        Dmg.hit(a, 100f, DamageType.physical);
        float phys = ha - a.health;

        float hb = b.health;
        Dmg.hit(b, 100f, DamageType.thunder);
        float thunder = hb - b.health;

        c.apply(StatusEffects.wet, 300f);
        float hc = c.health;
        Dmg.hit(c, 100f, DamageType.thunder);
        float wetThunder = hc - c.health;

        Dmg.hit(d, 10f, DamageType.water);
        boolean wet = d.hasEffect(StatusEffects.wet);

        a.remove(); b.remove(); c.remove(); d.remove();

        sb.append(" armor=").append(thunder > phys);
        sb.append(" bonus=").append(wetThunder > thunder);
        sb.append(" wet=").append(wet);
        return sb.toString();
    }

    static void registerEvents(){
        arc.util.Log.info("[IRONWORKS] registering verbose logger events");
        arc.Events.on(mindustry.game.EventType.TapEvent.class, e -> IronworksLog.event("tap " + (e.tile == null ? "-" : e.tile.x + "," + e.tile.y)));
        arc.Events.on(mindustry.game.EventType.BlockBuildBeginEvent.class, e -> IronworksLog.event("buildBegin " + (e.tile == null ? "-" : e.tile.x + "," + e.tile.y) + " old=" + (e.tile == null ? "-" : e.tile.block().name) + " break=" + e.breaking));
        arc.Events.on(mindustry.game.EventType.BlockBuildEndEvent.class, e -> IronworksLog.event("buildEnd " + (e.tile == null ? "-" : e.tile.x + "," + e.tile.y) + " new=" + (e.tile == null ? "-" : e.tile.block().name) + " break=" + e.breaking));
        arc.Events.on(mindustry.game.EventType.BlockDestroyEvent.class, e -> IronworksLog.event("blockDestroyed " + (e.tile == null ? "-" : e.tile.x + "," + e.tile.y) + " block=" + (e.tile == null ? "-" : e.tile.block().name)));
        arc.Events.on(mindustry.game.EventType.ConfigEvent.class, e -> IronworksLog.event("config " + (e.player == null ? "-" : e.player.name) + " value=" + e.value));
        arc.Events.on(mindustry.game.EventType.DepositEvent.class, e -> IronworksLog.event("deposit item=" + e.item.name + " amount=" + e.amount));
        arc.Events.on(mindustry.game.EventType.UnitDestroyEvent.class, e -> IronworksLog.event("unitDestroyed " + e.unit.type.name));
        arc.Events.on(mindustry.game.EventType.WorldLoadEvent.class, e -> IronworksLog.event("worldLoad"));
    }
}