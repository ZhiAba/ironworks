package ironworks;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.layout.Table;
import mindustry.ui.Elems;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import static mindustry.Vars.*;

/**
 * 「铁流工业 · 辅助 HUD」子设置界面。
 *
 * 不放进主设置分类，避免和搜索/多人暂停混在一起；这里只放客户端显示类开关，
 * 所有开关只影响本机绘制，不改变服务端数值。
 */
public class IronworksAuxSettings{

    private IronworksAuxSettings(){
    }

    public static void show(){
        if(headless || ui == null) return;

        BaseDialog dialog = new BaseDialog(Core.bundle.get("ironworks.aux.title"));
        dialog.closeOnBack();

        float width = Math.min(560f, Core.graphics.getWidth() / arc.scene.ui.layout.Scl.scl(1f) - 48f);
        float descWidth = Math.max(220f, width - 72f);
        dialog.cont.pane(t -> {
            t.top().left();
            t.add(Core.bundle.get("ironworks.aux.hint"))
                .color(Color.lightGray).wrap().width(width - 32f).left().pad(8f).row();

            feature(t, IronworksHud.powerKey, false, descWidth,
                "setting.ironworks.hud.power.name", "setting.ironworks.hud.power.description");
            feature(t, IronworksHud.flowKey, false, descWidth,
                "setting.ironworks.hud.flow.name", "setting.ironworks.hud.flow.description");

            t.add().height(4f).row();
        }).width(width).maxHeight(Core.graphics.getHeight() / arc.scene.ui.layout.Scl.scl(1f) * 0.68f);

        dialog.buttons.defaults().size(180f, 64f).pad(4f);
        dialog.buttons.button(Core.bundle.get("ironworks.aux.off"), () -> {
            Core.settings.put(IronworksHud.powerKey, false);
            Core.settings.put(IronworksHud.flowKey, false);
            IronworksHud.reset();
            dialog.hide();
        });
        dialog.buttons.button("@close", dialog::hide);
        dialog.show();
    }

    private static void feature(Table table, String key, boolean def, float descWidth, String nameKey, String descriptionKey){
        table.table(Styles.grayPanel, card -> {
            card.left().margin(8f);
            card.add(Elems.check(Core.bundle.get(nameKey), Core.settings.getBool(key, def), value -> {
                Core.settings.put(key, value);
            })).growX().left().row();
            card.add(Core.bundle.get(descriptionKey))
                .color(Color.lightGray).left().wrap().width(descWidth).padTop(4f).row();
        }).growX().pad(4f).row();
    }
}
