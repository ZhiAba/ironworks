package ironworks;

import mindustry.world.blocks.defense.Wall;

/**
 * 带元素/能量抗性说明的墙体。
 *
 * <p>减伤本身由 {@link Dmg} 在运行时按 {@link Resistance} 表结算；这里只负责把
 * 抗性写进方块详情页，避免玩家只能靠猜。装甲仍由原版 Wall 的 armor 字段负责。</p>
 */
public class ResistWall extends Wall{

    public ResistWall(String name){
        super(name);
    }

    @Override
    public void setStats(){
        super.setStats();
        stats.add(IronworksStats.wallResistance, Resistance.summary(name));
    }
}
