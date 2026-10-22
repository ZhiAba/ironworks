package ironworks;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.content.StatusEffects;
import mindustry.entities.Units;
import mindustry.entities.bullet.BulletType;
import mindustry.gen.Healthc;
import mindustry.gen.Unit;

/**
 * 天罚：四炮口电弧塔。
 *
 * 4 个炮口按 1→2→3→4 轮流开火，共用同一条装填/过热槽（不是四门同时打）。
 * 冷却液：冷冻液（1.25×射速、过热减半、叠层 9%/发、上限 135%）
 *        石墨烯冷却液（1.45×射速、不再过热、叠层 13%/发、上限 260%）
 * 命中附带「过载」状态：移速 0.65×，每 tick 9 真伤，遇潮湿翻倍并溅射电弧。
 */
public class TianfaTurret extends OverheatTurret{

    public float shotDamage = 370f;
    public int chainTargets = 3;
    public float chainRange = 20f;
    public float overloadDuration = 60f;
    public float boltScale = 0.4f;

    /** 底座贴图：不随炮塔旋转 */
    public TextureRegion baseRegion;

    /** 4 个炮口的局部坐标（相对方块中心，炮口在贴图上方） */
    // 炮口局部坐标（世界单位，1 格 = 8 单位；贴图 128px = 32 单位）
    // 由 128x128 贴图上四个炮口尖端的像素位置换算：px/4
    public float[] muzzleX = {-7.5f, -1.75f, 3.25f, 8.75f};
    public float[] muzzleY = {11.5f, 12.75f, 12.25f, 11.5f};

    public TianfaTurret(String name){
        super(name);
    }

    @Override
    public void load(){
        super.load();
        baseRegion = Core.atlas.find(name + "-base");
    }

    public class TianfaBuild extends OverheatTurretBuild{
        public int muzzle = 0;

        /** 底座固定、炮塔头随 rotation 旋转 */
        @Override
        public void draw(){
            if(baseRegion != null && baseRegion.found()){
                Draw.rect(baseRegion, x, y);
            }
            super.draw();
        }

        @Override
        protected void shoot(BulletType type){
            float tx, ty;
            boolean aimed = false;
            if(target != null){
                tx = target.getX(); ty = target.getY(); aimed = true;
            }else if(controlled() || logicControlled() || alwaysShooting){
                float dx = targetPos.x - x, dy = targetPos.y - y;
                if((float)Math.sqrt(dx * dx + dy * dy) > range) return;
                tx = targetPos.x; ty = targetPos.y; aimed = true;
            }else{
                tx = x; ty = y;
            }
            if(!aimed) return;

            int idx = Math.floorMod(muzzle, muzzleX.length);
            float ox = muzzleX[idx], oy = muzzleY[idx];
            // 炮塔贴图的"上方"对应 rotation = 90°，所以局部坐标要按 rotation - 90 旋转
            float rot = rotation - 90f;
            float cs = Mathf.cosDeg(rot), sn = Mathf.sinDeg(rot);
            float mx = x + ox * cs - oy * sn;
            float my = y + ox * sn + oy * cs;

            if(target instanceof Healthc h){
                h.damage(shotDamage);
                if(h instanceof Unit u) u.apply(IronworksStatus.overload, overloadDuration);
            }

            int[] remain = {chainTargets};
            float fx = tx, fy = ty;
            Units.nearbyEnemies(team, tx, ty, chainRange, u -> {
                if(remain[0] <= 0 || u.dead()) return;
                remain[0]--;
                u.damage(shotDamage);
                u.apply(IronworksStatus.overload, overloadDuration);
                u.apply(StatusEffects.shocked, 60f);
                mindustry.entities.Lightning.create(team, arc.graphics.Color.valueOf("b6a8ff"), fx, fy, u.x, u.y, 2);
            });

            IronworksFx.arcBolt(team, mx, my, tx, ty, boltScale);
            IronworksFx.muzzle.at(mx, my, rotation);
            if(shootSound != null) shootSound.at(x, y, Mathf.random(0.9f, 1.05f));

            shootWarmup = 1f;
            curRecoil = 1f;
            slowDownReload = slowDownReloadTime;
            speedupScl = Math.min(speedupScl + tierPerShot(), tierCap());
            if(shotHeat){
                overheat = Math.min(overheat + overheatTime * shotHeatAmount * tierHeatMult(), overheatTime + 0.05f);
                if(overheat > overheatTime) requireCompleteCooling = true;
            }
            muzzle++;
        }

        @Override
        public byte version(){
            return 3;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.i(muzzle);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 3) muzzle = read.i();
        }
    }
}
