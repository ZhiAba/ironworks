package ironworks;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.io.Reads;
import arc.util.io.Writes;
import ironworks.unitmodule.*;
import ironworks.unitmodule.adapter.AttachmentModuleAdapter;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.content.UnitTypes;
import mindustry.entities.bullet.BasicBulletType;
import mindustry.gen.Unit;
import mindustry.type.ItemStack;
import mindustry.type.Weapon;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/** Headless self-check entry points for T237-T245. Not player-facing content. */
public class IronworksUnitModuleTests{
    private static final String WEAPON_A = "test-module-weapon-a";
    private static final String WEAPON_B = "test-module-weapon-b";
    private static final String ARMOR = "test-module-armor";
    private static final String SOCKET = "test-module-socket";

    static{
        registerContent();
    }

    private static void registerContent(){
        if(ModuleCatalog.has(WEAPON_A)) return;

        ModuleCatalog.register(new ModuleSpec(WEAPON_A, "Test Gun A")
            .tag("weapon")
            .behavior(new WeaponBehavior().add(new MountSpec("main", () -> gun("test-a", 10f), 0f, 0f)))
            .cost(new ItemStack(Items.copper, 10)));

        ModuleCatalog.register(new ModuleSpec(WEAPON_B, "Test Gun B")
            .tag("weapon")
            .behavior(new WeaponBehavior().add(new MountSpec("main", () -> gun("test-b", 20f), 0f, 0f)))
            .cost(new ItemStack(Items.copper, 20)));

        ModuleCatalog.register(new ModuleSpec(ARMOR, "Test Armor")
            .tag("armor")
            .behavior(new StatBehavior().armor(12f).health(1.15f))
            .visual(new ModuleVisual("test-armor").at(0f, 0f)));

        ModuleCatalog.register(new ModuleSpec(SOCKET, "Test Socket")
            .behavior(new SocketBehavior().add(new ModuleSocket("top", 0f, 7f).accepts("weapon"))));
    }

    private static Weapon gun(String name, float damage){
        BasicBulletType bullet = new BasicBulletType(5f, damage, "bullet");
        bullet.lifetime = 30f;
        bullet.collidesAir = true;
        bullet.collidesGround = true;
        Weapon weapon = new Weapon(name);
        weapon.bullet = bullet;
        weapon.reload = 10f;
        weapon.mirror = false;
        weapon.shootX = 0f;
        weapon.shootY = 0f;
        return weapon;
    }

    public static String t237(){
        return safe("T237", () -> {
            Unit unit = spawn(UnitTypes.mace);
            int base = unit.mounts.length;
            ModuleNode a = UnitModuleRuntime.install(unit, WEAPON_A, 8f, 2f, "test-gun-a");
            ModuleNode b = UnitModuleRuntime.install(unit, WEAPON_B, -8f, 2f, "test-gun-b");
            UnitModuleRuntime.flush();
            boolean grew = a != null && b != null && unit.mounts.length == base + 2;
            int nodes = UnitModuleRuntime.stateOf(unit).nodes.size;
            UnitModuleRuntime.uninstall(unit, a.instanceId);
            UnitModuleRuntime.flush();
            boolean shrank = unit.mounts.length == base + 1;
            boolean baseKept = true;
            for(int i = 0; i < base; i++) baseKept &= unit.mounts[i] != null;
            cleanup(unit);
            return "T237 base=" + base + " grew=" + grew + " shrank=" + shrank + " nodes=" + nodes
                + " baseKept=" + baseKept + " ok=" + (grew && shrank && nodes == 2 && baseKept);
        });
    }

    public static String t238(){
        return safe("T238", () -> {
            Unit unit = spawn(UnitTypes.dagger);
            int base = unit.mounts.length;
            ModuleNode node = UnitModuleRuntime.install(unit, WEAPON_A, 6f, 0f);
            UnitModuleRuntime.flush();
            unit.mounts[base].reload = 42f;
            unit.mounts[base].rotation = 7f;

            boolean replaced = UnitModuleRuntime.replace(unit, node.instanceId, WEAPON_B);
            UnitModuleRuntime.flush();
            boolean preserved = Math.abs(unit.mounts[base].reload - 42f) < 0.01f
                && Math.abs(unit.mounts[base].rotation - 7f) < 0.01f;
            boolean invalid = !UnitModuleRuntime.replace(unit, node.instanceId, "missing-module");
            boolean revision = UnitModuleRuntime.stateOf(unit).revision > 0;
            boolean mounts = unit.mounts.length == base + 1;
            cleanup(unit);
            return "T238 replaced=" + replaced + " preserved=" + preserved + " invalid=" + invalid
                + " mounts=" + mounts + " rev=" + revision + " ok=" + (replaced && preserved && invalid && mounts && revision);
        });
    }

    public static String t239(){
        return safe("T239", () -> {
            Unit unit = spawn(UnitTypes.dagger);
            float before = unit.armor;
            ModuleNode armor = UnitModuleRuntime.install(unit, ARMOR, 5f, -2f, "test-armor-side");
            UnitModuleRuntime.flush();
            float after = unit.armor;
            boolean stats = Math.abs(after - (before + 12f)) < 0.01f;
            boolean region = armor != null && "test-armor-side".equals(armor.regionOverride);
            boolean coords = armor != null && Math.abs(armor.x - 5f) < 0.01f && Math.abs(armor.y + 2f) < 0.01f;
            boolean healthMul = Math.abs(UnitModuleRuntime.statsOf(unit).healthMul - 1.15f) < 0.001f;
            UnitModuleRuntime.uninstall(unit, armor.instanceId);
            UnitModuleRuntime.flush();
            boolean restored = Math.abs(unit.armor - before) < 0.01f;
            cleanup(unit);
            return "T239 armor=" + before + "->" + after + " region=" + region + " coords=" + coords
                + " healthMul=" + healthMul + " restored=" + restored
                + " ok=" + (stats && region && coords && healthMul && restored);
        });
    }

    public static String t240(){
        return safe("T240", () -> {
            Unit unit = spawn(UnitTypes.dagger);
            int base = unit.mounts.length;
            ModuleNode parent = UnitModuleRuntime.install(unit, SOCKET, 0f, 0f);
            ModulePlacement childPlace = new ModulePlacement(0f, 0f).parent(parent.instanceId).socket("top");
            ModuleNode child = UnitModuleRuntime.install(unit, WEAPON_A, childPlace);
            UnitModuleRuntime.flush();
            boolean nested = child != null && UnitModuleRuntime.stateOf(unit).node(child.instanceId) != null;
            boolean mount = unit.mounts.length == base + 1;
            UnitModuleRuntime.uninstall(unit, parent.instanceId);
            UnitModuleRuntime.flush();
            boolean removed = UnitModuleRuntime.stateOf(unit).nodes.isEmpty() && unit.mounts.length == base;
            cleanup(unit);
            return "T240 nested=" + nested + " mount=" + mount + " removed=" + removed
                + " ok=" + (nested && mount && removed);
        });
    }

    public static String t241(){
        return safe("T241", () -> {
            Unit host = spawn(UnitTypes.dagger);
            Unit source = spawn(UnitTypes.dagger);
            ModuleSource attached = AttachmentModuleAdapter.attach(host, source, ARMOR, new ModulePlacement(3f, 3f));
            boolean attachedOk = attached != null && !UnitModuleRuntime.stateOf(host).nodes.isEmpty();
            boolean detached = AttachmentModuleAdapter.detach(host, attached.id)
                && UnitModuleRuntime.stateOf(host).nodes.isEmpty();
            AttachmentModuleAdapter.attach(host, source, ARMOR, new ModulePlacement(4f, 4f));
            source.destroy();
            UnitModuleRuntime.flush();
            boolean cleanup = UnitModuleRuntime.stateOf(host).nodes.isEmpty();
            cleanup(host);
            cleanup(source);
            return "T241 attached=" + attachedOk + " detached=" + detached + " sourceDeath=" + cleanup
                + " ok=" + (attachedOk && detached && cleanup);
        });
    }

    public static String t242(){
        return safe("T242", () -> {
            Unit unit = spawn(UnitTypes.dagger);
            UnitModuleRuntime.install(unit, WEAPON_A, 8f, 2f, "save-gun");
            UnitModuleRuntime.install(unit, ARMOR, 5f, -2f, "save-armor");
            UnitModuleRuntime.flush();
            UnitModuleState before = UnitModuleRuntime.stateOf(unit).copy();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            Writes writes = new Writes(new DataOutputStream(out));
            UnitModuleIO.write(writes, before);
            Reads reads = new Reads(new DataInputStream(new ByteArrayInputStream(out.toByteArray())));
            UnitModuleState after = UnitModuleIO.read(reads);

            boolean nodes = after.nodes.size == before.nodes.size;
            boolean revision = after.revision == before.revision;
            boolean region = "save-armor".equals(after.node(after.nodes.get(1).instanceId).regionOverride);
            boolean sources = after.sources.size == before.sources.size;
            cleanup(unit);
            return "T242 nodes=" + nodes + " rev=" + revision + " region=" + region + " sources=" + sources
                + " ok=" + (nodes && revision && region && sources);
        });
    }

    public static String t243(){
        return safe("T243", () -> {
            Unit unit = spawn(UnitTypes.dagger);
            UnitModuleRuntime.install(unit, WEAPON_A, 8f, 2f);
            UnitModuleRuntime.flush();
            UnitModuleState state = UnitModuleRuntime.stateOf(unit).copy();
            UnitModuleSyncPacket packet = new UnitModuleSyncPacket(unit.id, state);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            packet.write(new Writes(new DataOutputStream(out)));
            UnitModuleSyncPacket decoded = new UnitModuleSyncPacket();
            decoded.read(new Reads(new DataInputStream(new ByteArrayInputStream(out.toByteArray()))));

            boolean fields = decoded.unitId == unit.id && decoded.state.nodes.size == state.nodes.size
                && decoded.state.revision == state.revision;
            UnitModuleRuntime.pendingRemote().clear();
            UnitModuleRuntime.applyRemote(987654, state);
            boolean pending = UnitModuleRuntime.pendingRemote().containsKey(987654);
            UnitModuleRuntime.pendingRemote().clear();
            cleanup(unit);
            return "T243 fields=" + fields + " pending=" + pending + " ok=" + (fields && pending);
        });
    }

    public static String t244(){
        return safe("T244", () -> {
            Unit unit = spawn(UnitTypes.dagger);
            int base = unit.mounts.length;
            ModuleEditSession session = new ModuleEditSession(unit);
            session.install(WEAPON_A, new ModulePlacement(8f, 2f));
            boolean pending = session.size() == 1;
            session.undo();
            boolean undone = session.isEmpty();
            session.redo();
            boolean redone = session.size() == 1;
            ObjectMap<mindustry.type.Item, Integer> delta = session.previewDelta();
            boolean cost = delta.get(Items.copper, 0) == 10;
            boolean committed = session.commit();
            UnitModuleRuntime.flush();
            boolean mounted = unit.mounts.length == base + 1;
            boolean bad = new ModuleEditSession(unit).install("missing-module", new ModulePlacement(0f, 0f)).commit();
            cleanup(unit);
            return "T244 pending=" + pending + " undo=" + undone + " redo=" + redone + " cost=" + cost
                + " committed=" + committed + " mounted=" + mounted + " bad=" + bad
                + " ok=" + (pending && undone && redone && cost && committed && mounted && !bad);
        });
    }

    public static String t245(){
        return safe("T245", () -> {
            Unit unit = spawn(UnitTypes.dagger);
            int base = unit.mounts.length;
            final float[] revision = {1f};
            ModuleModifierSource source = new ModuleModifierSource(){
                @Override
                public long revision(){
                    return (long)revision[0];
                }

                @Override
                public void modify(Weapon weapon, ModuleContext ctx){
                    weapon.reload = 3f;
                }
            };
            UnitModuleRuntime.registerModifierSource(source);
            UnitModuleRuntime.install(unit, WEAPON_A, 8f, 2f);
            UnitModuleRuntime.flush();
            boolean applied = Math.abs(unit.mounts[base].weapon.reload - 3f) < 0.01f;
            revision[0] = 2f;
            unit.mounts[base].weapon.reload = 99f;
            UnitModuleRuntime.flush();
            boolean refreshed = Math.abs(unit.mounts[base].weapon.reload - 3f) < 0.01f;
            cleanup(unit);
            return "T245 applied=" + applied + " refreshed=" + refreshed + " ok=" + (applied && refreshed);
        });
    }

    private static Unit spawn(mindustry.type.UnitType type){
        int x = Math.floorDiv(Vars.world.width(), 2);
        int y = Math.floorDiv(Vars.world.height(), 2);
        Unit unit = type.spawn(Vars.state.rules.defaultTeam, x * 8f, y * 8f);
        unit.health = unit.maxHealth;
        return unit;
    }

    private static void cleanup(Unit unit){
        if(unit == null) return;
        try{
            UnitModuleRegistry.remove(unit);
            unit.remove();
        }catch(Throwable ignored){}
    }

    private static String safe(String id, java.util.function.Supplier<String> action){
        try{
            return action.get();
        }catch(Throwable t){
            return id + " error=" + t.getClass().getSimpleName() + ":" + String.valueOf(t.getMessage()) + " ok=false";
        }
    }
}