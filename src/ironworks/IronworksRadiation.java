package ironworks;

import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.struct.ObjectMap;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.units.StatusEntry;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.gen.Building;
import mindustry.gen.CrawlUnit;
import mindustry.gen.Groups;
import mindustry.gen.LegsUnit;
import mindustry.gen.TankUnit;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.StatusEffect;
import mindustry.type.UnitType;
import mindustry.world.Block;

/**
 * 辐射系统：污染值 -> 四级状态 -> 百分比伤害/电子干扰 -> 永久结构损伤。
 *
 * <p>原版 Unit 没有可模组扩展的字段，因此运行时数据放在 tracks 里；
 * 永久损伤另用两个永久状态做存档标记。污染值本身是运行时状态，读档后从 0
 * 重新累积，永久损伤标记仍然保留。</p>
 *
 * <p>辐射源分两类：实现 Emitter 的建筑（RBMK、corium）和注册过的方块，
 * 以及建筑/单位库存里的放射性物品。多源强度按平方和开方叠加，距离按
 * (1 - d / radius)^2 衰减。</p>
 */
public class IronworksRadiation{
    public static final int NONE = 0, MILD = 1, MODERATE = 2, SEVERE = 3, LETHAL = 4;
    public static final int BIOLOGICAL = 0, MECHANICAL = 1, ARMORED = 2;

    /** 污染阈值：轻度 / 中度 / 重度 / 致死。 */
    public static final float[] THRESHOLDS = {0f, 15f, 40f, 80f, 140f};
    /** 污染上限，避免数值无限增长。 */
    public static final float MAX_POLLUTION = 240f;
    /** 单源强度为 1、贴脸时的每秒污染增长。 */
    public static final float GAIN_PER_SECOND = 6f;
    /** 脱离所有辐射源后的污染半衰期（秒）。 */
    public static final float DECAY_HALF_LIFE = 10f;
    /** 每级每秒扣除当前最大生命的比例。 */
    public static final float[] DAMAGE_PER_SECOND = {0f, 0.002f, 0.008f, 0.025f, 0.07f};
    /** 结构损伤：重度/致死的一次性最大生命削减。 */
    public static final float SCAR_SEVERE = 0.15f, SCAR_LETHAL = 0.35f;
    /** 机械/装甲每次电子干扰的停火时长（tick）与冷却（秒）。 */
    public static final float JAM_SEVERE = 18f, JAM_LETHAL = 30f;
    public static final float JAM_SEVERE_COOLDOWN = 18f, JAM_LETHAL_COOLDOWN = 12f;
    /** 物品放射性对单位/建筑的源强度系数。 */
    public static final float UNIT_ITEM_SCALE = 0.35f, BUILDING_ITEM_SCALE = 0.02f;
    /** 单个建筑物品源的强度上限。 */
    public static final float BUILDING_ITEM_CAP = 8f;
    /** 每多少 tick 重建一次建筑源缓存。 */
    public static final float SOURCE_REFRESH_TICKS = 15f;

    public static StatusEffect exposureMild, exposureModerate, exposureSevere, exposureLethal;
    public static StatusEffect scar15, scar35, jam;
    /** 给未来抗辐射装备预留的状态名；当前由外挂装甲状态提供抗性。 */
    public static StatusEffect radiationResistance;

    /** 单位类型级抗辐射覆盖（0~1）。 */
    public static final ObjectMap<UnitType, Float> typeResistance = new ObjectMap<>();
    /** 明确标为“生物”的单位类型；原版没有生物标签，默认收录爬虫线。 */
    public static final ObjectSet<UnitType> biologicalTypes = new ObjectSet<>();
    /** 方块常数源。 */
    private static final ObjectMap<Block, SourceDef> blockSources = new ObjectMap<>();
    /** 当前建筑源缓存。 */
    private static final Seq<Source> sources = new Seq<>();
    /** 达到致死级或携带放射性物品的单位，也会向周围发射。 */
    private static final Seq<UnitSource> unitSources = new Seq<>();
    /** 单位运行时数据。 */
    private static final ObjectMap<Unit, Track> tracks = new ObjectMap<>();
    /** 所有有放射性的物品，避免每帧遍历整个内容表。 */
    private static final Seq<Item> radioactiveItems = new Seq<>();

    private static float sourceTimer;
    private static boolean loaded;

    /** 自定义建筑辐射源。 */
    public interface Emitter{
        float radiationStrength();
        default float radiationRadius(){
            return 64f;
        }
    }

    private static class SourceDef{
        final float strength, radius;

        SourceDef(float strength, float radius){
            this.strength = strength;
            this.radius = radius;
        }
    }

    private static class Source{
        final Building building;
        final float strength, radius;

        Source(Building building, float strength, float radius){
            this.building = building;
            this.strength = strength;
            this.radius = radius;
        }
    }

    private static class UnitSource{
        final Unit unit;
        final float strength, radius;

        UnitSource(Unit unit, float strength, float radius){
            this.unit = unit;
            this.strength = strength;
            this.radius = radius;
        }
    }

    private static class Track{
        int id;
        float pollution, scar;
        float baseMax = -1f;
        UnitType type;
        float jamCooldown;
        boolean dynamicApplied;
    }

    /** 在内容加载完成后调用一次：建状态、扫描物品、注册建筑源。 */
    public static void load(){
        if(loaded) return;
        loaded = true;

        exposureMild = exposure("radiation-mild", "9be86a");
        exposureModerate = exposure("radiation-moderate", "b6e34d");
        exposureSevere = exposure("radiation-severe", "ffb84d");
        exposureLethal = exposure("radiation-lethal", "ff5a48");

        scar15 = new StatusEffect("radiation-scar-15"){{
            color = Color.valueOf("d38b5a");
            permanent = true;
            effect = Fx.none;
        }};
        scar35 = new StatusEffect("radiation-scar-35"){{
            color = Color.valueOf("b45a5a");
            permanent = true;
            effect = Fx.none;
        }};
        jam = new StatusEffect("radiation-jam"){{
            color = Color.valueOf("9be86a");
            disarm = true;
            effect = Fx.none;
        }};
        radiationResistance = new StatusEffect("radiation-resistance"){{
            color = Color.valueOf("7fe3d0");
            effect = Fx.none;
        }};

        for(Item item : Vars.content.items()){
            if(item.radioactivity > 0f) radioactiveItems.add(item);
        }

        // 原版 crawler 系列使用 MechUnit 渲染，没有生物标签；这里显式登记。
        for(String name : new String[]{"crawler", "atrax", "spiroct", "arkyid", "toxopid"}){
            UnitType type = Vars.content.unit(name);
            if(type != null) biologicalTypes.add(type);
        }

        // 核电建筑的常数源；反应堆与 corium 走 Emitter，强度随工况变化。
        source(IronworksNuclear.leachPlant, 0.12f, 40f);
        source(IronworksNuclear.enrichmentPlant, 0.25f, 48f);
        source(IronworksNuclear.fuelFactory, 0.35f, 48f);
        source(IronworksNuclear.spentFuelPool, 0.9f, 72f);
        source(IronworksNuclear.reprocessor, 1.0f, 72f);
        source(IronworksNuclear.vitrifier, 0.3f, 48f);
        source(IronworksNuclear.dryStorage, 1.2f, 80f);

        Events.run(Trigger.update, IronworksRadiation::update);
        Events.on(ResetEvent.class, e -> clear());
        Events.on(UnitDestroyEvent.class, e -> onUnitDestroyed(e.unit));
    }

    private static StatusEffect exposure(String name, String hex){
        return new StatusEffect(name){{
            this.color = Color.valueOf(hex);
            effect = Fx.none;
        }};
    }

    /** 注册一个固定强度的建筑源。 */
    public static void source(Block block, float strength, float radius){
        if(block != null && strength > 0f && radius > 0f){
            blockSources.put(block, new SourceDef(strength, radius));
        }
    }

    /** 注册/覆盖单位类型的抗辐射（0~1）。 */
    public static void resistance(UnitType type, float value){
        if(type != null) typeResistance.put(type, Mathf.clamp(value, 0f, 0.95f));
    }

    /** 当前污染值。 */
    public static float pollution(Unit unit){
        Track t = unit == null ? null : tracks.get(unit);
        return t == null ? 0f : t.pollution;
    }

    /** 当前辐射等级（0~4）。 */
    public static int level(Unit unit){
        return levelOf(pollution(unit));
    }

    public static int levelOf(float value){
        if(value >= THRESHOLDS[LETHAL]) return LETHAL;
        if(value >= THRESHOLDS[SEVERE]) return SEVERE;
        if(value >= THRESHOLDS[MODERATE]) return MODERATE;
        if(value >= THRESHOLDS[MILD]) return MILD;
        return NONE;
    }

    /** 当前永久生命削减比例（0~0.35）。 */
    public static float scar(Unit unit){
        Track t = unit == null ? null : tracks.get(unit);
        return t == null ? 0f : t.scar;
    }

    /** 外挂/自定义抗性（0~1）；外挂装甲状态也会计入。 */
    public static float resistanceOf(Unit unit){
        if(unit == null || unit.type == null) return 0f;
        Float base = typeResistance.get(unit.type);
        float r = base == null ? 0f : base;
        if(unit.hasEffect(radiationResistance)) r += 0.35f;
        if(IronworksStatus.reactiveArmor != null && unit.hasEffect(IronworksStatus.reactiveArmor)) r += 0.15f;
        if(IronworksStatus.compositeArmor != null && unit.hasEffect(IronworksStatus.compositeArmor)) r += 0.25f;
        if(IronworksStatus.naniteArmor != null && unit.hasEffect(IronworksStatus.naniteArmor)) r += 0.35f;
        return Mathf.clamp(r, 0f, 0.95f);
    }

    /** 注册一个生物单位类型（原版/模组都可用）。 */
    public static void biological(UnitType type){
        if(type != null) biologicalTypes.add(type);
    }

    /** 单位分类：显式生物 > 坦克/高护甲 > 其余机械。 */
    public static int classOf(Unit unit){
        if(unit == null || unit.type == null) return MECHANICAL;
        if(hasArmorStatus(unit) || unit.type.armor >= 8f || unit instanceof TankUnit) return ARMORED;
        if(biologicalTypes.contains(unit.type) || unit instanceof LegsUnit || unit instanceof CrawlUnit) return BIOLOGICAL;
        return MECHANICAL;
    }

    private static boolean hasArmorStatus(Unit unit){
        return (IronworksStatus.reactiveArmor != null && unit.hasEffect(IronworksStatus.reactiveArmor))
            || (IronworksStatus.compositeArmor != null && unit.hasEffect(IronworksStatus.compositeArmor))
            || (IronworksStatus.naniteArmor != null && unit.hasEffect(IronworksStatus.naniteArmor));
    }

    /** 外部直接加污染（RBMK 落尘、核弹等）。 */
    public static void addPollution(Unit unit, float amount){
        if(unit == null || unit.dead() || amount <= 0f) return;
        Track t = track(unit);
        t.pollution = Math.min(MAX_POLLUTION, t.pollution + amount);
    }

    /** 净化：按比例减少永久损伤，同时快速冲掉污染。amount 是本次要削减的“比例”。 */
    public static void purify(Unit unit, float amount){
        if(unit == null || unit.dead() || amount <= 0f) return;
        Track t = track(unit);
        t.scar = Math.max(0f, t.scar - amount);
        t.pollution = Math.max(0f, t.pollution - amount * 800f);
        syncScar(unit, t);
    }

    /** 客户端测试/调试入口：只推进一个单位，不影响全局时间。 */
    public static void tickUnit(Unit unit, float dt){
        if(unit == null || unit.dead() || dt <= 0f) return;
        updateUnit(unit, dt);
    }

    /** 测试/调试：移除单位运行时数据（不触发死亡污染）。 */
    public static void forget(Unit unit){
        if(unit != null) tracks.remove(unit);
    }

    private static Track track(Unit unit){
        Track t = tracks.get(unit);
        if(t == null){
            t = new Track();
            t.id = unit.id;
            t.type = unit.type;
            t.baseMax = unit.type == null ? unit.maxHealth : unit.type.health;
            // 读档时用永久状态标记重建削减比例。
            if(unit.hasEffect(scar35)) t.scar = SCAR_LETHAL;
            else if(unit.hasEffect(scar15)) t.scar = SCAR_SEVERE;
            tracks.put(unit, t);
            syncScar(unit, t);
        }else if(t.id != unit.id){
            // 单位对象被实体池复用：按新单位清空运行时污染，只保留状态标记可重建的永久损伤。
            t.id = unit.id;
            t.pollution = 0f;
            t.scar = 0f;
            t.jamCooldown = 0f;
            t.dynamicApplied = false;
            t.type = unit.type;
            t.baseMax = unit.type == null ? unit.maxHealth : unit.type.health;
            if(unit.hasEffect(scar35)) t.scar = SCAR_LETHAL;
            else if(unit.hasEffect(scar15)) t.scar = SCAR_SEVERE;
            syncScar(unit, t);
        }else if(t.type != unit.type){
            t.type = unit.type;
            t.baseMax = unit.type == null ? unit.maxHealth : unit.type.health;
            syncScar(unit, t);
        }
        return t;
    }

    private static void update(){
        if(Vars.state == null || !Vars.state.isPlaying()) return;

        if(sourceTimer <= 0f){
            refreshSources();
            sourceTimer = SOURCE_REFRESH_TICKS;
        }else{
            sourceTimer -= Time.delta;
        }

        float dt = Time.delta / 60f;
        for(Unit unit : Groups.unit){
            if(unit == null || unit.dead()) continue;
            updateUnit(unit, dt);
        }
    }

    private static void updateUnit(Unit unit, float dt){
        Track t = track(unit);
        int cls = classOf(unit);
        float resist = resistanceOf(unit);

        float source = sourceAt(unit);
        float item = unitItemStrength(unit);
        float incoming = (float)Math.sqrt(source * source + item * item);

        if(incoming > 0.0001f){
            t.pollution = Math.min(MAX_POLLUTION,
                t.pollution + incoming * GAIN_PER_SECOND * (1f - resist) * dt);
        }else{
            float halfLife = DECAY_HALF_LIFE / (1f + resist * 1.5f);
            t.pollution *= (float)Math.pow(0.5, dt / halfLife);
            if(t.pollution < 0.01f) t.pollution = 0f;
        }

        int level = levelOf(t.pollution);

        // 生命伤害：生物全额，装甲减半，机械免疫；高阶单位有有限存活优势。
        if(level > NONE && cls != MECHANICAL){
            float tierFactor = 1f - Mathf.clamp((unit.type.health - 500f) / 10000f, 0f, 0.3f);
            float amount = unit.maxHealth() * DAMAGE_PER_SECOND[level] * tierFactor * dt;
            if(cls == ARMORED) amount *= 0.5f;
            if(amount > 0f) unit.damagePierce(amount);
        }

        // 永久结构损伤：无专用抗性时，重度和致死各触发一次，只增不减。
        if(cls != BIOLOGICAL && resist < 0.15f && level >= SEVERE){
            float target = level >= LETHAL ? SCAR_LETHAL : SCAR_SEVERE;
            if(t.scar < target){
                t.scar = target;
                syncScar(unit, t);
            }
        }

        applyLevelStatus(unit, level);
        applyInterference(unit, t, cls, level, dt);
    }

    private static void applyLevelStatus(Unit unit, int level){
        StatusEffect wanted = switch(level){
            case MILD -> exposureMild;
            case MODERATE -> exposureModerate;
            case SEVERE -> exposureSevere;
            case LETHAL -> exposureLethal;
            default -> null;
        };

        StatusEffect[] all = {exposureMild, exposureModerate, exposureSevere, exposureLethal};
        for(StatusEffect effect : all){
            if(effect != null && effect != wanted && unit.hasEffect(effect)) unit.unapply(effect);
        }
        if(wanted != null) unit.apply(wanted, 2.5f);
    }

    private static void applyInterference(Unit unit, Track t, int cls, int level, float dt){
        boolean machine = cls != BIOLOGICAL;
        if(machine && level > NONE){
            float speed = switch(level){ case MILD -> 0.97f; case MODERATE -> 0.92f; case SEVERE -> 0.84f; default -> 0.70f; };
            float reload = switch(level){ case MILD -> 0.97f; case MODERATE -> 0.90f; case SEVERE -> 0.80f; default -> 0.60f; };
            // 装甲：移动/装填减益打七折（不等于免疫），电子干扰仍全额。
            if(cls == ARMORED){
                speed = 1f - (1f - speed) * 0.7f;
                reload = 1f - (1f - reload) * 0.7f;
            }

            StatusEntry e = unit.applyDynamicStatus();
            e.speedMultiplier = speed;
            e.reloadMultiplier = reload;
            t.dynamicApplied = true;

            if(level >= SEVERE){
                t.jamCooldown -= dt;
                if(t.jamCooldown <= 0f){
                    unit.apply(jam, level == LETHAL ? JAM_LETHAL : JAM_SEVERE);
                    t.jamCooldown = level == LETHAL ? JAM_LETHAL_COOLDOWN : JAM_SEVERE_COOLDOWN;
                }
            }
        }else if(t.dynamicApplied){
            // 只复位本系统写过的两项；外挂装甲的 armorOverride 不动。
            StatusEntry e = unit.applyDynamicStatus();
            e.speedMultiplier = 1f;
            e.reloadMultiplier = 1f;
            t.dynamicApplied = false;
        }
    }

    private static void syncScar(Unit unit, Track t){
        if(t.baseMax <= 0f) t.baseMax = unit.type == null ? unit.maxHealth : unit.type.health;
        float target = Math.max(1f, t.baseMax * (1f - t.scar));
        unit.maxHealth(target);
        if(unit.health > target) unit.health(target);

        // 两个永久状态只做“读档标记”，实际连续比例在 Track.scar 里。
        if(t.scar >= SCAR_LETHAL){
            if(!unit.hasEffect(scar35)) unit.apply(scar35, Float.POSITIVE_INFINITY);
            if(unit.hasEffect(scar15)) unit.unapply(scar15);
        }else if(t.scar >= SCAR_SEVERE){
            if(!unit.hasEffect(scar15)) unit.apply(scar15, Float.POSITIVE_INFINITY);
            if(unit.hasEffect(scar35)) unit.unapply(scar35);
        }else{
            if(unit.hasEffect(scar35)) unit.unapply(scar35);
            if(unit.hasEffect(scar15)) unit.unapply(scar15);
        }
    }

    private static void onUnitDestroyed(Unit unit){
        if(unit == null) return;
        Track t = tracks.remove(unit);
        if(t == null) return;

        float item = unitItemStrength(unit);
        boolean contaminated = t.pollution >= THRESHOLDS[SEVERE] || item > 0f || t.scar > 0f;
        if(!contaminated || Vars.world == null) return;

        float strength = Mathf.clamp(1f + item * 0.5f + t.pollution / 70f, 1f, 8f);
        float radius = Mathf.clamp(48f + t.pollution * 0.22f + item * 2f, 48f, 120f);
        IronworksNuclear.spawnFalloutCorium(unit.x, unit.y, unit.team, strength, radius);
    }

    private static float unitItemStrength(Unit unit){
        ItemStack stack = unit.stack;
        if(stack == null || stack.item == null || stack.amount <= 0 || stack.item.radioactivity <= 0f) return 0f;
        return Math.min(25f, stack.item.radioactivity * stack.amount * UNIT_ITEM_SCALE);
    }

    private static float buildingItemStrength(Building building){
        if(building.items == null || building.block == null || !building.block.hasItems) return 0f;
        float sum = 0f;
        for(int i = 0; i < radioactiveItems.size; i++){
            Item item = radioactiveItems.get(i);
            int amount = building.items.get(item);
            if(amount > 0) sum += item.radioactivity * amount * BUILDING_ITEM_SCALE;
        }
        return Math.min(BUILDING_ITEM_CAP, sum);
    }

    private static void refreshSources(){
        sources.clear();
        unitSources.clear();
        for(Building building : Groups.build){
            if(building == null || building.dead() || !building.isValid() || building.block == null) continue;

            float strength = 0f, radius = 0f;
            if(building instanceof Emitter emitter){
                strength = Math.max(0f, emitter.radiationStrength());
                radius = Math.max(1f, emitter.radiationRadius());
            }else{
                SourceDef def = blockSources.get(building.block);
                if(def != null){
                    strength = def.strength;
                    radius = def.radius;
                }
            }

            float item = buildingItemStrength(building);
            if(item > 0f){
                strength += item;
                radius = Math.max(radius, 48f);
            }

            if(strength > 0.0001f && radius > 0f){
                sources.add(new Source(building, strength, radius));
            }
        }

        // 致死级或携带放射性物品的单位也是源；自己不会被自己的单位源反向污染。
        for(Unit unit : Groups.unit){
            if(unit == null || unit.dead()) continue;
            float item = unitItemStrength(unit);
            float polluted = pollution(unit);
            if(item <= 0f && polluted < THRESHOLDS[LETHAL]) continue;

            float strength = item * 0.45f;
            if(polluted >= THRESHOLDS[LETHAL]){
                strength += 0.5f + Math.min(1f, (polluted - THRESHOLDS[LETHAL]) / 100f) * 0.5f;
            }
            float radius = Math.min(96f, 40f + polluted * 0.2f + item * 2f);
            if(strength > 0.0001f){
                unitSources.add(new UnitSource(unit, strength, radius));
            }
        }
    }

    private static float sourceAt(Unit unit){
        float sum = sourceAt(unit.x, unit.y);
        for(int i = 0; i < unitSources.size; i++){
            UnitSource source = unitSources.get(i);
            if(source.unit == unit || source.unit == null || source.unit.dead()) continue;

            float dx = source.unit.x - unit.x, dy = source.unit.y - unit.y;
            float dist = (float)Math.sqrt(dx * dx + dy * dy);
            if(dist >= source.radius) continue;
            float falloff = 1f - dist / source.radius;
            float value = source.strength * falloff * falloff;
            sum += value * value;
        }
        return (float)Math.sqrt(sum);
    }

    private static float sourceAt(float x, float y){
        float sum = 0f;
        for(int i = 0; i < sources.size; i++){
            Source source = sources.get(i);
            Building building = source.building;
            if(building == null || building.dead()) continue;

            float dist = (float)Math.sqrt((building.x - x) * (building.x - x) + (building.y - y) * (building.y - y));
            if(dist >= source.radius) continue;
            float falloff = 1f - dist / source.radius;
            float value = source.strength * falloff * falloff;
            sum += value * value;
        }
        return (float)Math.sqrt(sum);
    }

    /** 测试/调试：立即重建源缓存。 */
    public static void refreshSourcesNow(){
        refreshSources();
        sourceTimer = SOURCE_REFRESH_TICKS;
    }

    /** 测试/调试：采样某点的合成源强度（不推进单位）。 */
    public static float sourceStrengthAt(float x, float y){
        refreshSources();
        sourceTimer = SOURCE_REFRESH_TICKS;
        return sourceAt(x, y);
    }

    private static void clear(){
        tracks.clear();
        sources.clear();
        unitSources.clear();
        sourceTimer = 0f;
    }

    /** 给详情页/调试用的简短描述。 */
    public static String summary(Unit unit){
        int level = level(unit);
        String name = switch(level){
            case MILD -> "轻度";
            case MODERATE -> "中度";
            case SEVERE -> "重度";
            case LETHAL -> "致死";
            default -> "无";
        };
        return name + " (" + Mathf.round(pollution(unit)) + ")";
    }
}
