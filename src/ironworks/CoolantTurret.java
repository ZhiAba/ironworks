package ironworks;

import arc.Core;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.type.Liquid;
import mindustry.world.blocks.defense.turrets.ItemTurret;
import mindustry.world.meta.Stat;

/**
 * 带循环冷却的炮塔。
 *
 * 规则：
 * 1. 只收冷液，热液只出不进；
 * 2. 只在**锁定目标或开火时**消耗冷液（不再每 tick 空转消耗）；
 * 3. 热液罐满了就停止消耗冷液，同时**失去冷液加速**，直到热液被排走；
 * 4. 冷液与热液 1:1 转化，不做温差仿真。
 */
public class CoolantTurret extends ItemTurret{

    /** 冷液（输入）。 */
    public Liquid coolantLiquid;
    /** 热液（输出）。 */
    public Liquid hotLiquid;
    /** 每 tick 消耗的冷液量。 */
    public float coolantUse = 0.04f;
    /** 有冷却液时的装填速度倍率。 */
    public float coolantBoost = 1.6f;
    /** 只在锁定/开火时消耗。 */
    public boolean coolantOnFireOnly = true;

    public CoolantTurret(String name){
        super(name);
        hasLiquids = true;
        liquidCapacity = 40f;
    }

    @Override
    public void setStats(){
        super.setStats();
        if(coolantLiquid != null){
            stats.add(Stat.booster, IronworksStats.liquidBoosts("stat.ironworks-action-fire", coolantLiquid, coolantBoost, coolantUse * 60f));
        }
    }

    @Override
    public void setBars(){
        super.setBars();
        removeBar("liquid");
        if(coolantLiquid != null) addLiquidBar(coolantLiquid);
        if(hotLiquid != null) addLiquidBar(hotLiquid);
    }

    public class CoolantTurretBuild extends ItemTurretBuild{
        /** 本 tick 是否正在被冷液加速。 */
        public boolean cooling;

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            return coolantLiquid != null && liquid == coolantLiquid
                && liquids.get(liquid) < block.liquidCapacity - 0.001f;
        }

        @Override
        public float baseReloadSpeed(){
            return super.baseReloadSpeed() * (cooling ? coolantBoost : 1f);
        }

        @Override
        public void updateTile(){
            super.updateTile();

            if(coolantLiquid == null || !enabled) return;

            boolean active = !coolantOnFireOnly || target != null || wasShooting || isShooting();
            boolean hotFull = hotLiquid != null && liquids.get(hotLiquid) >= block.liquidCapacity - 0.001f;

            cooling = active && !hotFull && liquids.get(coolantLiquid) > 0.001f;

            if(cooling){
                float amount = Math.min(coolantUse * delta(), liquids.get(coolantLiquid));

                // 热液罐装不下就少消耗，绝不溢出
                if(hotLiquid != null){
                    amount = Math.min(amount, Math.max(block.liquidCapacity - liquids.get(hotLiquid), 0f));
                }

                if(amount > 0f){
                    liquids.remove(coolantLiquid, amount);
                    if(hotLiquid != null){
                        liquids.add(hotLiquid, amount);
                    }
                }
            }

            if(hotLiquid != null && liquids.get(hotLiquid) > 0.001f){
                dumpLiquid(hotLiquid, 2f);
            }
        }

        @Override
        public byte version(){
            return 2;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.bool(cooling);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 2){
                cooling = read.bool();
            }
        }
    }
}
