package ironworks.unitmodule;

import arc.struct.ObjectMap;
import mindustry.Vars;
import mindustry.gen.Unit;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.world.blocks.storage.CoreBlock;

/** Core-team item economy for module edits. */
public class ModuleEconomy{
    public static boolean infinite(Unit unit){
        return Vars.state.rules.infiniteResources || unit.team.rules().infiniteResources;
    }

    public static boolean canAfford(Unit unit, ItemStack[] cost){
        if(unit == null || cost == null || cost.length == 0) return true;
        if(infinite(unit)) return true;
        CoreBlock.CoreBuild core = unit.team.core();
        return core != null && core.items.has(cost);
    }

    public static boolean charge(Unit unit, ItemStack[] cost){
        if(unit == null || cost == null || cost.length == 0 || infinite(unit)) return true;
        CoreBlock.CoreBuild core = unit.team.core();
        if(core == null || !core.items.has(cost)) return false;
        core.items.remove(cost);
        return true;
    }

    public static void refund(Unit unit, ItemStack[] cost){
        if(unit == null || cost == null || cost.length == 0 || infinite(unit)) return;
        CoreBlock.CoreBuild core = unit.team.core();
        if(core == null) return;
        for(ItemStack stack : cost) core.items.add(stack.item, stack.amount);
    }

    public static ItemStack[] toStacks(ObjectMap<Item, Integer> delta){
        arc.struct.Seq<ItemStack> out = new arc.struct.Seq<>();
        for(ObjectMap.Entry e : delta){
            int amount = (Integer)e.value;
            if(amount > 0) out.add(new ItemStack((Item)e.key, amount));
        }
        return out.toArray(ItemStack.class);
    }
}