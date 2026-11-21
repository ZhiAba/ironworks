package ironworks.unitmodule;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.ItemStack;

/** Pending graph edits with undo/redo, net cost, and atomic commit. */
public class ModuleEditSession{
    private static final int INSTALL = 0, UNINSTALL = 1, REPLACE = 2, MOVE = 3;

    private final Unit unit;
    private final Seq<Op> ops = new Seq<>();
    private final Seq<Op> redo = new Seq<>();

    private static class Op{
        int type;
        String instanceId;
        String moduleId;
        ModulePlacement placement;
        float x, y, rotation;
    }

    public ModuleEditSession(Unit unit){
        this.unit = unit;
    }

    public Unit unit(){
        return unit;
    }

    public ModuleEditSession install(String moduleId, ModulePlacement placement){
        Op op = new Op();
        op.type = INSTALL;
        op.moduleId = moduleId;
        op.placement = placement == null ? new ModulePlacement() : placement.copy();
        ops.add(op);
        redo.clear();
        return this;
    }

    public ModuleEditSession uninstall(String instanceId){
        Op op = new Op();
        op.type = UNINSTALL;
        op.instanceId = instanceId;
        ops.add(op);
        redo.clear();
        return this;
    }

    public ModuleEditSession replace(String instanceId, String moduleId){
        Op op = new Op();
        op.type = REPLACE;
        op.instanceId = instanceId;
        op.moduleId = moduleId;
        ops.add(op);
        redo.clear();
        return this;
    }

    public ModuleEditSession move(String instanceId, float x, float y, float rotation){
        Op op = new Op();
        op.type = MOVE;
        op.instanceId = instanceId;
        op.x = x;
        op.y = y;
        op.rotation = rotation;
        ops.add(op);
        redo.clear();
        return this;
    }

    public boolean undo(){
        if(ops.isEmpty()) return false;
        redo.add(ops.pop());
        return true;
    }

    public boolean redo(){
        if(redo.isEmpty()) return false;
        ops.add(redo.pop());
        return true;
    }

    public boolean isEmpty(){
        return ops.isEmpty();
    }

    public int size(){
        return ops.size;
    }

    public ObjectMap<Item, Integer> previewDelta(){
        ObjectMap<Item, Integer> delta = new ObjectMap<>();
        UnitModuleState state = UnitModuleRuntime.stateOf(unit);
        for(Op op : ops){
            if(op.type == INSTALL){
                ModuleSpec spec = ModuleCatalog.get(op.moduleId);
                add(delta, spec == null ? null : spec.cost, 1);
            }else if(op.type == UNINSTALL){
                ModuleNode node = state == null ? null : state.node(op.instanceId);
                ModuleSpec spec = node == null ? null : ModuleCatalog.get(node.moduleId);
                add(delta, spec == null ? null : spec.cost, -1);
            }else if(op.type == REPLACE){
                ModuleNode node = state == null ? null : state.node(op.instanceId);
                ModuleSpec oldSpec = node == null ? null : ModuleCatalog.get(node.moduleId);
                ModuleSpec newSpec = ModuleCatalog.get(op.moduleId);
                add(delta, oldSpec == null ? null : oldSpec.cost, -1);
                add(delta, newSpec == null ? null : newSpec.cost, 1);
            }
        }
        return delta;
    }

    public boolean commit(){
        if(unit == null || unit.dead || ops.isEmpty()) return false;
        UnitModuleState before = UnitModuleRuntime.stateOf(unit);
        UnitModuleState snapshot = before == null ? null : before.copy();
        ObjectMap<Item, Integer> delta = previewDelta();

        for(Op op : ops){
            boolean ok;
            switch(op.type){
                case INSTALL -> ok = UnitModuleRuntime.install(unit, op.moduleId, op.placement) != null;
                case UNINSTALL -> ok = UnitModuleRuntime.uninstall(unit, op.instanceId);
                case REPLACE -> ok = UnitModuleRuntime.replace(unit, op.instanceId, op.moduleId);
                case MOVE -> ok = UnitModuleRuntime.move(unit, op.instanceId, op.x, op.y, op.rotation);
                default -> ok = false;
            }
            if(!ok){
                rollback(snapshot);
                return false;
            }
        }

        Seq<ItemStack> charged = new Seq<>();
        for(ObjectMap.Entry e : delta){
            int amount = (Integer)e.value;
            if(amount <= 0) continue;
            ItemStack stack = new ItemStack((Item)e.key, amount);
            if(!ModuleEconomy.charge(unit, new ItemStack[]{stack})){
                for(ItemStack c : charged) ModuleEconomy.refund(unit, new ItemStack[]{c});
                rollback(snapshot);
                return false;
            }
            charged.add(stack);
        }
        for(ObjectMap.Entry e : delta){
            int amount = (Integer)e.value;
            if(amount < 0) ModuleEconomy.refund(unit, new ItemStack[]{new ItemStack((Item)e.key, -amount)});
        }

        ops.clear();
        redo.clear();
        return true;
    }

    public void cancel(){
        ops.clear();
        redo.clear();
    }

    private void rollback(UnitModuleState snapshot){
        if(snapshot == null){
            UnitModuleRegistry.remove(unit);
        }else{
            UnitModuleRegistry.put(unit, snapshot);
        }
        MountManager.captureBase(unit);
        UnitModuleRuntime.queue(unit);
        UnitModuleRuntime.applyStats(unit);
    }

    private static void add(ObjectMap<Item, Integer> delta, ItemStack[] cost, int sign){
        if(cost == null) return;
        for(ItemStack stack : cost){
            delta.put(stack.item, delta.get(stack.item, 0) + stack.amount * sign);
        }
    }
}