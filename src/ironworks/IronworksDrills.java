package ironworks;

import arc.struct.ObjectMap;
import mindustry.Vars;
import mindustry.world.Block;
import mindustry.world.blocks.production.Drill;

/**
 * 把资源匮乏/采矿倍率作用到原版和模组钻头。
 * 原版 Drill 没有队伍级的产出倍率字段，因此直接缩放 drillTime；
 * UI 的钻速统计读取的也是这个字段，重新打开详情即可看到变化。
 */
public final class IronworksDrills{
    private static final ObjectMap<Drill, Float> baseTimes = new ObjectMap<>();
    private static boolean captured;

    private IronworksDrills(){
    }

    public static void apply(){
        if(Vars.content == null) return;
        if(!captured || baseTimes.size == 0){
            baseTimes.clear();
            for(Block block : Vars.content.blocks()){
                if(block instanceof Drill drill){
                    baseTimes.put(drill, Math.max(0.0001f, drill.drillTime));
                }
            }
            captured = baseTimes.size > 0;
        }
        if(baseTimes.size == 0) return;
        float multiplier = Math.max(0.05f, Difficulty.miningMultiplier());
        for(Block block : Vars.content.blocks()){
            if(block instanceof Drill drill){
                float base = baseTimes.get(drill, drill.drillTime);
                drill.drillTime = base / multiplier;
            }
        }
    }
}
