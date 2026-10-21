package ironworks;

import arc.graphics.Color;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.StatusEffects;
import mindustry.entities.bullet.ArtilleryBulletType;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.entities.bullet.BulletType;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.world.Block;
import ironworks.multicraft.MultiCrafter;
import ironworks.multicraft.Recipe;

import static mindustry.type.ItemStack.with;

/**
 * 大口径炮弹与迫击炮。
 *
 * 迫击炮是曲射武器，只能吃这里产的大口径炮弹，不能装填通用子弹。
 * 炮弹组装室一次供四种弹：高爆 / 穿甲 / 燃烧 / 破片。
 */
public class IronworksShells{

    public static Item heShell, apShell, incendiaryShell, fragShell;
    public static Block shellAssembly, mortar;

    static BulletType heBullet, apBullet, incendiaryBullet, fragBullet;

    public static void load(){
        heShell        = new Item("he-shell",        Color.valueOf("ff9a4d")){{ cost = 3.0f; hardness = 2; }};
        apShell        = new Item("ap-shell",        Color.valueOf("9fc4e0")){{ cost = 3.6f; hardness = 3; }};
        incendiaryShell= new Item("incendiary-shell",Color.valueOf("ff7a3d")){{ cost = 3.2f; hardness = 2; }};
        fragShell      = new Item("frag-shell",      Color.valueOf("c9c08a")){{ cost = 3.0f; hardness = 2; }};

        // ── 弹道（曲射）──
        heBullet = new ArtilleryBulletType(4f, 150){{
            width = 13f; height = 15f; lifetime = 140f;
            ammoMultiplier = 1f;
            splashDamage = 195f; splashDamageRadius = 110f;
            hitEffect = despawnEffect = Fx.hitBulletColor;
            hitColor = backColor = trailColor = Color.valueOf("ff9a4d");
            frontColor = Color.white;
        }};

        apBullet = new ArtilleryBulletType(6f, 260){{
            width = 11f; height = 17f; lifetime = 130f;
            ammoMultiplier = 1f;
            pierce = true; pierceCap = 3; pierceBuilding = true;
            splashDamage = 60f; splashDamageRadius = 50f;
            hitEffect = despawnEffect = Fx.hitBulletColor;
            hitColor = backColor = trailColor = Color.valueOf("9fc4e0");
            frontColor = Color.white;
        }};

        incendiaryBullet = new ArtilleryBulletType(4f, 110){{
            width = 13f; height = 15f; lifetime = 140f;
            ammoMultiplier = 2f;
            splashDamage = 105f; splashDamageRadius = 115f;
            incendAmount = 24; incendSpread = 16f; incendChance = 1f;
            status = StatusEffects.burning; statusDuration = 480f;
            hitEffect = despawnEffect = Fx.hitBulletColor;
            hitColor = backColor = trailColor = Color.valueOf("ff7a3d");
            frontColor = Color.valueOf("ffd08a");
        }};

        fragBullet = new ArtilleryBulletType(4.5f, 90){{
            width = 12f; height = 14f; lifetime = 135f;
            ammoMultiplier = 2f;
            splashDamage = 75f; splashDamageRadius = 125f;
            fragBullets = 6;
            fragBullet = new BasicBulletType(4.5f, 30){{
                width = 5f; height = 8f; lifetime = 18f;
                hitEffect = despawnEffect = Fx.hitBulletColor;
                hitColor = backColor = trailColor = Color.valueOf("c9c08a");
                frontColor = Color.white;
            }};
            hitEffect = despawnEffect = Fx.hitBulletColor;
            hitColor = backColor = trailColor = Color.valueOf("c9c08a");
            frontColor = Color.white;
        }};

        // ── 炮弹组装室：四种大口径弹 ──
        shellAssembly = new MultiCrafter("shell-assembly"){{
            requirements(Category.crafting,
                with(Items.titanium, 140, Items.silicon, 90, Items.metaglass, 70,
                     IronworksMaterials.ironPlate, 40));
            size = 3;
            itemCapacity = 40;
            recipes = new Recipe[]{
                new Recipe("he-shell", 110f)
                    .items(with(IronworksMaterials.casing, 1, IronworksMaterials.blackPowder, 2,
                                IronworksMaterials.ironPlate, 2))
                    .power(4f)
                    .output(with(heShell, 2)),

                new Recipe("ap-shell", 120f)
                    .items(with(IronworksMaterials.casing, 1, IronworksMaterials.blackPowder, 1,
                                IronworksMaterials.titaniumPlate, 2, IronworksMaterials.ironPlate, 1))
                    .power(4f)
                    .output(with(apShell, 2)),

                new Recipe("incendiary-shell", 110f)
                    .items(with(IronworksMaterials.casing, 1, IronworksMaterials.blackPowder, 1,
                                IronworksMaterials.ironPlate, 2))
                    .liquids(new mindustry.type.LiquidStack(mindustry.content.Liquids.oil, 6f))
                    .power(4f)
                    .output(with(incendiaryShell, 2)),

                new Recipe("frag-shell", 110f)
                    .items(with(IronworksMaterials.casing, 1, IronworksMaterials.blackPowder, 2,
                                IronworksMaterials.ironPlate, 1, IronworksMaterials.leadPlate, 1))
                    .power(4f)
                    .output(with(fragShell, 1))
            };
        }};

        // ── 迫击炮：最小射程 5 格，只吃大口径炮弹 ──
        mortar = new MortarTurret("mortar"){{
            requirements(Category.turret,
                with(IronworksItems.steelPlate, 80, IronworksMaterials.ironPlate, 60,
                     Items.titanium, 100, Items.silicon, 60));
            size = 3;
            range = 560f;      // 70 格
            minRange = 160f;   // 20 格盲区
            drawMinRange = true;
            reload = 60f;
            inaccuracy = 2f;
            rotateSpeed = 1.6f;
            shootCone = 8f;
            recoil = 4f;
            heatColor = Color.valueOf("ff9a4d");

            ammo(
                heShell, heBullet,
                apShell, apBullet,
                incendiaryShell, incendiaryBullet,
                fragShell, fragBullet
            );
        }};
    }
}
