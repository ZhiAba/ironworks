package ironworks;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.ObjectIntMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.gen.Healthc;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.ctype.UnlockableContent;
import mindustry.type.Planet;
import mindustry.type.Sector;
import mindustry.type.UnitType;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.blocks.production.Drill;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import ironworks.multicraft.MultiCrafter;

import static mindustry.Vars.*;

/**
 * 铁流工业全局难度协议（T0-T7）。
 *
 * <p>这是“物理法则重写器”，不是简单的数值开关：</p>
 * <ul>
 *   <li>敌方单位：血量/伤害/波次间隔；低难度可减甲（T0/T1），高难度加甲（T6 +36、T7 +50），实际护甲下限 0。</li>
 *   <li>我方：建造速度、建筑生命上限、建筑护甲（可为负）、产能/采矿。</li>
 *   <li>跳弹：T3 起给敌方目标额外跳弹率，T7 +0.03。</li>
 *   <li>T7 词条：迫降、豆腐渣工程、资源匮乏、重工业；可重复叠加，只加难度。</li>
 * </ul>
 *
 * <p><b>核电站不参与难度修正</b>：按主人要求，RBMK 的跳闸、熔毁、水锤、
 * 注水泵与卡棒逻辑保持现状，本类不引用也不修改 {@link IronworksRBMK}。</p>
 */
public final class Difficulty{
    public static final int maxTier = 7;

    public static final String[] tierIds = {"t0", "t1", "t2", "t3", "t4", "t5", "t6", "t7"};
    public static final String[] tierNames = {"安逸", "苏醒", "整备", "警戒", "失谐", "共振", "梦魇", "凝视深渊"};

    /** 难度色：低难绿，高难红/深红，T7 故障紫黑。 */
    public static final Color[] tierColors = {
        Color.valueOf("69d27a"), Color.valueOf("63c6b0"), Color.valueOf("5aa7e8"),
        Color.valueOf("e0c35a"), Color.valueOf("e08b4a"), Color.valueOf("e05a4a"),
        Color.valueOf("9c2535"), Color.valueOf("9b59d0")
    };

    // ── 数值表：T0..T7 ──
    private static final float[] enemyHealth = {0.70f, 0.85f, 1.00f, 1.25f, 1.45f, 1.95f, 2.40f, 2.60f};
    private static final float[] enemyDamage = {0.70f, 0.85f, 1.00f, 1.15f, 1.35f, 1.75f, 2.10f, 2.35f};
    /** 波次间隔倍率；越小越密集。 */
    private static final float[] waveSpacing = {1.35f, 1.15f, 1.00f, 0.90f, 0.75f, 0.70f, 0.70f, 0.70f};
    private static final float[] playerBuildSpeed = {1.50f, 1.15f, 1.00f, 0.90f, 0.85f, 0.80f, 0.80f, 0.80f};
    /** 建筑生命上限倍率（直接缩放 Building.maxHealth）。 */
    private static final float[] playerBlockHealth = {1.35f, 1.20f, 1.00f, 0.85f, 0.75f, 0.65f, 0.60f, 0.60f};
    private static final float[] playerBlockArmor = {15.0f, 5.0f, 0.0f, -1.0f, -2.0f, -4.0f, -8.0f, -12.0f};
    private static final float[] production = {1.30f, 1.10f, 1.00f, 0.90f, 0.85f, 0.80f, 0.80f, 0.80f};
    private static final float[] ricochetBonus = {0.00f, 0.00f, 0.00f, 0.01f, 0.015f, 0.02f, 0.025f, 0.03f};
    /** 敌方单位/建筑绝对护甲修正；T0/T1 为负值，实际护甲仍下限 0。 */
    private static final float[] enemyArmorBonus = {-15f, -5f, 0f, 8f, 18f, 28f, 36f, 50f};

    // ── T7 词条 ──
    public static final String MOD_FORCED = "forced-landing";
    public static final String MOD_JERRY = "jerry-build";
    public static final String MOD_SCARCITY = "scarcity";
    public static final String MOD_HEAVY = "heavy-industry";
    public static final String MOD_ENEMY_ARMOR = "enemy-armor";
    public static final String MOD_PRECISION = "precision-ammo";
    public static final String MOD_LOW_ACCURACY = "low-accuracy";
    public static final String MOD_ELITE = "elite-units";
    public static final String MOD_RAID = "raid-offense";
    public static final String MOD_GROUP = "group-offense";
    public static final String[] modIds = {
        MOD_FORCED, MOD_JERRY, MOD_SCARCITY, MOD_HEAVY,
        MOD_ENEMY_ARMOR, MOD_PRECISION, MOD_LOW_ACCURACY,
        MOD_ELITE, MOD_RAID, MOD_GROUP
    };
    /** 全局最多 IV 级；不同词条的实际上限由 {@link #modifierMaxLevel(String)} 决定。 */
    public static final int maxModifierLevel = 4;

    // 旧词条：每级数值不相等，随等级提升而增大。
    private static final float[] forcedLossLevels = {0.20f, 0.35f, 0.50f};
    private static final float[] jerryHealthLevels = {0.06f, 0.15f, 0.26f};
    private static final float[] jerryArmorLevels = {1.0f, 2.5f, 4.5f};
    private static final float[] jerryBuildLevels = {0.08f, 0.18f, 0.30f};
    private static final float[] heavyCostLevels = {0.25f, 0.60f, 1.00f};
    private static final float[] heavyStatLevels = {0.10f, 0.25f, 0.40f};

    // 资源匮乏：绝对值扣除，四级全开时 0.05+0.10+0.15+0.30 = 0.60。
    private static final float[] scarcityPenaltyLevels = {0.05f, 0.10f, 0.15f, 0.30f};
    private static final float[] scarcityEnemyRefundLevels = {0.05f, 0.10f, 0.15f, 0.30f};
    private static final float[] scarcityPlayerRefundLevels = {0.05f, 0.05f, 0.10f, 0.20f};
    private static final float recoveryConversion = 0.70f;

    // 敌方精良装甲：护甲/穿甲抵抗是惩罚，减速和跳弹疲劳是可用“关闭收益”移除的补偿。
    private static final float[] enemyArmorAddLevels = {5f, 10f, 17f, 26f};
    private static final float[] enemySpeedSlowLevels = {0.03f, 0.05f, 0.08f, 0.12f};
    private static final float[] enemyPenResistLevels = {0.04f, 0.07f, 0.11f, 0.17f};
    private static final int[] fatigueThresholds = {1, 2, 4, 6, 8, 10};
    private static final float[] fatiguePercentLevels = {0.01f, 0.02f, 0.03f, 0.04f, 0.05f, 0.06f};

    // 精尖弹药：消耗/射速是惩罚，伤害/穿甲/穿透是收益。
    private static final float[] precisionAmmoLevels = {0.15f, 0.30f, 0.50f, 0.75f};
    private static final float[] precisionRateLevels = {0.08f, 0.16f, 0.25f, 0.38f};
    private static final float[] precisionDamageLevels = {0.12f, 0.25f, 0.40f, 0.60f};
    private static final float[] precisionPenRateLevels = {0.04f, 0.08f, 0.13f, 0.20f};
    private static final float[] precisionPenFlatLevels = {0.10f, 0.20f, 0.32f, 0.50f};

    // 低精度射击：误差是惩罚，射速是收益。
    private static final float[] lowAccuracyDegrees = {4f, 8f, 14f};
    private static final float[] lowAccuracyRateLevels = {0.10f, 0.22f, 0.38f};

    // 精锐部队：属性是惩罚，波次减量是收益。
    private static final float[] eliteHealthLevels = {0.15f, 0.30f};
    private static final float[] eliteDamageLevels = {0.12f, 0.25f};
    private static final float[] eliteArmorLevels = {8f, 18f};
    private static final float[] eliteResistanceLevels = {0.08f, 0.16f};
    /** 精英“抗穿透率”：I/II 级分别把受到的穿透率绝对值降低 10%/20%。 */
    private static final float[] elitePenResistLevels = {0.10f, 0.20f};
    private static final float[] eliteWaveReductionLevels = {0.08f, 0.15f};

    // 袭击式/团体式：数量与间隔按加法合并。
    private static final float[] raidWaveReductionLevels = {0.05f, 0.15f, 0.20f};
    private static final float[] raidSpacingReductionLevels = {0.10f, 0.20f, 0.30f};
    private static final float[] groupWaveIncreaseLevels = {0.15f, 0.25f, 0.35f};
    private static final float[] groupSpacingIncreaseLevels = {0.10f, 0.15f, 0.20f};

    private static int tier;
    private static final ObjectIntMap<String> modifiers = new ObjectIntMap<>();
    /** 按 modIds 下标的收益关闭位图。 */
    private static int benefitDisabledMask;
    private static boolean benefitControlsVisible;
    /** 记录开局拆除返还倍率，供关闭词条收益时恢复。 */
    private static float baseDeconstructRefund = -1f;
    private static final ObjectSet<mindustry.world.Tile> deconstructing = new ObjectSet<>();
    private static final ObjectIntMap<Healthc> armorFatigue = new ObjectIntMap<>();
    private static float baseWaveSpacing = -1f, baseInitialWaveSpacing = -1f;
    private static Object rulesRef;
    private static boolean initialized;
    /** SaveIO 写入前临时剥掉 DifficultySpawnGroup，避免 Json 解析模组类名失败。 */
    private static boolean spawnWrappersStripped;
    /**
     * 读档后首次应用难度规则。存档里的 health 是绝对血量（且已被原版按方块基础
     * 血量 clamp 过），此时不能再按“当前最大血量的比例”缩放，否则会二次掉血。
     */
    private static boolean loadingWorld;

    private Difficulty(){
    }

    public static void init(){
        if(initialized) return;
        initialized = true;
        loadPrefs();

        Events.on(EventType.WorldLoadEvent.class, e -> onWorldLoad());
        Events.on(EventType.WorldLoadEndEvent.class, e -> onWorldLoadEnd());
        Events.on(EventType.UnitCreateEvent.class, e -> onUnitCreate(e.unit));
        Events.on(EventType.UnitSpawnEvent.class, e -> onUnitCreate(e.unit));
        Events.on(EventType.UnitDestroyEvent.class, e -> onUnitDestroy(e.unit));
        Events.on(EventType.BlockBuildEndEvent.class, e -> {
            if(e.breaking){
                deconstructing.add(e.tile);
            }else{
                applyPlayerBlockHealth(e.tile.build);
            }
        });
        Events.on(EventType.BlockDestroyEvent.class, e -> onBlockDestroy(e.tile));
        // SaveIO.write 在序列化 Rules.spawns 前触发；自定义 SpawnGroup 子类
        // 会被 Json 写成 "ironworks.DifficultySpawnGroup"，而保存加载器不一定
        // 能解析模组类名（自定义地图退出保存即 ClassNotFound）。保存前换回
        // 原始 SpawnGroup，下一 tick 再重新包装。
        Events.on(EventType.SaveWriteEvent.class, e -> stripSpawnGroupsForSave());
        // 无头/服务器可能在 mod init 之前就完成 world load；用 update 兜底，
        // 只在 rules 实例变化时应用一次，不会每帧重算。
        Events.run(EventType.Trigger.update, Difficulty::ensureApplied);
        Events.on(EventType.ResetEvent.class, e -> {
            rulesRef = null;
            baseWaveSpacing = baseInitialWaveSpacing = -1f;
            baseDeconstructRefund = -1f;
            spawnWrappersStripped = false;
            armorFatigue.clear();
            deconstructing.clear();
        });
        IronworksTurretStats.apply();
    }

    // ────────────────────────── 对外查询 ──────────────────────────

    public static int tier(){ return tier; }
    public static boolean isMaxTier(){ return tier >= maxTier; }
    public static String tierName(int value){
        int v = clampTier(value);
        return Core.bundle == null ? tierNames[v] : Core.bundle.get("ironworks.difficulty.tier." + tierIds[v], tierNames[v]);
    }
    public static Color tierColor(int value){ return tierColors[clampTier(value)]; }

    public static float enemyHealthMul(){ return enemyHealthFor(tier); }

    public static float enemyDamageMul(){ return enemyDamageFor(tier); }

    public static float enemyArmorBonus(){ return enemyArmorFor(tier); }

    public static float ricochetBonus(){ return ricochetBonus[tier]; }

    /** 敌方元素抗性；精锐部队的“全元素抗性”是纯惩罚。 */
    public static float enemyElementResistance(){
        if(tier != maxTier) return 0f;
        return Math.min(0.60f, modifierSum(MOD_ELITE, eliteResistanceLevels));
    }

    /** 敌方受到攻击时，穿透率被绝对值扣减；不作用于能量形态。 */
    public static float enemyPenResistance(){
        if(tier != maxTier) return 0f;
        // 精良装甲与精英单位都会降低实际穿透率；按绝对值相加，不做乘法叠乘。
        return Math.min(0.75f, modifierSum(MOD_ENEMY_ARMOR, enemyPenResistLevels)
            + modifierSum(MOD_ELITE, elitePenResistLevels));
    }

    /** 精良装甲的减速是收益，可用关闭收益移除。 */
    public static float enemySpeedMultiplier(){
        if(tier != maxTier || !benefitEnabled(MOD_ENEMY_ARMOR)) return 1f;
        return Math.max(0.35f, 1f - modifierSum(MOD_ENEMY_ARMOR, enemySpeedSlowLevels));
    }

    public static float productionMultiplier(){ return productionFor(tier); }

    /** 采矿/钻机倍率；下限按数值表为 0.05。 */
    public static float miningMultiplier(){ return miningFor(tier); }

    public static float buildSpeedMultiplier(){ return buildSpeedFor(tier); }

    public static float playerBlockHealthMultiplier(){ return blockHealthFor(tier); }

    public static float playerBlockArmorModifier(){ return armorFor(tier); }

    public static float playerUnitCostMultiplier(){ return unitCostFor(tier); }

    public static float playerUnitStatMultiplier(){ return unitStatFor(tier); }

    /** 敌方单位造价回收比例（资源匮乏）；关闭收益后为 0。 */
    public static float enemyRefund(){
        if(tier != maxTier || !benefitEnabled(MOD_SCARCITY)) return 0f;
        return Math.min(0.60f, modifierSum(MOD_SCARCITY, scarcityEnemyRefundLevels));
    }

    /** 我方单位造价回收比例（资源匮乏）；关闭收益后为 0。 */
    public static float playerRefund(){
        if(tier != maxTier || !benefitEnabled(MOD_SCARCITY)) return 0f;
        return Math.min(0.40f, modifierSum(MOD_SCARCITY, scarcityPlayerRefundLevels));
    }

    /** 兼容旧调用：默认指敌方回收。 */
    public static float killRefund(){ return enemyRefund(); }

    /** 拆除全额返还是资源匮乏的收益，关闭后恢复原版倍率。 */
    public static boolean fullDeconstructRefund(){
        return tier == maxTier && modifierMask(MOD_SCARCITY) != 0 && benefitEnabled(MOD_SCARCITY);
    }

    /** 回收物资转化为上一级材料的比例；关闭收益后为 0。 */
    public static float recoveryConversion(){
        return tier == maxTier && modifierMask(MOD_SCARCITY) != 0 && benefitEnabled(MOD_SCARCITY) ? recoveryConversion : 0f;
    }

    /** 波次数量倍率：精英/袭击减量，团体增量，全部按加法合并。 */
    public static float waveCountMultiplier(){
        if(tier != maxTier) return 1f;
        float value = 1f;
        if(benefitEnabled(MOD_ELITE)) value -= modifierSum(MOD_ELITE, eliteWaveReductionLevels);
        if(benefitEnabled(MOD_RAID)) value -= modifierSum(MOD_RAID, raidWaveReductionLevels);
        value += modifierSum(MOD_GROUP, groupWaveIncreaseLevels);
        return Math.max(0.10f, Math.min(5f, value));
    }

    /** 波次间隔：袭击缩短、团体延长，全部按加法合并；下限按数值表为 0.4。 */
    public static float waveSpacingMultiplier(){ return waveSpacingFor(tier); }

    /** 炮塔每发弹药倍率（精尖弹药惩罚）。 */
    public static float turretAmmoMultiplier(){
        if(tier != maxTier) return 1f;
        return 1f + modifierSum(MOD_PRECISION, precisionAmmoLevels);
    }

    /** 炮塔射速倍率：精尖降低，低精度射击提高；用于装填时间倒数。 */
    public static float turretFireRateMultiplier(){
        if(tier != maxTier) return 1f;
        float value = 1f - modifierSum(MOD_PRECISION, precisionRateLevels);
        if(benefitEnabled(MOD_LOW_ACCURACY)) value += modifierSum(MOD_LOW_ACCURACY, lowAccuracyRateLevels);
        return Math.max(0.10f, Math.min(3f, value));
    }

    public static float turretInaccuracyBonus(){
        return tier == maxTier ? modifierSum(MOD_LOW_ACCURACY, lowAccuracyDegrees) : 0f;
    }

    /** 精尖弹药的伤害收益；关闭收益后为 1。 */
    public static float precisionDamageMultiplier(){
        return tier == maxTier && benefitEnabled(MOD_PRECISION) ? 1f + modifierSum(MOD_PRECISION, precisionDamageLevels) : 1f;
    }

    public static float precisionPenRate(){
        return tier == maxTier && benefitEnabled(MOD_PRECISION) ? modifierSum(MOD_PRECISION, precisionPenRateLevels) : 0f;
    }

    public static float precisionPenFlatMultiplier(){
        return tier == maxTier && benefitEnabled(MOD_PRECISION) ? 1f + modifierSum(MOD_PRECISION, precisionPenFlatLevels) : 1f;
    }

    /** 给玩家炮塔弹体叠加穿甲收益；返回原对象以外的唯一新对象。 */
    public static ArmorPen precisionPen(ArmorPen source){
        if(source == null || (precisionPenRate() <= 0f && precisionPenFlatMultiplier() <= 1f)) return source;
        return new ArmorPen(Math.min(1f, source.rate + precisionPenRate()),
            source.flat * precisionPenFlatMultiplier(), source.decay, source.ricochet);
    }

    /** 精良装甲的跳弹疲劳：总等级决定最大层数，层数决定护甲百分比削减。 */
    public static int enemyArmorFatigueMaxStacks(){
        if(tier != maxTier || !benefitEnabled(MOD_ENEMY_ARMOR)) return 0;
        int total = modifierTotalLevel(MOD_ENEMY_ARMOR);
        int stacks = 0;
        for(int threshold : fatigueThresholds) if(total >= threshold) stacks++;
        return stacks;
    }

    public static int enemyArmorFatigueStacks(Healthc target){
        return target == null ? 0 : armorFatigue.get(target, 0);
    }

    public static float enemyArmorFatiguePercent(Healthc target){
        if(enemyArmorFatigueMaxStacks() <= 0) return 0f;
        int stacks = Math.min(enemyArmorFatigueStacks(target), Math.min(enemyArmorFatigueMaxStacks(), fatiguePercentLevels.length));
        float sum = 0f;
        for(int i = 0; i < stacks; i++) sum += fatiguePercentLevels[i];
        return sum;
    }

    public static void onEnemyRicochet(Healthc target){
        if(target == null || tier != maxTier || !benefitEnabled(MOD_ENEMY_ARMOR)) return;
        int max = enemyArmorFatigueMaxStacks();
        if(max <= 0) return;
        int stacks = armorFatigue.get(target, 0);
        if(stacks < max) armorFatigue.put(target, stacks + 1);
    }

    /** 迫降损失的开局资源比例；仅 T7 且本局尚未应用。 */    /** 迫降损失的开局资源比例；仅 T7 且本局尚未应用。 */
    public static float forcedLandingLoss(){
        if(tier != maxTier) return 0f;
        return Math.min(0.90f, modifierSum(MOD_FORCED, forcedLossLevels));
    }

    private static float modifierSum(String id, float[] perLevel){
        return ModifierMath.sum(modifierMask(id), perLevel);
    }

    /** UI 预览：按指定档位生成详细数值说明，包含已启用词条的实时效果。 */
    public static String describe(int value){
        value = clampTier(value);
        int forced = value == maxTier ? Math.round(forcedLandingLoss() * 100f) : 0;
        int refund = value == maxTier ? Math.round(enemyRefund() * 100f) : 0;
        float buildSpeed = buildSpeedFor(value);
        // 速度倍率和耗时倍率同时显示，避免把“建造 ×0.8”误读成 0.8 秒/0.8 倍耗时。
        String buildText = num3(buildSpeed) + "（耗时 ×" + num3(1f / Math.max(0.05f, buildSpeed)) + "）";
        String base = Core.bundle.format("ironworks.difficulty.detail",
            num(enemyHealthFor(value)), num(enemyDamageFor(value)), signed((int)enemyArmorFor(value)),
            num(ricochetBonus[value]), num4(waveSpacingFor(value)),
            buildText, num3(blockHealthFor(value)), num(armorFor(value)),
            num(unitCostFor(value)), num(unitStatFor(value)), num(productionFor(value)), num(miningFor(value)),
            String.valueOf(forced), String.valueOf(refund), activeModifierSummary(value));
        String extra = Core.bundle.format("ironworks.difficulty.detail.extra",
            Math.round(enemyArmorFatigueMaxPercent() * 100f), enemyArmorFatigueMaxStacks(),
            Math.round((1f - enemySpeedMultiplier()) * 100f), Math.round(enemyPenResistance() * 100f),
            Math.round(enemyElementResistance() * 100f), num(waveCountFor(value)), num4(waveSpacingFor(value)),
            num(turretAmmoMultiplier()), num(turretFireRateMultiplier()), Math.round(turretInaccuracyBonus()),
            num(precisionDamageMultiplier()), Math.round(precisionPenRate() * 100f),
            num(precisionPenFlatMultiplier()),
            Math.round((value == maxTier ? enemyRefund() : 0f) * 100f),
            Math.round((value == maxTier ? playerRefund() : 0f) * 100f),
            fullDeconstructRefund() ? "100%" : "原版",
            Math.round(recoveryConversion() * 100f), benefitState());
        return base + "\n" + extra;
    }

    private static float enemyHealthFor(int value){
        float result = enemyHealth[value];
        if(value == maxTier) result = ModifierMath.add(result, modifierMask(MOD_ELITE), eliteHealthLevels);
        return result;
    }

    private static float enemyDamageFor(int value){
        float result = enemyDamage[value];
        if(value == maxTier) result = ModifierMath.add(result, modifierMask(MOD_ELITE), eliteDamageLevels);
        return result;
    }

    private static float enemyArmorFor(int value){
        float result = enemyArmorBonus[value];
        if(value == maxTier){
            result = ModifierMath.add(result, modifierMask(MOD_ENEMY_ARMOR), enemyArmorAddLevels);
            result = ModifierMath.add(result, modifierMask(MOD_ELITE), eliteArmorLevels);
        }
        return result;
    }

    private static float waveCountFor(int value){
        return value == maxTier ? waveCountMultiplier() : 1f;
    }

    private static float waveSpacingFor(int value){
        float result = waveSpacing[value];
        if(value == maxTier){
            float bonus = benefitEnabled(MOD_GROUP) ? modifierSum(MOD_GROUP, groupSpacingIncreaseLevels) : 0f;
            float penalty = modifierSum(MOD_RAID, raidSpacingReductionLevels);
            result *= Math.max(0.20f, Math.min(3f, 1f + bonus - penalty));
        }
        return Math.max(0.4f, result);
    }

    private static float miningFor(int value){
        float result = production[value];
        if(value == maxTier) result -= modifierSum(MOD_SCARCITY, scarcityPenaltyLevels);
        return Math.max(0.05f, result);
    }

    private static float unitCostFor(int value){
        return value == maxTier ? ModifierMath.add(1f, modifierMask(MOD_HEAVY), heavyCostLevels) : 1f;
    }

    private static float unitStatFor(int value){
        return value == maxTier && benefitEnabled(MOD_HEAVY) ? ModifierMath.add(1f, modifierMask(MOD_HEAVY), heavyStatLevels) : 1f;
    }

    private static String activeModifierSummary(int value){
        return value == maxTier && tier == maxTier ? modifierSummary() : (Core.bundle == null ? "无词条" : Core.bundle.get("ironworks.difficulty.no-mods", "无词条"));
    }

    private static float productionFor(int value){
        float result = production[value];
        if(value == maxTier) result -= modifierSum(MOD_SCARCITY, scarcityPenaltyLevels);
        return Math.max(0.20f, result);
    }

    private static float buildSpeedFor(int value){
        float result = playerBuildSpeed[value];
        if(value == maxTier && benefitEnabled(MOD_JERRY)) result = ModifierMath.scale(result, modifierMask(MOD_JERRY), jerryBuildLevels);
        return Math.max(0.25f, result);
    }

    private static float blockHealthFor(int value){
        float result = playerBlockHealth[value];
        if(value == maxTier) result *= Math.max(0.55f, 1f - modifierSum(MOD_JERRY, jerryHealthLevels));
        return result;
    }

    private static float armorFor(int value){
        float result = playerBlockArmor[value];
        if(value == maxTier) result = ModifierMath.subtract(result, modifierMask(MOD_JERRY), jerryArmorLevels);
        return result;
    }

    /** 疲劳总值的预览上限；实际按目标已叠层数计算。 */
    public static float enemyArmorFatigueMaxPercent(){
        if(tier != maxTier || !benefitEnabled(MOD_ENEMY_ARMOR)) return 0f;
        int stacks = enemyArmorFatigueMaxStacks();
        float sum = 0f;
        for(int i = 0; i < stacks && i < fatiguePercentLevels.length; i++) sum += fatiguePercentLevels[i];
        return sum;
    }

    /** 当前收益开关状态，供详情页显示。 */
    public static String benefitState(){
        if(!benefitControlsVisible) return Core.bundle == null ? "默认" : Core.bundle.get("ironworks.difficulty.benefit.default", "默认");
        int selected = 0, disabled = 0;
        for(String id : modIds){
            if(modifierMask(id) == 0 || !hasBenefit(id)) continue;
            selected++;
            if(!benefitEnabled(id)) disabled++;
        }
        if(disabled == 0) return Core.bundle == null ? "全开" : Core.bundle.get("ironworks.difficulty.benefit.all-on", "全开");
        if(disabled == selected) return Core.bundle == null ? "全关" : Core.bundle.get("ironworks.difficulty.benefit.all-off", "全关");
        return Core.bundle == null ? "部分关闭" : Core.bundle.get("ironworks.difficulty.benefit.partial", "部分关闭");
    }

    private static int maskBits(){
        return (1 << maxModifierLevel) - 1;
    }

    /** 每个词条自己的等级上限；旧词条保持 3 级，新词条按需求到 4 级或 2 级。 */
    public static int modifierMaxLevel(String id){
        if(MOD_ENEMY_ARMOR.equals(id) || MOD_SCARCITY.equals(id) || MOD_PRECISION.equals(id)) return 4;
        if(MOD_ELITE.equals(id)) return 2;
        return 3;
    }

    /** 已启用等级的代数和；精良装甲疲劳按这个总等级计算。 */
    public static int modifierTotalLevel(String id){
        int mask = modifierMask(id), total = 0;
        for(int i = 1; i <= modifierMaxLevel(id); i++){
            if((mask & (1 << (i - 1))) != 0) total += i;
        }
        return total;
    }

    private static int modIndex(String id){
        for(int i = 0; i < modIds.length; i++) if(modIds[i].equals(id)) return i;
        return -1;
    }

    /** 这个词条是否拥有“可关闭的收益”。 */
    public static boolean hasBenefit(String id){
        return !MOD_FORCED.equals(id);
    }

    public static boolean benefitControlsVisible(){
        return benefitControlsVisible;
    }

    public static boolean benefitEnabled(String id){
        if(id == null || !hasBenefit(id) || !benefitControlsVisible) return true;
        int index = modIndex(id);
        return index < 0 || (benefitDisabledMask & (1 << index)) == 0;
    }

    /** 开启/恢复某个词条的收益；只影响该词条。 */
    public static void setBenefitEnabled(String id, boolean enabled){
        int index = modIndex(id);
        if(index < 0 || !hasBenefit(id)) return;
        int bit = 1 << index;
        if(enabled) benefitDisabledMask &= ~bit;
        else benefitDisabledMask |= bit;
        savePrefs();
        syncTags();
        applyRules();
        IronworksTurretStats.apply();
    }

    public static void setBenefitControlsVisible(boolean visible){
        benefitControlsVisible = visible;
        savePrefs();
        syncTags();
    }

    /** “关闭词条收益”：开启后把当前所有可选收益关闭，并显示逐词条开关。 */
    public static void disableAllBenefits(){
        benefitControlsVisible = true;
        benefitDisabledMask = 0;
        for(String id : modIds){
            if(modifierMask(id) != 0 && hasBenefit(id)){
                int index = modIndex(id);
                if(index >= 0) benefitDisabledMask |= 1 << index;
            }
        }
        savePrefs();
        syncTags();
        applyRules();
        IronworksTurretStats.apply();
    }

    /** 已启用等级的位掩码；第 1 级是 bit0。 */
    public static int modifierMask(String id){
        return modifiers.get(id, 0) & maskBits();
    }

    public static boolean hasModifierLevel(String id, int level){
        return level > 0 && level <= modifierMaxLevel(id) && (modifierMask(id) & (1 << (level - 1))) != 0;
    }

    /** 兼容查询：返回已启用的最高等级。 */
    public static int modifierLevel(String id){
        int mask = modifierMask(id);
        return mask == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(mask);
    }

    public static void toggleModifierLevel(String id, int level){
        if(tier != maxTier || id == null || level <= 0 || level > modifierMaxLevel(id)) return;
        int mask = modifierMask(id);
        int bit = 1 << (level - 1);
        boolean wasEmpty = mask == 0;
        mask ^= bit;
        if(mask == 0) modifiers.remove(id);
        else modifiers.put(id, mask);
        // 已开启“关闭词条收益”时，新选中的词条默认关闭收益；给同一词条补等级不重置原开关。
        if(wasEmpty && benefitControlsVisible && hasBenefit(id)){
            int index = modIndex(id);
            if(index >= 0) benefitDisabledMask |= 1 << index;
        }
        setModifiersFromUi();
    }

    public static String modifierSummary(){
        StringBuilder sb = new StringBuilder();
        for(String id : modIds){
            int mask = modifierMask(id);
            if(mask == 0) continue;
            if(sb.length() > 0) sb.append(" / ");
            sb.append(modName(id)).append(' ');
            boolean first = true;
            for(int i = 1; i <= modifierMaxLevel(id); i++){
                if((mask & (1 << (i - 1))) == 0) continue;
                if(!first) sb.append('+');
                sb.append(romanLevel(i));
                first = false;
            }
        }
        return sb.length() == 0 ? (Core.bundle == null ? "无词条" : Core.bundle.get("ironworks.difficulty.no-mods", "无词条")) : sb.toString();
    }

    private static String romanLevel(int value){
        return switch(value){
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> String.valueOf(value);
        };
    }

    public static String modName(String id){
        return Core.bundle == null ? id : Core.bundle.get("ironworks.difficulty.mod." + id + ".name", id);
    }

    public static String modDesc(String id){
        return Core.bundle == null ? "" : Core.bundle.get("ironworks.difficulty.mod." + id + ".desc", "");
    }

    /** 当前是否应把目标视为“敌方”，用于护甲上浮/跳弹加成。 */
    public static boolean isEnemyTarget(Healthc target){
        if(state == null || !state.isGame() || state.rules == null) return false;
        Team player = state.rules.defaultTeam;
        if(player == null) return false;
        // 不只看 waveTeam：玩家可以把自己的单位切换成红队，或其他阵营的
        // 单位也可能出现在地图上；只要不是默认玩家队伍，就按敌方处理。
        if(target instanceof Unit unit) return unit.team != null && unit.team != player;
        if(target instanceof Building build) return build.team != null && build.team != player;
        return false;
    }

    public static boolean isPlayerTeam(Team team){
        return state != null && state.isGame() && state.rules != null && team != null && team == state.rules.defaultTeam;
    }

    // ────────────────────────── 应用/持久化 ──────────────────────────

    /** 只在无受袭区块时由对话框调用；这里不主动弹 UI。 */
    public static void setTier(int value){
        tier = clampTier(value);
        syncTags();
        savePrefs();
        applyRules();
        IronworksDrills.apply();
        IronworksTurretStats.apply();
    }

    public static void setModifiersFromUi(){
        savePrefs();
        syncTags();
        applyRules();
        IronworksDrills.apply();
        IronworksTurretStats.apply();
    }

    public static void addModifier(String id){
        if(tier != maxTier || id == null) return;
        int mask = modifierMask(id);
        for(int level = 1; level <= modifierMaxLevel(id); level++){
            int bit = 1 << (level - 1);
            if((mask & bit) == 0){
                boolean wasEmpty = mask == 0;
                modifiers.put(id, mask | bit);
                if(wasEmpty && benefitControlsVisible && hasBenefit(id)){
                    int index = modIndex(id);
                    if(index >= 0) benefitDisabledMask |= 1 << index;
                }
                setModifiersFromUi();
                return;
            }
        }
    }

    /** 只启用指定等级；0 表示移除该词条。独立按钮应使用 toggleModifierLevel。 */
    public static void setModifierLevel(String id, int level){
        if(tier != maxTier || id == null) return;
        if(level <= 0 || level > modifierMaxLevel(id)) modifiers.remove(id);
        else modifiers.put(id, 1 << (level - 1));
        setModifiersFromUi();
    }

    public static void clearModifiers(){
        modifiers.clear();
        setModifiersFromUi();
    }

    private static void loadPrefs(){
        if(Core.settings == null) return;
        tier = clampTier(Core.settings.getInt("ironworks.difficulty.tier", 1));
        parseModifiers(Core.settings.getString("ironworks.difficulty.mods", ""));
        benefitDisabledMask = Core.settings.getInt("ironworks.difficulty.benefit-disabled", 0);
        benefitControlsVisible = Core.settings.getBool("ironworks.difficulty.benefit-controls", false);
    }

    private static void savePrefs(){
        if(Core.settings == null) return;
        Core.settings.putInt("ironworks.difficulty.tier", tier);
        Core.settings.put("ironworks.difficulty.mods", modifierString());
        Core.settings.putInt("ironworks.difficulty.benefit-disabled", benefitDisabledMask);
        Core.settings.put("ironworks.difficulty.benefit-controls", benefitControlsVisible);
    }

    /** 兜底：WorldLoadEvent 注册晚于世界加载时，下一 tick 补应用。 */
    private static void ensureApplied(){
        if(state == null || state.rules == null) return;
        if(rulesRef != state.rules){
            onWorldLoad();
        }
        if(spawnWrappersStripped){
            spawnWrappersStripped = false;
            wrapSpawnGroups();
        }
        applyForcedLanding();
    }

    private static void onWorldLoad(){
        if(state == null || state.rules == null) return;
        if(rulesRef != state.rules){
            rulesRef = state.rules;
            baseWaveSpacing = -1f;
            baseInitialWaveSpacing = -1f;
            baseDeconstructRefund = -1f;
            spawnWrappersStripped = false;
            armorFatigue.clear();
            deconstructing.clear();
        }

        String savedTier = state.rules.tags.get("ironworks.difficulty");
        if(savedTier != null){
            try{ tier = clampTier(Integer.parseInt(savedTier)); }catch(Exception ignored){}
        }

        String savedMods = state.rules.tags.get("ironworks.difficulty.mods");
        if(savedMods != null) parseModifiers(savedMods);
        String savedBenefits = state.rules.tags.get("ironworks.difficulty.benefit-disabled");
        if(savedBenefits != null){
            try{ benefitDisabledMask = Integer.parseInt(savedBenefits); }catch(Exception ignored){}
        }
        String savedBenefitControls = state.rules.tags.get("ironworks.difficulty.benefit-controls");
        if(savedBenefitControls != null) benefitControlsVisible = Boolean.parseBoolean(savedBenefitControls);

        String base = state.rules.tags.get("ironworks.base-wave-spacing");
        if(base != null && baseWaveSpacing <= 0f){
            try{ baseWaveSpacing = Float.parseFloat(base); }catch(Exception ignored){}
        }
        if(baseWaveSpacing <= 0f){
            baseWaveSpacing = Math.max(1f, state.rules.waveSpacing);
            state.rules.tags.put("ironworks.base-wave-spacing", String.valueOf(baseWaveSpacing));
        }
        if(baseInitialWaveSpacing <= 0f){
            baseInitialWaveSpacing = Math.max(0f, state.rules.initialWaveSpacing);
            state.rules.tags.put("ironworks.base-initial-wave", String.valueOf(baseInitialWaveSpacing));
        }
        String baseDec = state.rules.tags.get("ironworks.base-deconstruct-refund");
        if(baseDec != null){
            try{ baseDeconstructRefund = Float.parseFloat(baseDec); }catch(Exception ignored){}
        }
        if(baseDeconstructRefund < 0f){
            // 旧存档可能已经被“满额返还”写成 1；没有基础值标签时按原版 0.5 处理。
            baseDeconstructRefund = Math.abs(state.rules.deconstructRefundMultiplier - 1f) < 0.001f
                ? 0.5f : state.rules.deconstructRefundMultiplier;
            state.rules.tags.put("ironworks.base-deconstruct-refund", String.valueOf(baseDeconstructRefund));
        }

        loadingWorld = true;
        try{
            applyRules();
        }finally{
            loadingWorld = false;
        }
        // 没有迫降词条时立刻标记“开局结算已过”，避免中途加 T7 词条时倒扣已有资源。
        if(forcedLandingLoss() <= 0f){
            state.rules.tags.put("ironworks.forced-landing-applied", "1");
        }
    }

    private static void onWorldLoadEnd(){
        if(state == null || state.rules == null) return;
        // 此时地图里已经读回了所有建筑，health 是存档里的绝对血量。
        loadingWorld = true;
        try{
            applyRules();
        }finally{
            loadingWorld = false;
        }
        applyForcedLanding();
    }

    public static void applyRules(){
        if(state == null || state.rules == null) return;
        // 战役旧档可能还存着原版五档难度；把它归一为 normal，防止与 T0-T7 双重缩放。
        if(state.isCampaign() && state.getPlanet() != null && state.getPlanet().allowCampaignRules
            && state.getPlanet().campaignRules != null
            && state.getPlanet().campaignRules.difficulty != mindustry.game.Difficulty.normal){
            state.getPlanet().campaignRules.difficulty = mindustry.game.Difficulty.normal;
            state.getPlanet().saveRules();
        }
        Team player = state.rules.defaultTeam, enemy = state.rules.waveTeam;

        if(player != null){
            var r = state.rules.teams.get(player);
            // 难度表只定义“建造速度”；单位工厂的生产速度不应被同一个倍率隐式拖慢，
            // 否则详情页的计划时间会和实际产速对不上。工厂仍按原版速度生产。
            r.buildSpeedMultiplier = buildSpeedMultiplier();
            r.unitBuildSpeedMultiplier = 1f;
            r.unitMineSpeedMultiplier = miningMultiplier();
            // 建筑生命改为直接缩放 Building.maxHealth；不能再用 TeamRule 的受伤除数，
            // 否则会同时缩小上限又放大伤害，形成双重惩罚。
            r.blockHealthMultiplier = 1f;
            r.unitCostMultiplier = playerUnitCostMultiplier();
            if(tier == maxTier && modifierLevel(MOD_HEAVY) > 0){
                r.unitHealthMultiplier = playerUnitStatMultiplier();
                r.unitDamageMultiplier = playerUnitStatMultiplier();
            }else{
                r.unitHealthMultiplier = 1f;
                r.unitDamageMultiplier = 1f;
            }
        }

        if(enemy != null && enemy != player){
            var r = state.rules.teams.get(enemy);
            r.unitHealthMultiplier = enemyHealthMul();
            r.unitDamageMultiplier = enemyDamageMul();
            r.blockDamageMultiplier = enemyDamageMul();
            r.unitBuildSpeedMultiplier = 1f;
            r.blockHealthMultiplier = 1f;
        }

        if(baseWaveSpacing > 0f){
            state.rules.waveSpacing = Math.max(20f, baseWaveSpacing * waveSpacingMultiplier());
        }
        if(baseInitialWaveSpacing > 0f){
            state.rules.initialWaveSpacing = baseInitialWaveSpacing * waveSpacingMultiplier();
        }

        if(baseDeconstructRefund < 0f){
            baseDeconstructRefund = state.rules.deconstructRefundMultiplier;
        }
        state.rules.deconstructRefundMultiplier = fullDeconstructRefund() ? 1f : baseDeconstructRefund;

        applyPlayerBlockHealth();
        syncTags();
        IronworksDrills.apply();
        IronworksTurretStats.apply();
        wrapSpawnGroups();
        updateEnemySlowEffect();
    }

    /** 扫描所有玩家建筑，把最大生命同步到当前难度的实际值。 */
    private static void applyPlayerBlockHealth(){
        if(state == null || state.rules == null || world == null) return;
        Team player = state.rules.defaultTeam;
        if(player == null) return;

        // Groups.build 只包含 update=true 的建筑，墙/传送带等静态建筑不在里面，
        // 因此这里直接扫描世界格子，按 Building 去重（多格建筑会出现在多个格子上）。
        ObjectSet<Building> seen = new ObjectSet<>();
        for(Tile tile : world.tiles){
            Building build = tile.build;
            if(build == null || build.team != player || !seen.add(build)) continue;
            applyPlayerBlockHealth(build);
        }
    }

    private static void applyPlayerBlockHealth(Building build){
        if(state == null || state.rules == null || build == null || build.block == null) return;
        if(build.team != state.rules.defaultTeam) return;

        float target = Math.max(1f, build.block.health * playerBlockHealthMultiplier());
        float oldMax = build.maxHealth();
        float oldHealth = build.health();
        build.maxHealth(target);
        if(loadingWorld){
            // 读档：health 已经是绝对值。只修上限并夹紧，不再乘比例，否则存档里
            // 按 T0-T7 存档的 60% 满血会在重进时变成 36%。
            build.health(Math.min(target, oldHealth));
        }else{
            float ratio = oldMax > 0f ? Math.max(0f, oldHealth / oldMax) : 1f;
            build.health(Math.min(target, target * ratio));
        }
    }

    /** 把本局 SpawnGroup 换成累计小数版本；数量词条关闭时也保持原行为。 */
    private static void wrapSpawnGroups(){
        if(state == null || !state.isGame() || state.rules == null || state.rules.spawns == null) return;
        if(waveCountMultiplier() == 1f) return;
        for(int i = 0; i < state.rules.spawns.size; i++){
            var group = state.rules.spawns.get(i);
            if(group != null && (!(group instanceof DifficultySpawnGroup wrapper) || !wrapper.hasOriginal())){
                state.rules.spawns.set(i, new DifficultySpawnGroup(group));
            }
        }
    }

    /**
     * SaveIO 写出 Rules 前执行：把自定义 SpawnGroup 子类换回原版基类。
     * 否则自定义地图保存会把 "ironworks.DifficultySpawnGroup" 写入 JSON，
     * 读取端用系统 ClassLoader 解析时得到 ClassNotFound。
     */
    private static void stripSpawnGroupsForSave(){
        if(state == null || state.rules == null || state.rules.spawns == null) return;
        boolean changed = false;
        for(int i = 0; i < state.rules.spawns.size; i++){
            var group = state.rules.spawns.get(i);
            if(group instanceof DifficultySpawnGroup wrapper && wrapper.hasOriginal()){
                state.rules.spawns.set(i, wrapper.original());
                changed = true;
            }
        }
        if(changed) spawnWrappersStripped = true;
    }

    private static void syncTags(){
        if(state == null || state.rules == null) return;
        state.rules.tags.put("ironworks.difficulty", String.valueOf(tier));
        state.rules.tags.put("ironworks.difficulty.mods", modifierString());
        state.rules.tags.put("ironworks.difficulty.benefit-disabled", String.valueOf(benefitDisabledMask));
        state.rules.tags.put("ironworks.difficulty.benefit-controls", String.valueOf(benefitControlsVisible));
        if(baseDeconstructRefund >= 0f){
            state.rules.tags.put("ironworks.base-deconstruct-refund", String.valueOf(baseDeconstructRefund));
        }
    }

    private static void applyForcedLanding(){
        if(tier != maxTier || state.rules.tags.containsKey("ironworks.forced-landing-applied")) return;
        float loss = forcedLandingLoss();
        if(loss <= 0f) return;
        CoreBuild core = state.rules.defaultTeam == null ? null : state.rules.defaultTeam.core();
        if(core == null) return;
        for(Item item : content.items()){
            int amount = core.items.get(item);
            if(amount <= 0) continue;
            int remove = (int)Math.ceil(amount * loss);
            core.items.remove(item, Math.min(amount, remove));
        }
        state.rules.tags.put("ironworks.forced-landing-applied", "1");
        // 迫降只作用于下一局：结算后消耗词条，避免之后每个新档都重复扣资源。
        modifiers.remove(MOD_FORCED);
        savePrefs();
        syncTags();
    }

    private static void onUnitCreate(Unit unit){
        if(unit == null || state == null || !state.isGame() || state.rules == null) return;
        if(!isEnemyTarget(unit)) return;
        if(enemySpeedMultiplier() < 0.999f && IronworksStatus.enemyArmorSlow != null
            && !unit.hasEffect(IronworksStatus.enemyArmorSlow)){
            unit.apply(IronworksStatus.enemyArmorSlow, 999999f);
        }
    }

    /** 只给敌方单位施加减速状态；状态倍率动态读取当前词条，关闭收益后自动恢复。 */
    public static void updateEnemySlowEffect(){
        if(IronworksStatus.enemyArmorSlow != null){
            IronworksStatus.enemyArmorSlow.speedMultiplier = enemySpeedMultiplier();
        }
        if(state == null || !state.isGame() || state.rules == null) return;
        for(Unit unit : mindustry.gen.Groups.unit){
            if(isEnemyTarget(unit) && enemySpeedMultiplier() < 0.999f
                && IronworksStatus.enemyArmorSlow != null
                && !unit.hasEffect(IronworksStatus.enemyArmorSlow)){
                unit.apply(IronworksStatus.enemyArmorSlow, 999999f);
            }
        }
    }

    private static void onUnitDestroy(Unit unit){
        if(unit == null) return;
        armorFatigue.remove(unit);
        if(tier != maxTier || state == null || !state.isGame() || state.rules == null) return;
        Team player = state.rules.defaultTeam;
        if(player == null || unit.team == null) return;
        // 任何非默认玩家队伍都视为敌方。这样玩家把“尖刀”切成红队后，
        // 被黄队炮塔击杀时仍能按敌方单位回收，不会因为红队不是当前
        // waveTeam 而漏掉资源。
        float refund = unit.team == player ? playerRefund() : enemyRefund();
        if(refund <= 0f) return;
        ItemStack[] req = unit.type == null ? null : unit.type.getTotalRequirements();
        if(req == null || req.length == 0) return;
        CoreBuild core = player.core();
        if(core == null) return;
        for(ItemStack stack : req){
            int amount = (int)Math.ceil(stack.amount * refund);
            if(amount > 0) IronworksRecovery.recover(core, stack.item, amount);
        }
    }

    private static void onBlockDestroy(mindustry.world.Tile tile){
        if(tile == null) return;
        Healthc build = tile.build;
        if(build != null) armorFatigue.remove(build);
        // 拆除由原版 deconstructRefundMultiplier 全额返还，不能再算一次战损回收。
        if(deconstructing.remove(tile)) return;
        if(tier != maxTier || state == null || !state.isGame() || state.rules == null) return;
        Team player = state.rules.defaultTeam;
        Building building = tile.build;
        if(player == null || building == null || building.block == null) return;
        // 与单位回收一致：非玩家队伍都按敌方回收，兼容换队实验/额外阵营。
        float refund = tile.team() == player ? playerRefund() : enemyRefund();
        if(refund <= 0f) return;
        CoreBuild core = player.core();
        if(core == null || building.block.requirements == null) return;
        float cost = Math.max(0f, state.rules.buildCostMultiplier);
        for(ItemStack stack : building.block.requirements){
            int amount = (int)Math.ceil(stack.amount * cost * refund);
            if(amount > 0) IronworksRecovery.recover(core, stack.item, amount);
        }
    }

    /** 建筑/单位详情页的实时总值；返回空串表示没有额外内容。 */
    public static String contentDetail(UnlockableContent content){
        if(content instanceof UnitType unit){
            float baseHealth = unit.health;
            return Core.bundle.format("ironworks.difficulty.content.unit",
                num(baseHealth * playerUnitStatMultiplier()), num(playerUnitStatMultiplier()),
                num(baseHealth * enemyHealthMul()), num(enemyHealthMul()),
                num(unit.armor), num(Math.max(0f, unit.armor + enemyArmorBonus())),
                num(playerUnitStatMultiplier()), num(enemyDamageMul()),
                Math.round(enemyElementResistance() * 100f), num(playerUnitCostMultiplier()));
        }
        if(content instanceof Block block){
            StringBuilder result = new StringBuilder(Core.bundle.format(
                "ironworks.difficulty.content.block",
                num(block.health), num(block.health * playerBlockHealthMultiplier()),
                num3(playerBlockHealthMultiplier()),
                num(block.armor), num(Math.max(0f, block.armor + playerBlockArmorModifier())),
                num3(buildSpeedMultiplier()), fullDeconstructRefund() ? "100%" : "原版"));

            if(block instanceof Turret turret){
                result.append('\n').append(turretDetail(turret));
            }
            if(block instanceof Drill drill){
                result.append('\n').append(Core.bundle.format("ironworks.difficulty.content.drill",
                    num(miningMultiplier()),
                    num(60f / Math.max(0.0001f, drill.drillTime) * drill.size * drill.size)));
            }else if(block instanceof MultiCrafter || block instanceof DifficultyCrafter){
                result.append('\n').append(Core.bundle.format("ironworks.difficulty.content.factory",
                    num(productionMultiplier())));
            }
            return result.toString();
        }
        return "";
    }

    private static String turretDetail(Turret turret){
        String result = Core.bundle.format("ironworks.difficulty.content.turret",
            turret.ammoPerShot, num(turretAmmoMultiplier()),
            num(turret.reload / 60f), num(turretFireRateMultiplier()),
            Math.round(turret.inaccuracy), num(precisionDamageMultiplier()),
            Math.round(precisionPenRate() * 100f), num(precisionPenFlatMultiplier()));
        String damage = turretDamageDetail(turret);
        if(!damage.isEmpty()){
            result += "\n" + Core.bundle.format("ironworks.difficulty.content.turret.damage", damage);
        }
        return result;
    }

    private static String turretDamageDetail(Turret turret){
        StringBuilder result = new StringBuilder();
        for(var bullet : Dmg.ammoTypes(turret)){
            if(bullet == null || bullet.damage <= 0f) continue;
            if(result.length() > 0) result.append(" / ");
            result.append(num(bullet.damage)).append(" → ")
                .append(num(bullet.damage * precisionDamageMultiplier()));
            if(result.length() > 90) break;
        }
        return result.toString();
    }

    // ────────────────────────── 受袭区块 ──────────────────────────

    public static Seq<Sector> attackedSectors(){
        Seq<Sector> result = new Seq<>();
        // 刚大退重进时 state.planet 可能还没建立；扫描所有战役星球上的 sector。
        if(state == null || content == null) return result;
        for(Planet planet : content.planets()){
            if(planet == null) continue;
            for(Sector sector : planet.sectors){
                if(sector != null && sector.isAttacked()) result.add(sector);
            }
        }
        return result;
    }

    public static boolean hasAttackedSector(){
        return attackedSectors().size > 0;
    }

    public static boolean hasActiveAttackedSector(){
        for(Sector sector : attackedSectors()){
            if(sector.isBeingPlayed()) return true;
        }
        return false;
    }

    /** 放弃受袭但未在玩的区块；当前正在玩的受袭区块不会被静默摧毁。 */
    public static int abandonAttackedSectors(){
        int count = 0;
        for(Sector sector : attackedSectors()){
            if(sector.isBeingPlayed()) continue;
            sector.info.items.clear();
            sector.info.hasCore = false;
            sector.info.production.clear();
            sector.saveInfo();
            count++;
        }
        return count;
    }

    // ────────────────────────── 内部 ──────────────────────────

    private static int clampTier(int value){
        return Math.max(0, Math.min(maxTier, value));
    }

    private static String num(float value){
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    private static String num3(float value){
        return String.format(java.util.Locale.US, "%.3f", value);
    }

    private static String num4(float value){
        return String.format(java.util.Locale.US, "%.4f", value);
    }

    private static String signed(int value){
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    private static String modifierString(){
        StringBuilder sb = new StringBuilder();
        for(String id : modIds){
            int mask = modifierMask(id);
            if(mask <= 0) continue;
            if(sb.length() > 0) sb.append(',');
            // m 前缀表示位掩码；旧版本没有 m，按“等级计数”迁移。
            sb.append(id).append(":m").append(mask);
        }
        return sb.toString();
    }

    private static void parseModifiers(String text){
        modifiers.clear();
        if(text == null || text.isEmpty()) return;
        for(String part : text.split(",")){
            int colon = part.indexOf(':');
            if(colon <= 0) continue;
            String id = part.substring(0, colon);
            String raw = part.substring(colon + 1);
            try{
                int mask;
                if(raw.startsWith("m")){
                    mask = Integer.parseInt(raw.substring(1));
                }else{
                    int oldLevel = Math.max(0, Integer.parseInt(raw));
                    mask = oldLevel <= 0 ? 0 : (1 << Math.min(maxModifierLevel, oldLevel)) - 1;
                }
                mask &= maskBits();
                if(mask == 0) modifiers.remove(id);
                else modifiers.put(id, mask);
            }catch(Exception ignored){
            }
        }
    }

    public static void logState(){
        Log.info("ironworks difficulty=@ res=@ build=@ blockHP=@ armor=@ enemyHP=@ enemyDMG=@ enemyArmor=@ ricochet=@ mods=@",
            tier, productionMultiplier(), buildSpeedMultiplier(), playerBlockHealthMultiplier(),
            playerBlockArmorModifier(), enemyHealthMul(), enemyDamageMul(), enemyArmorBonus(),
            ricochetBonus(), modifierString());
    }
}
