package ironworks;

import arc.graphics.Color;
import mindustry.gen.Sounds;
import mindustry.content.StatusEffects;
import mindustry.type.Category;
import mindustry.world.Block;

import static mindustry.type.ItemStack.with;

/**
 * 天罚：电弧塔的升级版。
 *
 * 4x4（贴图 128）、射程 25 格、每秒 4096 电。
 * 四炮口轮流开火共用一条装填/过热槽；冷却液分级：
 * - 冷冻液：射速 1.25×，过热 -50%，叠层 9%/发，上限 135%
 * - 石墨烯冷却液：射速 1.45×，不过热，叠层 13%/发，上限 260%
 * 进水无效（只认这两种冷却液）。
 */
public class IronworksTianfa{

    public static TianfaTurret tianfa;

    public static void load(){
        tianfa = new TianfaTurret("tianfa"){{
            requirements(Category.turret,
                with(IronworksSilicon.advancedChip, 12, IronworksItems.steelPlate, 800,
                     mindustry.content.Items.silicon, 600, IronworksAluminum.aviationAluminum, 80,
                     IronworksSilicon.titaniumAlloy, 120));

            size = 4;
            range = 200f;              // 25 格
            reload = 40f;              // 1.5 发/秒
            shootCone = 30f;
            rotateSpeed = 2.2f;
            health = 6800;
            armor = 10f;
            consumePower(4096f);       // 每秒 4096 电

            shootSound = Sounds.shootArc;
            heatColor = Color.valueOf("8a7bff");
            heatEffect = mindustry.content.Fx.reactorsmoke;
            maxHeatEffectChance = 0.45f;

            liquidCapacity = 120f;
            coolantUse = 0.6f;
            coolantLiquids = new mindustry.type.Liquid[]{
                IronworksLiquids.chiller, IronworksLiquids.grapheneCoolant};
            coolantFireMult = new float[]{1.25f, 1.45f};
            coolantStackPerShot = new float[]{0.09f, 0.13f};
            coolantStackCap = new float[]{1.35f, 2.60f};
            coolantHeatMult = new float[]{0.50f, 0f};
            noCoolantStackCap = 0.75f;
            shotHeat = true;
            shotHeatAmount = 0.05f;
            coolOnlyWhenIdle = true;
            hotLiquid = null;
            hotLiquidTier = -1;

            // 直接结算炮塔也必须给一个 BulletType：
            // 原版详情页的"弹药"统计会读 BulletType.spawnUnit，null 会直接 NPE 崩游戏。
            shootType = new mindustry.entities.bullet.BulletType(0.001f, 370f){{
                lifetime = 1f;
            }};

            shotDamage = 370f;
            overloadDuration = 60f;
            chainTargets = 3;
            chainRange = 20f;          // 2.5 格
            boltScale = 0.6f;          // 闪电更细
        }};
    }
}
