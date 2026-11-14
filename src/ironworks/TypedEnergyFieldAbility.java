package ironworks;

import mindustry.entities.Damage;
import mindustry.entities.Units;
import mindustry.entities.abilities.EnergyFieldAbility;
import mindustry.gen.Building;
import mindustry.gen.Healthc;
import mindustry.gen.Statusc;
import mindustry.gen.Unit;
import mindustry.world.blocks.defense.turrets.Turret;

/**
 * 玄武的多目标雷电：保留原版索敌/治疗/吸收逻辑，只把对敌伤害接入属性管线。
 * 原版 EnergyFieldAbility 直接调用 damage()，会绕过护甲/属性/飘字统计。
 */
class TypedEnergyFieldAbility extends EnergyFieldAbility{
    TypedEnergyFieldAbility(EnergyFieldAbility source){
        super(source.damage, source.reload, source.range);
        healEffect = source.healEffect;
        hitEffect = source.hitEffect;
        damageEffect = source.damageEffect;
        status = source.status;
        shootSound = source.shootSound;
        statusDuration = source.statusDuration;
        x = source.x;
        y = source.y;
        targetGround = source.targetGround;
        targetAir = source.targetAir;
        hitBuildings = source.hitBuildings;
        hitUnits = source.hitUnits;
        maxTargets = source.maxTargets;
        healPercent = source.healPercent;
        sameTypeHealMult = source.sameTypeHealMult;
        displayHeal = source.displayHeal;
        layer = source.layer;
        blinkScl = source.blinkScl;
        blinkSize = source.blinkSize;
        effectRadius = source.effectRadius;
        sectorRad = source.sectorRad;
        rotateSpeed = source.rotateSpeed;
        sectors = source.sectors;
        color = source.color;
        useAmmo = source.useAmmo;
    }

    @Override
    public String getBundle(){
        // 继承原版能场的能力文案，避免详情页按子类名找不到 description。
        return "ability.energyfield";
    }

    @Override
    public void update(Unit unit){
        curStroke = arc.math.Mathf.lerpDelta(curStroke, anyNearby ? 1 : 0, 0.09f);

        if((timer += arc.util.Time.delta) >= reload){
            arc.math.geom.Vec2 v = arc.util.Tmp.v1.trns(unit.rotation - 90, x, y).add(unit.x, unit.y);
            float rx = v.x, ry = v.y;
            anyNearby = false;

            arc.struct.Seq<Healthc> targets = new arc.struct.Seq<>();

            if(hitUnits){
                Units.nearby(null, rx, ry, range, other -> {
                    if(other != unit && other.checkTarget(targetAir, targetGround) && other.targetable(unit.team) && (other.team != unit.team || other.damaged())){
                        targets.add(other);
                    }
                });
            }

            if(hitBuildings && targetGround){
                Units.nearbyBuildings(rx, ry, range, b -> {
                    if((b.team != mindustry.game.Team.derelict || mindustry.Vars.state.rules.coreCapture)
                        && ((b.team != unit.team && b.block.targetable) || b.damaged()) && !b.block.privileged){
                        targets.add(b);
                    }
                });
            }

            targets.sort(h -> h.dst2(rx, ry));
            int len = Math.min(targets.size, maxTargets);
            float scaledDamage = damage * mindustry.Vars.state.rules.unitDamage(unit.team) * unit.damageMultiplier;

            for(int i = 0; i < len; i++){
                Healthc other = targets.get(i);

                // 与原生一致：雷电会被塑钢墙吸收。
                var absorber = Damage.findAbsorber(unit.team, rx, ry, other.getX(), other.getY());
                if(absorber != null) other = absorber;

                if(((mindustry.gen.Teamc)other).team() == unit.team){
                    if(other.damaged()){
                        anyNearby = true;
                        float healMult = (other instanceof Unit u && u.type == unit.type) ? sameTypeHealMult : 1f;
                        other.heal(healPercent / 100f * other.maxHealth() * healMult);
                        healEffect.at(other);
                        damageEffect.at(rx, ry, 0f, color, other);
                        hitEffect.at(rx, ry, unit.angleTo(other), color);

                        if(other instanceof Building b){
                            mindustry.content.Fx.healBlockFull.at(b.x, b.y, 0f, color, b.block);
                        }
                    }
                }else{
                    anyNearby = true;
                    Dmg.hit(other, scaledDamage, DamageType.physical, true, true,
                        DamageType.physical.pen, false, DamageForm.ENERGY);
                    if(other instanceof Statusc s) s.apply(status, statusDuration);
                    hitEffect.at(other.x(), other.y(), unit.angleTo(other), color);
                    damageEffect.at(rx, ry, 0f, color, other);
                    hitEffect.at(rx, ry, unit.angleTo(other), color);
                }
            }

            if(anyNearby){
                shootSound.at(unit, 1f + arc.math.Mathf.random(0.1f), 1f);
            }

            timer = 0f;
        }
    }
}
