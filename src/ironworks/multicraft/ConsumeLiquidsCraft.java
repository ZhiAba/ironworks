package ironworks.multicraft;

import arc.func.Func;
import arc.scene.ui.layout.Table;
import mindustry.gen.Building;
import mindustry.type.LiquidStack;
import mindustry.world.Block;
import mindustry.world.consumers.Consume;

/**
 * 按「每 craft」扣液体的消耗器。
 *
 * 原版 ConsumeLiquidsDynamic 是每 tick 连续扣（update=true），而产出只在 craft 完成时发生；
 * 输入中途断供时，已经扣掉但没完成的进度会被浪费，冷却循环里表现为「液体凭空蒸发」。
 * 这里改成和物品一致：只在 craft() 的 consume() 里一次性 trigger，1:1 不丢。
 */
public class ConsumeLiquidsCraft extends Consume{
    public final Func<Building, LiquidStack[]> liquids;

    @SuppressWarnings("unchecked")
    public <T extends Building> ConsumeLiquidsCraft(Func<T, LiquidStack[]> liquids){
        this.liquids = (Func<Building, LiquidStack[]>)liquids;
        this.update = false; // 只在 craft() 里 trigger
    }

    @Override
    public void apply(Block block){
        block.hasLiquids = true;
    }

    @Override
    public void build(Building build, Table table){
        // UI 复用动态消耗器的显示即可，这里简单列图标
        table.clear();
        int i = 0;
        for(LiquidStack stack : liquids.get(build)){
            table.add(new arc.scene.ui.Image(stack.liquid.uiIcon)).size(32f).padRight(6f);
            if(++i % 4 == 0) table.row();
        }
    }

    @Override
    public void trigger(Building build){
        for(LiquidStack stack : liquids.get(build)){
            build.liquids.remove(stack.liquid, stack.amount);
        }
    }

    @Override
    public float efficiency(Building build){
        float min = 1f;
        for(LiquidStack stack : liquids.get(build)){
            if(stack.amount <= 0.0001f) continue;
            min = Math.min(min, Math.min(build.liquids.get(stack.liquid) / stack.amount, 1f));
        }
        return min;
    }
}
