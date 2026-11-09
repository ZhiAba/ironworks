package ironworks;

import arc.Core;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.ctype.UnlockableContent;
import mindustry.type.UnitType;
import mindustry.ui.dialogs.ContentInfoDialog;
import mindustry.world.Block;
import mindustry.world.blocks.units.UnitFactory;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatCat;

import java.util.Locale;

/**
 * 让内容详情页直接显示当前难度的实际值，而不是静态注册值。
 *
 * <p>原版详情页每次打开都会重新 computeStats()，读取 Block/UnitType 的静态字段。
 * 这里只在构建详情页的这段时间内把字段换成当前难度下的实际值，show() 返回后
 * 立即恢复，不改变战斗中的基础数值。护甲、特殊抗性、敌方单位属性等无法用
 * 一个静态字段表达的数据，作为普通“基础”属性行补在原版基础栏末尾。</p>
 */
public class IronworksContentInfoDialog extends ContentInfoDialog{
    @Override
    public void show(UnlockableContent content){
        Block block = content instanceof Block b ? b : null;
        UnitType unit = content instanceof UnitType u ? u : null;
        DifficultyCrafter crafter = block instanceof DifficultyCrafter c ? c : null;
        UnitFactory unitFactory = block instanceof UnitFactory f ? f : null;

        float baseArmor = block == null ? 0f : block.armor;
        float baseBuildTime = block == null ? 0f : block.buildTime;
        float baseUnitHealth = unit == null ? 0f : unit.health;
        float baseUnitArmor = unit == null ? 0f : unit.armor;
        float baseCraftTime = crafter == null ? 0f : crafter.craftTime;
        int[][] unitPlanCosts = null;
        if(unitFactory != null && unitFactory.plans != null){
            float costMultiplier = Difficulty.playerUnitCostMultiplier();
            unitPlanCosts = new int[unitFactory.plans.size][];
            for(int p = 0; p < unitFactory.plans.size; p++){
                UnitFactory.UnitPlan plan = unitFactory.plans.get(p);
                if(plan.requirements == null) continue;
                unitPlanCosts[p] = new int[plan.requirements.length];
                for(int i = 0; i < plan.requirements.length; i++){
                    unitPlanCosts[p][i] = plan.requirements[i].amount;
                    plan.requirements[i].amount = Math.max(1, Math.round(plan.requirements[i].amount * costMultiplier));
                }
            }
        }

        // 建筑生命现在直接缩放 Building.maxHealth；详情页显示同一个实际上限。
        // 不再通过 blockHealthMultiplier 受伤除数实现，避免“上限缩小 + 伤害放大”双重惩罚。
        float actualHealth = block == null ? 0f : Math.max(1f, block.health * Difficulty.playerBlockHealthMultiplier());
        float actualArmor = block == null ? 0f : Math.max(0f, baseArmor + Difficulty.playerBlockArmorModifier());
        float actualBuildTime = block == null || baseBuildTime <= 0f ? 0f
            : baseBuildTime / Math.max(0.05f, Difficulty.buildSpeedMultiplier());
        float actualUnitHealth = unit == null ? 0f : baseUnitHealth * Difficulty.playerUnitStatMultiplier();
        float actualUnitArmor = unit == null ? 0f : Math.max(0f, baseUnitArmor);
        float enemyUnitHealth = unit == null ? 0f : baseUnitHealth * Difficulty.enemyHealthMul();
        float enemyUnitArmor = unit == null ? 0f : Math.max(0f, baseUnitArmor + Difficulty.enemyArmorBonus());

        try{
            if(block != null){
                // 生命字段保持基础值；显示层改为实际最大生命（与 Building.maxHealth 同步）。
                // 护甲隐藏标准行，统一放到“基础”栏，实际护甲下限 0。
                block.armor = 0f;
                if(actualBuildTime > 0f){
                    block.buildTime = Math.max(0.01f, actualBuildTime);
                }
            }
            if(unit != null){
                // 原版只有一行生命/护甲；默认显示我方实际值，敌方实际值补在基础栏里。
                unit.health = Math.max(1f, actualUnitHealth);
                unit.armor = actualUnitArmor;
            }
            if(crafter != null){
                crafter.craftTime = Math.max(1f, baseCraftTime / Math.max(0.05f, Difficulty.productionMultiplier()));
            }
            super.show(content);
        }finally{
            if(block != null){
                block.armor = baseArmor;
                block.buildTime = baseBuildTime;
            }
            if(unit != null){
                unit.health = baseUnitHealth;
                unit.armor = baseUnitArmor;
            }
            if(crafter != null){
                crafter.craftTime = baseCraftTime;
            }
            if(unitFactory != null && unitPlanCosts != null){
                for(int p = 0; p < unitFactory.plans.size; p++){
                    if(unitPlanCosts[p] == null) continue;
                    UnitFactory.UnitPlan plan = unitFactory.plans.get(p);
                    for(int i = 0; i < unitPlanCosts[p].length; i++){
                        plan.requirements[i].amount = unitPlanCosts[p][i];
                    }
                }
            }
        }

        Table root = findScrollContent(cont);
        if(root == null) return;

        if(block != null){
            overrideStatValue(root, Stat.health, fmt(actualHealth));
            if(actualBuildTime > 0f){
                overrideStatValue(root, Stat.buildTime, fmt(actualBuildTime));
            }
        }else if(unit != null){
            overrideStatValue(root, Stat.health, fmt(actualUnitHealth));
            overrideStatValue(root, Stat.armor, fmt(actualUnitArmor));
        }

        Table extra = buildExtras(block, unit, actualArmor, enemyUnitHealth, enemyUnitArmor);
        if(extra != null){
            // 建筑放在生命值之后，单位放在护甲之后，保持原版基础栏的阅读顺序。
            String after = block != null ? Stat.health.localized() : Stat.armor.localized();
            addToGeneral(root, extra, after);
        }
    }

    private static Table buildExtras(Block block, UnitType unit, float actualArmor,
                                     float enemyHealth, float enemyArmor){
        Table extra = new Table();
        extra.left();

        if(block != null){
            addRow(extra, Core.bundle.get("ironworks.combat.armor", "护甲"), fmt(actualArmor));
            addRow(extra, Core.bundle.get("ironworks.combat.special-resistance", "特殊抗性"),
                Resistance.summary(block.name));
            return extra;
        }

        if(unit != null){
            addRow(extra, Core.bundle.get("ironworks.combat.enemy-health", "敌方生命"), fmt(enemyHealth));
            addRow(extra, Core.bundle.get("ironworks.combat.enemy-armor", "敌方护甲"), fmt(enemyArmor));
            addRow(extra, Core.bundle.get("ironworks.combat.enemy-resistance", "敌方特殊抗性"),
                Resistance.summaryEnemy(unit));
            addRow(extra, Core.bundle.get("ironworks.combat.material", "材质"), TargetMaterial.name(unit));
            addRow(extra, Core.bundle.get("ironworks.combat.weakness", "弱点"),
                ElementCombo.weaknessName(unit));
            return extra;
        }
        return null;
    }

    private static void addRow(Table table, String label, String value){
        Label row = new Label("[lightgray]" + label + ":[] " + value);
        row.setWrap(true);
        table.add(row).width(470f).left().row();
    }

    /** 把原版数值标签替换成实际值；原版的生命/护甲/建造时间都只有一个数值标签。 */
    private static void overrideStatValue(Table root, Stat stat, String value){
        String prefix = "[lightgray]" + stat.localized() + ":[]";
        for(Element child : root.getChildren()){
            if(!(child instanceof Table row)) continue;

            Seq<Label> labels = new Seq<>();
            collectLabels(row, labels);
            Label statLabel = null;
            for(Label label : labels){
                if(label.getText().toString().startsWith(prefix)){
                    statLabel = label;
                    break;
                }
            }
            if(statLabel == null) continue;

            boolean after = false;
            for(Label label : labels){
                if(label == statLabel){
                    after = true;
                }else if(after){
                    label.setText(value);
                    return;
                }
            }
        }
    }

    private static void collectLabels(Element element, Seq<Label> out){
        if(element instanceof Label label){
            out.add(label);
            return;
        }
        if(element instanceof Group group){
            for(Element child : group.getChildren()){
                collectLabels(child, out);
            }
        }
    }

    /** 找到 ContentInfoDialog 内部的滚动内容表。 */
    private static Table findScrollContent(Element element){
        if(element instanceof ScrollPane pane && pane.getWidget() instanceof Table table){
            return table;
        }
        if(element instanceof Group group){
            for(Element child : group.getChildren()){
                Table found = findScrollContent(child);
                if(found != null) return found;
            }
        }
        return null;
    }

    /** 把补充属性插在原版“基础”栏中指定属性之后；找不到时放到基础栏末尾。 */
    private static void addToGeneral(Table root, Table extra, String afterStat){
        int generalIndex = -1;
        for(int i = 0; i < root.getChildren().size; i++){
            if(root.getChildren().get(i) instanceof Label label
                && label.getText().toString().equals(StatCat.general.localized())){
                generalIndex = i;
                break;
            }
        }
        if(generalIndex < 0) return;

        int generalEnd = generalIndex + 1;
        for(int i = generalIndex + 1; i < root.getChildren().size; i++){
            if(root.getChildren().get(i) instanceof Label) break;
            generalEnd = i + 1;
        }

        int insertAt = generalEnd;
        if(afterStat != null){
            String prefix = "[lightgray]" + afterStat + ":[]";
            for(int i = generalIndex + 1; i < generalEnd; i++){
                if(!(root.getChildren().get(i) instanceof Table row)) continue;
                Seq<Label> labels = new Seq<>();
                collectLabels(row, labels);
                if(!labels.isEmpty() && labels.first().getText().toString().startsWith(prefix)){
                    insertAt = i + 1;
                    break;
                }
            }
        }

        root.add(extra).fillX().padLeft(10f).row();
        Cell<?> extraCell = root.getCells().pop();
        root.getCells().insert(insertAt, extraCell);
        root.getChildren().remove(extra, true);
        root.getChildren().insert(insertAt, extra);
        root.invalidate();
    }

    private static String fmt(float value){
        if(Math.abs(value - Math.round(value)) < 0.001f){
            return String.valueOf(Math.round(value));
        }
        String text = String.format(Locale.US, "%.2f", value);
        while(text.endsWith("0")) text = text.substring(0, text.length() - 1);
        if(text.endsWith(".")) text = text.substring(0, text.length() - 1);
        return text;
    }
}
