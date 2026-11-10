package ironworks;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;
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
import arc.scene.ui.Image;
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
    /** 主菜单用较窄的摄影机 FOV 抵消离轴投影把球体压扁；战役恢复 60°。 */
    private static final float MENU_FOV = 12f, CAMPAIGN_FOV = 60f;

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

    private static void updateAtmosphere(){
        if(custom != null) custom.updateAtmosphere();
    }

    /** 星空单独按 60° FOV 渲染；只影响星空那一次绘制，星球仍用菜单窄 FOV。 */
    private static void beginSkybox(){
        if(custom != null) custom.beginSkybox();
    }

    private static void endSkybox(){
        if(custom != null) custom.endSkybox();
    }

    public static void init(){
        Core.settings.defaults(KEY, true);
        preloadPlanet();
        Events.on(EventType.ClientLoadEvent.class, e -> {
            preloadPlanet();
            sync();
        });
        Events.run(EventType.Trigger.update, IronworksBetterMenu::sync);
        Events.run(EventType.Trigger.update, IronworksBetterMenu::updateAtmosphere);
        Events.run(EventType.Trigger.universeDrawBegin, IronworksBetterMenu::beginSkybox);
        Events.run(EventType.Trigger.universeDraw, IronworksBetterMenu::endSkybox);
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
        private Table titleLayer;
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
        private boolean returningToMenu;
        private Element nativeMainView;
        private float menuYaw = 0.72f, menuPitch = 0.15f;

        private float yaw = 0.72f, pitch = 0.15f;
        private float center, centerTarget;
        /** 主菜单/战役共用同一个 Serpulo 渲染层，大气层只能靠颜色强度渐隐，不能换渲染层。 */
        private Color baseAtmosphere;
        private float atmosphereAlpha = 1f, atmosphereTarget = 1f;
        /** 星空绘制前临时切到战役 FOV，画完立刻恢复，避免窄 FOV 把星空放糊。 */
        private float menuFov = MENU_FOV;
        private boolean skyboxWide;
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
            buildTitle();
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

        /** 主菜单大标题；只做 UI 层的透明淡入淡出，不参与星球渲染。 */
        private void buildTitle(){
            TextureRegion logo = Core.atlas.find("logo");
            if(!logo.found()) return;

            titleLayer = new Table();
            titleLayer.setFillParent(true);
            titleLayer.top();
            titleLayer.touchable = Touchable.disabled;
            titleLayer.color.a = 0f;

            float logoscl = Scl.scl(1f) * logo.scale;
            float logow = Math.min(logo.width * logoscl, Core.graphics.getWidth() - Scl.scl(20f));
            float logoh = logow * (float)logo.height / logo.width;
            // 与 MenuFragment.draw() 的原版 logo 位置一致：离内容区顶部 6px，竖屏再下移 30px。
            float top = Core.scene.marginTop + 6f + (Core.graphics.isPortrait() ? Scl.scl(30f) : 0f);
            titleLayer.add(new Image(logo)).size(logow, logoh).padTop(top);
            root.addChild(titleLayer);
        }

        private void fadeTitleIn(float duration){
            if(titleLayer == null) return;
            titleLayer.clearActions();
            titleLayer.color.a = 0f;
            titleLayer.addAction(Actions.fadeIn(duration, Interp.fade));
        }

        private void fadeTitleOut(float duration){
            if(titleLayer == null) return;
            titleLayer.clearActions();
            titleLayer.addAction(Actions.fadeOut(duration, Interp.fade));
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
            if(baseAtmosphere == null) baseAtmosphere = Planets.serpulo.atmosphereColor.cpy();
            menuSun = Planets.serpulo.solarSystem;
            menuCamera = new Planet("ironworks-menu-camera", null, Planets.serpulo.radius);
            menuCamera.camRadius = Planets.serpulo.camRadius;
            menuCamera.solarSystem = menuSun;
            menuCamera.visible = false;
            // Planet 的构造器会把自己临时挂进 content；这里只当相机目标用，立刻移除，
            // 否则会出现在星球列表里，并被 Universe.updateGlobal() 每帧重置位置。
            content.remove(menuCamera);
        }

        /** 大气层没有独立的 alpha uniform，这里缩放它的颜色强度来淡出/淡入。 */
        private void updateAtmosphere(){
            if(disposed || baseAtmosphere == null) return;
            atmosphereAlpha = Mathf.lerpDelta(atmosphereAlpha, atmosphereTarget, 0.22f);
            if(Math.abs(atmosphereAlpha - atmosphereTarget) < 0.002f) atmosphereAlpha = atmosphereTarget;
            applyAtmosphereAlpha();
        }

        private void beginSkybox(){
            if(disposed || campaignActive || !state.isMenu() || ui.planet == null || ui.planet.planets == null) return;
            skyboxWide = true;
            ui.planet.planets.cam.fov = CAMPAIGN_FOV;
            ui.planet.planets.cam.update();
        }

        private void endSkybox(){
            if(!skyboxWide) return;
            skyboxWide = false;
            if(ui.planet != null && ui.planet.planets != null){
                ui.planet.planets.cam.fov = menuFov;
                ui.planet.planets.cam.update();
            }
        }

        private void applyAtmosphereAlpha(){
            if(baseAtmosphere == null) return;
            Planets.serpulo.atmosphereColor.set(
                baseAtmosphere.r * atmosphereAlpha,
                baseAtmosphere.g * atmosphereAlpha,
                baseAtmosphere.b * atmosphereAlpha,
                baseAtmosphere.a
            );
        }

        /** 主菜单和战役共用 ui.planet.mainView 这一层；这里只改同一份相机状态，不创建第二层。 */
        private void updateMenuState(){
            if(disposed || campaignActive || ui.planet == null) return;
            createMenuCamera();

            // 从游戏/战役返回主菜单时，原生 PlanetDialog 可能已经被 hide；重新挂回同一层。
            if(!ui.planet.isShown() && !reopenPending && !returningToMenu && (ui.loadfrag == null || !ui.loadfrag.shown())){
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

            // 离轴看球体会把圆压成椭圆；菜单用窄 FOV + 相机后退，保持屏幕大小不变，
            // 进入战役时 FOV 平滑回到 60°。中心点画面尺寸连续，不会二次拉伸。
            float fov = Mathf.lerp(MENU_FOV, CAMPAIGN_FOV, center);
            float radius = Planets.serpulo.radius + Planets.serpulo.camRadius;
            float campaignLength = radius * PlanetRenderer.camLength;
            float targetLength = campaignLength
                * (float)(Math.tan(CAMPAIGN_FOV * Mathf.degRad * 0.5f) / Math.tan(fov * Mathf.degRad * 0.5f));
            float zoom = 1f + (targetLength - campaignLength) / (radius * 2f);

            menuFov = fov;
            if(ui.planet != null && ui.planet.planets != null) ui.planet.planets.cam.fov = fov;
            params.planet = menuCamera;
            params.drawUi = false;
            params.drawSkybox = true;
            params.uiAlpha = 1f;
            params.zoom = zoom;
            params.viewW = -1;
            params.viewH = -1;
            params.renderer = null;
            params.otherCamPos = null;
            params.otherCamAlpha = 1f;
            if(ui.planet != null) ui.planet.zoom = zoom;

            // 相机直接看向 Serpulo 左侧一点，使 Serpulo 在菜单落在右侧；
            // 星空由同一个 PlanetRenderer 铺满全屏，不做整张画面平移。
            float aspect = Core.graphics.getWidth() / (float)Math.max(1, Core.graphics.getHeight());
            float d = (1f - center) * 0.45f * targetLength
                * (float)Math.tan(fov * Mathf.degRad * 0.5f) * aspect;

            tmpDir.set(params.camPos).nor();
            tmpRight.set(Vec3.Y).crs(tmpDir).nor();
            menuCamera.position.set(Planets.serpulo.position).sub(tmpRight.scl(d));

            if(returningToMenu && center < 0.005f){
                returningToMenu = false;
                animating = false;
                atmosphereTarget = 1f;
                applyMenuScene();
            }
        }

        private void startNativeLayer(){
            startNativeLayer(false);
        }

        private void startNativeLayer(boolean returning){
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
            if(!returning){
                center = 0f;
                centerTarget = 0f;
                atmosphereTarget = 1f;
            }
            setNativeCampaignUi(false);
            if(!returning) applyMenuScene();
            fadeTitleIn(0.55f);
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
            atmosphereTarget = 0f;
            fadeTitleOut(0.28f);
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
            returningToMenu = false;
            atmosphereAlpha = 0f;
            atmosphereTarget = 1f;
            applyAtmosphereAlpha();
            params.planet = Planets.serpulo;
            if(ui.planet != null && ui.planet.planets != null) ui.planet.planets.cam.fov = CAMPAIGN_FOV;
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
            returningToMenu = false;
            campaignActive = false;
            centerTarget = 0f;
            center = 0f;
            atmosphereTarget = 1f;
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
            if(titleLayer != null) titleLayer.color.a = 1f;
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
            dragging = false;
            pointer = -1;
            currentMenu = null;

            if(backToMenu){
                // 从战役返回菜单：沿用同一个 mainView，从战役视角连续拉回右侧菜单视角。
                campaignActive = false;
                returningToMenu = true;
                animating = true;
                atmosphereTarget = 0f;
                center = 1f;
                centerTarget = 0f;

                float camLen = params.camPos.len();
                if(camLen > 0.0001f){
                    yaw = (float)Math.atan2(params.camPos.z, params.camPos.x);
                    pitch = (float)Math.asin(Mathf.clamp(params.camPos.y / camLen, -1f, 1f));
                }

                params.planet = Planets.serpulo;
                params.renderer = null;
                params.drawUi = false;
                params.uiAlpha = 1f;
                params.zoom = 1f;
                params.otherCamPos = null;
                params.otherCamAlpha = 1f;
                if(ui.planet != null){
                    ui.planet.color.a = 1f;
                    ui.planet.zoom = 1f;
                }

                // 这里故意不恢复菜单太阳/行星：返回动画期间保持战役完整太阳系，
                // 相机拉到菜单位、菜单 UI 淡入后，再在 updateMenuState 末尾隐藏太阳/其它行星。
                if(root != null) root.visible = true;
                if(menuLayer != null){
                    menuLayer.clearActions();
                    menuLayer.translation.set(-60f, 0f);
                    menuLayer.color.a = 0f;
                    menuLayer.visible = true;
                    menuLayer.addAction(Actions.parallel(
                        Actions.translateBy(60f, 0f, 0.55f, Interp.pow3Out),
                        Actions.fadeIn(0.45f, Interp.fade)
                    ));
                }
                fadeTitleIn(0.5f);
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
                Core.app.post(() -> startNativeLayer(true));
                return;
            }

            // 真正进入游戏：不要把 PlanetDialog 再挂回来。
            returningToMenu = false;
            animating = false;
            campaignActive = false;
            atmosphereTarget = 1f;
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
            if(root != null) root.visible = false;
            if(ui.planet != null){
                resetGui(ui.planet.buttons);
                resetGui(ui.planet.sectorTop);
                resetGui(ui.planet.expandTable);
            }
            restoreNativePlanet();
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
            if(baseAtmosphere != null) Planets.serpulo.atmosphereColor.set(baseAtmosphere);
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
