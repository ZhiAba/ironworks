package ironworks;

import mindustry.content.Items;
import mindustry.entities.Units;
import mindustry.type.Category;
import mindustry.type.StatusEffect;
import mindustry.world.Block;
import mindustry.gen.Building;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.type.ItemStack.with;

/**
 * 战术支援方块：把状态效果挂到范围内的友方单位上。
 * 三个方块共用一套「光环」逻辑（StatusAura），只有状态与触发条件不同。
 */
public class IronworksSupport{

    public static Block nanoField, jadeBeacon, criticalDevice;

    /**
     * 状态光环基类。
     * range 用世界单位（与炮塔 range 同口径），显示时换算成格。
     */
    public static class StatusAura extends Block{
        public StatusEffect status;
        public float duration = 60f;
        public float range = 64f;
        public float useTime = 60f;
        /** 只给残血单位上状态时启用。 */
        public boolean lowHealthOnly = false;
        public float healthThreshold = 0.4f;

        public StatusAura(String name){
            super(name);
            update = true;
            solid = true;
            hasPower = true;
            hasItems = false;
            configurable = false;
            rotate = false;
        }

        @Override
        public void setStats(){
            super.setStats();
            stats.add(Stat.range, range / mindustry.Vars.tilesize, StatUnit.blocks);
            if(lowHealthOnly){
                stats.add(Stat.health, healthThreshold * 100f, StatUnit.percent);
            }
        }

        public class StatusAuraBuild extends Building{
            protected float useProgress;
            public int appliedCount; // debug

            @Override
            public void updateTile(){
                if(efficiency <= 0f) return;

                useProgress += delta();
                if(useProgress >= useTime){
                    useProgress %= useTime;
                    consume();
                }

                apply();
            }

            protected void apply(){
                Units.nearby(team, x, y, range, u -> {
                    if(u.dead || !u.isValid()) return;
                    if(lowHealthOnly && u.health / u.maxHealth > healthThreshold) return;
                    u.apply(status, duration);
                    appliedCount++;
                });
            }
        }
    }

    public static void load(){
        // 纳米修复场：持续修复范围内的友军单位
        nanoField = new StatusAura("nano-field"){{
            requirements(Category.effect,
                with(Items.lead, 80, Items.silicon, 60, Items.titanium, 50, IronworksItems.steelPlate, 20, IronworksMaterials.castIron, 20));
            size = 3;
            range = 80f;
            duration = 60f;
            status = IronworksStatus.nanoArmor;
            consumePower(8f);
        }};

        // 玉碎信标：给友军挂上「死亡爆裂」协议
        jadeBeacon = new StatusAura("jade-beacon"){{
            requirements(Category.effect,
                with(Items.silicon, 70, Items.titanium, 40, IronworksItems.steelPlate, 30, IronworksMaterials.castIron, 20));
            size = 3;
            range = 64f;
            duration = 120f;
            status = IronworksStatus.jadeShatter;
            consumePower(5f);
        }};

        // 临界装置：只对残血单位启用超载
        criticalDevice = new StatusAura("critical-device"){{
            requirements(Category.effect,
                with(Items.silicon, 60, IronworksMaterials.pureCopper, 40, IronworksItems.steelPlate, 30, IronworksMaterials.castIron, 20));
            size = 3;
            range = 72f;
            duration = 90f;
            status = IronworksStatus.critical;
            lowHealthOnly = true;
            healthThreshold = 0.4f;
            consumePower(6f);
        }};
    }
}
