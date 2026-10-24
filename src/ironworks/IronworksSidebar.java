package ironworks;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.scene.event.Touchable;
import arc.scene.style.Drawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ImageButton.ImageButtonStyle;
import arc.scene.ui.Tooltip;
import arc.scene.ui.layout.Table;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;

import static mindustry.Vars.*;

/**
 * 左侧快捷工具栏（v2.1.2.0）。
 *
 * 仿 DeepSpace 的 ToolBarFrag：窄条 + 50px 图标按钮 + 悬停提示 + 可勾选状态。
 * 没有挂进 PlacementFragment.blockCatTable：我们的方块搜索已经在重排那张表，
 * 再塞一列会互相拆台；这里独立挂在 hudGroup 左侧中部，位置稳定、不挡建造栏。
 */
public class IronworksSidebar{
    public static final String key = "ironworks.sidebar.enabled";
    public static final boolean defaultEnabled = true;

    private static boolean initialized;
    private static Table root, panel;
    private static ImageButton powerButton, flowButton, damageButton;

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
        panel.visible = true;
        powerButton.setChecked(Core.settings.getBool(IronworksHud.powerKey, false));
        flowButton.setChecked(Core.settings.getBool(IronworksHud.flowKey, false));
        damageButton.setChecked(Core.settings.getBool(DamageNumbers.settingKey, true));
    }

    private static void hide(){
        if(panel != null) panel.visible = false;
    }

    private static void ensure(){
        if(panel == null){
            panel = new Table(Styles.black6);
            panel.margin(3f);
            panel.touchable = Touchable.enabled;
            panel.top();
            panel.visible = false;

            settingsButton(panel, Icon.settings, "ironworks.sidebar.settings", IronworksAuxSettings::show);
            panel.image(mindustry.gen.Tex.whiteui).height(1f).growX().color(Pal.gray).pad(4f).row();
            powerButton = toggleButton(panel, Icon.power, "ironworks.sidebar.power", IronworksHud.powerKey, false);
            flowButton = toggleButton(panel, Icon.list, "ironworks.sidebar.flow", IronworksHud.flowKey, false);
            damageButton = toggleButton(panel, Icon.eye, "ironworks.sidebar.damage", DamageNumbers.settingKey, true);
            panel.image(mindustry.gen.Tex.whiteui).height(1f).growX().color(Pal.gray).pad(4f).row();
            toggleButton(panel, Icon.left, "ironworks.sidebar.hide", key, defaultEnabled);
        }

        if(root == null || root.parent == null){
            if(panel.parent != null) panel.remove();
            root = null;
            if(ui != null && ui.hudGroup != null){
                ui.hudGroup.fill(t -> {
                    root = t;
                    t.touchable = Touchable.childrenOnly;
                    t.left().add(panel).width(54f).padLeft(6f);
                });
            }
        }
    }

    private static void settingsButton(Table table, Drawable icon, String tooltipKey, Runnable action){
        ImageButton button = new ImageButton(style(icon, false));
        button.resizeImage(30f);
        button.clicked(action);
        addTooltip(button, tooltipKey);
        table.add(button).size(44f).pad(2f).row();
    }

    private static ImageButton toggleButton(Table table, Drawable icon, String tooltipKey, String settingKey, boolean def){
        ImageButton button = new ImageButton(style(icon, true));
        button.resizeImage(30f);
        button.setChecked(Core.settings.getBool(settingKey, def));
        button.clicked(() -> {
            boolean value = !Core.settings.getBool(settingKey, def);
            Core.settings.put(settingKey, value);
            button.setChecked(value);
        });
        button.update(() -> button.setChecked(Core.settings.getBool(settingKey, def)));
        addTooltip(button, tooltipKey);
        table.add(button).size(44f).pad(2f).row();
        return button;
    }

    private static ImageButtonStyle style(Drawable icon, boolean toggle){
        ImageButtonStyle style = new ImageButtonStyle(toggle ? Styles.clearNoneTogglei : Styles.clearNonei);
        style.imageUp = icon;
        style.imageUpColor = Color.white;
        return style;
    }

    private static void addTooltip(ImageButton button, String tooltipKey){
        Tooltip tip = new Tooltip(t -> t.background(Styles.black6).margin(4f)
            .add(Core.bundle.get(tooltipKey)).style(Styles.outlineLabel));
        tip.allowMobile = true;
        button.addListener(tip);
    }
}
