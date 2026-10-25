package ironworks;

import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Liquids;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;
import mindustry.world.Block;
import mindustry.world.Tile;

/** 调试内容（不进科技树，不影响正常内容）。 */
public class IronworksDebug{
    public static Block liquidMonitor, verboseLogger;

    public static void load(){
        liquidMonitor = new LiquidMonitor("liquid-monitor");
        verboseLogger = new VerboseLogger("verbose-logger");

        registerEvents();
    }

    /** 属性伤害回归：归属 + 穿甲曲线 + 护甲保底 + Combo + 附着。 */
    public static String damageTest(Building at){
        StringBuilder sb = new StringBuilder();
        sb.append("auraTicks=").append((int)ElementCombo.auraTicks);
        sb.append(" tsun=").append(Dmg.typeOf(Blocks.tsunami).id);
        sb.append(" liquid=").append(Dmg.typeOf(((mindustry.world.blocks.defense.turrets.LiquidTurret) Blocks.tsunami).ammoTypes.get(Liquids.water)).id);
        sb.append(" lancer=").append(Dmg.typeOf(((mindustry.world.blocks.defense.turrets.PowerTurret) Blocks.lancer).shootType).id);
        sb.append(" malign=").append(Dmg.typeOf(((mindustry.world.blocks.defense.turrets.PowerTurret) Blocks.malign).shootType).id);
        sb.append(" napalm=").append(Dmg.typeOf(IronworksTurrets.napalmBullet).id);
        sb.append(" corr=").append(Dmg.typeOf(IronworksTurrets.corrosiveBullet).id);
        sb.append(" tianfa=").append(Dmg.typeOf(IronworksTianfa.tianfa).id);
        sb.append(" railForm=").append(Dmg.formOf(IronworksSilicon.railTower).id);
        sb.append(" arcForm=").append(Dmg.formOf(IronworksSilicon.arcTower).id);
        sb.append(" malignForm=").append(Dmg.formOf(Blocks.malign).id);
        sb.append(" apForm=").append(Dmg.formOf(IronworksShells.apBullet).id);
        sb.append(" heForm=").append(Dmg.formOf(IronworksShells.heBullet).id);
        sb.append(" napalmForm=").append(Dmg.formOf(IronworksTurrets.napalmBullet).id);

        float x = at.x, y = at.y;
        Unit a = UnitTypes.fortress.spawn(Team.crux, x + 40f, y);
        Unit b = UnitTypes.fortress.spawn(Team.crux, x + 72f, y);
        Unit c = UnitTypes.fortress.spawn(Team.crux, x + 104f, y);
        Unit d = UnitTypes.dagger.spawn(Team.crux, x + 136f, y);
        Unit e = UnitTypes.dagger.spawn(Team.crux, x + 168f, y);
        Unit g = UnitTypes.dagger.spawn(Team.crux, x + 200f, y);
        Unit f = UnitTypes.dagger.spawn(Team.crux, x + 232f, y);
        Unit h = UnitTypes.dagger.spawn(Team.crux, x + 264f, y);
        Unit i = UnitTypes.fortress.spawn(Team.crux, x + 296f, y);
        Unit j = UnitTypes.dagger.spawn(Team.crux, x + 328f, y);
        g.shield(100f);

        float ha = a.health;
        Dmg.hit(a, 100f, DamageType.physical, false, false);
        float phys = ha - a.health;

        float hb = b.health;
        Dmg.hit(b, 100f, DamageType.thunder, false, false);
        float thunder = hb - b.health;

        ElementCombo.apply(c, DamageType.water);
        float hc = c.health;
        Dmg.hit(c, 100f, DamageType.thunder, true, false);
        float wetThunder = hc - c.health;
        boolean consume = "thunder".equals(ElementCombo.auraName(c)) && wetThunder > thunder;

        ElementCombo.apply(e, DamageType.water);
        float he = e.health;
        Dmg.hit(e, 1000f, DamageType.thunder, true, false);
        boolean overpen = Dmg.lastOverpen && (he - e.health) <= e.maxHealth() * Dmg.overpenMaxFraction + 0.1f;
        boolean overpenNoCombo = overpen && "water".equals(ElementCombo.auraName(e));
        float hg = g.health;
        Dmg.hit(g, 20f, DamageType.water, true, false, DamageType.water.pen, true, DamageForm.ENERGY);
        boolean energyShield = Math.abs(g.shield() - 100f) < 0.01f && g.health < hg;
        Dmg.hit(f, 10f, DamageType.physical, false, false);
        boolean kineticNoOverpen = !Dmg.lastOverpen;
        Dmg.hit(h, 1000f, DamageType.water, true, false, DamageType.water.pen, true, DamageForm.ENERGY);
        boolean energyNoOverpen = !Dmg.lastOverpen;
        Dmg.hit(i, 1000f, DamageType.thunder, true, false, DamageType.thunder.pen, true, DamageForm.KINETIC);
        boolean armoredNoOverpen = !Dmg.lastOverpen;

        Bullet ap = Bullet.create();
        ap.type = IronworksShells.apBullet;
        ap.team = Team.crux;
        ap.x = j.x; ap.y = j.y;
        ap.damage = 260f;
        Dmg.bulletHit(IronworksShells.apBullet, ap, j, j.health);
        boolean apOverpen = Dmg.lastOverpen;
        boolean[] splashRan = {false};
        Dmg.splash(IronworksShells.apBullet, ap, j.x, j.y, DamageType.physical, () -> splashRan[0] = true);
        boolean splashSkipped = apOverpen && !splashRan[0];
        ap.remove();

        Dmg.hit(d, 10f, DamageType.water, true, false);
        boolean wet = d.hasEffect(StatusEffects.wet);

        ArmorPen test = new ArmorPen(0.5f, 15f, 0.025f);
        boolean pierce = test.effective(10f) > test.effective(25f)
            && test.effective(25f) > test.effective(35f)
            && Math.abs(test.effective(35f)) < 0.001f;

        float hFloor = a.health;
        Dmg.hit(a, 10f, DamageType.fire, false, false, ArmorPen.none);
        float floor = hFloor - a.health;
        boolean floorKept = Math.abs(floor - 1f) < 0.01f;

        Tile wallTile = Vars.world.tile(at.tileX() + 10, at.tileY());
        boolean building = false;
        if(wallTile != null){
            wallTile.setBlock(Blocks.thoriumWall, Team.crux);
            Building wall = wallTile.build;
            float wallBefore = wall.health;
            Dmg.hit(wall, 50f, DamageType.physical, false, false);
            building = wall.health < wallBefore;
            wallTile.setBlock(Blocks.air, Team.derelict);
        }

        a.remove(); b.remove(); c.remove(); d.remove(); e.remove(); f.remove(); g.remove(); h.remove(); i.remove(); j.remove();

        sb.append(" armor=").append(Dmg.armorOf(a) > 0f && phys < 100f);
        sb.append(" bonus=").append(wetThunder > thunder);
        sb.append(" pierce=").append(pierce);
        sb.append(" floor=").append(floorKept);
        sb.append(" consume=").append(consume);
        sb.append(" overpen=").append(overpen);
        sb.append(" overpenNoCombo=").append(overpenNoCombo);
        sb.append(" energyShield=").append(energyShield);
        sb.append(" kineticNoOverpen=").append(kineticNoOverpen);
        sb.append(" energyNoOverpen=").append(energyNoOverpen);
        sb.append(" armoredNoOverpen=").append(armoredNoOverpen);
        sb.append(" apOverpen=").append(apOverpen);
        sb.append(" splashSkipped=").append(splashSkipped);
                sb.append(" building=").append(building);
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