package ironworks;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

import static mindustry.Vars.*;

/**
 * 左侧长条点击后打开的日志面板（仿 DeepSpace 的 notificationHistory）。
 * 显示最近 100 条铁流工业事件；可清空、可跳到辅助界面。
 */
public class IronworksLogDialog{

    private IronworksLogDialog(){
    }

    public static void show(){
        if(headless || ui == null) return;

        BaseDialog dialog = new BaseDialog(Core.bundle.get("ironworks.log.title"));
        dialog.closeOnBack();

        float width = Math.min(820f, Core.graphics.getWidth() / Scl.scl(1f) * 0.82f);
        dialog.cont.table(header -> {
            header.left();
            header.add(Core.bundle.format("ironworks.log.count", IronworksNotifications.entries().size))
                .left().color(Pal.accent);
            header.add(Core.bundle.get("ironworks.log.keep"))
                .right().color(Color.lightGray).growX();
        }).growX().pad(8f).row();

        dialog.cont.pane(t -> {
            t.top().left();
            if(IronworksNotifications.entries().isEmpty()){
                t.add(Core.bundle.get("ironworks.log.empty")).color(Color.lightGray).pad(24f);
            }else{
                for(int i = IronworksNotifications.entries().size - 1; i >= 0; i--){
                    IronworksNotifications.Entry entry = IronworksNotifications.entries().get(i);
                    t.table(Styles.grayPanel, card -> {
                        card.left().margin(6f);
                        card.image(entry.warning ? Icon.warning : Icon.info)
                            .size(28f).color(entry.warning ? Pal.remove : Pal.accent).padRight(8f);
                        card.table(text -> {
                            text.add(entry.text).left().wrap().width(width - 130f).row();
                            text.add(IronworksNotifications.time(entry))
                                .left().color(Color.lightGray).padTop(2f).row();
                        }).growX().left();
                    }).growX().pad(3f).row();
                }
            }
        }).width(width).maxHeight(Core.graphics.getHeight() / Scl.scl(1f) * 0.66f);

        dialog.buttons.defaults().size(190f, 64f).pad(4f);
        dialog.buttons.button(Core.bundle.get("ironworks.log.aux"), () -> {
            dialog.hide();
            IronworksAuxSettings.show();
        });
        dialog.buttons.button(Core.bundle.get("ironworks.log.clear"), () -> {
            IronworksNotifications.clear();
            dialog.hide();
            show();
        });
        dialog.buttons.button("@close", dialog::hide);
        dialog.show();
    }
}
