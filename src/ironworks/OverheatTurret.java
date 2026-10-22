package ironworks;

import arc.Core;
import arc.math.Mathf;
import arc.util.Strings;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.Liquid;
import mindustry.ui.Bar;
import mindustry.world.blocks.defense.turrets.PowerTurret;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

/**
 * 连续射击炮塔：过热 + 持续射击加速 + 可选的循环冷却液。
 *
 * - 无冷液：持续射击会让 overheat 快速上升，满后强制冷却（参考 NewHorizonMod 的 SpeedupTurret）。
 * - 有冷液且热液能排出：overheat 被压回 0，维持高射速，不进入冷却；
 *   冷液被极快地加热（每 tick 消耗远高于普通炮塔），1:1 产热液。
 * - 热液排放：每 tick 尽可能把热液推给所有邻居，只在外部装不下时才留在罐里。
 */
public class OverheatTurret extends PowerTurret{

    public float overheatTime = 300f;
    public float overheatCoolAmount = 1.6f;
    public float maxSpeedupScl = 5.5f;
    /** 每次开火增加的射速（叠层） */
    public float speedupPerShoot = 0.15f;
    /** 持续开火多少秒爬到满速（线性） */
    public float rampUpSeconds = 4f;
    /** 停火多少秒回落到常态（线性） */
    public float rampDownSeconds = 6f;
    public float slowDownReloadTime = 90f;
    public Effect heatEffect = Fx.reactorsmoke;
    public float maxHeatEffectChance = 0.3f;

    /** 无冷液时每秒积热占满过热的比例（越大越容易过热） */
    public float dryHeatPerSecond = 0.35f;

    /** 冷却液分级（水 / 冷冻液 / 石墨烯冷却液） */
    public Liquid[] coolantLiquids = {};
    /** 对应射速乘数 */
    public float[] coolantFireMult = {};
    /** 对应每次攻击叠层 */
    public float[] coolantStackPerShot = {};
    /** 对应叠层上限 */
    public float[] coolantStackCap = {};
    /** 只有石墨烯冷却液参与热液循环 */
    public Liquid hotLiquid;
    /** 每 tick 消耗的冷却液 */
    public float coolantUse = 0.25f;

    /** true = 过热只按开火次数累计（天罚），不按秒连续积热 */
    public boolean shotHeat = false;
    /** 每次开火增加的过热比例（占满槽） */
    public float shotHeatAmount = 0.05f;
    /** 各冷却液对"每次攻击过热"的倍率（0 = 不过热）；null = 全部 1 */
    public float[] coolantHeatMult = null;
    /** 无冷却液时的叠层上限 */
    public float noCoolantStackCap = 2f;
    /** 哪种冷却液会生成热液（下标）；-1 = 没有 */
    public int hotLiquidTier = 2;
    /** 有冷却液时，是否只有停火才降温（天罚用：开火时仍按发积热） */
    public boolean coolOnlyWhenIdle = false;
    /** 命中附加效果的说明 key（详情页「命中效果」栏） */
    public String onHitKey;
    /** true = 只有开火时才消耗冷却液（停火不喝） */
    public boolean consumeOnlyWhenFiring = false;
    /** true = 热液存在独立的虚拟热槽里（不占冷液容量），并单独显示条 */
    public boolean virtualHot = false;
    public float hotCapacity = 1440f;

    public OverheatTurret(String name){
        super(name);
        hasLiquids = true;
        liquidCapacity = 60f;
    }

    @Override
    public void setStats(){
        super.setStats();
        // 白板最大射速（叠层上限）
        stats.add(IronworksStats.maxFire, (int)((1f + noCoolantStackCap) * 100f) + "%");
        // 每种冷却液：射速倍率 + 叠层上限，两行都写清楚
        for(int i = 0; i < coolantLiquids.length; i++){
            stats.add(Stat.booster, IronworksStats.liquidBoostsCapped("stat.ironworks-action-fire",
                coolantLiquids[i], coolantFireMult[i], coolantUse * 60f,
                (1f + coolantStackCap[i]) * 100f));
        }
        if(onHitKey != null){
            stats.add(IronworksStats.onHit, Core.bundle.get(onHitKey));
        }
    }

    @Override
    public void setBars(){
        super.setBars();
        removeBar("liquid");
        for(Liquid l : coolantLiquids) addLiquidBar(l);
        if(hotLiquid != null && !virtualHot) addLiquidBar(hotLiquid);
        if(virtualHot){
            addBar("hot-storage", (OverheatTurretBuild e) -> e.hotStored <= 0.01f ? null : new Bar(
                () -> Core.bundle.format("bar.ironworks-hot-storage", Strings.autoFixed(e.hotStored, 0),
                    Strings.autoFixed(hotCapacity, 0)),
                () -> Pal.orangeSpark,
                () -> Mathf.clamp(e.hotStored / hotCapacity)
            ));
        }

        addBar("overheat", (OverheatTurretBuild e) -> e.overheat <= 0.01f ? null : new Bar(
            () -> Core.bundle.format("bar.ironworks-overheat", Strings.autoFixed(Mathf.clamp(e.overheat / overheatTime) * 100f, 0)),
            () -> e.requireCompleteCooling ? Pal.redderDust : Pal.powerLight,
            () -> Mathf.clamp(e.overheat / overheatTime)
        ));
        addBar("speedboost", (OverheatTurretBuild e) -> e.speedupScl <= 0.01f ? null : new Bar(
            () -> Core.bundle.format("bar.ironworks-speedboost", Strings.autoFixed(e.speedupScl * 100f, 0)),
            () -> Pal.accent,
            () -> e.speedupScl / maxSpeedupScl
        ));
    }

    public class OverheatTurretBuild extends PowerTurretBuild implements IronworksTeleport.JumpSource{

        @Override
        public float jumpLiquid(Liquid liquid){
            if(virtualHot && liquid == IronworksLiquids.hotCoolant) return hotStored;
            if(hotLiquid != null && liquid == hotLiquid) return liquids.get(liquid);
            return 0f;
        }

        @Override
        public float jumpExtract(Liquid liquid, float amount){
            float take = Math.min(amount, jumpLiquid(liquid));
            if(take <= 0f) return 0f;
            if(virtualHot && liquid == IronworksLiquids.hotCoolant) hotStored -= take;
            else liquids.remove(liquid, take);
            return take;
        }
        public float overheat, speedupScl, slowDownReload, hotStored;
        public boolean requireCompleteCooling;
        public boolean cooling;

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            // 邻居可能是空罐/空路由器，会带着 null 来问；不判空会在 updateProximity 里 NPE
            if(liquid == null) return false;
            if(liquids.get(liquid) >= block.liquidCapacity - 0.001f) return false;
            for(Liquid l : coolantLiquids){
                if(l == liquid) return true;
            }
            return false;
        }

        /** 当前生效的冷却液等级：-1 无，否则为下标。 */
        int tier(){
            for(int i = 0; i < coolantLiquids.length; i++){
                if(liquids.get(coolantLiquids[i]) > 0.001f) return i;
            }
            return -1;
        }

        float tierMult(){
            int t = tier();
            return t < 0 ? 1f : coolantFireMult[t];
        }

        float tierPerShot(){
            int t = tier();
            return t < 0 ? 0.05f : coolantStackPerShot[t];
        }

        float tierCap(){
            int t = tier();
            return t < 0 ? noCoolantStackCap : coolantStackCap[t];
        }

        float tierHeatMult(){
            int t = tier();
            if(t < 0 || coolantHeatMult == null) return 1f;
            return coolantHeatMult[Math.min(t, coolantHeatMult.length - 1)];
        }

        /** 尽可能往外吐热液：每个邻居能收多少就收多少，不看比例。 */
        void dumpHot(){
            if(hotLiquid == null) return;
            if(virtualHot){
                float hotRemain = hotStored;
                if(hotRemain <= 0.001f) return;
                float hotCap = Time.delta * 240f;
                for(Building other : proximity){
                    if(hotRemain <= 0.001f) break;
                    Building dest = other.getLiquidDestination(this, hotLiquid);
                    if(dest == null || dest == this || !dest.block.hasLiquids) continue;
                    if(!dest.acceptLiquid(this, hotLiquid)) continue;
                    float space = dest.block.liquidCapacity - dest.liquids.get(hotLiquid);
                    if(space <= 0.001f) continue;
                    float flow = Math.min(Math.min(hotRemain, space), hotCap);
                    dest.handleLiquid(this, hotLiquid, flow);
                    hotStored -= flow;
                    hotRemain -= flow;
                }
                return;
            }
            float remain = liquids.get(hotLiquid);
            if(remain <= 0.001f) return;

            float cap = Time.delta * 240f; // 单 tick 上限，防止超大 delta 一次倒太空
            for(Building other : proximity){
                if(remain <= 0.001f) break;
                Building dest = other.getLiquidDestination(this, hotLiquid);
                if(dest == null || dest == this || !dest.block.hasLiquids) continue;
                if(!dest.acceptLiquid(this, hotLiquid)) continue;

                float space = dest.block.liquidCapacity - dest.liquids.get(hotLiquid);
                if(space <= 0.001f) continue;

                float flow = Math.min(Math.min(remain, space), cap);
                dest.handleLiquid(this, hotLiquid, flow);
                liquids.remove(hotLiquid, flow);
                remain -= flow;
            }
        }

        boolean hotHasSpace(){
            if(hotLiquid == null) return false;
            if(virtualHot) return hotStored < hotCapacity - 0.001f;
            return liquids.get(hotLiquid) < block.liquidCapacity - 0.001f;
        }

        @Override
        public void updateTile(){
            dumpHot();

            int tierNow = tier();
            boolean hasCold = tierNow >= 0 && (tierNow != hotLiquidTier || hotHasSpace());
            boolean firing = target != null || isShooting();
            cooling = hasCold && (overheat < overheatTime * 0.98f);
            if(coolOnlyWhenIdle) cooling = cooling && !firing;
            if(consumeOnlyWhenFiring && !firing && !requireCompleteCooling) cooling = false;
            int ti = tier();

            if(requireCompleteCooling){
                overheat = Math.max(0f, overheat - overheatCoolAmount * (1f + coolantEfficiency()) * Time.delta);
            }else if(cooling){
                int t = ti;
                Liquid liq = t < 0 ? null : coolantLiquids[t];
                if(liq != null){
                    float amount = Math.min(coolantUse * delta(), liquids.get(liq));
                    if(t == hotLiquidTier && hotLiquid != null){
                        amount = virtualHot
                            ? Math.min(amount, Math.max(hotCapacity - hotStored, 0f))
                            : Math.min(amount, Math.max(block.liquidCapacity - liquids.get(hotLiquid), 0f));
                    }
                    if(amount > 0f){
                        liquids.remove(liq, amount);
                        if(t == hotLiquidTier && hotLiquid != null){
                            if(virtualHot) hotStored = Math.min(hotCapacity, hotStored + amount);
                            else liquids.add(hotLiquid, amount);
                        }
                    }
                }
                overheat = Math.max(0f, overheat - overheatCoolAmount * 3f * Time.delta);
                requireCompleteCooling = false;
            }else if(firing){
                if(!shotHeat) overheat = Mathf.approachDelta(overheat, overheatTime + 0.05f, dryHeatPerSecond / 60f);
                if(overheat > overheatTime) requireCompleteCooling = true;
            }else{
                // 没开火就不该积热：闲置时自然冷却
                overheat = Math.max(0f, overheat - overheatCoolAmount * (1f + coolantEfficiency()) * Time.delta);
            }

            // 叠层：每次攻击在 shoot() 里 +speedupPerShoot；停火后缓慢回落
            speedupScl = Math.min(speedupScl, tierCap());
            if(!firing || requireCompleteCooling){
                speedupScl = Math.max(speedupScl - Time.delta / (60f * Math.max(rampDownSeconds, 0.001f)) * maxSpeedupScl, 0f);
            }

            if(overheat > overheatTime * 0.3f && Mathf.chanceDelta(maxHeatEffectChance * (requireCompleteCooling ? 1f : overheat / overheatTime))){
                heatEffect.at(x + Mathf.range(Vars.tilesize * size / 2f), y + Mathf.range(Vars.tilesize * size / 2f), rotation, heatColor);
            }

            if(requireCompleteCooling){
                forceCoolDown();
            }else{
                super.updateTile();
            }

            dumpHot();
        }

        void forceCoolDown(){
            slowDownReload = 0f;
            if(overheat > 0f){
                overheat -= overheatCoolAmount * (1f + coolantEfficiency()) * Time.delta;
            }
            shootWarmup = Mathf.approachDelta(shootWarmup, 0f, shootWarmupSpeed);
            curRecoil = Mathf.approachDelta(curRecoil, 0f, 1f / recoilTime);
            recoilOffset.trns(rotation, -Mathf.pow(curRecoil, recoilPow) * recoil);
            if(overheat <= 0f){
                overheat = 0f;
                requireCompleteCooling = false;
            }
        }

        float coolantEfficiency(){
            return liquids == null || liquids.current() == null ? 0f : liquids.current().heatCapacity;
        }

        boolean hotFull(){
            return virtualHot && hotStored >= hotCapacity - 0.01f;
        }

        @Override
        protected void updateShooting(){
            // 热冷却液堆满 = 热量排不出去，强制过热停机
            if(hotFull()){
                overheat = Mathf.approachDelta(overheat, overheatTime, 3f / 60f);
                return;
            }
            if(reloadCounter >= reload){
                BulletType type = peekAmmo() != null ? peekAmmo() : shootType;
                shoot(type);
                reloadCounter = 0f;
            }else{
                float mult = tierMult();
                reloadCounter += (1f + speedupScl) * mult * delta() * (peekAmmo() == null ? 1f : peekAmmo().reloadMultiplier) * baseReloadSpeed();
            }
        }

        @Override
        protected void shoot(BulletType type){
            // 直接结算：不发射任何弹体
            float tx, ty;
            boolean aimed = false;

            if(target != null){
                tx = target.getX(); ty = target.getY(); aimed = true;
            }else if(controlled() || logicControlled() || alwaysShooting){
                float dx = targetPos.x - x, dy = targetPos.y - y;
                if((float)Math.sqrt(dx * dx + dy * dy) > range) return; // 点击超出射程：不攻击
                tx = targetPos.x; ty = targetPos.y; aimed = true;
            }else{
                tx = x; ty = y;
            }

            if(!aimed) return;

            if(target instanceof mindustry.gen.Healthc h){
                h.damage(35f);
            }

            int[] remain = {3};
            mindustry.entities.Units.nearbyEnemies(team, tx, ty, 20f, u -> {
                if(remain[0] <= 0 || u.dead()) return;
                remain[0]--;
                u.damage(35f);
                u.apply(mindustry.content.StatusEffects.shocked, 60f);
                mindustry.entities.Lightning.create(team, arc.graphics.Color.valueOf("8a7bff"), tx, ty, u.x, u.y, 3);
            });

            IronworksFx.arcBolt(team, x, y, tx, ty);
            if(shootSound != null) shootSound.at(x, y, arc.math.Mathf.random(0.9f, 1.1f));
            shootWarmup = 1f;
            curRecoil = 1f;
            slowDownReload = slowDownReloadTime;
            speedupScl = Math.min(speedupScl + tierPerShot(), tierCap());
            if(shotHeat){
                overheat = Math.min(overheat + overheatTime * shotHeatAmount * tierHeatMult(), overheatTime + 0.05f);
                if(overheat > overheatTime) requireCompleteCooling = true;
            }
        }

        @Override
        public byte version(){
            return 3;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.f(overheat);
            write.f(slowDownReload);
            write.f(speedupScl);
            write.bool(requireCompleteCooling);
            write.f(hotStored);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 2){
                overheat = read.f();
                slowDownReload = read.f();
                speedupScl = read.f();
                requireCompleteCooling = read.bool();
            }
            if(revision >= 3){
                hotStored = read.f();
            }
        }
    }
}
