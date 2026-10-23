package ironworks.core;

import mindustry.type.Liquid;
import mindustry.world.Block;

/**
 * 铁流核心组件库 · 状态条层。
 *
 * <p>引擎会给带 {@code ConsumeLiquid} 的方块自动加一条 `liquid` 条，
 * 它显示的是 `liquids.current()`，多液体方块上会来回跳。
 * 统一做法：先删掉通用条，再给每种液体单独加一条。
 */
public final class CoreBars{

    private CoreBars(){
    }

    /** 只删掉引擎自动加的通用液体条。 */
    public static void removeGenericLiquidBar(Block block){
        if(block != null) block.removeBar("liquid");
    }

    /** 删通用条 + 每种液体各一条（null 跳过，顺序即显示顺序）。 */
    public static void perLiquid(Block block, Liquid... liquids){
        removeGenericLiquidBar(block);
        if(liquids == null) return;
        for(Liquid l : liquids){
            if(l != null) block.addLiquidBar(l);
        }
    }
}
