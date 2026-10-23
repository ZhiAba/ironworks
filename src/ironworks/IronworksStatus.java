package ironworks;

import arc.Events;
import arc.math.Mathf;
import arc.util.Time;
import arc.graphics.Color;
import mindustry.content.Fx;
import mindustry.entities.Damage;
import mindustry.game.EventType.UnitDestroyEvent;
import mindustry.gen.Unit;
import mindustry.type.StatusEffect;

/**
 * 状态效果批。设计约束来自引擎本身（v160.3）：
 *  - StatusEffect 只能改「自身」的 伤害/生命上限/移速/装填/建造速度/拖拽，
 *    没有「受到伤害加成」的钩子 → 所以「易伤」这类效果不在这里实现。
 *  - 爆炸/死亡类效果只能靠全局事件（UnitDestroyEvent）兑现。
 *  - 破甲用 intervalDamagePierce（真实伤害）表达，而不是改护甲值。
 */
public class IronworksStatus{

    public static StatusEffect corrosion, nanoArmor, jadeShatter, critical, reactiveArmor, compositeArmor, naniteArmor, overload;
    /** RBMK 熔毁后的辐射：持续真实伤害 + 减速，靠 IronworksRBMK.meltdown() 施加。 */
    public static StatusEffect radiation;

    /** 玉碎爆裂参数（世界单位 / 伤害）。 */
    public static final float jadeRadius = 44f, jadeDamage = 60f;

    private static boolean registered = false;

    public static void load(){
        // 一、腐蚀：硫化物侵蚀。破甲持续伤害 + 迟缓 + 装填下降
        corrosion = new StatusEffect("corrosion"){{
            color = Color.valueOf("8fbf5a");
            intervalDamage = 12f;
            intervalDamageTime = 30f;
            intervalDamagePierce = true;     // 无视护甲：这就是「破甲」
            speedMultiplier = 0.85f;
            reloadMultiplier = 0.9f;
            effect = Fx.corrosionVapor;
            effectChance = 0.12f;
        }};

        // 一·五、过载：天罚炮塔命中后的持续放电
        // 每 tick 9 真伤（9*60/s），移速 0.65x；遇到潮湿（wet）伤害翻倍并溅射电弧
        overload = new StatusEffect("overload"){
            {
                color = Color.valueOf("b6a8ff");
                speedMultiplier = 0.65f;
                effect = Fx.none;
            }
            @Override
            public void update(Unit unit, mindustry.entities.units.StatusEntry entry){
                super.update(unit, entry);
                boolean wet = unit.hasEffect(mindustry.content.StatusEffects.wet);
                float dmg = 9f * Time.delta;
                if(wet){
                    dmg *= 2f;
                    if(Mathf.chanceDelta(0.10f)){
                        Fx.hitLancer.at(unit.x, unit.y);
                        Fx.lightning.at(unit.x, unit.y, Mathf.random(360f), Color.valueOf("b6a8ff"), null);
                    }
                }
                Dmg.pierce(unit, dmg, DamageType.thunder);
            }
        };

        // 二、纳米装甲：会自我修复的装甲层。回血 + 小幅增益
        nanoArmor = new StatusEffect("nano-armor"){{
            color = Color.valueOf("7fe3d0");
            damage = -0.07f;                 // 每秒约 4.2 点修复
            speedMultiplier = 1.1f;
            damageMultiplier = 1.1f;
            reloadMultiplier = 0.9f;
            effect = Fx.heal;
            effectChance = 0.08f;
            applyEffect = Fx.shieldBreak;
        }};

        // 三、玉碎：死亡时爆裂。加成数据不存在，效果由 UnitDestroyEvent 兑现
        jadeShatter = new StatusEffect("jade-shatter"){{
            color = Color.valueOf("a8f0d8");
            effect = Fx.none;
        }};

        // 四、临界：残血时超载。伤害 ×1.5，代价是装填变慢
        critical = new StatusEffect("critical"){{
            color = Color.valueOf("ff5a48");
            damageMultiplier = 1.5f;
            speedMultiplier = 1.1f;
            reloadMultiplier = 1.15f;
            effect = Fx.none;
            applyEffect = Fx.shieldBreak;
        }};

        // 五、反应装甲：外挂装备。护甲本体由 statusArmor() 叠加，这里只放附带效果
        reactiveArmor = new StatusEffect("reactive-armor"){{
            color = Color.valueOf("9aa86f");
            healthMultiplier = 1.1f;
            speedMultiplier = 0.9f;
            effect = Fx.none;
            applyEffect = Fx.shieldBreak;
        }};

        // 六、复合装甲：外挂装备。抗打、略慢、装填稍快
        compositeArmor = new StatusEffect("composite-armor"){{
            color = Color.valueOf("c8b48a");
            healthMultiplier = 1.2f;
            reloadMultiplier = 1.05f;
            speedMultiplier = 0.95f;
            effect = Fx.none;
            applyEffect = Fx.shieldBreak;
        }};

        // 七、纳米装甲块（外挂）：和修复场的纳米装甲同源，但由装甲坞挂载
        naniteArmor = new StatusEffect("nanite-armor"){{
            color = Color.valueOf("62d6c0");
            damage = -0.07f;
            speedMultiplier = 1.1f;
            damageMultiplier = 1.1f;
            reloadMultiplier = 0.9f;
            effect = Fx.heal;
            effectChance = 0.08f;
            applyEffect = Fx.shieldBreak;
        }};

        // 辐射：无视护甲的持续伤害 + 减速（RBMK 熔毁后撒在区域里）
        radiation = new StatusEffect("radiation"){{
            color = Color.valueOf("9be86a");
            intervalDamage = 10f;
            intervalDamageTime = 45f;
            intervalDamagePierce = true;
            speedMultiplier = 0.9f;
            reloadMultiplier = 0.92f;
            effect = Fx.none;
        }};

        // 玉碎爆裂：只结算一次，且只打敌方（Damage.damage 会跳过同队）
        if(!registered){
            registered = true;
            Events.on(UnitDestroyEvent.class, e -> {
                Unit unit = e.unit;
                if(unit == null || jadeShatter == null || !unit.hasEffect(jadeShatter)) return;

                Dmg.area(unit.team, unit.x, unit.y, jadeRadius, jadeDamage, DamageType.physical, false);
                Fx.dynamicExplosion.at(unit.x, unit.y, jadeRadius / 8f);
            });
        }
    }
}
