package ironworks;

import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.StatusEffects;
import mindustry.content.UnitTypes;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Bullet;
import mindustry.gen.Unit;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.LiquidTurret;

/** 调试内容（不进科技树，不影响正常内容）。 */
public class IronworksDebug{
    public static Block liquidMonitor, verboseLogger;

    public static void load(){
        liquidMonitor = new LiquidMonitor("liquid-monitor");
        verboseLogger = new VerboseLogger("verbose-logger");

        registerEvents();
    }

    /** v3.0 战斗回归：平方衰减、跳弹、过穿、护盾、抗性、Combo、腐蚀与真伤。 */
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

        boolean vanillaItem = ((ItemTurret) Blocks.scatter).ammoTypes.get(Items.scrap) instanceof TypedBullet
            && ((ItemTurret) Blocks.ripple).ammoTypes.get(Items.graphite) instanceof TypedBullet;
        boolean vanillaLiquid = ((LiquidTurret) Blocks.wave).ammoTypes.get(Liquids.water) instanceof TypedBullet;
        boolean vanillaPower = ((mindustry.world.blocks.defense.turrets.PowerTurret) Blocks.lancer).shootType instanceof TypedBullet;
        boolean vanillaLightning = ((mindustry.world.blocks.defense.turrets.PowerTurret) Blocks.arc).shootType instanceof TypedBullet;
        boolean vanillaContinuous = ((mindustry.world.blocks.defense.turrets.PowerTurret) Blocks.meltdown).shootType instanceof TypedBullet;
        boolean lightningChunk = ((mindustry.world.blocks.defense.turrets.PowerTurret) Blocks.arc).shootType instanceof TypedLightningBulletType lightning
            && lightning.lightningType instanceof TypedBullet
            && Dmg.formOf(lightning.lightningType) == DamageForm.ENERGY;

        ArmorPen test = new ArmorPen(0.50f, 15f, 0.30f, 0.02f);
        boolean rateFlat = Math.abs(test.effective(10f) - 0.50f) < 0.0001f;
        boolean rateSquare = Math.abs(test.effective(30f) - 0.20f) < 0.002f;
        boolean rateCut = test.effective(60f) < 0.0001f;
        boolean armorDR = Math.abs(test.armorReduction(15f) - (7.5f / 107.5f)) < 0.002f;
        boolean noTenPercent = new ArmorPen(0f, 0f, 0f, 0f).damageAfterArmor(100f, 1000f) < 10f;
        boolean ricFormula = Math.abs(test.ricochetChance(30f) - 0.30f) < 0.001f
            && test.ricochetChance(200f) >= 0.899f;
        boolean overpenGate = test.canOverpen(7f) && !test.canOverpen(8f);

        float x = at.x, y = at.y;
        Unit a = UnitTypes.fortress.spawn(Team.crux, x + 40f, y);
        Unit b = UnitTypes.fortress.spawn(Team.crux, x + 72f, y);
        Unit c = UnitTypes.fortress.spawn(Team.crux, x + 104f, y);
        Unit d = UnitTypes.dagger.spawn(Team.crux, x + 136f, y);
        Unit e = UnitTypes.dagger.spawn(Team.crux, x + 168f, y);
        Unit f = UnitTypes.dagger.spawn(Team.crux, x + 200f, y);
        Unit g = UnitTypes.dagger.spawn(Team.crux, x + 232f, y);
        Unit h = UnitTypes.dagger.spawn(Team.crux, x + 264f, y);
        Unit i = UnitTypes.dagger.spawn(Team.crux, x + 296f, y);
        Unit j = UnitTypes.dagger.spawn(Team.crux, x + 328f, y);
        Unit k = UnitTypes.fortress.spawn(Team.crux, x + 360f, y);
        Unit l = UnitTypes.fortress.spawn(Team.crux, x + 392f, y);
        Unit m = UnitTypes.fortress.spawn(Team.crux, x + 424f, y);
        Unit n = UnitTypes.dagger.spawn(Team.crux, x + 456f, y);
        Unit o = UnitTypes.fortress.spawn(Team.crux, x + 488f, y);
        Unit p = UnitTypes.dagger.spawn(Team.crux, x + 520f, y);

        // 跳弹：护甲 9 > 白值 1，强制 roll=0；伤害归零且不消耗 Aura。
        ElementCombo.apply(c, DamageType.water);
        float hc = c.health;
        Dmg.ricochetRoll = 0f;
        Dmg.hit(c, 100f, DamageType.physical, false, false, new ArmorPen(0.50f, 1f, 0.30f, 0.10f), true, DamageForm.KINETIC);
        boolean ricochet = Dmg.lastRicochet && Math.abs(c.health - hc) < 0.01f
            && "water".equals(ElementCombo.auraName(c));
        Dmg.ricochetRoll = -1f;

        // 过穿：白值 18 / 护甲 9 = 2.0 → 保留 30%；跳过 Combo/Aura。
        ElementCombo.apply(b, DamageType.water);
        float hb = b.health;
        Dmg.hit(b, 100f, DamageType.thunder, true, false, new ArmorPen(0.50f, 18f, 0.30f, 0f), true, DamageForm.KINETIC);
        boolean overpen = Dmg.lastOverpen
            && Math.abs((hb - b.health) - 30f) < 0.5f
            && Math.abs(Dmg.lastOverpenKeepRate - 0.30f) < 0.001f
            && "water".equals(ElementCombo.auraName(b));
        boolean overpenKeepZero = Math.abs(new ArmorPen(0.5f, 20f, 0.3f, 0f).overpenKeepRate(0f) - 0.10f) < 0.001f;
        boolean overpenKeepHigh = Math.abs(new ArmorPen(0.5f, 90f, 0.3f, 0f).overpenKeepRate(9f) - 0.10f) < 0.001f;

        // 能量形态：不触发过穿，且穿透护盾。
        float hd = d.health;
        Dmg.hit(d, 100f, DamageType.water, false, false, new ArmorPen(0.50f, 20f, 0.30f, 0f), false, DamageForm.ENERGY);
        boolean energyNoOverpen = !Dmg.lastOverpen && Math.abs(Dmg.lastDealt - 100f) < 0.5f;

        // 非能量盾：先扣盾，溢出 50 打本体。
        e.shield(100f);
        float he = e.health;
        Dmg.hit(e, 150f, DamageType.fire, false, false, new ArmorPen(0.50f, 20f, 0.30f, 0f), false, DamageForm.EXPLOSIVE);
        boolean shieldOverflow = Math.abs(e.shield()) < 0.01f && Math.abs(Dmg.lastShieldAbsorbed - 100f) < 0.01f
            && Math.abs((he - e.health) - 50f) < 0.5f;

        // 盾完全吸收：本体不掉血、不挂 Aura。
        f.shield(100f);
        float hf = f.health;
        Dmg.hit(f, 50f, DamageType.fire, false, false, new ArmorPen(0.50f, 20f, 0.30f, 0f), false, DamageForm.EXPLOSIVE);
        boolean shieldBlocked = Dmg.lastShieldBlocked && Math.abs(f.shield() - 50f) < 0.01f
            && Math.abs(f.health - hf) < 0.01f;

        // 能量形态无视盾：盾值不变，本体吃满。
        g.shield(100f);
        float hg = g.health;
        Dmg.hit(g, 100f, DamageType.water, false, false, new ArmorPen(0.50f, 20f, 0.30f, 0f), false, DamageForm.ENERGY);
        boolean energyShield = Math.abs(g.shield() - 100f) < 0.01f
            && Math.abs((hg - g.health) - 100f) < 0.5f;

        // 抗性：火抗 50%，能量抗 50%；封顶 90%。
        Resistance.clear();
        Resistance.set("dagger", DamageType.fire, 0.50f);
        Resistance.setEnergy("dagger", 0.50f);
        float hh = h.health;
        Dmg.hit(h, 100f, DamageType.fire, false, false, new ArmorPen(0.50f, 20f, 0.30f, 0f), false, DamageForm.EXPLOSIVE);
        boolean fireResist = Math.abs((hh - h.health) - 50f) < 0.5f
            && Math.abs(Dmg.lastElementResistance - 0.50f) < 0.001f;
        float hi = i.health;
        Dmg.hit(i, 100f, DamageType.water, false, false, new ArmorPen(0.50f, 20f, 0.30f, 0f), false, DamageForm.ENERGY);
        boolean energyResist = Math.abs((hi - i.health) - 50f) < 0.5f
            && Math.abs(Dmg.lastEnergyResistance - 0.50f) < 0.001f;
        Resistance.set("dagger", DamageType.fire, 2f);
        boolean resistCap = Math.abs(Resistance.element(UnitTypes.dagger, DamageType.fire) - 0.90f) < 0.001f;
        Resistance.clear();

        // Combo 在抗性之后：50 伤害 × 1.5 = 75。
        ElementCombo.apply(j, DamageType.water);
        float hj = j.health;
        Dmg.hit(j, 50f, DamageType.thunder, true, false, new ArmorPen(0.50f, 20f, 0.30f, 0f), false, DamageForm.KINETIC);
        boolean comboAfterResist = Math.abs((hj - j.health) - 75f) < 0.5f
            && Math.abs(Dmg.lastComboMultiplier - 1.5f) < 0.001f
            && "thunder".equals(ElementCombo.auraName(j));

        // 旧 10% 保底已删除；未跳弹时只保留 1/5% 防浮点保底。
        float ho = o.health;
        Dmg.hit(o, 1f, DamageType.physical, false, false, ArmorPen.none, false, DamageForm.KINETIC);
        boolean floorKept = Dmg.lastDealt >= 1f && Math.abs((ho - o.health) - 1f) < 0.2f;

        // 永久腐蚀：普通 100 tick 有削减；潮湿酸蚀上限 8%。
        Corrosion.clear();
        float p0 = Dmg.armorOf(k);
        Corrosion.apply(k);
        for(int tick = 0; tick < 100; tick++) Corrosion.update();
        float p1 = Dmg.armorOf(k);
        boolean corrosion = p1 < p0 && Corrosion.reduction(k) > 0f;
        Corrosion.apply(l);
        l.apply(StatusEffects.wet, 240f);
        for(int tick = 0; tick < 2500; tick++) Corrosion.update();
        boolean acidCap = Corrosion.reduction(l) <= Dmg.baseArmorOf(l) * Corrosion.acidCap + 0.01f;
        boolean noCorrCombo = !ElementCombo.summary(DamageType.corrosive).contains("×");
        Corrosion.clear();

        // 真伤：红色分类走 Dmg.pierce，不受护甲影响。
        float hm = m.health;
        Dmg.pierce(m, 50f, DamageType.corrosive);
        boolean trueDamage = Math.abs((hm - m.health) - 50f) < 0.5f;

        // 建筑：结构材质，不用过穿/跳弹。
        Tile wallTile = Vars.world.tile(at.tileX() + 10, at.tileY());
        boolean building = false;
        if(wallTile != null){
            wallTile.setBlock(Blocks.thoriumWall, Team.crux);
            Building wall = wallTile.build;
            float wallBefore = wall.health;
            Dmg.hit(wall, 50f, DamageType.physical, false, false, new ArmorPen(0.50f, 20f, 0.30f, 0f), false, DamageForm.KINETIC);
            building = wall.health < wallBefore;
            wallTile.setBlock(Blocks.air, Team.derelict);
        }

        // AP 炮弹过穿时，Bullet.data 标记要挡住整次溅射。
        Bullet ap = Bullet.create();
        ap.type = IronworksShells.apBullet;
        ap.team = Team.crux;
        ap.x = n.x; ap.y = n.y;
        ap.damage = 260f;
        Dmg.bulletHit(IronworksShells.apBullet, ap, n, n.health);
        boolean apOverpen = Dmg.lastOverpen;
        boolean[] splashRan = {false};
        Dmg.splash(IronworksShells.apBullet, ap, n.x, n.y, DamageType.physical, () -> splashRan[0] = true);
        boolean splashSkipped = apOverpen && !splashRan[0];
        ap.remove();

        Dmg.hit(p, 10f, DamageType.water, true, false, new ArmorPen(0.30f, 12f, 0.35f, 0f), false, DamageForm.ENERGY);
        boolean wet = p.hasEffect(StatusEffects.wet);

        a.remove(); b.remove(); c.remove(); d.remove(); e.remove(); f.remove(); g.remove(); h.remove(); i.remove();
        j.remove(); k.remove(); l.remove(); m.remove(); n.remove(); o.remove(); p.remove();

        sb.append(" vanillaItem=").append(vanillaItem);
        sb.append(" vanillaLiquid=").append(vanillaLiquid);
        sb.append(" vanillaPower=").append(vanillaPower);
        sb.append(" vanillaLightning=").append(vanillaLightning);
        sb.append(" lightningChunk=").append(lightningChunk).append(" lightningChunkEnergy=").append(lightningChunk);
        sb.append(" vanillaContinuous=").append(vanillaContinuous);
        sb.append(" rateFlat=").append(rateFlat);
        sb.append(" rateSquare=").append(rateSquare);
        sb.append(" rateCut=").append(rateCut);
        sb.append(" armorDR=").append(armorDR);
        sb.append(" noTenPercent=").append(noTenPercent);
        sb.append(" ricFormula=").append(ricFormula);
        sb.append(" overpenGate=").append(overpenGate);
        sb.append(" ricochet=").append(ricochet);
        sb.append(" overpen=").append(overpen);
        sb.append(" overpenKeepZero=").append(overpenKeepZero);
        sb.append(" overpenKeepHigh=").append(overpenKeepHigh);
        sb.append(" energyNoOverpen=").append(energyNoOverpen);
        sb.append(" shieldOverflow=").append(shieldOverflow);
        sb.append(" shieldBlocked=").append(shieldBlocked);
        sb.append(" energyShield=").append(energyShield);
        sb.append(" fireResist=").append(fireResist);
        sb.append(" energyResist=").append(energyResist);
        sb.append(" resistCap=").append(resistCap);
        sb.append(" comboAfterResist=").append(comboAfterResist);
        sb.append(" floorKept=").append(floorKept);
        sb.append(" corrosion=").append(corrosion);
        sb.append(" acidCap=").append(acidCap);
        sb.append(" noCorrCombo=").append(noCorrCombo);
        sb.append(" trueDamage=").append(trueDamage);
        sb.append(" building=").append(building);
        sb.append(" apOverpen=").append(apOverpen);
        sb.append(" splashSkipped=").append(splashSkipped);
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