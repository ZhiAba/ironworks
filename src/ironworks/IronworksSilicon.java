package ironworks;

import arc.graphics.Color;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.entities.bullet.BulletType;
import mindustry.entities.bullet.LaserBulletType;
import mindustry.content.StatusEffects;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.LiquidStack;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import ironworks.multicraft.MultiCrafter;
import ironworks.multicraft.Recipe;

import static mindustry.type.ItemStack.with;

/**
 * 硅基时代：贵金属、钛合金、三级芯片、电弧塔。
 */
public class IronworksSilicon{

    public static Item silver, gold, titaniumAlloy;
    public static Item basicChip, advancedChip, quantumCore;

    public static Block preciousLeach, chipFab, arcTower, railTower, singularityTower;

    public static void load(){
        silver       = new Item("silver",        Color.valueOf("c9d0d8")){{ cost = 3.0f; hardness = 3; }};
        gold         = new Item("gold",          Color.valueOf("ffd75e")){{ cost = 4.0f; hardness = 3; }};
        titaniumAlloy= new Item("titanium-alloy",Color.valueOf("9fb6c9")){{ cost = 4.5f; hardness = 4; }};
        basicChip    = new Item("basic-chip",    Color.valueOf("6fd49a")){{ cost = 5.0f; hardness = 4; }};
        advancedChip = new Item("advanced-chip", Color.valueOf("6fb6ff")){{ cost = 8.0f; hardness = 5; }};
        quantumCore  = new Item("quantum-core",  Color.valueOf("c48bff")){{ cost = 12f;  hardness = 5; }};

        // —— 贵金属浸出：铁精矿 + 酸 → 银 / 金 ——
        preciousLeach = new MultiCrafter("precious-leach"){{
            requirements(Category.crafting,
                with(IronworksItems.steelPlate, 60, Items.silicon, 80, Items.metaglass, 60));
            size = 2;
            itemCapacity = 20;
            liquidCapacity = 40f;
            recipes = new Recipe[]{
                new Recipe("silver", 60f)
                    .items(with(Items.scrap, 4))
                    .liquids(new LiquidStack(IronworksLiquids.acid, 6f))
                    .power(3f)
                    .output(with(silver, 1)),
                new Recipe("gold", 75f)
                    .items(with(Items.scrap, 6))
                    .liquids(new LiquidStack(IronworksLiquids.acid, 9f))
                    .power(5f)
                    .output(with(gold, 1))
            };
        }};

        // —— 芯片车间：三级芯片 ——
        chipFab = new MultiCrafter("chip-fab"){{
            requirements(Category.crafting,
                with(IronworksItems.steelPlate, 120, Items.silicon, 150, Items.metaglass, 100,
                     IronworksMaterials.ironPlate, 40));
            size = 3;
            itemCapacity = 30;
            liquidCapacity = 40f;
            recipes = new Recipe[]{
                new Recipe("basic-chip", 60f)
                    .items(with(Items.silicon, 2, IronworksMaterials.copperPlate, 1))
                    .liquids(new LiquidStack(IronworksLiquids.acid, 4f))
                    .power(3f)
                    .output(with(basicChip, 1)),
                new Recipe("advanced-chip", 90f)
                    .items(with(basicChip, 2, silver, 1, gold, 1, Items.plastanium, 1))
                    .power(5f)
                    .output(with(advancedChip, 1)),
                new Recipe("quantum-core", 120f)
                    .items(with(advancedChip, 1, IronworksMaterials.graphene, 2, titaniumAlloy, 1))
                    .power(15f)
                    .output(with(quantumCore, 1))
            };
        }};

        // —— 电弧塔：连锁闪电，耗电 ——
        arcTower = new OverheatTurret("arc-tower"){{
            requirements(Category.turret,
                with(basicChip, 2, IronworksItems.steelPlate, 20, Items.silicon, 25, Items.titanium, 20,
                     IronworksAluminum.aluminumSheet, 15));
            size = 2;
            range = 150f;
            reload = 90f;
            shootCone = 15f;
            rotateSpeed = 3f;
            heatColor = Color.valueOf("7a8cff");
            shootEffect = Fx.lightningShoot;
            shootSound = mindustry.gen.Sounds.shootArc;

            consumePower(18f);   // 18/tick ≈ 1080/s
            coolantLiquids = new mindustry.type.Liquid[]{
                mindustry.content.Liquids.water,
                mindustry.content.Liquids.cryofluid,
                IronworksLiquids.grapheneCoolant
            };
            coolantFireMult = new float[]{1.05f, 1.15f, 1.35f};
            coolantStackPerShot = new float[]{0.10f, 0.10f, 0.20f};
            coolantStackCap = new float[]{2.7f, 2.7f, 5.5f};
            hotLiquid = IronworksLiquids.hotCoolant;
            coolantUse = 0.82f;   // 约 49/s 的加热速率
            dryHeatPerSecond = 12f;
            overheatTime = 300f;
            overheatCoolAmount = 1.6f;
            maxSpeedupScl = 5.5f;
            speedupPerShoot = 0.15f;
            slowDownReloadTime = 90f;

            // 点对点：从炮口沿瞄准方向（索敌点）打出一条雷电线，而不是连锁弹跳
            // 不发射任何弹体：攻击由 OverheatTurret.shoot 直接结算
            shootType = new BulletType(0.001f, 35f){{
                lifetime = 1f;
                collides = false;
                hittable = false;
            }};
        }};

        // —— 磁轨穿刺炮（T2）：超远、穿透、充能慢 ——
        railTower = new RailTurret("rail-tower"){{
            requirements(Category.turret,
                with(advancedChip, 3, IronworksItems.steelPlate, 60, Items.silicon, 80,
                     IronworksSilicon.titaniumAlloy, 10, IronworksAluminum.aviationAluminum, 8));
            size = 3;
            range = 420f;
            reload = 180f;
            shootCone = 4f;
            rotateSpeed = 1.2f;
            heatColor = Color.valueOf("cfd8ff");
            consumePower(40f);

            shootType = new BulletType(0.001f, 240f){{
                lifetime = 1f;
            }};
        }};

        // —— 引力奇点发射器（T3）——
        singularityTower = new SingularityTurret("singularity-tower"){{
            requirements(Category.turret,
                with(IronworksSilicon.quantumCore, 5, IronworksItems.steelPlate, 120, Items.silicon, 200,
                     IronworksMaterials.siliconCarbide, 40, IronworksAluminum.aviationAluminum, 20,
                     IronworksAluminum.aluminumSheet, 40));
            size = 3;
            range = 640f;        // 80 格
            reload = 3900f;      // 65 秒
            canOverdrive = false;
            shootCone = 6f;
            rotateSpeed = 0.8f;
            heatColor = Color.valueOf("a06bff");
            consumePower(60f);

            // 详情页伤害显示用；实际攻击由 SingularityTurret 的引力场结算，不发射弹体
            shootType = new BulletType(0.001f, 680f){{
                lifetime = 1f;
                collides = false;
                hittable = false;
            }};
        }};
    }
}
