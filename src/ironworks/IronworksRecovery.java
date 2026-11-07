package ironworks;

import arc.struct.ObjectMap;
import mindustry.Vars;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.world.Block;
import mindustry.world.blocks.storage.CoreBlock.CoreBuild;
import ironworks.multicraft.MultiCrafter;
import ironworks.multicraft.Recipe;

/**
 * 资源匮乏升级后的回收：
 * 70% 回收物按“启动时注册的第一个产出配方”转化成一个非液体输入物品，
 * 剩余部分保留原物品；数量按 1:1 转化，不放大配方输入数量。
 */
public final class IronworksRecovery{
    private static final ObjectMap<Item, Item> precursor = new ObjectMap<>();
    private static boolean captured;

    private IronworksRecovery(){
    }

    public static void recover(CoreBuild core, Item item, int amount){
        if(core == null || item == null || amount <= 0) return;
        ensureIndex();

        float conversion = Difficulty.recoveryConversion();
        int converted = conversion > 0f ? (int)Math.floor(amount * conversion + 0.0001f) : 0;
        Item previous = precursor.get(item);
        if(converted > 0 && previous != null && previous != item){
            add(core, previous, converted);
            add(core, item, amount - converted);
        }else{
            add(core, item, amount);
        }
    }

    /** 拆除完成后把核心中已返还的 70% 原物品替换成上一级材料。 */
    public static void convertInCore(CoreBuild core, Item item, int amount){
        if(core == null || item == null || amount <= 0) return;
        ensureIndex();
        int converted = Difficulty.recoveryConversion() > 0f ? (int)Math.floor(amount * Difficulty.recoveryConversion() + 0.0001f) : 0;
        Item previous = precursor.get(item);
        if(converted <= 0 || previous == null || previous == item) return;
        int available = Math.min(converted, core.items.get(item));
        if(available <= 0) return;
        core.items.remove(item, available);
        add(core, previous, available);
    }

    private static void add(CoreBuild core, Item item, int amount){
        if(item == null || amount <= 0) return;
        int accepted = Math.max(0, Math.min(amount, core.storageCapacity - core.items.get(item)));
        if(accepted > 0) core.items.add(item, accepted);
    }

    private static void ensureIndex(){
        if(captured) return;
        precursor.clear();
        if(Vars.content != null){
            // content.blocks() 的注册顺序就是“启动时第一个配方”的顺序。
            for(Block block : Vars.content.blocks()){
                if(!(block instanceof MultiCrafter crafter)) continue;
                for(Recipe recipe : crafter.recipes){
                    if(recipe.outputs == null) continue;
                    for(ItemStack out : recipe.outputs){
                        if(out == null || out.item == null || precursor.containsKey(out.item)) continue;
                        Item input = firstItemInput(recipe);
                        if(input != null){ precursor.put(out.item, input); break; }
                    }
                }
            }
        }
        captured = true;
    }

    private static Item firstItemInput(Recipe recipe){
        if(recipe.inputs == null) return null;
        for(ItemStack in : recipe.inputs){
            if(in != null && in.item != null) return in.item;
        }
        return null;
    }
}
