package ironworks;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Label;
import arc.scene.ui.ImageButton.ImageButtonStyle;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
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
    private static float barHeight;
    /** 通知条顶部距屏幕上缘的逻辑像素；与 barHeight 一起变化时重建根表。 */
    private static float barTopPad = -1f;

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

        layoutBounds();
        ensure();
        bar.visible = barHeight >= 60f;
    }

    private static void hide(){
        if(bar != null) bar.visible = false;
    }

    /**
     * 通知条严格夹在左上 HUD 下边界与左下指挥按钮上边界之间。
     * 指挥模式会在“指挥”按钮上方多出队列按钮，扫描到的容器高度会自动包含这一层。
     */
    private static void layoutBounds(){
        float screenH = Core.graphics.getHeight();
        float scl = Scl.scl(1f);
        float gap = 6f * scl;
        float hudBottom = topHudBottom(screenH, scl);
        float commandTop = commandTop(screenH, scl);

        float available = Math.max(0f, hudBottom - commandTop - gap * 2f);
        float desiredHeight = Math.min(available, 900f * scl) / scl;
        float desiredTop = Math.max(0f, screenH - hudBottom + gap) / scl;
        float oldHeight = barHeight;

        if(desiredHeight < 60f){
            barHeight = 0f;
        }else{
            barHeight = desiredHeight;
        }

        if(Math.abs(desiredTop - barTopPad) > 0.5f || Math.abs(barHeight - oldHeight) > 0.5f){
            barTopPad = desiredTop;
            if(root != null){
                root.remove();
                root = null;
            }
        }
    }

    /** 左上角 HUD 的实际下边界；用状态/目标栏的底边，而不是铺满屏幕的外层容器。 */
    private static float topHudBottom(float screenH, float scl){
        Element waves = findNamed(ui.hudGroup, "waves/editor");
        if(waves != null && waves.visible && waves.getHeight() > 1f){
            return Mathf.clamp(waves.y, 0f, screenH);
        }
        return Math.max(0f, screenH - 220f * scl);
    }

    /** 左下指挥按钮的上边界；队列按钮可见时取队列层顶部，避免遮住新增的一层。 */
    private static float commandTop(float screenH, float scl){
        String queue = Core.bundle.get("command.queue", "队列指挥");
        String command = Core.bundle.get("command", "指挥");
        for(String text : new String[]{queue, command}){
            Label label = findCommandLabel(ui.hudGroup, text);
            if(label == null || !label.visible) continue;
            Element e = label;
            while(e != null && !(e instanceof Button)) e = e.parent;
            if(e instanceof Button b && b.visible && b.getHeight() > 1f){
                return Mathf.clamp(b.y + b.getHeight(), 0f, screenH);
            }
        }
        // 找不到按钮时按底栏高度保守估算，宁可留空也不压住底部 UI。
        return Mathf.clamp(screenH * 0.16f, 0f, screenH);
    }

    private static Element findNamed(Group group, String name){
        if(group == null) return null;
        for(Element child : group.getChildren()){
            if(name.equals(child.name)) return child;
            if(child instanceof Group nested){
                Element found = findNamed(nested, name);
                if(found != null) return found;
            }
        }
        return null;
    }

    private static Label findCommandLabel(Group group, String text){
        if(group == null) return null;
        for(Element child : group.getChildren()){
            if(child instanceof Label label && label.getText() != null && text.contentEquals(label.getText())){
                return label;
            }
            if(child instanceof Group nested){
                Label found = findCommandLabel(nested, text);
                if(found != null) return found;
            }
        }
        return null;
    }

    private static void ensure(){
        if(bar == null){
            bar = new Table(Tex.pane);
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

            bar.add(button).width(26f).growY();
        }

        if(root == null || root.parent == null){
            if(bar.parent != null) bar.remove();
            root = null;
            if(ui != null && ui.hudGroup != null){
                ui.hudGroup.fill(t -> {
                    root = t;
                    t.touchable = Touchable.childrenOnly;
                    t.left().top();
                    // 先放顶部占位，再把通知条紧贴左上 HUD 下缘；高度按实际可用空间计算。
                    t.add().height(Math.max(0f, barTopPad)).row();
                    t.add(bar).width(26f).height(barHeight);
                });
            }
        }
    }
}
