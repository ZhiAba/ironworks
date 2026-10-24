package ironworks;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.math.Interp;
import arc.scene.actions.Actions;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.ResizeEvent;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;

import static mindustry.Vars.*;

/**
 * 左侧长条点开的独立日志界面（v2.1.4.0）。
 *
 * 不是 Dialog：windowBack + panel 直接挂在 hudGroup，打开时背景淡入、
 * 面板从左侧滑入居中；关闭反向滑出。每条日志右侧的 < 按钮会让该行
 * 淡出并向左滑走，动画结束后才从数据里移除，和 DeepSpace 的
 * notificationHistory 行为一致。
 */
public class IronworksLogPanel{
    private static boolean initialized;
    private static boolean open;
    private static Table windowBack, panel, list;
    private static Label countLabel;
    private static float panelWidth, panelHeight;

    private IronworksLogPanel(){
    }

    public static void init(){
        if(initialized || headless) return;
        initialized = true;
        Events.on(ResetEvent.class, e -> hideNow());
        Events.on(WorldLoadEvent.class, e -> hideNow());
        Events.on(ResizeEvent.class, e -> {
            if(open) layoutOverlay();
        });
    }

    public static void toggle(){
        if(open){
            hide();
        }else{
            show();
        }
    }

    public static void show(){
        if(headless || ui == null || ui.hudGroup == null) return;
        ensure();
        if(windowBack.parent != ui.hudGroup){
            if(windowBack.parent != null) windowBack.remove();
            ui.hudGroup.addChild(windowBack);
        }
        layoutOverlay();
        if(open){
            rebuildRows();
            updateCount();
            return;
        }

        open = true;
        rebuildRows();
        updateCount();

        windowBack.visible = true;
        windowBack.color.a = 0f;
        windowBack.clearActions();
        windowBack.actions(Actions.fadeIn(0.18f));

        panel.visible = true;
        panel.color.a = 1f;
        panel.clearActions();
        panel.setPosition(-panel.getWidth(), (Core.graphics.getHeight() - panel.getHeight()) / 2f);
        panel.actions(Actions.moveToAligned(
            Core.graphics.getWidth() / 2f,
            Core.graphics.getHeight() / 2f,
            Align.center, 0.42f, Interp.pow3Out));
    }

    public static void hide(){
        if(!open || panel == null) return;
        open = false;

        windowBack.clearActions();
        windowBack.actions(Actions.fadeOut(0.28f), Actions.hide());

        panel.clearActions();
        panel.actions(
            Actions.moveToAligned(-panel.getWidth() / 2f, Core.graphics.getHeight() / 2f,
                Align.center, 0.30f, Interp.pow3In),
            Actions.fadeOut(0.30f),
            Actions.hide());
    }

    public static void hideNow(){
        open = false;
        if(windowBack != null){
            windowBack.clearActions();
            windowBack.visible = false;
        }
        if(panel != null){
            panel.clearActions();
            panel.visible = false;
        }
    }

    private static void ensure(){
        if(windowBack == null){
            windowBack = new Table(Styles.black6);
            windowBack.touchable = Touchable.enabled;
            windowBack.visible = false;
            windowBack.setSize(Core.graphics.getWidth(), Core.graphics.getHeight());
            windowBack.addListener(new InputListener(){
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                    if(event.targetActor == windowBack){
                        hide();
                        return true;
                    }
                    return false;
                }
            });

            panel = new Table(Styles.black6);
            panel.touchable = Touchable.enabled;
            panel.top();
            panel.visible = false;
            windowBack.addChild(panel);

            buildContent();
            if(ui != null && ui.hudGroup != null){
                ui.hudGroup.addChild(windowBack);
            }
        }
    }

    private static void buildContent(){
        Table header = new Table();
        header.left();
        header.add(Core.bundle.get("ironworks.log.title"))
            .style(Styles.outlineLabel).color(Pal.accent).left().growX();
        header.button(Icon.cancel, Styles.clearNonei, IronworksLogPanel::hide).size(40f).right();
        panel.add(header).growX().pad(8f).row();

        countLabel = new Label("", Styles.outlineLabel);
        countLabel.setColor(Color.lightGray);
        panel.add(countLabel).left().padLeft(10f).padBottom(4f).row();

        list = new Table();
        ScrollPane pane = new ScrollPane(list, Styles.smallPane);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabledX(true);
        panel.add(pane).grow().pad(8f).row();

        Table footer = new Table();
        footer.right();
        footer.button(Core.bundle.get("ironworks.log.aux"), Styles.defaultt, () -> {
            hide();
            IronworksAuxSettings.show();
        }).size(170f, 52f).padRight(6f);
        footer.button(Core.bundle.get("ironworks.log.clear"), Styles.defaultt, () -> {
            IronworksNotifications.clear();
            rebuildRows();
            updateCount();
        }).size(120f, 52f);
        panel.add(footer).right().pad(8f).row();
    }

    private static void layoutOverlay(){
        if(windowBack == null || panel == null) return;
        float w = Core.graphics.getWidth(), h = Core.graphics.getHeight();
        windowBack.setSize(w, h);
        panelWidth = Math.min(920f, w * 0.78f);
        panelHeight = Math.min(720f, h * 0.72f);
        panel.setSize(panelWidth, panelHeight);
        if(open){
            panel.setPosition((w - panelWidth) / 2f, (h - panelHeight) / 2f);
        }
    }

    private static void rebuildRows(){
        if(list == null) return;
        list.clearChildren();
        list.top().left();

        if(IronworksNotifications.entries().isEmpty()){
            list.add(Core.bundle.get("ironworks.log.empty"))
                .color(Color.lightGray).pad(24f).row();
            return;
        }

        float textWidth = Math.max(220f, panelWidth - 150f);
        for(int i = IronworksNotifications.entries().size - 1; i >= 0; i--){
            IronworksNotifications.Entry entry = IronworksNotifications.entries().get(i);
            Table row = new Table(Styles.grayPanel);
            row.left().margin(6f);

            row.image(entry.warning ? Icon.warning : Icon.info)
                .size(28f).color(entry.warning ? Pal.remove : Pal.accent).padRight(8f);

            Table text = new Table();
            text.left();
            text.add(entry.text).left().wrap().width(textWidth).row();
            text.add(IronworksNotifications.time(entry))
                .left().color(Color.lightGray).padTop(2f).row();
            row.add(text).growX().left();

            ImageButton dismiss = row.button(Icon.left, Styles.clearNonei, () -> dismiss(row, entry))
                .size(36f).padLeft(6f).get();
            dismiss.resizeImage(24f);

            list.add(row).growX().pad(3f).row();
        }
    }

    private static void dismiss(Table row, IronworksNotifications.Entry entry){
        if(row == null) return;
        row.clearActions();
        row.actions(
            Actions.parallel(
                Actions.fadeOut(0.25f),
                Actions.moveBy(-Math.max(160f, row.getWidth()), 0f, 0.25f, Interp.pow3In)),
            Actions.run(() -> {
                IronworksNotifications.remove(entry);
                rebuildRows();
                updateCount();
            }));
    }

    private static void updateCount(){
        if(countLabel != null){
            countLabel.setText(Core.bundle.format("ironworks.log.count", IronworksNotifications.entries().size));
        }
    }
}
