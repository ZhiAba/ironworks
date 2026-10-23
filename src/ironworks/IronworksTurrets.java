package ironworks;

import arc.graphics.Color;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.type.Category;
import mindustry.world.Block;

import static mindustry.type.ItemStack.with;

/**
 * 弹药消费端。
 * 遵循「炮塔不可能三角」：每门炮只取 范围/伤害/射速 中的两项。
 *  - 速射炮：射速 + 伤害，射程短
 *  - 穿甲炮：射程 + 伤害，射速低
 */
public class IronworksTurrets{

    public static Block rapidCannon, piercer;

    // ── 弹道 ──
    static BulletType copperBullet, heavyBullet, leadCoreBullet, tiPierceBullet, tiOverpressureBullet, napalmBullet, corrosiveBullet;

    static Color c(String hex){
        return Color.valueOf(hex);
    }

    public static void load(){
        copperBullet = new TypedBasicBulletType(DamageType.physical, 5f, 16){{
            width = 7f; height = 9f; lifetime = 34f;
            ammoMultiplier = 1f;
            hitEffect = despawnEffect = Fx.hitBulletColor;
            hitColor = backColor = trailColor = c("c87c4a");
            frontColor = Color.white;
        }};

        heavyBullet = new TypedBasicBulletType(DamageType.physical, 3.5f, 50){{
            width = 9f; height = 11f; lifetime = 38f;
            ammoMultiplier = 2f;
            reloadMultiplier = 1.5f;   // 重弹头：更慢
            knockback = 3f;
            hitEffect = despawnEffect = Fx.hitBulletColor;
            hitColor = backColor = trailColor = c("787c84");
            frontColor = Color.white;
        }};

        leadCoreBullet = new TypedBasicBulletType(DamageType.physical, 6f, 30){{
            width = 6f; height = 10f; lifetime = 50f;
            pierce = true; pierceCap = 2;
            ammoMultiplier = 3f;
            hitEffect = despawnEffect = Fx.hitBulletColor;
            hitColor = backColor = trailColor = c("808492");
            frontColor = Color.white;
        }};

        tiPierceBullet = new TypedBasicBulletType(DamageType.physical, 7.5f, 42){{
            width = 6f; height = 11f; lifetime = 46f;
            pierce = true; pierceCap = 4; pierceBuilding = true;
            ammoMultiplier = 5f;
            hitEffect = despawnEffect = Fx.hitBulletColor;
            hitColor = backColor = trailColor = c("92b0c8");
            frontColor = Color.white;
        }};

        tiOverpressureBullet = new TypedBasicBulletType(DamageType.physical, 5f, 90){{
            width = 8f; height = 12f; lifetime = 44f;
            splashDamage = 40f; splashDamageRadius = 26f;
            ammoMultiplier = 8f;
            hitEffect = despawnEffect = Fx.hitBulletColor;
            hitColor = backColor = trailColor = c("d2be8c");
            frontColor = Color.white;
        }};

        // 凝固汽油弹：命中点燃地面 + 持续燃烧
        napalmBullet = new TypedBasicBulletType(DamageType.fire, 4f, 38){{
            width = 9f; height = 12f; lifetime = 62f;
            ammoMultiplier = 4f;
            splashDamage = 34f; splashDamageRadius = 32f;
            incendAmount = 14; incendSpread = 12f; incendChance = 1f;
            status = StatusEffects.burning;
            statusDuration = 300f;
            hitEffect = despawnEffect = Fx.hitBulletColor;
            hitColor = backColor = trailColor = c("ff8a3c");
            frontColor = c("ffd08a");
        }};

        // 腐蚀弹：直接伤害不高，但命中后挂「腐蚀」——破甲持续伤害 + 迟缓
        corrosiveBullet = new TypedBasicBulletType(DamageType.corrosive, 6.5f, 30){{
            width = 7f; height = 11f; lifetime = 50f;
            ammoMultiplier = 3f;
            status = IronworksStatus.corrosion;
            statusDuration = 300f;
            hitEffect = despawnEffect = Fx.hitBulletColor;
            hitColor = backColor = trailColor = c("8fbf5a");
            frontColor = c("d6f0a8");
        }};

        // ── 速射炮：射速 + 伤害，短射程 ──
        rapidCannon = new CoolantTurret("rapid-cannon"){{
            requirements(Category.turret,
                with(IronworksMaterials.copperPlate, 40, Items.copper, 120, Items.lead, 80));
            size = 2;
            range = 160f;
            reload = 8f;
            inaccuracy = 2f;
            rotateSpeed = 6f;
            shootCone = 12f;
            heatColor = c("ffb04a");
            recoil = 1.5f;

            // 循环冷却：冷液进、热液出
            coolantLiquid = IronworksLiquids.grapheneCoolant;
            hotLiquid = IronworksLiquids.hotCoolant;
            coolantUse = 0.03f;
            coolantBoost = 1.7f;

            ammo(
                IronworksAmmo.copperAmmo, copperBullet,
                IronworksAmmo.heavyAmmo, heavyBullet
            );
        }};

        // ── 穿甲炮：射程 + 伤害，慢 ──
        piercer = new CoolantTurret("piercer"){{
            requirements(Category.turret,
                with(IronworksItems.steelPlate, 60, Items.titanium, 120, Items.silicon, 80, IronworksMaterials.castIron, 30));
            size = 3;
            range = 300f;
            reload = 25f;
            inaccuracy = 0.5f;
            rotateSpeed = 2.5f;
            shootCone = 6f;
            heatColor = c("92b0c8");
            recoil = 3f;

            coolantLiquid = IronworksLiquids.grapheneCoolant;
            hotLiquid = IronworksLiquids.hotCoolant;
            coolantUse = 0.06f;
            coolantBoost = 1.8f;

            ammo(
                IronworksAmmo.leadCoreAmmo, leadCoreBullet,
                IronworksAmmo.tiPierceAmmo, tiPierceBullet,
                IronworksAmmo.tiOverpressureAmmo, tiOverpressureBullet,
                IronworksAmmo.napalmAmmo, napalmBullet,
                IronworksAmmo.corrosiveAmmo, corrosiveBullet
            );
        }};
    }
}
