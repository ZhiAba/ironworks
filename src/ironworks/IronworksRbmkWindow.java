package ironworks;

import arc.Core;
import arc.Events;
import arc.func.Func;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.math.Interp;
import arc.math.Mathf;
import arc.scene.actions.Actions;
import arc.scene.event.ClickListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.Label;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.Slider;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import ironworks.IronworksRBMK.RBMKReactor;
import ironworks.IronworksRBMK.RBMKReactor.RBMKReactorBuild;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.ResizeEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.ui.Styles;

import static mindustry.Vars.*;

/**
 * RBMK 弹出式控制台。
 *
 * <p>不再把整堆控件塞进方块 config 的长条里：方块 config 只保留一个入口按钮，
 * 真正的 UI 是挂在 hudGroup 上的黑色半透明浮层，跟左侧日志/通知界面同一套视觉。
 * 四个页签：总览、控制、分区、维护；所有数值用 update() 实时刷新。
 */
public class IronworksRbmkWindow{
    private static final int TAB_OVERVIEW = 0, TAB_CONTROL = 1, TAB_ZONES = 2, TAB_SERVICE = 3;

    private static boolean initialized, open;
    private static Table windowBack, panel, body;
    private static RBMKReactorBuild current;
    private static int tab = TAB_OVERVIEW;
    private static float panelWidth, panelHeight, contentWidth;

    private IronworksRbmkWindow(){
    }

    public static void init(){
        if(initialized || headless) return;
        initialized = true;
        Events.on(ResetEvent.class, e -> hideNow());
        Events.on(WorldLoadEvent.class, e -> hideNow());
        Events.on(ResizeEvent.class, e -> {
            if(open){
                layoutOverlay();
                buildBody();
            }
        });
        Events.run(Trigger.update, IronworksRbmkWindow::tick);
    }

    public static boolean isOpen(){
        return open;
    }

    private static void tick(){
        if(!open) return;
        if(current == null || current.dead || current.block == null){
            hideNow();
            return;
        }
        // 面板本身不重建；页签内容里的 Label 自己 update()。
    }

    public static void show(RBMKReactorBuild build){
        if(headless || build == null || build.dead || ui == null || ui.hudGroup == null) return;
        ensure();
        current = build;
        if(windowBack.parent != ui.hudGroup){
            if(windowBack.parent != null) windowBack.remove();
            ui.hudGroup.addChild(windowBack);
        }
        layoutOverlay();
        buildBody();

        if(open) return;
        open = true;
        windowBack.visible = true;
        windowBack.color.a = 0f;
        windowBack.clearActions();
        windowBack.actions(Actions.fadeIn(0.18f));

        panel.visible = true;
        panel.color.a = 1f;
        panel.clearActions();
        panel.setPosition((Core.graphics.getWidth() - panelWidth) / 2f, -panelHeight);
        panel.actions(Actions.moveTo((Core.graphics.getWidth() - panelWidth) / 2f,
            (Core.graphics.getHeight() - panelHeight) / 2f, 0.35f, Interp.pow3Out));
    }

    public static void hide(){
        if(!open || panel == null) return;
        open = false;
        windowBack.clearActions();
        windowBack.actions(Actions.fadeOut(0.22f), Actions.hide());
        panel.clearActions();
        panel.actions(Actions.moveTo((Core.graphics.getWidth() - panelWidth) / 2f,
            -panelHeight, 0.25f, Interp.pow3In), Actions.hide());
    }

    public static void hideNow(){
        open = false;
        current = null;
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
        if(windowBack != null) return;

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

        Table header = new Table();
        header.left();
        header.add(Core.bundle.get("ironworks.rbmk.panel.title"))
            .style(Styles.outlineLabel).color(Pal.accent).left().growX();
        Label state = new Label("");
        state.update(() -> {
            if(current != null){
                state.setText(stateText(current));
                state.setColor(stateColor(current));
            }
        });
        header.add(state).right().padRight(10f);
        header.button(Icon.cancel, Styles.clearNonei, IronworksRbmkWindow::hide).size(40f);
        panel.add(header).growX().pad(8f).row();

        Table sub = new Table();
        sub.left();
        Label coords = new Label("");
        coords.update(() -> {
            if(current != null){
                coords.setText(Core.bundle.format("ironworks.rbmk.win.coords",
                    current.tileX(), current.tileY(), current.items.get(IronworksNuclear.fuelRod),
                    current.items.get(Items.thorium), current.items.get(IronworksNuclear.depletedRod)));
            }
        });
        sub.add(coords).left().color(Color.lightGray).growX();
        panel.add(sub).growX().padLeft(10f).padRight(10f).row();

        Table tabs = new Table();
        tabs.left();
        String[] keys = {"ironworks.rbmk.tab.overview", "ironworks.rbmk.tab.control",
            "ironworks.rbmk.tab.zones", "ironworks.rbmk.tab.service"};
        for(int i = 0; i < keys.length; i++){
            final int index = i;
            TextButton b = new TextButton(Core.bundle.get(keys[i]), Styles.flatTogglet);
            b.clicked(() -> {
                tab = index;
                buildBody();
            });
            b.update(() -> b.setChecked(tab == index));
            tabs.add(b).size(82f, 36f).padRight(4f);
        }
        panel.add(tabs).left().padLeft(8f).padTop(4f).padBottom(4f).row();

        body = new Table();
        body.top().left();
        ScrollPane pane = new ScrollPane(body, Styles.smallPane);
        pane.setFadeScrollBars(false);
        pane.setScrollingDisabledX(true);
        pane.setOverscroll(false, false);
        panel.add(pane).grow().pad(8f).row();
    }

    private static void layoutOverlay(){
        if(windowBack == null || panel == null) return;
        float w = Core.graphics.getWidth(), h = Core.graphics.getHeight();
        float scl = Scl.scl(1f);
        windowBack.setSize(w, h);
        panelWidth = Math.min(920f, w / scl * 0.90f) * scl;
        panelHeight = Math.min(800f, h / scl * 0.84f) * scl;
        contentWidth = panelWidth / scl - 24f;
        panel.setSize(panelWidth, panelHeight);
        if(open){
            panel.setPosition((w - panelWidth) / 2f, (h - panelHeight) / 2f);
        }
    }

    private static void buildBody(){
        if(body == null) return;
        body.clearChildren();
        body.top().left();
        if(current == null) return;
        switch(tab){
            case TAB_CONTROL -> buildControl();
            case TAB_ZONES -> buildZones();
            case TAB_SERVICE -> buildService();
            default -> buildOverview();
        }
    }

    // ------------------------------------------------------------------
    // 总览
    // ------------------------------------------------------------------
    private static void buildOverview(){
        Table grid = new Table();
        grid.defaults().pad(3f);
        stat(grid, "ironworks.rbmk.row.flux", b -> Mathf.round(b.flux * 100f) + "%", b -> Pal.accent);
        stat(grid, "ironworks.rbmk.win.fuelTemp", b -> Mathf.round(b.heat * 100f) + "%  (" + Mathf.round(b.tempK) + "°C)", b -> heatColor(b));
        stat(grid, "ironworks.rbmk.win.coolantTemp", b -> Mathf.round(b.coolantHeat * 100f) + "%  (" + Mathf.round(b.coolantK) + "°C)", b -> coolantColor(b));
        stat(grid, "ironworks.rbmk.row.coolant", b -> Mathf.round(b.flow * 100f) + "%", b -> b.flow < 0.9f ? Pal.remove : Color.valueOf("7fe08a"));
        stat(grid, "ironworks.rbmk.row.water", b -> Mathf.round(b.liquids.get(Liquids.water)) + "/" + (int)b.waterCap() + " L", b -> Liquids.water.color);
        stat(grid, "ironworks.rbmk.row.steam", b -> Mathf.round(b.liquids.get(IronworksLiquids.steam)) + "/" + (int)b.steamCap() + " L", b -> IronworksLiquids.steam.color);
        stat(grid, "ironworks.rbmk.win.decay", b -> Mathf.round(b.decayHeat * 100f) + "%", b -> Color.valueOf("ffb066"));
        stat(grid, "ironworks.rbmk.win.meltDamage", b -> b.block instanceof RBMKReactor r && r.zoned ? Mathf.round(maxMelt(b) * 100f) + "%" : "—", b -> maxMelt(b) > 0.5f ? Pal.remove : Color.valueOf("ffd166"));
        stat(grid, "ironworks.rbmk.win.rodTarget", b -> Mathf.round(b.targetRods * 100f) + "%", b -> Pal.accent);
        stat(grid, "ironworks.rbmk.win.rodNow", b -> Mathf.round(b.rods * 100f) + "%", b -> Pal.accent);
        stat(grid, "ironworks.rbmk.row.rate", b -> Mathf.round(b.steamRate) + " L/s", b -> Liquids.water.color);
        stat(grid, "ironworks.rbmk.row.xenon", b -> Mathf.round(b.xenon * 100f) + "%", b -> Color.valueOf("b6a8ff"));
        body.add(grid).width(contentWidth).row();

        Table tip = new Table(Styles.grayPanel);
        tip.left().margin(6f);
        tip.add(Core.bundle.get("ironworks.rbmk.win.rodtip")).wrap()
            .width(contentWidth - 20f).color(Color.valueOf("ffd166")).left().row();
        Label state = new Label("");
        state.update(() -> {
            if(current != null){
                state.setText(stateHint(current));
                state.setColor(stateColor(current));
            }
        });
        tip.add(state).wrap().width(contentWidth - 20f).left().padTop(4f).row();
        body.add(tip).width(contentWidth).padTop(4f).row();
    }

    private static void stat(Table grid, String key, Func<RBMKReactorBuild, String> value, Func<RBMKReactorBuild, Color> color){
        Table card = new Table(Styles.grayPanel);
        card.left().margin(4f);
        card.add(Core.bundle.get(key)).left().color(Color.lightGray).row();
        Label v = new Label("");
        v.update(() -> {
            if(current != null){
                v.setText(value.get(current));
                v.setColor(color.get(current));
            }
        });
        card.add(v).left().row();
        grid.add(card).width(contentWidth / 2f - 5f).top().left();
        if(grid.getChildren().size % 2 == 0) grid.row();
    }

    // ------------------------------------------------------------------
    // 控制
    // ------------------------------------------------------------------
    private static void buildControl(){
        Table mode = section("ironworks.rbmk.win.modeSection");
        Table row = new Table();
        TextButton semi = modeButton("ironworks.rbmk.mode.semi", IronworksRBMK.MODE_SEMI, mode);
        TextButton auto = modeButton("ironworks.rbmk.mode.auto", IronworksRBMK.MODE_AUTO, mode);
        TextButton manual = modeButton("ironworks.rbmk.mode.manual", IronworksRBMK.MODE_MANUAL, mode);
        row.add(semi).size(100f, 36f).padRight(4f);
        row.add(auto).size(100f, 36f).padRight(4f);
        row.add(manual).size(100f, 36f).row();
        mode.add(row).left().row();
        Label modeHint = new Label("");
        modeHint.update(() -> {
            if(current != null) modeHint.setText(Core.bundle.get(switch(current.controlMode){
                case IronworksRBMK.MODE_AUTO -> "ironworks.rbmk.win.mode.autoHint";
                case IronworksRBMK.MODE_MANUAL -> "ironworks.rbmk.win.mode.manualHint";
                default -> "ironworks.rbmk.win.mode.semiHint";
            }));
        });
        mode.add(modeHint).wrap().width(contentWidth - 24f).color(Color.lightGray).left().row();

        Table target = section("ironworks.rbmk.win.targetSection");
        Label targetLabel = new Label("");
        targetLabel.update(() -> {
            if(current == null) return;
            if(current.controlMode == IronworksRBMK.MODE_AUTO){
                targetLabel.setText(Core.bundle.format("ironworks.rbmk.win.autoTarget",
                    Mathf.round(current.autoPower * 100f), Mathf.round(Math.min(current.flux, 1f) * 100f)));
            }else if(current.controlMode == IronworksRBMK.MODE_MANUAL && current.selectedZone >= 0){
                targetLabel.setText(Core.bundle.format("ironworks.rbmk.win.zoneTarget",
                    current.selectedZone + 1, Mathf.round(current.zoneTarget[current.selectedZone] * 100f),
                    Mathf.round(current.zoneRods[current.selectedZone] * 100f)));
            }else{
                targetLabel.setText(Core.bundle.format("ironworks.rbmk.zone.rodline",
                    Mathf.round(current.targetRods * 100f), Mathf.round(current.rods * 100f)));
            }
        });
        target.add(targetLabel).left().row();
        Slider targetSlider = new Slider(0f, 100f, 1f, false);
        targetSlider.moved(v -> {
            if(current != null) current.configure((int)v);
        });
        targetSlider.update(() -> {
            if(current == null) return;
            float shown = current.controlMode == IronworksRBMK.MODE_AUTO ? current.autoPower
                : current.controlMode == IronworksRBMK.MODE_MANUAL && current.selectedZone >= 0
                    ? current.zoneTarget[current.selectedZone] : current.targetRods;
            if(!targetSlider.isDragging()) targetSlider.setValue(shown * 100f);
        });
        target.add(targetSlider).width(contentWidth - 30f).row();
        target.add(Core.bundle.get("ironworks.rbmk.win.targetHint")).wrap()
            .width(contentWidth - 24f).color(Color.lightGray).left().row();

        Table zones = section("ironworks.rbmk.win.zoneSection");
        Table zoneRow = new Table();
        TextButton master = zoneButton(Core.bundle.get("ironworks.rbmk.zone.master"), -1);
        zoneRow.add(master).size(88f, 34f).padRight(4f);
        for(int i = 0; i < 9; i++){
            zoneRow.add(zoneButton(Core.bundle.format("ironworks.rbmk.zone.unit", i + 1), i))
                .size(88f, 34f).padRight(4f);
        }
        ScrollPane zonePane = new ScrollPane(zoneRow);
        zonePane.setFadeScrollBars(false);
        zonePane.setScrollingDisabled(false, true);
        zonePane.setOverscroll(false, false);
        zones.add(zonePane).width(contentWidth - 16f).height(46f).row();
        zones.add(Core.bundle.get("ironworks.rbmk.win.zoneHint")).wrap()
            .width(contentWidth - 24f).color(Color.lightGray).left().row();

        Table water = section("ironworks.rbmk.win.waterSection");
        Label waterLine = new Label("");
        waterLine.update(() -> {
            if(current == null) return;
            int cur = Mathf.round(current.liquids.get(Liquids.water));
            float high = current.controlMode == IronworksRBMK.MODE_MANUAL ? current.waterTarget : current.waterLevel;
            float low = current.controlMode == IronworksRBMK.MODE_MANUAL ? current.waterLowTarget : current.waterLowLevel;
            waterLine.setText(Core.bundle.format("ironworks.rbmk.water.line", cur, (int)current.waterCap(),
                Mathf.round(low * 100f), Mathf.round(high * 100f)));
        });
        water.add(waterLine).left().row();

        water.add(Core.bundle.get("ironworks.rbmk.water.low")).left().color(Color.lightGray).row();
        Slider lowSlider = waterSlider(false);
        water.add(lowSlider).width(contentWidth - 30f).row();

        water.add(Core.bundle.get("ironworks.rbmk.water.high")).left().color(Color.lightGray).row();
        Slider highSlider = waterSlider(true);
        water.add(highSlider).width(contentWidth - 30f).row();

        water.add(Core.bundle.get("ironworks.rbmk.win.throttle")).left().color(Color.lightGray).row();
        Slider throttle = throttleSlider();
        water.add(throttle).width(contentWidth - 30f).row();
        Label waterHint = new Label("");
        waterHint.update(() -> {
            if(current != null) waterHint.setText(Core.bundle.get(current.controlMode == IronworksRBMK.MODE_MANUAL
                ? "ironworks.rbmk.waterhint.manual" : "ironworks.rbmk.waterhint.auto"));
        });
        water.add(waterHint).wrap().width(contentWidth - 24f).color(Color.lightGray).left().row();

        Table emergency = section("ironworks.rbmk.win.emergencySection");
        Table actions = new Table();
        actions.button(Core.bundle.get("ironworks.rbmk.scram"), Styles.defaultt, () -> {
            if(current != null) current.configure(-1);
        }).size(220f, 42f).padRight(8f).with(b -> b.setColor(Pal.remove));
        TextButton boron = new TextButton("", Styles.defaultt);
        boron.clicked(() -> {
            if(current != null) current.configure(-2);
        });
        boron.update(() -> boron.setText(current == null ? Core.bundle.get("ironworks.rbmk.boron.idle")
            : Core.bundle.get(current.boronArmed ? "ironworks.rbmk.boron.armed" : "ironworks.rbmk.boron.idle")));
        actions.add(boron).size(220f, 42f).row();

        TextButton reset = new TextButton(Core.bundle.get("ironworks.rbmk.win.resetScram"), Styles.defaultt);
        reset.clicked(() -> {
            if(current != null) current.configure(-4);
        });
        actions.add(reset).size(220f, 38f).padTop(4f).row();
        emergency.add(actions).left().row();
        TextButton mute = new TextButton("", Styles.flatTogglet);
        mute.clicked(() -> {
            if(current != null) current.configure(-3);
        });
        mute.update(() -> {
            if(current == null) return;
            mute.setText(Core.bundle.format("ironworks.rbmk.alerts",
                Core.bundle.get(current.alertsMuted ? "ironworks.rbmk.alerts.off" : "ironworks.rbmk.alerts.on")));
            mute.setChecked(current.alertsMuted);
            mute.touchable = current.controlMode == IronworksRBMK.MODE_MANUAL ? Touchable.enabled : Touchable.disabled;
        });
        emergency.add(mute).width(contentWidth - 30f).height(38f).padTop(6f).row();
        emergency.add(Core.bundle.get("ironworks.rbmk.alerts.hint")).wrap()
            .width(contentWidth - 24f).color(Color.lightGray).left().row();
    }

    private static TextButton modeButton(String key, int mode, Table parent){
        TextButton b = new TextButton(Core.bundle.get(key), Styles.flatTogglet);
        b.clicked(() -> {
            if(current != null) current.configure(switch(mode){
                case IronworksRBMK.MODE_AUTO -> -11;
                case IronworksRBMK.MODE_MANUAL -> -12;
                default -> -10;
            });
        });
        b.update(() -> b.setChecked(current != null && current.controlMode == mode));
        return b;
    }

    private static TextButton zoneButton(String text, int zone){
        TextButton b = new TextButton(text, Styles.flatTogglet);
        b.clicked(() -> {
            if(current != null) current.configure(zone < 0 ? -30 : -20 - zone);
        });
        b.update(() -> b.setChecked(current != null && (zone < 0 ? current.selectedZone < 0 : current.selectedZone == zone)));
        return b;
    }

    private static Slider waterSlider(boolean high){
        Slider s = new Slider(0f, 100f, 1f, false);
        s.moved(v -> {
            if(current != null && current.controlMode == IronworksRBMK.MODE_MANUAL){
                current.configure((high ? 1000 : 2000) + (int)v);
            }
        });
        s.update(() -> {
            if(current == null) return;
            s.touchable = current.controlMode == IronworksRBMK.MODE_MANUAL ? Touchable.enabled : Touchable.disabled;
            float shown = current.controlMode == IronworksRBMK.MODE_MANUAL
                ? (high ? current.waterTarget : current.waterLowTarget)
                : (high ? current.waterLevel : current.waterLowLevel);
            if(!s.isDragging()) s.setValue(shown * 100f);
        });
        return s;
    }

    private static Slider throttleSlider(){
        Slider s = new Slider(0f, 100f, 1f, false);
        s.moved(v -> {
            if(current != null && current.controlMode == IronworksRBMK.MODE_MANUAL){
                current.configure(3000 + (int)v);
            }
        });
        s.update(() -> {
            if(current == null) return;
            s.touchable = current.controlMode == IronworksRBMK.MODE_MANUAL ? Touchable.enabled : Touchable.disabled;
            float shown = 1f;
            if(current.controlMode == IronworksRBMK.MODE_MANUAL){
                if(current.selectedZone >= 0) shown = current.zoneThrottle[current.selectedZone];
                else{
                    shown = 0f;
                    for(int i = 0; i < 9; i++) shown += current.zoneThrottle[i];
                    shown /= 9f;
                }
            }
            if(!s.isDragging()) s.setValue(shown * 100f);
        });
        return s;
    }

    // ------------------------------------------------------------------
    // 分区
    // ------------------------------------------------------------------
    private static void buildZones(){
        if(!(current.block instanceof RBMKReactor r) || !r.zoned){
            body.add(Core.bundle.get("ironworks.rbmk.win.noZones")).pad(20f).color(Color.lightGray).row();
            return;
        }
        Label summary = new Label("");
        summary.update(() -> {
            if(current == null) return;
            summary.setText(Core.bundle.format("ironworks.rbmk.win.zoneSummary",
                Mathf.round(current.liquids.get(Liquids.water)), (int)current.waterCap(),
                Mathf.round(current.liquids.get(IronworksLiquids.steam)), (int)current.steamCap()));
        });
        body.add(summary).left().color(Color.lightGray).pad(4f).row();

        Table grid = new Table();
        grid.defaults().pad(3f);
        float cardWidth = contentWidth / 3f - 6f;
        for(int i = 0; i < 9; i++){
            final int z = i;
            Table card = new Table(Styles.grayPanel);
            card.left().top().margin(4f);
            card.touchable = Touchable.enabled;
            card.addListener(new ClickListener(){
                @Override
                public void clicked(InputEvent event, float x, float y){
                    if(current != null) current.configure(-20 - z);
                }
            });
            Label title = new Label("");
            title.update(() -> title.setText((current != null && current.selectedZone == z ? "▶ " : "")
                + Core.bundle.format("ironworks.rbmk.zone.unit", z + 1)));
            card.add(title).left().color(Pal.accent).row();
            card.add(zoneLine(z, "ironworks.rbmk.row.flux", b -> Mathf.round(b.zoneFlux[z] * 100f) + "%")).left().row();
            card.add(zoneLine(z, "ironworks.rbmk.win.fuelShort", b -> Mathf.round(b.zoneHeat[z] * 100f) + "%")).left().row();
            card.add(zoneLine(z, "ironworks.rbmk.win.coolantShort", b -> Mathf.round(b.zoneCoolant[z] * 100f) + "%")).left().row();
            card.add(zoneLine(z, "ironworks.rbmk.row.coolant", b -> Mathf.round(b.zoneFlow[z] * 100f) + "%")).left().row();
            card.add(zoneLine(z, "ironworks.rbmk.win.waterShort", b -> Mathf.round(b.zoneWater[z]) + "/" + (int)b.zoneWaterCap[z])).left().row();
            card.add(zoneLine(z, "ironworks.rbmk.win.steamShort", b -> Mathf.round(b.zoneSteam[z]) + "/" + (int)b.zoneSteamCap[z])).left().row();
            card.add(zoneLine(z, "ironworks.rbmk.win.rodsShort", b -> Mathf.round(b.zoneRods[z] * 100f) + "%")).left().row();
            card.add(zoneLine(z, "ironworks.rbmk.win.meltShort", b -> Mathf.round(b.zoneMeltDamage[z] * 100f) + "%")).left().row();
            grid.add(card).width(cardWidth).height(190f).top().left();
            if((i + 1) % 3 == 0) grid.row();
        }
        body.add(grid).width(contentWidth).row();
        body.add(Core.bundle.get("ironworks.rbmk.win.zoneTip")).wrap()
            .width(contentWidth - 10f).color(Color.lightGray).left().pad(6f).row();
    }

    private static Label zoneLine(int zone, String key, Func<RBMKReactorBuild, String> value){
        Label l = new Label("");
        l.update(() -> {
            if(current != null) l.setText(Core.bundle.get(key) + " " + value.get(current));
        });
        l.setColor(Color.lightGray);
        return l;
    }

    // ------------------------------------------------------------------
    // 维护
    // ------------------------------------------------------------------
    private static void buildService(){
        Table grid = new Table();
        grid.defaults().pad(3f);
        stat(grid, "ironworks.rbmk.win.decay", b -> Mathf.round(b.decayHeat * 100f) + "%", b -> Color.valueOf("ffb066"));
        stat(grid, "ironworks.rbmk.win.doppler", b -> Mathf.round(dopplerFactor(b.heat) * 100f) + "%", b -> Color.valueOf("8fd4ff"));
        stat(grid, "ironworks.rbmk.win.meltDamage", b -> b.block instanceof RBMKReactor r && r.zoned ? Mathf.round(maxMelt(b) * 100f) + "%" : "—", b -> maxMelt(b) > 0.5f ? Pal.remove : Color.valueOf("ffd166"));
        stat(grid, "ironworks.rbmk.row.xenon", b -> Mathf.round(b.xenon * 100f) + "%", b -> Color.valueOf("b6a8ff"));
        stat(grid, "ironworks.rbmk.row.burn", b -> Mathf.round(b.rodBurn * 100f) + "%", b -> Color.valueOf("9fe8b0"));
        stat(grid, "ironworks.rbmk.row.fuel", b -> Core.bundle.format("ironworks.rbmk.fuelcount",
            b.items.get(IronworksNuclear.fuelRod), b.items.get(Items.thorium),
            b.items.get(IronworksNuclear.depletedRod), b.block.itemCapacity), b -> IronworksNuclear.fuelRod.color);
        stat(grid, "ironworks.rbmk.win.coreBoom", b -> Mathf.round(b.coreBoom * 100f) + "%", b -> b.coreBoom > 0f ? Pal.remove : Color.lightGray);
        stat(grid, "ironworks.rbmk.win.rodEffect", b -> Mathf.round(IronworksRBMK.rodEffect(b.rods) * 100f) + "%", b -> Pal.accent);
        body.add(grid).width(contentWidth).row();

        Table notes = new Table(Styles.grayPanel);
        notes.left().margin(6f);
        notes.add(Core.bundle.get("ironworks.rbmk.win.notes")).wrap()
            .width(contentWidth - 20f).color(Color.lightGray).left().row();
        body.add(notes).width(contentWidth).padTop(4f).row();
    }

    private static Table section(String key){
        Table t = new Table(Styles.grayPanel);
        t.top().left().margin(6f);
        t.add(Core.bundle.get(key)).style(Styles.outlineLabel).color(Pal.accent).left().row();
        body.add(t).width(contentWidth).padBottom(6f).row();
        return t;
    }

    private static float maxMelt(RBMKReactorBuild b){
        if(!(b.block instanceof RBMKReactor r) || !r.zoned) return 0f;
        float max = 0f;
        for(int i = 0; i < 9; i++) max = Math.max(max, b.zoneMeltDamage[i]);
        return max;
    }

    private static float dopplerFactor(float heat){
        return IronworksRBMK.dopplerFactor(heat);
    }

    private static Color heatColor(RBMKReactorBuild b){
        float limit = b.block instanceof RBMKReactor r && r.zoned ? IronworksRBMK.zonedMeltTemp : IronworksRBMK.meltTemp;
        float f = b.heat / limit;
        return f < 0.6f ? Color.valueOf("7fe08a") : f < 0.9f ? Color.valueOf("ffd166") : Pal.remove;
    }

    private static Color coolantColor(RBMKReactorBuild b){
        return b.coolantHeat > 1.2f ? Pal.remove : b.coolantHeat > 0.9f ? Color.valueOf("ffd166") : Color.valueOf("7fe08a");
    }

    private static String stateText(RBMKReactorBuild b){
        if(b.poisoned) return Core.bundle.get("ironworks.rbmk.state.poisoned");
        if(b.melted) return Core.bundle.get("ironworks.rbmk.state.melt");
        if(maxMelt(b) >= 1f || b.heat >= (b.block instanceof RBMKReactor r && r.zoned ? IronworksRBMK.zonedMeltTemp : IronworksRBMK.meltTemp)){
            return Core.bundle.get("ironworks.rbmk.state.melt");
        }
        if(b.coreBoom > 0f) return Core.bundle.get("ironworks.rbmk.state.pressure");
        if(b.heat >= 1.55f) return Core.bundle.get("ironworks.rbmk.state.danger");
        if(b.flow < 0.9f) return Core.bundle.get("ironworks.rbmk.state.dry");
        return Core.bundle.get("ironworks.rbmk.state.ok");
    }

    private static Color stateColor(RBMKReactorBuild b){
        if(b.poisoned) return Color.valueOf("8fd4ff");
        if(b.melted || b.coreBoom > 0f || b.heat >= 1.55f) return Pal.remove;
        if(b.flow < 0.9f) return Color.valueOf("ffd166");
        return Color.valueOf("7fe08a");
    }

    private static String stateHint(RBMKReactorBuild b){
        if(b.decayHeat > 0.01f && b.flux < 0.01f){
            return Core.bundle.format("ironworks.rbmk.win.decayHint", Mathf.round(b.decayHeat * 100f));
        }
        if(b.controlMode == IronworksRBMK.MODE_AUTO){
            return Core.bundle.get("ironworks.rbmk.win.autoHint");
        }
        if(b.controlMode == IronworksRBMK.MODE_SEMI){
            return Core.bundle.get("ironworks.rbmk.win.semiHint");
        }
        return Core.bundle.get("ironworks.rbmk.win.manualHint");
    }
}
