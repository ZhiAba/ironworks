package ironworks;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Mat3D;
import arc.math.geom.Vec3;
import mindustry.type.Planet;
import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.actions.Actions;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.Dialog.DialogStyle;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ImageButton.ImageButtonStyle;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Stack;
import arc.scene.ui.layout.Table;
import arc.scene.ui.layout.WidgetGroup;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Log;
import arc.util.Time;

import java.lang.reflect.Field;
import mindustry.content.Planets;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.graphics.g3d.GenericMesh;
import mindustry.graphics.g3d.PlanetRenderer;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.PlanetDialog;
import mindustry.ui.fragments.MenuFragment;

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
        private ImageButton eggButton;
        private Button currentMenu;
        private final PlanetParams params = new PlanetParams();
        private final Vec3 tmpDir = new Vec3();
        private final Vec3 tmpRight = new Vec3();
        private Planet menuCamera;
        private Planet menuSun;
        private GenericMesh savedSunMesh;
        private GenericMesh savedSunCloud;
        private boolean savedSunAtmosphere;
        private boolean menuSceneHidden;
        private Seq<Planet> hiddenPlanets;
        private boolean campaignActive;
        private boolean disposed;
        private boolean reopenPending;
        private Element nativeMainView;
        private float menuYaw = 0.72f, menuPitch = 0.15f;

        private float yaw = 0.72f, pitch = 0.15f;
        private float center, centerTarget;
        private boolean dragging;
        private float lastX, lastY;
        private int pointer = -1;
        private boolean animating;

        @Override
        public void build(Group parent){
            params.planet = Planets.serpulo;
            params.drawUi = false;
            params.drawSkybox = true;
            params.zoom = 0.9f;
            params.viewW = -1;
            params.viewH = -1;

            root = new WidgetGroup();
            root.setFillParent(true);
            root.touchable = Touchable.childrenOnly;
            root.visible(() -> state.isMenu() && !ui.editor.isShown() && !campaignActive);
            root.update(this::updateMenuState);

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
            Core.app.post(this::startNativeLayer);
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
            menuLayer.visible(() -> !campaignActive);

            menuLayer.add().width(Math.max(Scl.scl(44f), 70f));

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
            ImageButtonStyle style = new ImageButtonStyle(Styles.clearNonei);
            style.imageUp = Icon.infoCircle;
            style.imageUpColor = Color.valueOf("d94b5a");
            ImageButton button = new ImageButton(style);
            button.resizeImage(Scl.scl(16f));
            button.visible(() -> !campaignActive);
            button.clicked(() -> ui.about.show());
            eggButton = button;
            // Pin the info button to the screen-left edge instead of a centered Table cell.
            button.update(() -> {
                float size = Scl.scl(32f);
                button.setSize(size, size);
                button.setPosition(6f, (Core.graphics.getHeight() - size) / 2f);
            });
            root.addChild(button);
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

        /** 离开战役后把原生对话框状态/样式还回去，避免影响游戏内再次打开星球界面。 */
        private void restoreNativePlanet(){
            if(ui.planet == null) return;
            if(nativePlanetState != null) ui.planet.state = nativePlanetState;
            if(nativeDialogStyle != null) ui.planet.setStyle(nativeDialogStyle);
        }

        private static final class EmptyMesh implements GenericMesh{
            @Override
            public void render(PlanetParams params, Mat3D projection, Mat3D transform){}

            @Override
            public void dispose(){}
        }

        private void createMenuCamera(){
            if(menuCamera != null) return;
            menuSun = Planets.serpulo.solarSystem;
            menuCamera = new Planet("ironworks-menu-camera", null, Planets.serpulo.radius);
            menuCamera.camRadius = Planets.serpulo.camRadius;
            menuCamera.solarSystem = menuSun;
            menuCamera.visible = false;
            // Planet 的构造器会把自己临时挂进 content；这里只当相机目标用，立刻移除，
            // 否则会出现在星球列表里，并被 Universe.updateGlobal() 每帧重置位置。
            content.remove(menuCamera);
        }

        /** 主菜单和战役共用 ui.planet.mainView 这一层；这里只改同一份相机状态，不创建第二层。 */
        private void updateMenuState(){
            if(disposed || campaignActive || ui.planet == null) return;
            createMenuCamera();

            // 从游戏/战役返回主菜单时，原生 PlanetDialog 可能已经被 hide；重新挂回同一层。
            if(!ui.planet.isShown() && !reopenPending && (ui.loadfrag == null || !ui.loadfrag.shown())){
                reopenPending = true;
                Core.app.post(() -> {
                    reopenPending = false;
                    startNativeLayer();
                });
            }

            center += (centerTarget - center) * Mathf.clamp(Time.delta * 0.1f, 0f, 1f);
            if(!dragging && !animating) yaw += Time.delta * ROTATION_SPEED;

            // 菜单每帧强制隐藏原生战役 UI；它仍和星球在同一个 Stack 中，只是不可见。
            setNativeCampaignUi(false);

            float cp = (float)Math.cos(pitch);
            params.camPos.set((float)Math.cos(yaw) * cp, (float)Math.sin(pitch), (float)Math.sin(yaw) * cp);
            params.planet = menuCamera;
            params.drawUi = false;
            params.drawSkybox = true;
            params.uiAlpha = 1f;
            params.zoom = Mathf.lerp(0.9f, 1f, center);
            params.viewW = -1;
            params.viewH = -1;
            params.renderer = null;
            params.otherCamPos = null;
            params.otherCamAlpha = 1f;
            if(ui.planet != null) ui.planet.zoom = params.zoom;

            // 相机直接看向 Serpulo 左侧一点，使 Serpulo 在菜单落在右侧；
            // 星空由同一个 PlanetRenderer 铺满全屏，不做整张画面平移。
            float radius = Planets.serpulo.radius + Planets.serpulo.camRadius;
            float depth = radius * PlanetRenderer.camLength + (params.zoom - 1f) * radius * 2f;
            float aspect = Core.graphics.getWidth() / (float)Math.max(1, Core.graphics.getHeight());
            float d = (1f - center) * 0.45f * depth
                * (float)Math.tan(ui.planet.planets.cam.fov * Mathf.degRad * 0.5f) * aspect;

            tmpDir.set(params.camPos).nor();
            tmpRight.set(Vec3.Y).crs(tmpDir).nor();
            menuCamera.position.set(Planets.serpulo.position).sub(tmpRight.scl(d));
        }

        private void startNativeLayer(){
            if(disposed || ui.planet == null || !state.isMenu() || (ui.loadfrag != null && ui.loadfrag.shown())) return;
            createMenuCamera();

            // setup() 必须用真实 Serpulo 建 UI；之后菜单周期再把 state.planet 换成相机目标。
            params.planet = Planets.serpulo;
            ui.planet.state = params;
            ui.planet.color.a = 1f;
            configurePlanetDialog();
            if(!ui.planet.isShown()){
                ui.planet.show(Core.scene, null);
                ui.planet.clearActions();
                ui.planet.color.a = 1f;
            }
            cacheNativeMainView();

            // 菜单 UI 必须是 ui.planet 之后加入 Scene 的独立 UI 层，才能盖在原生星球上；
            // 但它不渲染任何星球，只负责按钮/拖动，画面仍只有 mainView 一层。
            if(root != null){
                if(root.parent != null) root.parent.removeChild(root);
                root.setSize(Core.graphics.getWidth(), Core.graphics.getHeight());
                Core.scene.add(root);
            }

            campaignActive = false;
            center = 0f;
            centerTarget = 0f;
            setNativeCampaignUi(false);
            applyMenuScene();
            updateMenuState();
        }

        private void cacheNativeMainView(){
            if(nativeMainView != null && nativeMainView.parent != null) return;
            try{
                Field field = PlanetDialog.class.getDeclaredField("mainView");
                field.setAccessible(true);
                nativeMainView = (Element)field.get(ui.planet);
            }catch(Throwable t){
                Log.err("IronworksBetterMenu: 读取 PlanetDialog.mainView 失败", t);
            }
        }

        private void setNativeCampaignUi(boolean show){
            if(ui.planet == null) return;
            cacheNativeMainView();
            for(Element child : ui.planet.getChildren()){
                if(child instanceof Stack){
                    Seq<Element> children = ((Stack)child).getChildren();
                    Element main = nativeMainView;
                    boolean found = false;
                    for(Element e : children){
                        if(e == main){ found = true; break; }
                    }
                    if(!found && children.size > 0) main = children.first();
                    for(Element e : children){
                        // mainView 是唯一的星球渲染层；只隐藏/显示它之外的战役 UI。
                        e.visible = show || e == main;
                    }
                }
            }
        }

        private void applyMenuScene(){
            if(menuSceneHidden || menuSun == null) return;
            savedSunMesh = menuSun.mesh;
            savedSunCloud = menuSun.cloudMesh;
            savedSunAtmosphere = menuSun.hasAtmosphere;
            menuSun.mesh = new EmptyMesh();
            menuSun.cloudMesh = null;
            menuSun.hasAtmosphere = false;
            menuSun.visible = true;

            hiddenPlanets = new Seq<>();
            for(Planet p : content.planets()){
                if(p != Planets.serpulo && p != menuSun && p.visible){
                    p.visible = false;
                    hiddenPlanets.add(p);
                }
            }
            menuSceneHidden = true;
        }

        private void restoreMenuScene(){
            if(!menuSceneHidden || menuSun == null) return;
            menuSun.mesh = savedSunMesh;
            menuSun.cloudMesh = savedSunCloud;
            menuSun.hasAtmosphere = savedSunAtmosphere;
            if(hiddenPlanets != null){
                for(Planet p : hiddenPlanets) p.visible = true;
                hiddenPlanets = null;
            }
            menuSceneHidden = false;
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
            if(!state.isMenu() || ui.planet == null || !ui.planet.isShown()){
                cancelTransition();
                return;
            }

            // 菜单和战役是同一个 ui.planet.mainView：这里只切参数/UI，不 show/hide 对话框，
            // 也没有第二层星球画面，所以不会出现两层画面对齐造成的错位或拉伸。
            center = 1f;
            params.planet = Planets.serpulo;
            params.drawUi = true;
            params.drawSkybox = true;
            params.uiAlpha = 1f;
            params.zoom = 1f;
            params.viewW = -1;
            params.viewH = -1;
            params.renderer = ui.planet;
            params.otherCamPos = null;
            params.otherCamAlpha = 1f;
            ui.planet.state = params;
            ui.planet.zoom = 1f;
            ui.planet.color.a = 1f;

            // 菜单里为了去掉太阳光斑隐藏了太阳/其它行星，进入战役时恢复完整太阳系。
            restoreMenuScene();
            setNativeCampaignUi(true);
            campaignActive = true;
            if(root != null) root.visible = false;

            // 只让原生 UI 层淡入；星球画面完全不透明，不做全屏 alpha/缓冲。
            fadeCampaignUi(ui.planet.buttons);
            fadeCampaignUi(ui.planet.sectorTop);
            fadeCampaignUi(ui.planet.expandTable);
        }

        /** 只对 UI Table 做透明度淡入，不改 translation/位置，避免与原生布局叠加产生抖动。 */
        private void fadeCampaignUi(Table t){
            if(t == null) return;
            t.clearActions();
            t.color.a = 0f;
            t.addAction(Actions.fadeIn(0.18f, Interp.fade));
        }

        private void cancelTransition(){
            animating = false;
            campaignActive = false;
            centerTarget = 0f;
            center = 0f;
            params.planet = menuCamera;
            params.renderer = null;
            params.drawUi = false;
            params.uiAlpha = 1f;
            params.zoom = 0.9f;
            if(ui.planet != null){
                ui.planet.color.a = 1f;
                ui.planet.zoom = 0.9f;
            }
            applyMenuScene();
            setNativeCampaignUi(false);
            if(root != null) root.visible = true;
            if(ui.planet != null && !ui.planet.isShown()) Core.app.post(this::startNativeLayer);
            if(menuLayer != null){
                menuLayer.clearActions();
                menuLayer.translation.set(0f, 0f);
                menuLayer.color.a = 1f;
                menuLayer.visible = true;
            }
        }

        void rebuild(){
            if(root == null || animating) return;
            root.setSize(Core.graphics.getWidth(), Core.graphics.getHeight());
            buildMenu();
        }

        void onPlanetHidden(){
            if(disposed) return;

            boolean backToMenu = state.isMenu();
            animating = false;
            campaignActive = false;
            dragging = false;
            pointer = -1;
            centerTarget = 0f;
            center = 0f;
            yaw = menuYaw;
            pitch = menuPitch;
            params.planet = menuCamera;
            params.renderer = null;
            params.drawUi = false;
            params.uiAlpha = 1f;
            params.zoom = 0.9f;
            if(ui.planet != null){
                ui.planet.color.a = 1f;
                ui.planet.zoom = 0.9f;
            }

            restoreMenuScene();
            if(root != null) root.visible = backToMenu;

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

            if(backToMenu){
                Core.app.post(this::startNativeLayer);
            }else{
                restoreNativePlanet();
            }
        }

        private void resetGui(Table t){
            if(t == null) return;
            t.clearActions();
            t.translation.set(0f, 0f);
            t.color.a = 1f;
        }

        void dispose(){
            disposed = true;
            restoreMenuScene();
            if(root != null && root.parent != null) root.parent.removeChild(root);
            setNativeCampaignUi(true);
            if(ui.planet != null && ui.planet.isShown()){
                ui.planet.hide();
                ui.planet.clearActions();
                ui.planet.color.a = 1f;
            }
            restoreNativePlanet();
        }
    }
}
