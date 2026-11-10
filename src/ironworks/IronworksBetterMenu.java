package ironworks;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.Gl;
import arc.graphics.g2d.Draw;
import arc.graphics.gl.FrameBuffer;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Vec3;
import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.actions.Actions;
import arc.scene.event.ElementGestureListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.Dialog.DialogStyle;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ImageButton.ImageButtonStyle;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.scene.ui.layout.WidgetGroup;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Planets;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.graphics.g3d.PlanetRenderer;
import mindustry.type.Sector;
import mindustry.ui.dialogs.PlanetDialog;
import mindustry.ui.Styles;
import mindustry.ui.fragments.MenuFragment;

import java.lang.reflect.Field;

import static mindustry.Vars.*;

/**
 * 更好的主界面：真实渲染战役星球 + 可拖动旋转 + 进入战役的平滑过渡。
 *
 * <p>左侧按钮完全采用原版桌面版 MenuFragment 的结构（230×70、图标、子菜单、
 * flatToggleMenut），不再自己画一套文字按钮。只替换背景为真实星球。</p>
 *
 * <p>由设置项 {@value #KEY} 控制；关闭时恢复原版菜单。无头端完全不加载 UI。</p>
 */
public final class IronworksBetterMenu{
    public static final String KEY = "ironworks.menu.better";

    /** 原版 MenuButton 的桌面列宽/行高，不要乘 Scl —— 原版就是屏幕逻辑像素。 */
    private static final float MENU_WIDTH = 230f, MENU_HEIGHT = 70f;
    /** 自动旋转：120 秒一圈。Time.delta 的单位是“帧”（60 帧 = 1 秒），不是秒。 */
    private static final float ROTATION_SPEED = (Mathf.pi * 2f) / (120f * 60f);
    /** 原版星球拖动的角速度：水平 cx / 9 度，垂直 cy / 10 度。 */
    private static final float DRAG_YAW = 0.017453292f / 9f;
    private static final float DRAG_PITCH = 0.017453292f / 10f;

    private static MenuFragment vanilla;
    private static BetterMenuFragment custom;
    private static boolean installed;
    private static boolean planetHook;
    private static boolean campaignHook;
    private static boolean resizeHook;
    private static boolean failed;
    private static boolean preloaded;
    private static PlanetParams nativePlanetState;
    private static DialogStyle nativeDialogStyle;

    private IronworksBetterMenu(){
    }

    public static void init(){
        Core.settings.defaults(KEY, true);
        preloadPlanet();
        Events.on(EventType.ClientLoadEvent.class, e -> {
            preloadPlanet();
            sync();
        });
        Events.run(EventType.Trigger.update, IronworksBetterMenu::sync);
        if(!resizeHook){
            resizeHook = true;
            Events.on(EventType.ResizeEvent.class, e -> {
                if(custom != null) custom.rebuild();
            });
        }
        if(!headless && ui != null) sync();
    }

    /** 客户端加载阶段先把 Serpulo mesh 生成好，主菜单首次显示时不再等程序化网格。 */
    private static void preloadPlanet(){
        if(headless || preloaded || Planets.serpulo == null) return;
        preloaded = true;
        try{
            Planets.serpulo.reloadMeshAsync();
        }catch(Throwable t){
            Log.err("IronworksBetterMenu: 预加载 Serpulo mesh 失败", t);
        }
    }

    /** 设置页回调：立即切换，不必等重启。 */
    public static void setEnabled(boolean enabled){
        Core.settings.put(KEY, enabled);
        if(enabled) failed = false;
        sync();
    }

    private static void sync(){
        if(failed || headless || ui == null || ui.menuGroup == null || ui.menufrag == null) return;
        boolean enabled = Core.settings.getBool(KEY, true);
        if(enabled && !installed) install();
        else if(!enabled && installed) restore();
    }

    private static void install(){
        try{
            vanilla = ui.menufrag;
            if(nativePlanetState == null && ui.planet != null){
                nativePlanetState = ui.planet.state;
                nativeDialogStyle = ui.planet.getStyle();
            }
            ui.menuGroup.clear();
            custom = new BetterMenuFragment();
            custom.build(ui.menuGroup);
            ui.menufrag = custom;
            installed = true;

            if(!planetHook){
                planetHook = true;
                ui.planet.hidden(() -> {
                    if(custom != null) custom.onPlanetHidden();
                });
            }
            if(!campaignHook){
                campaignHook = true;
                ui.planet.shown(() -> {
                    if(custom != null) custom.onCampaignShown();
                });
            }
            Log.info("[IRONWORKS] better main menu installed");
        }catch(Throwable t){
            Log.err("IronworksBetterMenu: 安装主界面失败，回退原版", t);
            failed = true;
            restore();
        }
    }

    private static void restore(){
        if(vanilla == null || ui == null || ui.menuGroup == null) return;
        try{
            if(custom != null) custom.dispose();
            custom = null;
            if(ui.planet != null && !ui.planet.isShown()){
                if(nativePlanetState != null) ui.planet.state = nativePlanetState;
                if(nativeDialogStyle != null) ui.planet.setStyle(nativeDialogStyle);
            }
            ui.menuGroup.clear();
            ui.menufrag = vanilla;
            vanilla.build(ui.menuGroup);
        }catch(Throwable t){
            Log.err("IronworksBetterMenu: 恢复原版主界面失败", t);
        }
        installed = false;
    }

    private static final class BetterMenuFragment extends MenuFragment{
        private WidgetGroup root;
        private Table menuLayer;
        private Table panel;
        private Table submenu;
        private Button currentMenu;
        private PlanetRenderer planets;
        private FrameBuffer planetBuffer;
        private final PlanetParams params = new PlanetParams();
        private boolean campaignActive;
        private float menuYaw = 0.72f, menuPitch = 0.15f;

        private float yaw = 0.72f, pitch = 0.15f;
        private float center, centerTarget;
        private boolean dragging;
        private float lastX, lastY;
        private int pointer = -1;
        private boolean animating;

        @Override
        public void build(Group parent){
            planets = Vars.renderer == null ? null : Vars.renderer.planets;
            params.planet = Planets.serpulo;
            params.drawUi = false;
            params.drawSkybox = false;
            params.zoom = 0.9f;
            configurePlanetDialog();

            root = new WidgetGroup();
            root.setFillParent(true);
            root.touchable = Touchable.childrenOnly;
            root.visible(() -> state.isMenu() && !ui.editor.isShown());
            parent.addChild(root);

            Table background = new Table();
            background.setFillParent(true);
            background.touchable = Touchable.disabled;
            background.fill((x, y, w, h) -> renderPlanet(w, h));
            root.addChild(background);

            Table drag = new Table();
            drag.setFillParent(true);
            drag.touchable = Touchable.enabled;
            drag.addListener(new InputListener(){
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, arc.input.KeyCode button){
                    if(animating || campaignActive || BetterMenuFragment.this.pointer != -1) return false;
                    BetterMenuFragment.this.pointer = pointer;
                    dragging = true;
                    lastX = x;
                    lastY = y;
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer){
                    if(pointer != BetterMenuFragment.this.pointer) return;
                    //原版行星拖动：向右拖时绕 camUp 正方向旋转。之前符号写反了。
                    float speed = Mathf.clamp((float)Math.cos(pitch), 0f, 1f);
                    yaw += (x - lastX) * DRAG_YAW * speed;
                    //原版垂直拖动：cy / 10 度（场景坐标 y 向上，向下拖动时抬高俯仰角）。
                    pitch = Mathf.clamp(pitch - (y - lastY) * DRAG_PITCH, -1.25f, 1.25f);
                    lastX = x;
                    lastY = y;
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, arc.input.KeyCode button){
                    if(pointer == BetterMenuFragment.this.pointer){
                        BetterMenuFragment.this.pointer = -1;
                        dragging = false;
                    }
                }
            });
            root.addChild(drag);

            buildMenu();
            buildEgg();
        }

        /**
         * 复刻 MenuFragment.buildDesktop()：左侧 10% 留白 + 230px 按钮列，
         * 第二列是子菜单。这里不生成 MenuRenderer，背景由真实星球负责。
         */
        private void buildMenu(){
            if(menuLayer != null) menuLayer.remove();
            currentMenu = null;

            menuLayer = new Table();
            menuLayer.setFillParent(true);
            menuLayer.left().top();
            menuLayer.touchable = Touchable.childrenOnly;

            menuLayer.add().width(Core.graphics.getWidth() / 10f);

            panel = new Table(Styles.black6);
            panel.name = "buttons";
            panel.defaults().width(MENU_WIDTH).height(MENU_HEIGHT);

            submenu = new Table(Styles.black6);
            submenu.name = "submenu";
            submenu.top();
            submenu.color.a = 0f;
            submenu.visible(() -> !submenu.getChildren().isEmpty());
            submenu.defaults().width(MENU_WIDTH).height(MENU_HEIGHT);

            Seq<MenuButton> desktopButtons = Seq.with(
                new MenuButton("@play", Icon.play, () -> {},
                    new MenuButton("@campaign", Icon.play, this::beginCampaign),
                    new MenuButton("@joingame", Icon.add, () -> checkPlay(ui.join::show)),
                    new MenuButton("@customgame", Icon.terrain, () -> checkPlay(ui.custom::show)),
                    new MenuButton("@loadgame", Icon.download, () -> checkPlay(ui.load::show))
                ),
                new MenuButton("@database.button", Icon.menu, () -> {},
                    new MenuButton("@schematics", Icon.paste, ui.schematics::show),
                    new MenuButton("@database", Icon.book, ui.database::show),
                    new MenuButton("@about.button", Icon.info, ui.about::show)
                ),
                new MenuButton("@editor", Icon.terrain, () -> checkPlay(ui.maps::show)),
                new MenuButton("@mods", Icon.book, ui.mods::show),
                new MenuButton("@settings", Icon.settings, ui.settings::show)
            );

            buttons(panel, desktopButtons.toArray(MenuButton.class));
            buttons(panel, new MenuButton("@quit", Icon.exit, Core.app::exit));

            menuLayer.add(panel).width(MENU_WIDTH).growY();
            menuLayer.add(submenu).width(MENU_WIDTH).growY();
            root.addChild(menuLayer);
        }

        private void checkPlay(Runnable run){
            if(!mods.hasContentErrors()) run.run();
            else ui.showInfo("@mod.noerrorplay");
        }

        private void buttons(Table t, MenuButton... buttons){
            for(MenuButton b : buttons){
                if(b == null) continue;
                Button[] out = {null};
                out[0] = t.button(b.text, b.icon, Styles.flatToggleMenut, () -> {
                    if(animating) return;
                    if(currentMenu == out[0]){
                        currentMenu = null;
                        fadeOutMenu();
                    }else if(b.submenu != null && b.submenu.any()){
                        currentMenu = out[0];
                        submenu.clearChildren();
                        fadeInMenu();
                        //让子菜单第一项和父按钮对齐（按钮尺寸是原版逻辑像素，不再除 Scl）
                        submenu.add().height(Core.graphics.getHeight() - Core.scene.marginTop - Core.scene.marginBottom - out[0].getY(Align.topLeft));
                        submenu.row();
                        buttons(submenu, b.submenu.toArray(MenuButton.class));
                    }else{
                        currentMenu = null;
                        fadeOutMenu();
                        b.runnable.run();
                    }
                }).marginLeft(11f).get();
                out[0].update(() -> out[0].setChecked(currentMenu == out[0]));
                t.row();
            }
        }

        private void fadeInMenu(){
            if(submenu == null) return;
            submenu.clearActions();
            submenu.actions(Actions.alpha(1f, 0.15f, Interp.fade));
        }

        private void fadeOutMenu(){
            if(submenu == null || submenu.getChildren().isEmpty()) return;
            submenu.clearActions();
            submenu.actions(Actions.alpha(1f), Actions.alpha(0f, 0.2f, Interp.fade), Actions.run(() -> submenu.clearChildren()));
        }

        /** 最左缘的彩蛋入口；x=0 锚定，不随屏幕宽度/缩放居中。 */
        private void buildEgg(){
            Table egg = new Table();
            egg.setFillParent(true);
            egg.left().center();
            egg.touchable = Touchable.childrenOnly;
            egg.fill((x, y, w, h) -> drawEggArc(y, h));

            ImageButtonStyle style = new ImageButtonStyle(Styles.clearNonei);
            style.imageUp = Icon.infoCircle;
            style.imageUpColor = Color.valueOf("d94b5a");
            ImageButton button = new ImageButton(style);
            button.resizeImage(Scl.scl(16f));
            button.clicked(() -> ui.about.show());
            egg.add(button).size(Scl.scl(32f)).padLeft(4f);

            root.addChild(egg);
        }

        private void drawEggArc(float y, float h){
            float cx = -h * 0.04f;
            float cy = y + h * 0.53f;
            float radius = h * 0.22f;
            Draw.color(Color.valueOf("b52b3a"), 0.75f);
            Lines.stroke(Scl.scl(1.2f));
            float lastX = 0f, lastY = 0f;
            for(int i = 0; i <= 28; i++){
                float a = 0.32f + i / 28f * 0.95f;
                float px = cx + (float)Math.cos(a) * radius;
                float py = cy + (float)Math.sin(a) * radius;
                if(i > 0) Lines.line(lastX, lastY, px, py);
                lastX = px;
                lastY = py;
            }
            Draw.color();
        }

        private void configurePlanetDialog(){
            if(ui.planet == null) return;
            DialogStyle old = ui.planet.getStyle();
            if(old == null) return;
            DialogStyle style = new DialogStyle();
            style.background = old.background;
            style.titleFont = old.titleFont;
            style.titleFontColor = old.titleFontColor;
            // 战役界面自己的星球不再绘制，背景由主菜单的同一个离屏 PlanetRenderer 提供。
            style.stageBackground = null;
            ui.planet.setStyle(style);
        }

        private void renderPlanet(float w, float h){
            // 时间单位：Time.delta 是帧（60/秒），所以过渡系数直接乘 Time.delta。
            center += (centerTarget - center) * Mathf.clamp(Time.delta * 0.1f, 0f, 1f);
            if(!campaignActive && !dragging && !animating) yaw += Time.delta * ROTATION_SPEED;

            Draw.color(0.008f, 0.010f, 0.020f, 1f);
            Fill.crect(0f, 0f, w, h);
            drawStars(w, h);

            if(planets != null && params.planet != null){
                // 固定使用全屏 FBO，动画只改变绘制中心，避免过渡期间每帧重建纹理。
                int fw = Math.max(1, (int)w);
                int fh = Math.max(1, (int)h);

                if(!campaignActive){
                    float cp = (float)Math.cos(pitch);
                    params.camPos.set((float)Math.cos(yaw) * cp, (float)Math.sin(pitch), (float)Math.sin(yaw) * cp);
                    params.zoom = Mathf.lerp(0.9f, 1f, center);
                    // 过渡期间六边形网格从暗到亮；进入战役后由 PlanetDialog 的 state.uiAlpha 接管。
                    params.drawUi = center > 0.01f;
                    params.uiAlpha = Mathf.clamp(center, 0f, 1f);
                }
                params.viewW = fw;
                params.viewH = fh;

                try{
                    if(planetBuffer == null) planetBuffer = new FrameBuffer(2, 2, true);
                    if(planetBuffer.getWidth() != fw || planetBuffer.getHeight() != fh){
                        planetBuffer.resize(fw, fh);
                    }

                    // PlanetRenderer 的 bloom 会重置 GL viewport，所以不能直接 Gl.viewport 偏右；
                    // 先渲染到固定全屏 FBO，再把整张星球画面按 center 滑到右侧/中心（战役时贴满全屏）。
                    Draw.flush();
                    boolean begun = false;
                    try{
                        planetBuffer.begin(Color.clear);
                        begun = true;
                        planets.render(params);
                    }catch(Throwable t){
                        Log.err("IronworksBetterMenu: 星球渲染失败，回退纯色背景", t);
                        planets = null;
                    }finally{
                        if(begun) planetBuffer.end();
                    }
                    Gl.viewport(0, 0, Core.graphics.getBackBufferWidth(), Core.graphics.getBackBufferHeight());

                    if(planets != null){
                        float cx = Mathf.lerp(w * 0.725f, w * 0.5f, center);
                        Draw.color();
                        Draw.rect(Draw.wrap(planetBuffer.getTexture()), cx, h / 2f, fw, -fh);
                    }
                }catch(Throwable t){
                    Log.err("IronworksBetterMenu: 星球离屏渲染失败，回退纯色背景", t);
                    planets = null;
                }
            }

            Draw.color(0f, 0f, 0f, 0.10f);
            Fill.crect(0f, 0f, w, h);
            Draw.color();
        }

        private void drawStars(float w, float h){
            for(int i = 0; i < 96; i++){
                float sx = ((i * 977) % 10000) / 10000f * w;
                float sy = ((i * 613) % 10000) / 10000f * h;
                Draw.color(1f, 1f, 1f, 0.10f + (i % 5) * 0.035f);
                Draw.rect(Core.atlas.white(), sx, sy, Scl.scl(1.2f), Scl.scl(1.2f));
            }
            Draw.color();
        }

        private void beginCampaign(){
            if(animating || !state.isMenu()) return;
            if(mods.hasContentErrors()){
                ui.showInfo("@mod.noerrorplay");
                return;
            }
            if(net.client()){
                ui.showInfo("@map.multiplayer");
                return;
            }

            animating = true;
            menuYaw = yaw;
            menuPitch = pitch;
            centerTarget = 1f;

            if(menuLayer != null){
                menuLayer.clearActions();
                menuLayer.addAction(Actions.parallel(
                    Actions.fadeOut(0.32f, Interp.fade),
                    Actions.translateBy(-60f, 0f, 0.32f, Interp.pow3In)
                ));
                //星球在这段时间里自己扩到全屏；随后直接打开同姿态、同缩放的战役界面。
                menuLayer.addAction(Actions.sequence(
                    Actions.delay(0.92f),
                    Actions.run(this::openCampaign)
                ));
            }else{
                openCampaign();
            }
        }

        private void openCampaign(){
            if(!state.isMenu() || ui.planet == null){
                cancelTransition();
                return;
            }

            // 不创建第二个 Screen/PlanetRenderer：菜单背景和战役界面共用同一个离屏星球。
            center = 1f;
            campaignActive = true;
            params.planet = Planets.serpulo;
            params.drawUi = true;
            params.uiAlpha = 1f;
            params.zoom = 1f;

            Vec3 saved = new Vec3(params.camPos);
            ui.planet.state = params;
            ui.planet.color.a = 0f;
            configurePlanetDialog();
            ui.planet.show();

            if(!ui.planet.isShown()){
                cancelTransition();
                return;
            }

            // show() 内部会 lookAt 最后的区块；恢复菜单里已经渲染到的相机，避免视觉重载。
            params.camPos.set(saved);
            params.zoom = 1f;
            ui.planet.zoom = 1f;
            params.drawUi = true;
            params.uiAlpha = 1f;
            params.renderer = ui.planet;

            installCampaignInput();
            Core.app.post(this::installCampaignInput);
            animateCampaignGui();
            ui.planet.clearActions();
            ui.planet.addAction(Actions.alpha(1f, 0.45f, Interp.fade));
        }

        void onCampaignShown(){
            if(!campaignActive) return;
            installCampaignInput();
            animateCampaignGui();
        }

        private void animateCampaignGui(){
            if(!campaignActive || ui.planet == null) return;
            if(ui.planet.buttons != null){
                ui.planet.buttons.clearActions();
                ui.planet.buttons.translation.set(0f, -30f);
                ui.planet.buttons.color.a = 0f;
                ui.planet.buttons.addAction(Actions.parallel(
                    Actions.translateBy(0f, 30f, 0.42f, Interp.pow3Out),
                    Actions.fadeIn(0.42f, Interp.fade)
                ));
            }
            if(ui.planet.sectorTop != null){
                ui.planet.sectorTop.clearActions();
                ui.planet.sectorTop.translation.set(0f, -30f);
                ui.planet.sectorTop.color.a = 0f;
                ui.planet.sectorTop.addAction(Actions.parallel(
                    Actions.translateBy(0f, 30f, 0.46f, Interp.pow3Out),
                    Actions.fadeIn(0.46f, Interp.fade)
                ));
            }
            if(ui.planet.expandTable != null){
                ui.planet.expandTable.clearActions();
                ui.planet.expandTable.translation.set(38f, 0f);
                ui.planet.expandTable.color.a = 0f;
                ui.planet.expandTable.addAction(Actions.parallel(
                    Actions.translateBy(-38f, 0f, 0.5f, Interp.pow3Out),
                    Actions.fadeIn(0.5f, Interp.fade)
                ));
            }
        }

        /** 战役界面自己的 mainView 改成透明输入层；星球仍由主菜单的同一个渲染器绘制。 */
        private void installCampaignInput(){
            if(!campaignActive || ui.planet == null || !ui.planet.isShown()) return;
            try{
                Field field = PlanetDialog.class.getDeclaredField("mainView");
                field.setAccessible(true);
                Element old = (Element)field.get(ui.planet);
                if(old instanceof CampaignInputView) return;
                if(old == null || old.parent == null){
                    Core.app.post(this::installCampaignInput);
                    return;
                }

                Group parent = old.parent;
                int index = -1;
                for(int i = 0; i < parent.getChildren().size; i++){
                    if(parent.getChildren().get(i) == old){
                        index = i;
                        break;
                    }
                }

                CampaignInputView view = new CampaignInputView();
                parent.removeChild(old);
                parent.addChildAt(Math.max(0, index), view);
                field.set(ui.planet, view);
            }catch(Throwable t){
                Log.err("IronworksBetterMenu: 接管战役星球输入失败", t);
            }
        }

        private final class CampaignInputView extends Element{
            CampaignInputView(){
                touchable = Touchable.enabled;
                addListener(new ElementGestureListener(){
                    @Override
                    public void tap(InputEvent event, float x, float y, int count, KeyCode button){
                        if(ui.planet == null || !ui.planet.isShown()) return;
                        Sector sector = ui.planet.hovered;
                        if(sector != null){
                            ui.planet.selectSector(sector);
                        }
                    }
                });
            }
        }

        private void cancelTransition(){
            animating = false;
            campaignActive = false;
            centerTarget = 0f;
            center = 0f;
            params.renderer = null;
            params.drawUi = false;
            if(ui.planet != null) ui.planet.color.a = 1f;
            if(menuLayer != null){
                menuLayer.clearActions();
                menuLayer.translation.set(0f, 0f);
                menuLayer.color.a = 1f;
                menuLayer.visible = true;
            }
        }

        void rebuild(){
            if(root == null || animating) return;
            buildMenu();
        }

        void onPlanetHidden(){
            animating = false;
            campaignActive = false;
            dragging = false;
            pointer = -1;
            centerTarget = 0f;
            center = 0f;
            yaw = menuYaw;
            pitch = menuPitch;
            params.renderer = null;
            params.drawUi = false;
            params.uiAlpha = 1f;
            if(ui.planet != null) ui.planet.color.a = 1f;

            if(menuLayer != null){
                menuLayer.clearActions();
                menuLayer.translation.set(0f, 0f);
                menuLayer.color.a = 1f;
                menuLayer.visible = true;
            }
            if(submenu != null){
                submenu.clearActions();
                submenu.clearChildren();
                submenu.color.a = 0f;
            }
            if(ui.planet != null){
                resetGui(ui.planet.buttons);
                resetGui(ui.planet.sectorTop);
                resetGui(ui.planet.expandTable);
            }
            currentMenu = null;
        }

        private void resetGui(Table t){
            if(t == null) return;
            t.clearActions();
            t.translation.set(0f, 0f);
            t.color.a = 1f;
        }

        void dispose(){
            if(planetBuffer != null){
                planetBuffer.dispose();
                planetBuffer = null;
            }
        }
    }
}
