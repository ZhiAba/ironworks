package ironworks;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.ObjectIntMap;
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
import mindustry.type.Sector;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;

import static mindustry.Vars.*;

/**
 * 铁流工业全局难度协议（T0-T7）。
 *
 * <p>这是“物理法则重写器”，不是简单的数值开关：</p>
 * <ul>
 *   <li>敌方单位：血量/伤害/波次间隔；高难度额外绝对护甲（T6 +45）。</li>
 *   <li>我方：建造速度、有效建筑血量、建筑护甲（可为负）、产能/采矿。</li>
 *   <li>跳弹：T2 起给敌方目标额外跳弹率，T6 +0.05。</li>
 *   <li>T7 词条：迫降、豆腐渣工程、资源匮乏、重工业；可重复叠加，只加难度。</li>
 * </ul>
 *
 * <p><b>核电站不参与难度修正</b>：按主人要求，RBMK 的跳闸、熔毁、水锤、
 * 注水泵与卡棒逻辑保持现状，本类不引用也不修改 {@link IronworksRBMK}。</p>
 */
public final class Difficulty{
    public static final int maxTier = 7;

    public static final String[] tierIds = {"t0", "t1", "t2", "t3", "t4", "t5", "t6", "t7"};
    public static final String[] tierNames = {"安逸", "苏醒", "整备", "警戒", "失谐", "共振", "梦魇", "凝视深空"};

    /** 难度色：低难绿，高难红/深红，T7 故障紫黑。 */
    public static final Color[] tierColors = {
        Color.valueOf("69d27a"), Color.valueOf("63c6b0"), Color.valueOf("5aa7e8"),
        Color.valueOf("e0c35a"), Color.valueOf("e08b4a"), Color.valueOf("e05a4a"),
        Color.valueOf("9c2535"), Color.valueOf("9b59d0")
    };

    // ── 数值表：T0..T7 ──
    private static final float[] enemyHealth = {0.80f, 1.00f, 1.20f, 1.45f, 1.70f, 1.95f, 2.40f, 2.40f};
    private static final float[] enemyDamage = {0.80f, 1.00f, 1.15f, 1.30f, 1.50f, 1.75f, 2.10f, 2.20f};
    /** 波次间隔倍率；越小越密集。 */
    private static final float[] waveSpacing = {1.25f, 1.00f, 0.92f, 0.84f, 0.76f, 0.68f, 0.58f, 0.55f};
    private static final float[] playerBuildSpeed = {1.05f, 1.00f, 0.95f, 0.90f, 0.85f, 0.80f, 0.80f, 0.80f};
    /** 有效建筑血量倍率（参与受到伤害的除法）。 */
    private static final float[] playerBlockHealth = {1.05f, 1.00f, 0.94f, 0.86f, 0.78f, 0.70f, 0.62f, 0.58f};
    private static final float[] playerBlockArmor = {1.0f, 0.0f, -1.0f, -2.0f, -3.0f, -5.0f, -8.0f, -10.0f};
    private static final float[] production = {1.02f, 1.00f, 0.95f, 0.90f, 0.85f, 0.80f, 0.80f, 0.80f};
    private static final float[] ricochetBonus = {0.00f, 0.00f, 0.01f, 0.02f, 0.03f, 0.04f, 0.05f, 0.06f};
    /** 敌方单位绝对护甲上浮。T6 = +45，T7 = +50。 */
    private static final float[] enemyArmorBonus = {0f, 0f, 6f, 14f, 24f, 34f, 45f, 50f};

    // ── T7 词条 ──
    public static final String MOD_FORCED = "forced-landing";
    public static final String MOD_JERRY = "jerry-build";
    public static final String MOD_SCARCITY = "scarcity";
    public static final String MOD_HEAVY = "heavy-industry";
    public static final String[] modIds = {MOD_FORCED, MOD_JERRY, MOD_SCARCITY, MOD_HEAVY};
    /** 每个词条最多 III 级；不同等级可以同时启用，效果按已启用等级相加。 */
    public static final int maxModifierLevel = 3;

    // 每级数值不相等，随等级提升而增大；例如资源匮乏 I/II/III = 15%/30%/50%。
    private static final float[] forcedLossLevels = {0.20f, 0.35f, 0.50f};
    private static final float[] jerryHealthLevels = {0.06f, 0.15f, 0.26f};
    private static final float[] jerryArmorLevels = {1.0f, 2.5f, 4.5f};
    private static final float[] jerryBuildLevels = {0.08f, 0.18f, 0.30f};
    private static final float[] scarcityPenaltyLevels = {0.15f, 0.30f, 0.50f};
    private static final float[] scarcityRefundLevels = {0.05f, 0.10f, 0.20f};
    private static final float[] heavyCostLevels = {0.25f, 0.60f, 1.00f};
    private static final float[] heavyStatLevels = {0.10f, 0.25f, 0.40f};

    private static int tier;
    private static final ObjectIntMap<String> modifiers = new ObjectIntMap<>();
    private static float baseWaveSpacing = -1f, baseInitialWaveSpacing = -1f;
    private static Object rulesRef;
    private static boolean initialized;

    private Difficulty(){
    }

    public static void init(){
        if(initialized) return;
        initialized = true;
        loadPrefs();

        Events.on(EventType.WorldLoadEvent.class, e -> onWorldLoad());
        Events.on(EventType.WorldLoadEndEvent.class, e -> onWorldLoadEnd());
        Events.on(EventType.UnitDestroyEvent.class, e -> onUnitDestroy(e.unit));
        // 无头/服务器可能在 mod init 之前就完成 world load；用 update 兜底，
        // 只在 rules 实例变化时应用一次，不会每帧重算。
        Events.run(EventType.Trigger.update, Difficulty::ensureApplied);
        Events.on(EventType.ResetEvent.class, e -> {
            rulesRef = null;
            baseWaveSpacing = baseInitialWaveSpacing = -1f;
        });
    }

    // ────────────────────────── 对外查询 ──────────────────────────

    public static int tier(){ return tier; }
    public static boolean isMaxTier(){ return tier >= maxTier; }
    public static String tierName(int value){
        int v = clampTier(value);
        return Core.bundle == null ? tierNames[v] : Core.bundle.get("ironworks.difficulty.tier." + tierIds[v], tierNames[v]);
    }
    public static Color tierColor(int value){ return tierColors[clampTier(value)]; }

    public static float enemyHealthMul(){ return enemyHealth[tier]; }
    public static float enemyDamageMul(){ return enemyDamage[tier]; }
    public static float enemyArmorBonus(){ return enemyArmorBonus[tier]; }
    public static float ricochetBonus(){ return ricochetBonus[tier]; }
    public static float productionMultiplier(){
        float value = production[tier];
        if(tier == maxTier) value *= 1f - modifierSum(MOD_SCARCITY, scarcityPenaltyLevels);
        // 主人指定的防死锁底线：产能/资源惩罚不低过 0.80。
        return Math.max(0.80f, value);
    }

    /**
     * 资源/采掘倍率：T7 资源匮乏可把钻机/单位采矿压到 0.50。
     * MultiCrafter 的产能仍单独遵守 0.80 底线。
     */
    public static float miningMultiplier(){
        float value = production[tier];
        if(tier == maxTier) value *= 1f - modifierSum(MOD_SCARCITY, scarcityPenaltyLevels);
        return Math.max(0.50f, value);
    }

    public static float buildSpeedMultiplier(){
        float value = playerBuildSpeed[tier];
        if(tier == maxTier) value *= 1f + modifierSum(MOD_JERRY, jerryBuildLevels);
        // 正向补偿有上限，避免“豆腐渣”叠加把建造速度刷成外挂。
        return Math.max(0.35f, Math.min(1.35f, value));
    }

    public static float playerBlockHealthMultiplier(){
        float value = playerBlockHealth[tier];
        if(tier == maxTier) value *= Math.max(0.55f, 1f - modifierSum(MOD_JERRY, jerryHealthLevels));
        return value;
    }

    public static float playerBlockArmorModifier(){
        float value = playerBlockArmor[tier];
        if(tier == maxTier) value -= modifierSum(MOD_JERRY, jerryArmorLevels);
        return value;
    }

    public static float playerUnitCostMultiplier(){
        return tier == maxTier ? 1f + modifierSum(MOD_HEAVY, heavyCostLevels) : 1f;
    }

    public static float playerUnitStatMultiplier(){
        return tier == maxTier ? 1f + modifierSum(MOD_HEAVY, heavyStatLevels) : 1f;
    }

    /** 击杀回收比例；T7 资源匮乏各级回收相加。 */
    public static float killRefund(){
        if(tier != maxTier) return 0f;
        return Math.min(0.35f, modifierSum(MOD_SCARCITY, scarcityRefundLevels));
    }

    /** 迫降损失的开局资源比例；仅 T7 且本局尚未应用。 */
    public static float forcedLandingLoss(){
        if(tier != maxTier) return 0f;
        return Math.min(0.90f, modifierSum(MOD_FORCED, forcedLossLevels));
    }

    private static float modifierSum(String id, float[] perLevel){
        int mask = modifierMask(id);
        float sum = 0f;
        for(int i = 0; i < perLevel.length; i++){
            if((mask & (1 << i)) != 0) sum += perLevel[i];
        }
        return sum;
    }

    /** UI 预览：按指定档位生成详细数值说明，包含已启用词条的实时效果。 */
    public static String describe(int value){
        value = clampTier(value);
        int forced = value == maxTier ? Math.round(forcedLandingLoss() * 100f) : 0;
        int refund = value == maxTier ? Math.round(killRefund() * 100f) : 0;
        return Core.bundle.format("ironworks.difficulty.detail",
            num(enemyHealth[value]), num(enemyDamage[value]), String.valueOf((int)enemyArmorBonus[value]),
            num(ricochetBonus[value]), num(waveSpacing[value]),
            num(buildSpeedFor(value)), num(blockHealthFor(value)), num(armorFor(value)),
            num(unitCostFor(value)), num(unitStatFor(value)), num(productionFor(value)), num(miningFor(value)),
            String.valueOf(forced), String.valueOf(refund), activeModifierSummary(value));
    }

    private static float miningFor(int value){
        float result = value == maxTier ? miningMultiplier() : production[value];
        return value == maxTier ? result : Math.max(0.80f, result);
    }

    private static float unitCostFor(int value){
        return value == maxTier ? 1f + modifierSum(MOD_HEAVY, heavyCostLevels) : 1f;
    }

    private static float unitStatFor(int value){
        return value == maxTier ? 1f + modifierSum(MOD_HEAVY, heavyStatLevels) : 1f;
    }

    private static String activeModifierSummary(int value){
        return value == maxTier && tier == maxTier ? modifierSummary() : (Core.bundle == null ? "无词条" : Core.bundle.get("ironworks.difficulty.no-mods", "无词条"));
    }

    private static float productionFor(int value){
        float result = production[value];
        if(value == maxTier) result *= 1f - modifierSum(MOD_SCARCITY, scarcityPenaltyLevels);
        return Math.max(0.80f, result);
    }

    private static float buildSpeedFor(int value){
        float result = playerBuildSpeed[value];
        if(value == maxTier) result *= 1f + modifierSum(MOD_JERRY, jerryBuildLevels);
        return Math.max(0.35f, Math.min(1.35f, result));
    }

    private static float blockHealthFor(int value){
        float result = playerBlockHealth[value];
        if(value == maxTier) result *= Math.max(0.55f, 1f - modifierSum(MOD_JERRY, jerryHealthLevels));
        return result;
    }

    private static float armorFor(int value){
        float result = playerBlockArmor[value];
        if(value == maxTier) result -= modifierSum(MOD_JERRY, jerryArmorLevels);
        return result;
    }

    private static int maskBits(){
        return (1 << maxModifierLevel) - 1;
    }

    /** 已启用等级的位掩码；第 1 级是 bit0。 */
    public static int modifierMask(String id){
        return modifiers.get(id, 0) & maskBits();
    }

    public static boolean hasModifierLevel(String id, int level){
        return level > 0 && level <= maxModifierLevel && (modifierMask(id) & (1 << (level - 1))) != 0;
    }

    /** 兼容查询：返回已启用的最高等级。 */
    public static int modifierLevel(String id){
        int mask = modifierMask(id);
        return mask == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(mask);
    }

    public static void toggleModifierLevel(String id, int level){
        if(tier != maxTier || id == null || level <= 0 || level > maxModifierLevel) return;
        int mask = modifierMask(id);
        int bit = 1 << (level - 1);
        mask ^= bit;
        if(mask == 0) modifiers.remove(id);
        else modifiers.put(id, mask);
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
            for(int i = 1; i <= maxModifierLevel; i++){
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
        Team enemy = state.rules.waveTeam, player = state.rules.defaultTeam;
        if(enemy == null || player == null || enemy == player) return false;
        if(target instanceof Unit unit) return unit.team == enemy && unit.team != player;
        if(target instanceof Building build) return build.team == enemy && build.team != player;
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
    }

    public static void setModifiersFromUi(){
        savePrefs();
        syncTags();
        applyRules();
        IronworksDrills.apply();
    }

    public static void addModifier(String id){
        if(tier != maxTier || id == null) return;
        int mask = modifierMask(id);
        for(int level = 1; level <= maxModifierLevel; level++){
            int bit = 1 << (level - 1);
            if((mask & bit) == 0){
                modifiers.put(id, mask | bit);
                setModifiersFromUi();
                return;
            }
        }
    }

    /** 只启用指定等级；0 表示移除该词条。独立按钮应使用 toggleModifierLevel。 */
    public static void setModifierLevel(String id, int level){
        if(tier != maxTier || id == null) return;
        if(level <= 0 || level > maxModifierLevel) modifiers.remove(id);
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
    }

    private static void savePrefs(){
        if(Core.settings == null) return;
        Core.settings.putInt("ironworks.difficulty.tier", tier);
        Core.settings.put("ironworks.difficulty.mods", modifierString());
    }

    /** 兜底：WorldLoadEvent 注册晚于世界加载时，下一 tick 补应用。 */
    private static void ensureApplied(){
        if(state == null || state.rules == null) return;
        if(rulesRef != state.rules){
            onWorldLoad();
        }
        applyForcedLanding();
    }

    private static void onWorldLoad(){
        if(state == null || state.rules == null) return;
        if(rulesRef != state.rules){
            rulesRef = state.rules;
            baseWaveSpacing = -1f;
            baseInitialWaveSpacing = -1f;
        }

        String savedTier = state.rules.tags.get("ironworks.difficulty");
        if(savedTier != null){
            try{ tier = clampTier(Integer.parseInt(savedTier)); }catch(Exception ignored){}
        }

        String savedMods = state.rules.tags.get("ironworks.difficulty.mods");
        if(savedMods != null) parseModifiers(savedMods);

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

        applyRules();
        // 没有迫降词条时立刻标记“开局结算已过”，避免中途加 T7 词条时倒扣已有资源。
        if(forcedLandingLoss() <= 0f){
            state.rules.tags.put("ironworks.forced-landing-applied", "1");
        }
    }

    private static void onWorldLoadEnd(){
        if(state == null || state.rules == null) return;
        applyRules();
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
            r.buildSpeedMultiplier = buildSpeedMultiplier();
            r.unitBuildSpeedMultiplier = buildSpeedMultiplier();
            r.unitMineSpeedMultiplier = miningMultiplier();
            r.blockHealthMultiplier = playerBlockHealthMultiplier();
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
            state.rules.waveSpacing = Math.max(20f, baseWaveSpacing * waveSpacing[tier]);
        }
        if(baseInitialWaveSpacing > 0f){
            state.rules.initialWaveSpacing = baseInitialWaveSpacing * waveSpacing[tier];
        }

        syncTags();
        IronworksDrills.apply();
    }

    private static void syncTags(){
        if(state == null || state.rules == null) return;
        state.rules.tags.put("ironworks.difficulty", String.valueOf(tier));
        state.rules.tags.put("ironworks.difficulty.mods", modifierString());
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

    private static void onUnitDestroy(Unit unit){
        if(unit == null || tier != maxTier || state == null || !state.isGame() || state.rules == null) return;
        Team player = state.rules.defaultTeam, enemy = state.rules.waveTeam;
        if(player == null || enemy == null || player == enemy || unit.team != enemy) return;
        float refund = killRefund();
        if(refund <= 0f) return;
        ItemStack[] req = unit.type == null ? null : unit.type.getTotalRequirements();
        if(req == null || req.length == 0) return;
        CoreBuild core = player.core();
        if(core == null) return;
        for(ItemStack stack : req){
            int amount = (int)Math.ceil(stack.amount * refund);
            if(amount > 0) core.items.add(stack.item, amount);
        }
    }

    // ────────────────────────── 受袭区块 ──────────────────────────

    public static Seq<Sector> attackedSectors(){
        Seq<Sector> result = new Seq<>();
        if(state == null || state.getPlanet() == null) return result;
        for(Sector sector : state.getPlanet().sectors){
            if(sector != null && sector.isAttacked()) result.add(sector);
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
