package ironworks;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.scene.event.Touchable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ImageButton.ImageButtonStyle;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Table;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Icon;
import mindustry.ui.Styles;

import static mindustry.Vars.*;

/**
 * 左侧长条（v2.1.3.0）。
 *
 * 仿 DeepSpace NotificationFrag 的侧边历史按钮：26×520 的窄条贴在屏幕左缘，
 * 中间一枚齿轮，点击打开铁流工业日志。没有塞进 PlacementFragment.blockCatTable，
 * 因为我们的方块搜索已经在重排那张表。
 */
public class IronworksSidebar{
    public static final String key = "ironworks.sidebar.enabled";
    public static final boolean defaultEnabled = true;

    private static boolean initialized;
    private static Table root, bar;

    private IronworksSidebar(){
    }

    public static void init(){
        if(initialized || headless) return;
        initialized = true;

        Core.settings.defaults(key, defaultEnabled);
        Events.run(Trigger.update, IronworksSidebar::update);
        Events.on(ResetEvent.class, e -> hide());
        Events.on(WorldLoadEvent.class, e -> hide());
    }

    private static void update(){
        if(headless || ui == null || ui.hudGroup == null) return;
        if(!state.isGame() || ui.hudfrag == null || !ui.hudfrag.shown){
            hide();
            return;
        }
        if(!Core.settings.getBool(key, defaultEnabled)){
            hide();
            return;
        }

        ensure();
        bar.visible = true;
    }

    private static void hide(){
        if(bar != null) bar.visible = false;
    }

    private static void ensure(){
        if(bar == null){
            bar = new Table(Styles.black6);
            bar.margin(0f);
            bar.touchable = Touchable.enabled;
            bar.visible = false;

            ImageButtonStyle style = new ImageButtonStyle(Styles.clearNonei);
            style.imageUp = Icon.settings;
            style.imageUpColor = Color.lightGray;
            ImageButton button = new ImageButton(style);
            button.resizeImage(20f);
            button.clicked(IronworksLogPanel::toggle);

            Tooltip tip = new Tooltip(t -> t.background(Styles.black6).margin(4f)
                .add(Core.bundle.get("ironworks.sidebar.tooltip")).style(Styles.outlineLabel));
            tip.allowMobile = true;
            button.addListener(tip);

            bar.add(button).size(26f, 520f);
        }

        if(root == null || root.parent == null){
            if(bar.parent != null) bar.remove();
            root = null;
            if(ui != null && ui.hudGroup != null){
                ui.hudGroup.fill(t -> {
                    root = t;
                    t.touchable = Touchable.childrenOnly;
                    t.left().add(bar).width(26f).height(520f);
                });
            }
        }
    }
}
