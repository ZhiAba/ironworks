package ironworks.unitmodule;

import arc.Events;
import arc.struct.IntMap;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType;
import mindustry.gen.Unit;
import mindustry.io.SaveVersion;
import mindustry.net.Net;
import mindustry.type.Weapon;

/** Public facade for the unit module graph. */
public class UnitModuleRuntime{
    public static final String SAVE_CHUNK = "ironworks-unit-modules";
    private static boolean initialized;
    private static long nextNodeId = 1;
    private static final Seq<Unit> rebuildQueue = new Seq<>();
    private static final Seq<ModuleModifierSource> modifierSources = new Seq<>();
    private static final IntMap<UnitModuleState> pendingRemote = new IntMap<>();
    private static long lastModifierRevision = Long.MIN_VALUE;

    public static void init(){
        if(initialized) return;
        initialized = true;

        Events.on(EventType.UnitCreateEvent.class, e -> {
            MountManager.captureBase(e.unit);
            UnitModuleRegistry.ensure(e.unit);
            UnitModuleState pending = pendingRemote.remove(e.unit.id);
            if(pending != null) applyRemote(e.unit, pending);
        });

        Events.on(EventType.UnitDestroyEvent.class, e -> {
            removeSourcesFrom(e.unit);
            UnitModuleRegistry.remove(e.unit);
            MountManager.remove(e.unit);
        });

        Events.on(EventType.UnitChangeEvent.class, e -> {
            if(e.unit == null) return;
            MountManager.resetBase(e.unit);
            queue(e.unit);
        });

        Events.on(EventType.WorldLoadEvent.class, e -> {
            UnitModuleRegistry.clear();
            MountManager.clear();
            pendingRemote.clear();
            rebuildQueue.clear();
            nextNodeId = 1;
            lastModifierRevision = Long.MIN_VALUE;
        });

        Events.run(EventType.Trigger.update, UnitModuleRuntime::flush);
        Events.run(EventType.Trigger.draw, ModuleVisualRenderer::drawAll);

        SaveVersion.addCustomChunk(SAVE_CHUNK, new UnitModuleSaveChunk());
        Net.registerPacket(UnitModuleSyncPacket::new);
    }

    public static UnitModuleState stateOf(Unit unit){
        return UnitModuleRegistry.get(unit);
    }

    public static UnitModuleState ensure(Unit unit){
        return UnitModuleRegistry.ensure(unit);
    }

    public static ModuleNode install(Unit unit, String moduleId, float x, float y){
        return install(unit, moduleId, new ModulePlacement(x, y), "base");
    }

    public static ModuleNode install(Unit unit, String moduleId, float x, float y, String region){
        return install(unit, moduleId, new ModulePlacement(x, y).region(region), "base");
    }

    public static ModuleNode install(Unit unit, String moduleId, ModulePlacement placement){
        return install(unit, moduleId, placement, "base");
    }

    public static ModuleNode install(Unit unit, String moduleId, ModulePlacement placement, String sourceId){
        if(unit == null || unit.dead) return null;
        ModuleSpec spec = ModuleCatalog.get(moduleId);
        if(spec == null){
            Log.warn("unit-module: unknown module '@'", moduleId);
            return null;
        }
        UnitModuleState state = ensure(unit);
        ModulePlacement place = placement == null ? new ModulePlacement() : placement.copy();

        if(place.parentId != null && !"root".equals(place.parentId)){
            ModuleNode parent = state.node(place.parentId);
            if(parent == null){
                Log.warn("unit-module: parent '@' not found for '@'", place.parentId, moduleId);
                return null;
            }
            ModuleSpec parentSpec = ModuleCatalog.get(parent.moduleId);
            if(parentSpec == null) return null;
            if(place.socketId != null){
                ModuleSocket socket = findSocket(unit, state, parent, parentSpec, place.socketId);
                if(socket == null){
                    Log.warn("unit-module: socket '@' not found on '@'", place.socketId, parent.moduleId);
                    return null;
                }
                if(!socket.accepts(spec)){
                    Log.warn("unit-module: socket '@' rejects module '@'", socket.id, moduleId);
                    return null;
                }
                place.x += socket.x;
                place.y += socket.y;
                place.rotation += socket.rotation;
                place.scale *= socket.scale;
            }
        }else{
            place.parentId = "root";
        }

        if(depthOf(state, place.parentId) >= 8){
            Log.warn("unit-module: nesting depth limit reached for '@'", moduleId);
            return null;
        }

        ModuleSource source = null;
        if(sourceId != null && !"base".equals(sourceId)){
            source = state.source(sourceId);
            if(source == null){
                source = new ModuleSource(sourceId, "attachment", 10, true);
                state.sources.add(source);
            }
        }

        ModuleNode node = new ModuleNode("n" + (nextNodeId++), moduleId);
        node.parentId = place.parentId;
        node.socketId = place.socketId;
        node.sourceId = sourceId == null ? "base" : sourceId;
        node.x = place.x;
        node.y = place.y;
        node.rotation = place.rotation;
        node.scale = place.scale;
        node.z = place.z;
        node.regionOverride = place.region;
        node.tint = place.tint;
        node.mirror = place.mirror;
        state.nodes.add(node);
        if(source != null) source.nodeIds.add(node.instanceId);

        ModuleContext ctx = new ModuleContext(unit, state, node, spec);
        for(ModuleBehavior behavior : spec.behaviors) behavior.onInstall(ctx);

        state.touch();
        queue(unit);
        applyStats(unit);
        changed(unit, state);
        return node;
    }

    public static boolean uninstall(Unit unit, String instanceId){
        UnitModuleState state = stateOf(unit);
        if(state == null) return false;
        ModuleNode root = state.node(instanceId);
        if(root == null) return false;

        Seq<ModuleNode> remove = new Seq<>();
        collectSubtree(state, root.instanceId, remove);

        for(int i = remove.size - 1; i >= 0; i--){
            ModuleNode node = remove.get(i);
            ModuleSpec spec = ModuleCatalog.get(node.moduleId);
            if(spec != null){
                ModuleContext ctx = new ModuleContext(unit, state, node, spec);
                for(ModuleBehavior behavior : spec.behaviors) behavior.onUninstall(ctx);
            }
            ModuleSource source = state.source(node.sourceId);
            if(source != null) source.nodeIds.remove(node.instanceId);
            state.nodes.remove(node);
        }

        for(int i = state.sources.size - 1; i >= 0; i--){
            if(state.sources.get(i).nodeIds.isEmpty() && !"base".equals(state.sources.get(i).id)){
                state.sources.remove(i);
            }
        }

        state.touch();
        queue(unit);
        applyStats(unit);
        changed(unit, state);
        return true;
    }

    public static boolean replace(Unit unit, String instanceId, String moduleId){
        UnitModuleState state = stateOf(unit);
        ModuleNode node = state == null ? null : state.node(instanceId);
        ModuleSpec spec = ModuleCatalog.get(moduleId);
        if(node == null || spec == null) return false;

        Seq<ModuleNode> children = new Seq<>();
        for(ModuleNode child : state.children(node.instanceId)) collectSubtree(state, child.instanceId, children);
        for(int i = children.size - 1; i >= 0; i--) uninstall(unit, children.get(i).instanceId);

        ModuleSpec oldSpec = ModuleCatalog.get(node.moduleId);
        if(oldSpec != null){
            ModuleContext oldCtx = new ModuleContext(unit, state, node, oldSpec);
            for(ModuleBehavior behavior : oldSpec.behaviors) behavior.onUninstall(oldCtx);
        }

        node.moduleId = moduleId;
        ModuleContext ctx = new ModuleContext(unit, state, node, spec);
        for(ModuleBehavior behavior : spec.behaviors) behavior.onInstall(ctx);

        state.touch();
        queue(unit);
        applyStats(unit);
        changed(unit, state);
        return true;
    }

    public static boolean move(Unit unit, String instanceId, float x, float y, float rotation){
        UnitModuleState state = stateOf(unit);
        ModuleNode node = state == null ? null : state.node(instanceId);
        if(node == null) return false;
        node.x = x;
        node.y = y;
        node.rotation = rotation;
        state.touch();
        queue(unit);
        changed(unit, state);
        return true;
    }

    public static ModuleSource attachSource(Unit host, Unit sourceUnit, String sourceId, String moduleId, ModulePlacement placement){
        if(host == null) return null;
        UnitModuleState state = ensure(host);
        ModuleSource source = state.source(sourceId);
        if(source == null){
            source = new ModuleSource(sourceId, "attachment", 10, true);
            if(sourceUnit != null) source.unit(sourceUnit.id);
            state.sources.add(source);
        }
        ModuleNode node = install(host, moduleId, placement, sourceId);
        return node == null ? null : source;
    }

    public static boolean detachSource(Unit host, String sourceId){
        UnitModuleState state = stateOf(host);
        ModuleSource source = state == null ? null : state.source(sourceId);
        if(source == null) return false;
        Seq<String> ids = new Seq<>(source.nodeIds);
        for(String id : ids) uninstall(host, id);
        return true;
    }

    public static void removeSourcesFrom(Unit sourceUnit){
        if(sourceUnit == null) return;
        for(Unit host : mindustry.gen.Groups.unit){
            UnitModuleState state = stateOf(host);
            if(state == null) continue;
            Seq<String> toRemove = new Seq<>();
            for(ModuleSource source : state.sources){
                if(source.sourceUnitId == sourceUnit.id) toRemove.add(source.id);
            }
            for(String id : toRemove) detachSource(host, id);
        }
    }

    public static ModuleStatSet statsOf(Unit unit){
        ModuleStatSet stats = new ModuleStatSet();
        UnitModuleState state = stateOf(unit);
        if(state == null) return stats;
        for(ModuleNode node : state.nodes){
            ModuleSpec spec = ModuleCatalog.get(node.moduleId);
            if(spec == null) continue;
            stats.add(spec.collectStats(new ModuleContext(unit, state, node, spec)));
        }
        return stats;
    }

    public static void applyStats(Unit unit){
        if(unit == null) return;
        UnitModuleState state = stateOf(unit);
        if(state == null || state.nodes.isEmpty()){
            if(UnitModuleRegistry.hasBaseArmor(unit)){
                unit.armor = UnitModuleRegistry.baseArmor(unit, unit.armor);
            }
            return;
        }
        if(!UnitModuleRegistry.hasBaseArmor(unit)){
            UnitModuleRegistry.setBaseArmor(unit, unit.armor);
        }
        ModuleStatSet stats = statsOf(unit);
        unit.armor = UnitModuleRegistry.baseArmor(unit, unit.armor) + stats.armorAdd;
    }

    public static void queue(Unit unit){
        if(unit != null && !rebuildQueue.contains(unit)) rebuildQueue.add(unit);
    }

    public static void flush(){
        if(!rebuildQueue.isEmpty()){
            Seq<Unit> units = new Seq<>(rebuildQueue);
            rebuildQueue.clear();
            for(Unit unit : units){
                if(unit == null || unit.dead || !unit.isAdded()) continue;
                UnitModuleState state = stateOf(unit);
                if(state != null) MountManager.rebuild(unit, state);
            }
        }
        pollModifiers();
    }

    public static void rebuildNow(Unit unit){
        if(unit == null) return;
        UnitModuleState state = stateOf(unit);
        if(state != null && !unit.dead && unit.isAdded()){
            MountManager.rebuild(unit, state);
            applyStats(unit);
        }
    }

    public static void refreshAll(){
        for(Unit unit : mindustry.gen.Groups.unit){
            rebuildNow(unit);
        }
    }

    public static void registerModifierSource(ModuleModifierSource source){
        if(source != null && !modifierSources.contains(source)) modifierSources.add(source);
    }

    public static void modifyWeapon(Weapon weapon, ModuleContext ctx){
        for(ModuleModifierSource source : modifierSources) source.modify(weapon, ctx);
    }

    private static void pollModifiers(){
        if(modifierSources.isEmpty()) return;
        long revision = 0;
        for(ModuleModifierSource source : modifierSources) revision = revision * 31 + source.revision();
        if(revision != lastModifierRevision){
            lastModifierRevision = revision;
            refreshAll();
        }
    }

    private static void changed(Unit unit, UnitModuleState state){
        Events.fire(new UnitModuleEvents.ModuleChangedEvent(unit, state, state.revision));
        sendSync(unit, state);
    }

    private static void sendSync(Unit unit, UnitModuleState state){
        if(Vars.net == null || !Vars.net.active()) return;
        Vars.net.send(new UnitModuleSyncPacket(unit.id, state.copy()), true);
    }

    public static void applyRemote(int unitId, UnitModuleState state){
        Unit unit = mindustry.gen.Groups.unit.getByID(unitId);
        if(unit == null){
            pendingRemote.put(unitId, state);
            return;
        }
        applyRemote(unit, state);
    }

    private static void applyRemote(Unit unit, UnitModuleState state){
        UnitModuleState local = stateOf(unit);
        if(local != null && local.revision > state.revision) return;
        boolean hadLocal = local != null;
        bumpNodeId(state);
        UnitModuleRegistry.put(unit, state);
        if(!hadLocal) MountManager.captureBase(unit);
        queue(unit);
        applyStats(unit);
    }

    public static void bumpNodeId(UnitModuleState state){
        for(ModuleNode node : state.nodes){
            if(node.instanceId != null && node.instanceId.startsWith("n")){
                try{
                    long id = Long.parseLong(node.instanceId.substring(1));
                    if(id >= nextNodeId) nextNodeId = id + 1;
                }catch(NumberFormatException ignored){}
            }
        }
    }

    public static Seq<ModuleModifierSource> modifierSources(){
        return modifierSources;
    }

    public static Seq<Unit> rebuildQueue(){
        return rebuildQueue;
    }

    public static IntMap<UnitModuleState> pendingRemote(){
        return pendingRemote;
    }

    private static ModuleSocket findSocket(Unit unit, UnitModuleState state, ModuleNode parent, ModuleSpec parentSpec, String socketId){
        Seq<ModuleSocket> sockets = parentSpec.collectSockets(new ModuleContext(unit, state, parent, parentSpec));
        for(ModuleSocket socket : sockets){
            if(socket.id.equals(socketId)) return socket;
        }
        return null;
    }

    private static void collectSubtree(UnitModuleState state, String rootId, Seq<ModuleNode> out){
        ModuleNode node = state.node(rootId);
        if(node == null) return;
        out.add(node);
        for(ModuleNode child : state.children(rootId)) collectSubtree(state, child.instanceId, out);
    }

    private static int depthOf(UnitModuleState state, String parentId){
        int depth = 0;
        ModuleNode cur = state.node(parentId);
        while(cur != null && depth < 32){
            depth++;
            cur = state.node(cur.parentId);
        }
        return depth;
    }

}