package ironworks;

import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.StatusEffects;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.MissileBulletType;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.type.StatusEffect;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.BaseTurret;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.blocks.defense.turrets.LiquidTurret;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.blocks.defense.turrets.ReloadTurret;
import mindustry.world.blocks.defense.turrets.TractorBeamTurret;
import mindustry.world.blocks.defense.turrets.Turret;

import static mindustry.type.ItemStack.with;

/**
 * 炮塔数值表（由《数值_炮塔.txt》驱动）。
 *
 * <p>原版炮塔是同一批 Block 实例，运行期不能重新 new；这里在内容创建后、
 * {@link Dmg#load()} 重定型前统一改写 range/reload/造价/弹体字段。弹体的属性
 * 与形态通过 {@link Dmg#overrideBullet(BulletType, DamageType, DamageForm)}
 * 按“具体弹体”覆盖，而不是按物品全局覆盖，以处理巨浪合金在蜂群/气旋/厄兆
 * 里的不同属性。</p>
 */
public final class IronworksTurretBalance{

    private IronworksTurretBalance(){
    }

    /** 创建完所有内容、Dmg 重定型之前调用。 */
    public static void load(){
        // 原版炮塔：射程/射速/对空对地/造价按数值表统一铺一遍。
        // 特殊炮塔（视差/裂解/熔毁/升华/光辉/创伤）的射击机制仍由各自类处理。
        common(Blocks.duo, 20f, 3f, true, true, "copper", 25);
        common(Blocks.scatter, 27.5f, 3.33f, true, false,
            "copper", 65, "lead", 35);
        common(Blocks.scorch, 7.5f, 8f, false, true,
            "ironworks-copper-plate", 25, "graphite", 25);
        common(Blocks.hail, 30f, 1.2f, false, true,
            "copper", 40, "graphite", 20);
        common(Blocks.wave, 18f, 25f, true, true,
            "copper", 25, "lead", 65, "metaglass", 55);
        common(Blocks.lancer, 25f, 0.85f, false, true,
            "ironworks-copper-plate", 70, "ironworks-lead-plate", 70,
            "titanium", 30, "silicon", 60, "graphite", 25);
        common(Blocks.arc, 10f, 1.75f, false, true,
            "ironworks-copper-plate", 40, "ironworks-lead-plate", 40);
        common(Blocks.parallax, 32.5f, 0f, true, false,
            "graphite", 50, "ironworks-titanium-plate", 110, "ironworks-silicon-carbide", 70);
        common(Blocks.swarmer, 35f, 2.35f, true, true,
            "ironworks-graphene", 35, "ironworks-titanium-plate", 35, "ironworks-silicon-carbide", 30);
        common(Blocks.salvo, 24f, 2f, true, true,
            "ironworks-copper-plate", 80, "graphite", 70, "ironworks-titanium-plate", 20);
        common(Blocks.segment, 24f, 8f, false, false,
            "ironworks-titanium-plate", 40, "thorium", 80, "ironworks-silicon-carbide", 130,
            "phase-fabric", 40, "ironworks-graphite-electrode", 45);
        common(Blocks.tsunami, 28f, 30f, true, true,
            "ironworks-steel-plate", 240, "metaglass", 100,
            "ironworks-titanium-plate", 150, "ironworks-silicon-carbide", 120);
        common(Blocks.fuse, 12f, 1.6f, true, true,
            "ironworks-titanium-plate", 225, "graphite", 225, "thorium", 120);
        common(Blocks.ripple, 38f, 0.7f, false, true,
            "ironworks-copper-plate", 150, "graphite", 135, "ironworks-titanium-plate", 60);
        common(Blocks.cyclone, 25f, 6f, true, true,
            "ironworks-titanium-plate", 200, "graphite", 125, "plastanium", 80);
        common(Blocks.foreshadow, 62.5f, 0.25f, true, true,
            "ironworks-copper-plate", 600, "ironworks-titanium-plate", 600,
            "ironworks-silicon-carbide", 600, "ironworks-aluminum-sheet", 200, "surge-alloy", 300);
        common(Blocks.spectre, 36f, 12f, true, true,
            "ironworks-steel-plate", 700, "graphite", 300, "thorium", 250,
            "ironworks-silicon-carbide", 175, "surge-alloy", 150);
        common(Blocks.meltdown, 24.4f, 0.65f, true, true,
            "ironworks-copper-plate", 1100, "ironworks-lead-plate", 250,
            "ironworks-graphene", 400, "ironworks-silicon-carbide", 325, "surge-alloy", 255);
        common(Blocks.breach, 23.8f, 1.5f, true, true,
            "graphite", 125, "silicon", 150, "beryllium", 150);
        common(Blocks.diffuse, 15.6f, 2f, true, true,
            "graphite", 200, "silicon", 200, "beryllium", 150, "tungsten", 50);
        common(Blocks.sublimate, 16.3f, 6f, true, true,
            "silicon", 200, "beryllium", 400, "tungsten", 150, "oxide", 40);
        common(Blocks.titan, 48.8f, 0.43f, false, true,
            "thorium", 400, "silicon", 300, "tungsten", 250);
        common(Blocks.disperse, 38.8f, 6.67f, true, false,
            "thorium", 50, "silicon", 200, "beryllium", 350, "oxide", 50);
        common(Blocks.afflict, 46f, 1.2f, true, true,
            "graphite", 250, "silicon", 200, "surge-alloy", 100, "oxide", 40);
        common(Blocks.lustre, 31.3f, 6f, true, true,
            "graphite", 200, "silicon", 250, "oxide", 50, "carbide", 90);
        common(Blocks.scathe, 168.8f, 0.1f, false, true,
            "graphite", 400, "silicon", 450, "tungsten", 500, "oxide", 100, "carbide", 200);
        common(Blocks.smite, 37.5f, 0.6f, true, true,
            "silicon", 800, "phase-fabric", 300, "surge-alloy", 400,
            "oxide", 200, "carbide", 500);
        common(Blocks.malign, 51.3f, 17.14f, true, true,
            "graphite", 500, "silicon", 500, "phase-fabric", 200,
            "beryllium", 1000, "carbide", 200);

        // 本模组炮塔：数值表从“速射炮”一节开始。轨炮的当前机制按用户要求保留
        // （5 道 270 能量束 / 52.5 格 / 0.33 次每秒），这里不改它。
        common(IronworksTurrets.rapidCannon, 20f, 7.5f, true, true,
            "copper", 120, "lead", 80, "ironworks-copper-plate", 40);
        common(IronworksTurrets.piercer, 37.5f, 2.4f, true, true,
            "titanium", 120, "silicon", 80, "ironworks-steel-plate", 60, "ironworks-cast-iron", 30);
        common(IronworksShells.mortar, 70f, 1f, true, true,
            "titanium", 100, "silicon", 60, "ironworks-steel-plate", 80, "ironworks-iron-plate", 60);
        common(IronworksSilicon.arcTower, 18.8f, 0.67f, true, true,
            "titanium", 20, "silicon", 25, "ironworks-steel-plate", 20,
            "ironworks-aluminum-sheet", 15, "ironworks-basic-chip", 2);
        common(IronworksSilicon.singularityTower, 80f, 0.02f, true, true,
            "silicon", 200, "ironworks-steel-plate", 120, "ironworks-silicon-carbide", 40,
            "ironworks-aluminum-sheet", 40, "ironworks-aviation-aluminum", 20,
            "ironworks-quantum-core", 5);
        common(IronworksTianfa.tianfa, 45f, 1.5f, true, true,
            "silicon", 600, "ironworks-steel-plate", 800,
            "ironworks-aviation-aluminum", 80, "ironworks-titanium-alloy", 120,
            "ironworks-advanced-chip", 12);

        vanillaAmmo();
        swarmer();
    }

    private static void common(Block block, float rangeTiles, float rate, boolean air, boolean ground, Object... costs){
        if(block == null) return;
        if(block instanceof BaseTurret turret){
            turret.range = rangeTiles * 8f;
        }
        if(rate > 0f && block instanceof ReloadTurret reload){
            reload.reload = 60f / rate;
        }
        if(block instanceof Turret turret){
            turret.targetAir = air;
            turret.targetGround = ground;
        }else if(block instanceof TractorBeamTurret tractor){
            tractor.targetAir = air;
            tractor.targetGround = ground;
        }
        if(costs.length > 0){
            block.requirements(Category.turret, stacks(costs));
        }
    }

    private static ItemStack[] stacks(Object... data){
        ItemStack[] out = new ItemStack[data.length / 2];
        for(int i = 0; i < out.length; i++){
            String name = (String)data[i * 2];
            Item item = Vars.content.item(name);
            if(item == null) throw new IllegalArgumentException("Unknown turret cost item: " + name);
            out[i] = new ItemStack(item, ((Number)data[i * 2 + 1]).intValue());
        }
        return out;
    }

    /**
     * 原版常规炮塔的弹药数值。只改“数据表明确写了的字段”，射击机制
     * （激光延时、电弧链、分裂破片等）仍由原版类实现。
     */
    private static void vanillaAmmo(){
        BulletType x;
        ItemTurret duo = (ItemTurret)Blocks.duo;
        hit(duo, Items.copper, 9f, .45f, 18f, DamageType.physical, DamageForm.KINETIC);
        x = hit(duo, Items.graphite, 16f, .55f, 22f, DamageType.physical, DamageForm.KINETIC);
        extras(x, .8f, 2f, 0f, 0f);
        x = hit(duo, Items.silicon, 13f, .45f, 16f, DamageType.physical, DamageForm.KINETIC);
        extras(x, 1.8f, 0f, 0f, 0f);
        homing(x, .2f);

        ItemTurret scatter = (ItemTurret)Blocks.scatter;
        x = hit(scatter, Items.scrap, 3f, .65f, 18f, DamageType.physical, DamageForm.KINETIC);
        extras(x, .5f, 0f, 33f, 3f);
        x = hit(scatter, Items.lead, 3f, .65f, 18f, DamageType.physical, DamageForm.KINETIC);
        extras(x, 1f, 0f, 40.5f, 1.9f);
        x = hit(scatter, Items.metaglass, 3f, .65f, 18f, DamageType.physical, DamageForm.KINETIC);
        extras(x, .8f, 0f, 45f, 2.5f);
        frag(x, 6, 5f);

        ItemTurret scorch = (ItemTurret)Blocks.scorch;
        x = hit(scorch, Items.coal, 18f, 0f, 0f, DamageType.fire, DamageForm.EXPLOSIVE);
        status(x, StatusEffects.burning, 2f);
        pierce(x, -1, false);
        x = hit(scorch, Items.pyratite, 32f, 0f, 0f, DamageType.fire, DamageForm.EXPLOSIVE);
        status(x, StatusEffects.burning, 6f);
        pierce(x, -1, false);

        ItemTurret hail = (ItemTurret)Blocks.hail;
        x = hit(hail, Items.graphite, 26f, .55f, 20f, DamageType.physical, DamageForm.KINETIC);
        extras(x, 1f, 0f, 30f, 2.3f);
        noBuildings(x);
        x = hit(hail, Items.silicon, 20f, .45f, 18f, DamageType.physical, DamageForm.KINETIC);
        extras(x, 1.2f, 0f, 28f, 2f);
        homing(x, .08f);
        noBuildings(x);
        x = hit(hail, Items.pyratite, 24f, .25f, 14f, DamageType.fire, DamageForm.EXPLOSIVE);
        extras(x, 1f, 0f, 45f, 2.3f);
        status(x, StatusEffects.burning, 4f);
        noBuildings(x);
        knockAll(hail, .8f);

        ItemTurret salvo = (ItemTurret)Blocks.salvo;
        hit(salvo, Items.copper, 14f, .65f, 16f, DamageType.physical, DamageForm.KINETIC);
        x = hit(salvo, Items.graphite, 32f, .65f, 22f, DamageType.physical, DamageForm.KINETIC);
        extras(x, .8f, 6f, 0f, 0f);
        x = hit(salvo, Items.pyratite, 24f, .25f, 10f, DamageType.fire, DamageForm.EXPLOSIVE);
        extras(x, 1f, 0f, 18f, 2.8f);
        status(x, StatusEffects.burning, 4f);
        x = hit(salvo, Items.silicon, 26f, .55f, 18f, DamageType.physical, DamageForm.KINETIC);
        extras(x, 1.5f, 0f, 0f, 0f);
        homing(x, .2f);
        hit(salvo, Items.thorium, 38f, .75f, 32f, DamageType.physical, DamageForm.KINETIC);

        ItemTurret fuse = (ItemTurret)Blocks.fuse;
        x = hit(fuse, Items.titanium, 65f, .65f, 36f, DamageType.physical, DamageForm.KINETIC);
        extras(x, 1.4f, 0f, 0f, 0f);
        pierce(x, -1, false);
        x = hit(fuse, Items.thorium, 105f, .75f, 42f, DamageType.physical, DamageForm.KINETIC);
        pierce(x, -1, false);

        ItemTurret ripple = (ItemTurret)Blocks.ripple;
        x = hit(ripple, Items.graphite, 42f, .65f, 26f, DamageType.physical, DamageForm.KINETIC);
        extras(x, 1f, 0f, 70f, 2.8f);
        x = hit(ripple, Items.silicon, 38f, .65f, 22f, DamageType.physical, DamageForm.KINETIC);
        extras(x, 1.2f, 0f, 70f, 2.8f);
        homing(x, .08f);
        x = hit(ripple, Items.pyratite, 32f, .25f, 10f, DamageType.fire, DamageForm.EXPLOSIVE);
        extras(x, 1f, 0f, 90f, 2.8f);
        status(x, StatusEffects.burning, 4f);
        x = hit(ripple, Items.blastCompound, 38f, .45f, 14f, DamageType.physical, DamageForm.EXPLOSIVE);
        extras(x, 1f, 0f, 90f, 4.5f);
        status(x, StatusEffects.blasted, 8f);
        x = hit(ripple, Items.plastanium, 44f, .6f, 28f, DamageType.physical, DamageForm.KINETIC);
        extras(x, 1f, 0f, 80f, 3.8f);
        frag(x, 18, 16f);

        ItemTurret cyclone = (ItemTurret)Blocks.cyclone;
        x = hit(cyclone, Items.metaglass, 8f, .6f, 22f, DamageType.physical, DamageForm.KINETIC);
        extras(x, .8f, 0f, 55f, 3.1f);
        frag(x, 4, 12f);
        x = hit(cyclone, Items.blastCompound, 8f, .4f, 16f, DamageType.physical, DamageForm.EXPLOSIVE);
        extras(x, 1f, 0f, 65f, 7.5f);
        status(x, StatusEffects.blasted, 8f);
        x = hit(cyclone, Items.plastanium, 8f, .55f, 28f, DamageType.physical, DamageForm.KINETIC);
        extras(x, 1f, 0f, 38.5f, 5f);
        frag(x, 14, 12f);
        x = hit(cyclone, Items.surgeAlloy, 16f, .75f, 36f, DamageType.thunder, DamageForm.KINETIC);
        extras(x, 1f, 0f, 75f, 4.8f);
        status(x, StatusEffects.shocked, 8f);

        x = hit((ItemTurret)Blocks.foreshadow, Items.surgeAlloy, 3276.7f, 1f, 175f,
            DamageType.physical, DamageForm.KINETIC);
        pierce(x, -1, true);

        ItemTurret spectre = (ItemTurret)Blocks.spectre;
        x = hit(spectre, Items.graphite, 50f, .6f, 28f, DamageType.physical, DamageForm.KINETIC);
        extras(x, 1.7f, 0f, 0f, 0f);
        x = hit(spectre, Items.thorium, 80f, .75f, 38f, DamageType.physical, DamageForm.KINETIC);
        pierce(x, 4, true);
        x = hit(spectre, Items.pyratite, 70f, .25f, 10f, DamageType.fire, DamageForm.EXPLOSIVE);
        extras(x, 1f, 0f, 30f, 3.1f);
        status(x, StatusEffects.burning, 4f);
        pierce(x, 2, true);

        ItemTurret breach = (ItemTurret)Blocks.breach;
        x = hit(breach, Items.beryllium, 63.8f, .65f, 18f, DamageType.physical, DamageForm.KINETIC);
        pierce(x, 2, true);
        x = hit(breach, Items.tungsten, 71.3f, .65f, 18f, DamageType.physical, DamageForm.KINETIC);
        extras(x, 1f, 5f, 0f, 0f);
        pierce(x, 4, true);
        x = hit(breach, Items.carbide, 325f, .65f, 18f, DamageType.physical, DamageForm.KINETIC);
        extras(x, .2f, 7f, 0f, 0f);
        frag(x, 3, 227f);

        ItemTurret diffuse = (ItemTurret)Blocks.diffuse;
        x = hit(diffuse, Items.graphite, 30.8f, .65f, 18f, DamageType.physical, DamageForm.KINETIC);
        knock(x, 4f);
        x = hit(diffuse, Items.oxide, 67.5f, .65f, 18f, DamageType.physical, DamageForm.KINETIC);
        knock(x, 3f);
        x = hit(diffuse, Items.silicon, 26.3f, .65f, 18f, DamageType.physical, DamageForm.KINETIC);
        homing(x, .05f);
        knock(x, 3f);

        ItemTurret titan = (ItemTurret)Blocks.titan;
        x = hit(titan, Items.thorium, 262.5f, .65f, 18f, DamageType.physical, DamageForm.KINETIC);
        extras(x, 1f, 0f, 350f, 8.1f);
        status(x, StatusEffects.blasted, 8f);
        x = hit(titan, Items.carbide, 525f, .65f, 18f, DamageType.physical, DamageForm.KINETIC);
        extras(x, .8f, 10f, 750f, 4.5f);
        status(x, StatusEffects.blasted, 8f);
        frag(x, 12, 50f);
        x = hit(titan, Items.oxide, 225f, .65f, 18f, DamageType.physical, DamageForm.KINETIC);
        extras(x, .7f, 1f, 180f, 13.8f);
        status(x, StatusEffects.corroded, 8f);

        ItemTurret disperse = (ItemTurret)Blocks.disperse;
        hit(disperse, Items.tungsten, 48.8f, .65f, 18f, DamageType.physical, DamageForm.KINETIC);
        x = hit(disperse, Items.thorium, 67.5f, .65f, 18f, DamageType.physical, DamageForm.KINETIC);
        extras(x, .85f, -15f, 0f, 0f);
        pierce(x, 2, false);
        x = hit(disperse, Items.silicon, 27.8f, .65f, 18f, DamageType.physical, DamageForm.KINETIC);
        homing(x, .05f);
        x = hit(disperse, Items.surgeAlloy, 48.8f, .65f, 18f, DamageType.physical, DamageForm.KINETIC);
        extras(x, .75f, 3f, 0f, 0f);
        status(x, StatusEffects.shocked, 8f);

        x = hit((ItemTurret)Blocks.smite, Items.surgeAlloy, 187.5f, .65f, 18f,
            DamageType.physical, DamageForm.KINETIC);
        pierce(x, 4, true);

        // 液体炮塔
        LiquidTurret wave = (LiquidTurret)Blocks.wave;
        x = liquid(wave, Liquids.water, 0f, 0f, 0f, DamageType.water, DamageForm.ENERGY);
        status(x, StatusEffects.wet, 4f);
        knock(x, .7f, .01f);
        x = liquid(wave, Liquids.oil, 0f, 0f, 0f, DamageType.physical, DamageForm.ENERGY);
        status(x, StatusEffects.tarred, 2f);
        knock(x, .55f, .01f);
        x = liquid(wave, Liquids.cryofluid, 0f, 0f, 0f, DamageType.ice, DamageForm.ENERGY);
        status(x, StatusEffects.freezing, 2f);
        knock(x, .55f, .01f);
        x = liquid(wave, Liquids.slag, 2f, 0f, 0f, DamageType.fire, DamageForm.ENERGY);
        status(x, StatusEffects.burning, 4f);
        knock(x, .55f, .01f);

        LiquidTurret tsunami = (LiquidTurret)Blocks.tsunami;
        x = liquid(tsunami, Liquids.water, .2f, 0f, 0f, DamageType.water, DamageForm.ENERGY);
        status(x, StatusEffects.wet, 6f);
        knock(x, 1.7f, 0f);
        x = liquid(tsunami, Liquids.oil, .2f, 0f, 0f, DamageType.physical, DamageForm.KINETIC);
        status(x, StatusEffects.tarred, 4f);
        knock(x, 1.3f, 0f);
        x = liquid(tsunami, Liquids.cryofluid, .2f, 0f, 0f, DamageType.ice, DamageForm.ENERGY);
        status(x, StatusEffects.freezing, 2f);
        knock(x, 1.3f, 0f);
        x = liquid(tsunami, Liquids.slag, 4.8f, 0f, 0f, DamageType.fire, DamageForm.EXPLOSIVE);
        status(x, StatusEffects.burning, 4f);
        knock(x, 1.3f, 0f);

        // 固定弹体：蓝瑟/电弧/熔毁
        if(Blocks.lancer instanceof PowerTurret lancer && lancer.shootType != null){
            x = lancer.shootType;
            x.damage = 140f;
            x.status = StatusEffects.shocked;
            x.statusDuration = 90f;
            x.pierce = true;
            x.pierceCap = 4;
            Dmg.overrideBullet(x, DamageType.thunder, DamageForm.ENERGY);
            Dmg.pen(x, 0f, 0f, 0f, 0f);
        }
        if(Blocks.arc instanceof PowerTurret arc && arc.shootType != null){
            x = arc.shootType;
            x.damage = 11f;
            x.status = StatusEffects.shocked;
            x.statusDuration = 90f;
            x.pierceCap = 5;
            Dmg.overrideBullet(x, DamageType.thunder, DamageForm.ENERGY);
            Dmg.pen(x, 0f, 0f, 0f, 0f);
        }
        if(Blocks.meltdown instanceof PowerTurret meltdown && meltdown.shootType != null){
            x = meltdown.shootType;
            x.damage = 99f;
            x.status = StatusEffects.melting;
            x.statusDuration = 480f;
            x.pierce = true;
            x.pierceCap = -1;
            Dmg.overrideBullet(x, DamageType.fire, DamageForm.ENERGY);
            Dmg.pen(x, 0f, 0f, 0f, 0f);
        }
    }

    private static BulletType hit(ItemTurret turret, Item item, float damage, float rate, float flat,
                                  DamageType type, DamageForm form){
        BulletType bullet = turret == null ? null : turret.ammoTypes.get(item);
        if(bullet == null) return null;
        bullet.damage = damage;
        Dmg.overrideBullet(bullet, type, form);
        Dmg.pen(bullet, rate, flat, 0.20f);
        return bullet;
    }

    private static BulletType liquid(LiquidTurret turret, Liquid liquid, float damage, float rate, float flat,
                                     DamageType type, DamageForm form){
        BulletType bullet = turret == null ? null : turret.ammoTypes.get(liquid);
        if(bullet == null) return null;
        bullet.damage = damage;
        Dmg.overrideBullet(bullet, type, form);
        Dmg.pen(bullet, rate, flat, 0.20f);
        return bullet;
    }

    private static void extras(BulletType bullet, float reloadMul, float rangeTiles, float splash, float radiusTiles){
        if(bullet == null) return;
        if(reloadMul != 1f) bullet.reloadMultiplier = reloadMul;
        if(rangeTiles != 0f) bullet.rangeChange = rangeTiles * 8f;
        if(splash > 0f){
            bullet.splashDamage = splash;
            bullet.splashDamageRadius = radiusTiles * 8f;
        }
    }

    private static void status(BulletType bullet, StatusEffect effect, float seconds){
        if(bullet == null || effect == null) return;
        bullet.status = effect;
        bullet.statusDuration = seconds * 60f;
    }

    private static void pierce(BulletType bullet, int cap, boolean buildings){
        if(bullet == null) return;
        bullet.pierce = true;
        bullet.pierceCap = cap;
        if(buildings) bullet.pierceBuilding = true;
    }

    private static void homing(BulletType bullet, float power){
        if(bullet == null) return;
        bullet.homingPower = power;
        bullet.homingRange = 40f * 8f;
    }

    private static void knock(BulletType bullet, float power){
        knock(bullet, power, -1f);
    }

    private static void knock(BulletType bullet, float power, float drag){
        if(bullet == null) return;
        bullet.knockback = power;
        if(drag >= 0f) bullet.drag = drag;
    }

    private static void knockAll(ItemTurret turret, float power){
        if(turret == null) return;
        for(BulletType bullet : turret.ammoTypes.values()) knock(bullet, power);
    }

    private static void frag(BulletType bullet, int count, float damage){
        if(bullet == null || bullet.fragBullet == null) return;
        bullet.fragBullets = count;
        bullet.fragBullet.damage = damage;
    }

    private static void noBuildings(BulletType bullet){
        if(bullet != null) bullet.targetBlocks = false;
    }

    private static void swarmer(){
        ItemTurret t = (ItemTurret)Blocks.swarmer;
        t.range = 35f * 8f;
        t.reload = 60f / 2.35f;
        t.targetAir = true;
        t.targetGround = true;
        t.requirements(Category.turret,
            with(IronworksMaterials.graphene, 35, IronworksMaterials.titaniumPlate, 35,
                 IronworksMaterials.siliconCarbide, 30));

        BulletType blast = t.ammoTypes.get(Items.blastCompound);
        if(blast instanceof MissileBulletType b){
            b.damage = 12f;
            b.splashDamage = 40f;
            b.splashDamageRadius = 4.2f * 8f;
            b.status = StatusEffects.blasted;
            b.statusDuration = 8f * 60f;
            b.homingPower = 0.08f;
            Dmg.overrideBullet(b, DamageType.physical, DamageForm.EXPLOSIVE);
            Dmg.pen(b, 0.35f, 18f, 0.30f, 0.02f);
        }

        BulletType pyra = t.ammoTypes.get(Items.pyratite);
        if(pyra instanceof MissileBulletType b){
            b.damage = 8f;
            b.splashDamage = 36f;
            b.splashDamageRadius = 3.0f * 8f;
            b.status = StatusEffects.burning;
            b.statusDuration = 4f * 60f;
            b.makeFire = true;
            b.homingPower = 0.08f;
            Dmg.overrideBullet(b, DamageType.fire, DamageForm.EXPLOSIVE);
            Dmg.pen(b, 0.25f, 12f, 0.35f, 0.02f);
        }

        BulletType surge = t.ammoTypes.get(Items.surgeAlloy);
        if(surge instanceof MissileBulletType b){
            b.damage = 18f;
            b.splashDamage = 48f;
            b.splashDamageRadius = 2.8f * 8f;
            b.status = StatusEffects.shocked;
            b.statusDuration = 6f * 60f;
            b.homingPower = 0.08f;
            b.reloadMultiplier = 0.7f;
            b.lightning = 0;
            b.lightningDamage = 0f;
            Dmg.overrideBullet(b, DamageType.thunder, DamageForm.KINETIC);
            Dmg.pen(b, 0.75f, 36f, 0.20f, 0.02f);
        }
    }
}
