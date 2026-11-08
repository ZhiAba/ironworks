package ironworks;

import mindustry.game.SpawnGroup;
import mindustry.gen.Unit;
import mindustry.game.Team;
import arc.func.Cons;

/**
 * 波次数量按比例缩放，并使用“累计小数进位”而不是逐波独立四舍五入：
 * 6.75 -> 6 (存 0.75)；下一波 6.75+0.75=7.5 -> 7 (存 0.5)。
 *
 * <p>getSpawned 会被 WaveGraph/UI 等外部代码以不同 wave 调用，因此这里不能维护
 * 可变 credit；改为对累计值取 floor 后求差，保证任意调用都是纯函数。</p>
 */
public class DifficultySpawnGroup extends SpawnGroup{
    private transient SpawnGroup original;

    public DifficultySpawnGroup(){
    }

    public DifficultySpawnGroup(SpawnGroup source){
        this.original = source;
        this.type = source.type;
        this.begin = source.begin;
        this.end = source.end;
        this.spacing = source.spacing;
        this.max = source.max;
        this.unitScaling = source.unitScaling;
        this.unitAmount = source.unitAmount;
        this.shields = source.shields;
        this.shieldScaling = source.shieldScaling;
        this.spawn = source.spawn;
        this.effect = source.effect;
        this.items = source.items;
        this.team = source.team;
        this.payloads = source.payloads;
    }

    public boolean hasOriginal(){
        return original != null;
    }

    /** 保存前把包装器换回原始 SpawnGroup，避免 Json 尝试加载模组专属类名。 */
    public SpawnGroup original(){
        return original;
    }

    @Override
    public int getSpawned(int wave){
        if(original == null) return super.getSpawned(wave);
        return Math.max(0, cumulative(wave) - cumulative(wave - 1));
    }

    private int cumulative(int wave){
        if(wave < begin) return 0;
        int last = Math.min(wave, end);
        int total = 0;
        for(int i = begin; i <= last; i++){
            if(spacing <= 0 || (i - begin) % spacing != 0) continue;
            total += original.getSpawned(i);
        }
        return (int)Math.floor(total * Difficulty.waveCountMultiplier() + 0.0001f);
    }

    @Override
    public Unit createUnit(Team team, float x, float y, float rotation, int wave, Cons<Unit> cons){
        return original == null ? super.createUnit(team, x, y, rotation, wave, cons)
            : original.createUnit(team, x, y, rotation, wave, cons);
    }
}
