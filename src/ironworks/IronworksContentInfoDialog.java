package ironworks;

import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Cell;
import arc.scene.ui.layout.Table;
import mindustry.ctype.UnlockableContent;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.ContentInfoDialog;
import mindustry.world.Block;

/**
 * 在建筑/单位详细信息顶部追加铁流工业的实时难度属性。
 *
 * <p>原版详情页读取的是静态 Block/UnitType 字段；建造速度、有效建筑血量、
 * 建筑护甲、单位造价/属性这些实际由 TeamRule 或难度逻辑控制，因此这里单独
 * 展示当前档位与 T7 词条的真实结果。</p>
 */
public class IronworksContentInfoDialog extends ContentInfoDialog{
    @Override
    public void show(UnlockableContent content){
        // 原版详情页在 show() 内重新 computeStats()，读取的是 Block/UnitType
        // 的静态字段。建筑血量在游戏里通过 TeamRule.blockHealthMultiplier
        // 生效，所以这里只在“构建详情页”的这一小段时间里把字段换成
        // 实际有效值，让原版“生命/护甲”行本身也显示真实数据；show() 返回后
        // 立刻恢复，绝不改变战斗里的基础数值。
        Block block = content instanceof Block b ? b : null;
        DifficultyCrafter crafter = block instanceof DifficultyCrafter c ? c : null;
        int baseHealth = block == null ? 0 : block.health;
        float baseArmor = block == null ? 0f : block.armor;
        float baseCraftTime = crafter == null ? 0f : crafter.craftTime;
        try{
            if(block != null){
                block.health = Math.max(1, Math.round(baseHealth * Difficulty.playerBlockHealthMultiplier()));
                block.armor = baseArmor + Difficulty.playerBlockArmorModifier();
            }
            // DifficultyCrafter 在原版 getProgressIncrease 上乘产能倍率，
            // 详情页的“生产时间”读取 craftTime；这里只做同样的展示换算。
            if(crafter != null){
                crafter.craftTime = Math.max(1f, baseCraftTime / Math.max(0.05f, Difficulty.productionMultiplier()));
            }
            super.show(content);
        }finally{
            if(block != null){
                block.health = baseHealth;
                block.armor = baseArmor;
            }
            if(crafter != null){
                crafter.craftTime = baseCraftTime;
            }
        }
        if(!(content instanceof Block) && !(content instanceof UnitType)) return;

        Table extra = new Table(Styles.black6);
        extra.margin(8f);
        extra.add("[accent]铁流工业实时属性[]").left().row();

        // 先显示这个内容的实际值，再显示当前难度的全局汇总。把它放进
        // ScrollPane 的内容表里，避免以前附加到 cont 底部后超出可视区域。
        Label specific = new Label("", Styles.outlineLabel);
        specific.setWrap(true);
        extra.add(specific).width(560f).left().row();

        Label label = new Label("", Styles.outlineLabel);
        label.setWrap(true);
        extra.add(label).width(560f).left().top();

        Table target = findScrollContent(cont);
        if(target == null) target = cont;
        addAtTop(target, extra);

        specific.update(() -> specific.setText(Difficulty.contentDetail(content)));
        label.update(() -> label.setText(Difficulty.describe(Difficulty.tier())));
    }

    /** 找到 ContentInfoDialog 内部的滚动内容表，把实时属性放到可滚动区域顶部。 */
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

    /**
     * Arc 的 Table.add() 只能追加；先追加再把这个 Cell/Element 移到最前，
     * 让玩家打开详情页时第一眼就能看到实时属性，而不是滚到最底部才看见。
     */
    private static void addAtTop(Table table, Table extra){
        table.row();
        Cell<?> cell = table.add(extra).growX().pad(8f).left();
        table.row(); // 标记 extra 自己占一行
        table.getCells().pop();
        table.getCells().insert(0, cell);
        table.getChildren().remove(extra, true);
        table.getChildren().insert(0, extra);
        table.invalidate();
    }
}
