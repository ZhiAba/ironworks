package ironworks;

import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import mindustry.ctype.UnlockableContent;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.ContentInfoDialog;
import mindustry.world.Block;

/**
 * 在建筑/单位详细信息底部追加铁流工业的实时难度属性。
 *
 * <p>原版详情页读取的是静态 Block/UnitType 字段；建造速度、有效建筑血量、
 * 建筑护甲、单位造价/属性这些实际由 TeamRule 或难度逻辑控制，因此这里单独
 * 展示当前档位与 T7 词条的真实结果。</p>
 */
public class IronworksContentInfoDialog extends ContentInfoDialog{
    @Override
    public void show(UnlockableContent content){
        super.show(content);
        if(!(content instanceof Block) && !(content instanceof UnitType)) return;

        Table extra = new Table(Styles.black6);
        extra.margin(8f);
        Label label = new Label("", Styles.outlineLabel);
        label.setWrap(true);
        extra.add(label).width(560f).left().row();

        Label specific = new Label("", Styles.outlineLabel);
        specific.setWrap(true);
        extra.add(specific).width(560f).left();
        label.update(() -> label.setText(Difficulty.describe(Difficulty.tier())));
        specific.update(() -> specific.setText(Difficulty.contentDetail(content)));

        cont.row();
        cont.add(extra).growX().pad(8f).row();
    }
}
