package ironworks.unitmodule;

import arc.Core;
import arc.struct.IntMap;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.entities.units.WeaponMount;
import mindustry.gen.Unit;
import mindustry.type.Weapon;

/**
 * Owns the mapping between module graph weapon behaviors and unit.mounts.
 *
 * <p>The array is rebuilt only during a structural flush, never while units are
 * being iterated. Base mounts are preserved; module mounts are appended.</p>
 */
public class MountManager{
    private static final IntMap<MountState> mountStates = new IntMap<>();

    private static class MountState{
        WeaponMount[] base;
        final ObjectMap<String, WeaponMount> managed = new ObjectMap<>();
    }

    public static void captureBase(Unit unit){
        if(unit == null) return;
        MountState state = mountStates.get(unit.id);
        if(state == null){
            state = new MountState();
            mountStates.put(unit.id, state);
        }
        state.base = unit.mounts == null ? new WeaponMount[0] : unit.mounts.clone();
        state.managed.clear();
    }

    public static void resetBase(Unit unit){
        if(unit == null) return;
        MountState state = mountStates.get(unit.id);
        if(state == null){
            captureBase(unit);
            return;
        }
        state.base = unit.type == null ? new WeaponMount[0] : new WeaponMount[unit.type.weapons.size];
        for(int i = 0; i < state.base.length; i++){
            Weapon weapon = unit.type.weapons.get(i).copy();
            weapon.mirror = false;
            weapon.init();
            state.base[i] = new WeaponMount(weapon);
        }
        state.managed.clear();
    }

    public static void remove(Unit unit){
        if(unit != null) mountStates.remove(unit.id);
    }

    public static void clear(){
        mountStates.clear();
    }

    public static void rebuild(Unit unit, UnitModuleState graph){
        if(unit == null || graph == null) return;
        MountState state = mountStates.get(unit.id);
        if(state == null){
            captureBase(unit);
            state = mountStates.get(unit.id);
        }

        Seq<WeaponMount> result = new Seq<>();
        if(state.base != null){
            for(WeaponMount mount : state.base){
                if(mount != null) result.add(mount);
            }
        }

        Seq<String> liveKeys = new Seq<>();
        collectWeapons(unit, graph, "root", result, liveKeys, state, 0);

        for(String key : state.managed.keys().toSeq()){
            if(!liveKeys.contains(key)) state.managed.remove(key);
        }

        unit.mounts = result.toArray(WeaponMount.class);
    }

    private static void collectWeapons(Unit unit, UnitModuleState graph, String parentId, Seq<WeaponMount> result,
                                       Seq<String> liveKeys, MountState mountState, int depth){
        if(depth > 8) return;
        for(ModuleNode node : graph.children(parentId)){
            ModuleSpec spec = ModuleCatalog.get(node.moduleId);
            if(spec == null) continue;
            ModuleContext ctx = new ModuleContext(unit, graph, node, spec);
            Seq<MountSpec> specs = spec.collectMounts(ctx);
            for(int i = 0; i < specs.size; i++){
                MountSpec mountSpec = specs.get(i);
                String mountId = mountSpec.id == null ? String.valueOf(i) : mountSpec.id;
                String key = "module:" + node.instanceId + ":" + mountId;
                liveKeys.add(key);

                WeaponMount old = mountState.managed.get(key);
                Weapon weapon = mountSpec.factory.get();
                weapon.name = spec.id + "/" + mountId;
                weapon.x = node.x + mountSpec.x;
                weapon.y = node.y + mountSpec.y;
                weapon.shootX = mountSpec.shootX;
                weapon.shootY = mountSpec.shootY;
                weapon.mirror = mountSpec.mirror;
                UnitModuleRuntime.modifyWeapon(weapon, new ModuleContext(unit, graph, node, spec));
                weapon.init();
                if(!Vars.headless && Core.atlas != null){
                    weapon.load();
                }

                WeaponMount mount = new WeaponMount(weapon);
                if(old != null) copyMount(old, mount);
                mountState.managed.put(key, mount);
                result.add(mount);
            }
            collectWeapons(unit, graph, node.instanceId, result, liveKeys, mountState, depth + 1);
        }
    }

    private static void copyMount(WeaponMount from, WeaponMount to){
        try{
            to.reload = from.reload;
            to.rotation = from.rotation;
            to.recoil = from.recoil;
            to.recoils = from.recoils;
            to.targetRotation = from.targetRotation;
            to.heat = from.heat;
            to.warmup = from.warmup;
            to.charging = from.charging;
            to.charge = from.charge;
            to.smoothReload = from.smoothReload;
            to.aimX = from.aimX;
            to.aimY = from.aimY;
            to.shoot = from.shoot;
            to.allowShootEffects = from.allowShootEffects;
            to.rotate = from.rotate;
            to.side = from.side;
            to.totalShots = from.totalShots;
            to.barrelCounter = from.barrelCounter;
            to.lastLength = from.lastLength;
            to.target = from.target;
            to.retarget = from.retarget;
        }catch(Throwable ignored){
            // A future game version may remove a field; state copy is best-effort.
        }
    }
}