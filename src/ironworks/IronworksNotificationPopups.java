package ironworks;

import arc.Core;
import arc.Events;
import arc.math.Interp;
import arc.math.Mathf;
import arc.scene.ui.layout.WidgetGroup;
import arc.scene.actions.Actions;
import arc.scene.event.ClickListener;
import arc.scene.event.InputEvent;
import arc.scene.event.Touchable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.ResizeEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;

import static mindustry.Vars.*;

/**
 * 左侧日志的「弹出通知条」（v2.1.6.0）。
 *
 * 和 DeepSpace 的 NotifyItemBar 一样：新警告从屏幕右侧滑入，最多同时 5 条，
 * 超出的排队；自动停留 8 秒，点通知条打开完整日志，点右侧 < 收起；
 * 面板打开时不再弹，避免遮住日志界面。
 */
public class IronworksNotificationPopups{
    private static final int maxBars = 5;
    private static final float staySeconds = 8f;
    private static final float gapUnits = 6f;
    private static final float heightUnits = 96f;

    private static boolean initialized;
    private static WidgetGroup root;
    private static final Seq<Popup> showing = new Seq<>();
    private static final Seq<IronworksNotifications.Entry> queue = new Seq<>();
    private static float popupWidth, popupHeight, gap;

    private IronworksNotificationPopups(){
    }

    public static void init(){
        if(initialized || headless) return;
        initialized = true;
        IronworksNotifications.addListener(IronworksNotificationPopups::enqueue);
        Events.on(ResetEvent.class, e -> clearAll());
        Events.on(WorldLoadEvent.class, e -> clearAll());
        Events.on(ResizeEvent.class, e -> {
            if(root != null) layoutRoot();
        });
        Events.run(Trigger.update, IronworksNotificationPopups::update);
    }

    private static void enqueue(IronworksNotifications.Entry entry){
        if(headless || !entry.warning || ui == null || ui.hudGroup == null) return;
        if(IronworksLogPanel.isOpen()) return;
        if(!state.isGame() || ui.hudfrag == null || !ui.hudfrag.shown) return;
        queue.add(entry);
        if(queue.size > 20) queue.remove(0);
    }

    private static void update(){
        if(headless) return;
        if(IronworksLogPanel.isOpen()){
            clearAll();
            return;
        }
        if(!state.isGame() || ui == null || ui.hudGroup == null || ui.hudfrag == null || !ui.hudfrag.shown){
            clearAll();
            return;
        }

        ensureRoot();
        layoutRoot();
        drainQueue();
        layoutBars();
        if(root != null) root.visible = showing.size > 0;
    }

    private static void ensureRoot(){
        if(root == null || root.parent != ui.hudGroup){
            if(root != null && root.parent != null) root.remove();
            root = new WidgetGroup();
            root.touchable = Touchable.childrenOnly;
            ui.hudGroup.addChild(root);
        }
    }

    private static void layoutRoot(){
        if(root == null || ui == null) return;
        float w = Core.graphics.getWidth(), h = Core.graphics.getHeight();
        float scl = Scl.scl(1f);
        popupWidth = Math.max(160f, Math.min(400f * scl, w - 12f * scl));
        popupHeight = heightUnits * scl;
        gap = gapUnits * scl;
        float rootW = popupWidth + 12f * scl;
        float rootH = maxBars * (popupHeight + gap);
        root.setSize(rootW, rootH);
        root.setPosition(w - rootW - 8f * scl, h - rootH - 8f * scl);
        for(Popup popup : showing){
            popup.bar.setSize(popupWidth, popupHeight);
        }
    }

    private static void drainQueue(){
        if(root == null) return;
        while(showing.size < maxBars && !queue.isEmpty()){
            Popup popup = create(queue.remove(0));
            showing.insert(0, popup);
        }
    }

    private static void layoutBars(){
        if(root == null) return;
        for(int i = 0; i < showing.size; i++){
            Popup popup = showing.get(i);
            float target = root.getHeight() - (i + 1) * popupHeight - i * gap;
            if(popup.justAdded){
                popup.bar.y = target;
                popup.justAdded = false;
            }else{
                popup.bar.y = Mathf.lerpDelta(popup.bar.y, target, 0.25f);
            }
        }
    }

    private static Popup create(IronworksNotifications.Entry entry){
        Table bar = new Table(Tex.pane);
        bar.touchable = Touchable.enabled;
        bar.margin(4f);
        bar.left();

        bar.image(Tex.whiteui).width(4f).growY().color(Pal.remove);
        bar.image(Icon.warning).size(28f).color(Pal.remove).padLeft(6f).padRight(6f);

        Table text = new Table();
        text.left();
        float textUnits = Math.max(100f, popupWidth / Scl.scl(1f) - 84f);
        text.add(entry.text).width(textUnits).wrap().left().fontScale(0.8f).row();
        bar.add(text).growX().left();

        Popup popup = new Popup(bar);
        ImageButton close = bar.button(Icon.left, Styles.clearNonei, () -> dismiss(popup, false))
            .size(36f).padLeft(4f).get();
        close.resizeImage(22f);
        // 箭头自己的点击不要冒泡到通知条，否则会同时打开日志面板。
        close.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                event.stop();
            }
        });

        bar.addListener(new ClickListener(){
            @Override
            public void clicked(InputEvent event, float x, float y){
                if(event.targetActor != close){
                    dismiss(popup, true);
                }
            }
        });

        bar.setSize(popupWidth, popupHeight);
        bar.x = popupWidth;
        bar.color.a = 0f;
        bar.actions(Actions.sequence(
            Actions.parallel(
                Actions.fadeIn(0.2f),
                Actions.moveBy(-popupWidth, 0f, 0.28f, Interp.pow3Out)),
            Actions.delay(staySeconds),
            Actions.run(() -> dismiss(popup, false))));
        if(root != null) root.addChild(bar);
        return popup;
    }

    private static void dismiss(Popup popup, boolean openPanel){
        if(popup.removing) return;
        popup.removing = true;
        popup.bar.clearActions();
        popup.bar.actions(
            Actions.parallel(
                Actions.fadeOut(0.25f),
                Actions.moveBy(popupWidth, 0f, 0.25f, Interp.pow3In)),
            Actions.run(() -> {
                if(popup.bar.parent != null) popup.bar.remove();
                showing.remove(popup, true);
                if(root != null && showing.isEmpty()) root.visible = false;
                if(!IronworksLogPanel.isOpen()){
                    drainQueue();
                    layoutBars();
                }
            }));
        if(openPanel){
            IronworksLogPanel.show();
        }
    }

    private static void clearAll(){
        queue.clear();
        for(Popup popup : showing){
            popup.removing = true;
            if(popup.bar.parent != null) popup.bar.remove();
        }
        showing.clear();
        if(root != null) root.visible = false;
    }

    private static class Popup{
        final Table bar;
        boolean removing;
        boolean justAdded = true;

        Popup(Table bar){
            this.bar = bar;
        }
    }
}
