package ironworks;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.Gl;
import arc.graphics.g2d.TextureRegion;
import arc.graphics.gl.Shader;
import arc.math.Interp;
import arc.math.Mathf;
import arc.math.geom.Mat3D;
import arc.math.geom.Vec3;
import mindustry.type.Planet;
import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.actions.Actions;
import arc.scene.event.EventListener;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.Dialog;
import arc.scene.ui.Dialog.DialogStyle;
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
import mindustry.ctype.ContentType;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.graphics.Shaders;
import mindustry.graphics.g3d.GenericMesh;
import mindustry.graphics.g3d.PlanetRenderer;
import mindustry.graphics.g3d.PlanetMesh;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.graphics.g3d.MultiMesh;
import mindustry.graphics.g3d.MatMesh;
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

    /** 进入游戏后确保主菜单的 PlanetDialog 背景不会残留；战役里用户主动打开星球界面时不能误关。 */
    private static void guardGameLayer(){
        if(custom != null) custom.guardGameLayer();
    }

    private static void updateOverlayLayer(){
        if(custom != null) custom.updateOverlay();
    }

    /** 星球层绘制前确保混合开启；返回菜单末尾用它让剩余星球按透明度退出。 */
    private static void preparePlanetFade(){
        if(custom != null) custom.preparePlanetFade();
    }

    /** 场景 act/draw 之前再压一次返回期间的 Dialog 透明度，避免 hide 动作在同一帧先进一拍。 */
    private static void keepDialogOpaqueLayer(){
        if(custom != null) custom.keepDialogOpaque();
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
        Events.run(EventType.Trigger.uiDrawBegin, IronworksBetterMenu::keepDialogOpaqueLayer);
        Events.run(EventType.Trigger.universeDrawBegin, IronworksBetterMenu::beginSkybox);
        Events.run(EventType.Trigger.universeDraw, IronworksBetterMenu::endSkybox);
        Events.run(EventType.Trigger.update, IronworksBetterMenu::guardGameLayer);
        Events.run(EventType.Trigger.universeDrawBegin, IronworksBetterMenu::updateOverlayLayer);
        Events.run(EventType.Trigger.universeDraw, IronworksBetterMenu::preparePlanetFade);
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
        // 设置页自己也在 ui.settings 里；切换开关时如果立刻 clear()/rebuild menuGroup，
        // 会把正在显示的设置对话框下方的菜单层抽掉，导致关闭设置后按钮失去响应。
        // 等设置页关闭、且回到主菜单后再应用安装/还原。
        if(state == null || !state.isMenu() || (ui.settings != null && ui.settings.isShown())) return;
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
        private Table menuBackdrop;
        private ScrollPane menuScroll;
        private Table panel;
        private Table submenu;
        private Table titleLayer;
        private Button currentMenu;
        private final PlanetParams params = new PlanetParams();
        private final Vec3 tmpDir = new Vec3();
        private final Vec3 tmpRight = new Vec3();
        private Planet menuCamera;
        private Planet menuPlanet;
        private Planet menuRoot;
        private Planet atmospherePlanet;
        private boolean menuPlanetWasVisible = true;
        private boolean menuSceneHidden;
        private Seq<Planet> hiddenPlanets;
        private Seq<SavedShell> savedShells;
        private boolean campaignActive;
        /** true 表示当前 ui.planet 是主菜单背景层，而不是游戏内用户主动打开的星球界面。 */
        private boolean menuLayerActive;
        private boolean disposed;
        private boolean reopenPending;
        private boolean returningToMenu;
        /** 返回菜单期间压住 Dialog.hide() 的 alpha 淡出；<0.99 会让 PlanetDialog 走全屏 FBO 缓冲，造成几帧横向挤压。 */
        private boolean dialogHideSuppressed;
        private Element nativeMainView;
        private float menuYaw = 0.72f, menuPitch = 0.15f;

        private float yaw = 0.72f, pitch = 0.15f;
        private float center, centerTarget;
        /** 主菜单/战役共用同一个 PlanetRenderer 渲染层，大气层只能靠颜色强度渐隐，不能换渲染层。 */
        private Color baseAtmosphere;
        private float atmosphereAlpha = 1f, atmosphereTarget = 1f;
        /** 战役 UI 层（区块/包裹层）与菜单包裹层的交叉淡入淡出。 */
        private boolean overlayFading;
        private float overlayTarget = 1f, overlayAlpha;
        private boolean returnOverlayHold;
        /** 从战役返回时保留玩家缩放，随镜头一起过渡到菜单尺寸。 */
        private float returnZoom = 1f;
        /** 返回菜单末尾对非选中星球做真正的透明度渐隐，避免被遮挡的星球在隐藏时突然消失。 */
        private boolean planetFadeActive, planetFadeTried, planetFadeFinishing;
        private float planetFadeAlpha = 1f;
        private Seq<FadePlanet> planetFades;
        private FadeShader planetFadeShader, unlitFadeShader;
        private CloudFadeShader cloudFadeShader;
        private static Field planetMeshShaderField, multiMeshField, matMeshField;
        /** 天空盒单次绘制前保存的实际相机 FOV；画完只恢复到绘制前的值。 */
        private float skyboxPrevFov = CAMPAIGN_FOV;
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
            buildTitle();
            Core.app.post(this::startNativeLayer);
        }

        /**
         * 复刻 MenuFragment.buildDesktop()：左侧 10% 留白 + 230px 按钮列，
         * 第二列是子菜单。这里不生成 MenuRenderer，背景由真实星球负责。
         */
        private void buildMenu(){
            if(menuLayer != null) menuLayer.remove();
            if(menuBackdrop != null) menuBackdrop.remove();
            currentMenu = null;

            menuLayer = new Table();
            menuLayer.setFillParent(true);
            menuLayer.left().top();
            menuLayer.touchable = Touchable.childrenOnly;
            menuLayer.visible(() -> !campaignActive);

            float leftGutter = Math.max(Scl.scl(44f), 70f);
            // 手机版顶部太贴边：用一整行空高度把按钮列下移一个按键高度。
            menuLayer.add().height(MENU_HEIGHT).colspan(3).row();
            menuLayer.add().width(leftGutter);

            panel = new Table();
            panel.name = "buttons";
            panel.top();
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
                    new MenuButton("@database", Icon.book, ui.database::show)
                ),
                new MenuButton("@editor", Icon.terrain, () -> checkPlay(ui.maps::show)),
                new MenuButton("@mods", Icon.book, ui.mods::show),
                new MenuButton("@settings", Icon.settings, ui.settings::show),
                new MenuButton("@about.button", Icon.info, ui.about::show)
            );

            buttons(panel, desktopButtons.toArray(MenuButton.class));
            buttons(panel, new MenuButton("@quit", Icon.exit, Core.app::exit));

            // 大 UI 缩放时按钮总高度可能超过屏幕；和原版 MenuFragment 一样用纵向滚动，
            // 不再把底部按钮裁掉。
            menuScroll = new ScrollPane(panel, Styles.noBarPane);
            menuScroll.setFadeScrollBars(false);
            menuScroll.setScrollingDisabledX(true);
            menuScroll.setOverscroll(false, false);
            menuLayer.add(menuScroll).width(MENU_WIDTH).growY().top();
            menuLayer.add(submenu).width(MENU_WIDTH).growY().top();

            // 背景单独铺满菜单栏的整列高度：Table 的 growY 在竖屏下会让背景只按
            // 内容高度（6×70）计算，顶部留出一段空白；这里不依赖单元格高度。
            menuBackdrop = new Table(Styles.black6);
            menuBackdrop.touchable = Touchable.disabled;
            menuBackdrop.visible(() -> menuLayer != null && menuLayer.visible && !campaignActive);
            menuBackdrop.update(() -> {
                if(menuLayer == null || panel == null) return;
                menuBackdrop.setSize(menuScroll.getWidth(), Math.max(menuLayer.getHeight(), Core.graphics.getHeight()));
                menuBackdrop.setPosition(menuScroll.x + menuLayer.x + menuLayer.translation.x, 0f);
                menuBackdrop.color.a = menuLayer.color.a;
            });
            root.addChild(menuBackdrop);
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
                        //让子菜单第一项和父按钮对齐；与原版一样按 Scl 还原逻辑像素
                        submenu.add().height((Core.graphics.getHeight() - Core.scene.marginTop - Core.scene.marginBottom - out[0].getY(Align.topLeft)) / Scl.scl(1f));
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
            titleLayer.left().top();
            titleLayer.touchable = Touchable.disabled;
            titleLayer.color.a = 0f;

            // Cell 的 size/pad 会再乘一次 Scl；这里统一用逻辑像素算，避免把物理像素
            // 传进去后二次放大，导致标题没缩小、还被推到屏幕外。
            float screenW = Core.graphics.getWidth() / Scl.scl(1f);
            // 标题不再居中：缩到屏宽约 48%，从左侧 34% 处开始，落在右上区域。
            float logow = screenW * 0.48f;
            float logoh = logow * (float)logo.height / logo.width;
            float left = screenW * 0.34f;
            // 竖屏保留额外的 30px 安全距离；marginTop 也是场景像素，先折回逻辑像素。
            float top = Core.scene.marginTop / Scl.scl(1f) + 6f + (Core.graphics.isPortrait() ? 30f : 0f);
            titleLayer.add(new Image(logo)).size(logow, logoh).padLeft(left).padTop(top);
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

        /** 选中星球到太阳系根节点之间的祖先需要保持 visible，才能递归渲染到目标星球；它们自身的球体/大气层要暂时隐藏。 */
        private static final class SavedShell{
            final Planet planet;
            final GenericMesh mesh;
            final GenericMesh cloud;
            final boolean atmosphere;
            final boolean visible;

            SavedShell(Planet planet){
                this.planet = planet;
                this.mesh = planet.mesh;
                this.cloud = planet.cloudMesh;
                this.atmosphere = planet.hasAtmosphere;
                this.visible = planet.visible;
            }

            void restore(){
                planet.mesh = mesh;
                planet.cloudMesh = cloud;
                planet.hasAtmosphere = atmosphere;
                planet.visible = visible;
            }
        }

        private static final class EmptyMesh implements GenericMesh{
            @Override
            public void render(PlanetParams params, Mat3D projection, Mat3D transform){}

            @Override
            public void dispose(){}
        }

        private static Planet resolvePlanet(Planet planet){
            if(planet == null || planet.removed || planet.solarSystem == null || !planet.isLandable()) return Planets.serpulo;
            return planet;
        }

        /** 原生战役用 lastplanet 记住当前星球；主菜单也读同一个设置，支持埃里克尔和模组星球。 */
        private static Planet settingPlanet(){
            Planet planet = content.getByName(ContentType.planet, Core.settings.getString("lastplanet", "serpulo"));
            return resolvePlanet(planet);
        }

        private void ensureMenuPlanet(){
            if(menuPlanet == null || menuCamera == null){
                selectMenuPlanet(menuPlanet == null ? settingPlanet() : menuPlanet);
            }
        }

        private void restoreAtmosphereBase(){
            if(atmospherePlanet != null && baseAtmosphere != null){
                atmospherePlanet.atmosphereColor.set(baseAtmosphere);
            }
            atmospherePlanet = null;
            baseAtmosphere = null;
        }

        /**
         * 切换主菜单展示的星球。菜单相机仍是一个只做注视目标的临时 Planet；
         * 换星球时按目标星球半径/相机半径重建它，放在目标星球左侧，目标自然落在右侧。
         */
        private void selectMenuPlanet(Planet planet){
            planet = resolvePlanet(planet);
            if(planet == menuPlanet && menuCamera != null && menuRoot != null) return;

            if(menuSceneHidden) restoreMenuScene();
            restoreAtmosphereBase();

            menuPlanet = planet;
            menuRoot = planet.solarSystem;
            // 首次进入战役的选择框可能只改 state.planet；这里把菜单实际展示的星球也写回设置，
            // 保证下次启动仍能恢复埃里克尔/模组星球。
            Core.settings.put("lastplanet", planet.name);
            if(menuRoot == null) menuRoot = planet;

            if(menuCamera != null){
                content.remove(menuCamera);
                menuCamera = null;
            }
            menuCamera = new Planet("ironworks-menu-camera", null, planet.radius);
            menuCamera.camRadius = planet.camRadius;
            menuCamera.solarSystem = menuRoot;
            menuCamera.visible = false;
            // Planet 的构造器会把自己临时挂进 content；这里只当相机目标用，立刻移除，
            // 否则会出现在星球列表里，并被 Universe.updateGlobal() 每帧重置位置。
            content.remove(menuCamera);

            baseAtmosphere = planet.atmosphereColor.cpy();
            atmospherePlanet = planet;
            applyAtmosphereAlpha();

            // 模组星球可能还没生成 mesh；先放空 mesh 防止渲染器空指针，同时异步加载真 mesh。
            if(planet.mesh == null){
                planet.mesh = new EmptyMesh();
                try{
                    planet.reloadMeshAsync();
                }catch(Throwable t){
                    Log.err("IronworksBetterMenu: 加载星球 mesh 失败: " + planet.name, t);
                }
            }
        }

        /** 战役里切换星球时立即写回 lastplanet；即使没有走返回菜单流程也不会丢。 */
        private void rememberPlanet(){
            if(ui == null || ui.planet == null || ui.planet.state == null) return;
            Planet current = ui.planet.state.planet;
            if(current == null || current == menuCamera || !current.isLandable()) return;
            if(!current.name.equals(Core.settings.getString("lastplanet", ""))){
                Core.settings.put("lastplanet", current.name);
            }
        }

        /** 返回过渡中 Dialog 必须保持不透明；否则 PlanetDialog.draw() 会启用 FBO 缓冲，尺寸取整时出现几帧横向挤压。 */
        private void keepDialogOpaque(){
            if(!dialogHideSuppressed || disposed || ui.planet == null) return;
            if(ui.planet.isShown()) cancelDialogHide();
        }

        /** 大气层没有独立的 alpha uniform，这里缩放它的颜色强度来淡出/淡入。 */
        private void updateAtmosphere(){
            if(disposed) return;
            keepDialogOpaque();
            rememberPlanet();
            if(baseAtmosphere == null) return;
            atmosphereAlpha = Mathf.lerpDelta(atmosphereAlpha, atmosphereTarget, 0.22f);
            if(Math.abs(atmosphereAlpha - atmosphereTarget) < 0.002f) atmosphereAlpha = atmosphereTarget;
            applyAtmosphereAlpha();
        }

        /** 战役层和菜单包裹层共用 PlanetParams.uiAlpha；在同一个星球上做真正的交叉淡入淡出。 */
        private void updateOverlay(){
            if(!overlayFading) return;
            overlayAlpha = Mathf.lerpDelta(overlayAlpha, overlayTarget, 0.25f);
            if(Math.abs(overlayAlpha - overlayTarget) < 0.002f){
                overlayAlpha = overlayTarget;
                params.uiAlpha = overlayAlpha;
                overlayFading = false;
                if(returnOverlayHold && overlayTarget <= 0.001f){
                    // 不在 universeDrawBegin 中途换相机目标；下一帧再启动向右的返回动画。
                    Core.app.post(this::beginReturnCamera);
                }
            }else{
                params.uiAlpha = overlayAlpha;
            }
        }

        /** 返回动画后段开始让非选中星球整体淡出；阴影和大气层一起变暗，不再突然隐藏。 */
        private void preparePlanetFade(){
            if(disposed || !planetFadeActive || planetFades == null) return;
            Gl.enable(Gl.blend);
            Gl.blendFunc(Gl.srcAlpha, Gl.oneMinusSrcAlpha);
            planetFadeAlpha = Mathf.lerpDelta(planetFadeAlpha, 0f, 0.12f);
            if(planetFadeAlpha <= 0.01f){
                planetFadeAlpha = 0f;
                applyPlanetFadeAlpha();
                if(!planetFadeFinishing){
                    planetFadeFinishing = true;
                    // 不在 PlanetRenderer 递归绘制中途隐藏星球；下一帧再收起场景。
                    Core.app.post(this::finishPlanetFade);
                }
            }else{
                applyPlanetFadeAlpha();
            }
        }

        private void applyPlanetFadeAlpha(){
            if(planetFades == null) return;
            for(FadePlanet fp : planetFades){
                for(FadeLeaf leaf : fp.leaves) leaf.fade.setAlpha(planetFadeAlpha);
                fp.planet.atmosphereColor.set(
                    fp.atmosphere.r * planetFadeAlpha,
                    fp.atmosphere.g * planetFadeAlpha,
                    fp.atmosphere.b * planetFadeAlpha,
                    fp.atmosphere.a
                );
            }
        }

        /** 递归收集星球本体/云层里的所有 PlanetMesh 叶子；小行星常是 MultiMesh/MatMesh。 */
        private void collectFadeLeaves(GenericMesh mesh, boolean body, Field shaderField, Seq<PlanetMesh> seen, Seq<FadeLeaf> out){
            if(mesh == null) return;
            if(mesh instanceof PlanetMesh){
                PlanetMesh pm = (PlanetMesh)mesh;
                if(seen.contains(pm, true)) return;
                seen.add(pm);
                try{
                    Shader original = (Shader)shaderField.get(pm);
                    Shader fade = null;
                    if(body){
                        if(original == Shaders.planet){
                            if(planetFadeShader == null) planetFadeShader = new PlanetFadeShader();
                            fade = planetFadeShader;
                        }else if(original == Shaders.unlit){
                            if(unlitFadeShader == null) unlitFadeShader = new UnlitFadeShader();
                            fade = unlitFadeShader;
                        }
                    }else if(original == Shaders.clouds){
                        if(cloudFadeShader == null) cloudFadeShader = new CloudFadeShader();
                        fade = cloudFadeShader;
                    }
                    if(fade != null){
                        shaderField.set(pm, fade);
                        out.add(new FadeLeaf(pm, original, (AlphaFade)fade));
                    }
                }catch(Throwable t){
                    Log.err("IronworksBetterMenu: 创建星球淡出着色器失败: " + pm.getClass().getSimpleName(), t);
                }
            }else if(mesh instanceof MultiMesh){
                Field field = multiMeshField();
                if(field == null) return;
                try{
                    GenericMesh[] meshes = (GenericMesh[])field.get(mesh);
                    if(meshes != null) for(GenericMesh child : meshes) collectFadeLeaves(child, body, shaderField, seen, out);
                }catch(Throwable t){
                    Log.err("IronworksBetterMenu: 读取 MultiMesh.meshes 失败", t);
                }
            }else if(mesh instanceof MatMesh){
                Field field = matMeshField();
                if(field == null) return;
                try{
                    collectFadeLeaves((GenericMesh)field.get(mesh), body, shaderField, seen, out);
                }catch(Throwable t){
                    Log.err("IronworksBetterMenu: 读取 MatMesh.mesh 失败", t);
                }
            }
        }

        /** 为所有非选中星球换上一层带 alpha uniform 的着色器；小行星/云层递归处理，不支持的自定义 mesh 跳过。 */
        private boolean beginPlanetFade(){
            if(planetFadeActive) return true;
            planetFadeTried = true;
            if(menuPlanet == null) return false;
            Field field = planetMeshShaderField();
            if(field == null) return false;

            Seq<FadePlanet> list = new Seq<>();
            for(Planet p : content.planets()){
                if(p == menuPlanet || !p.visible) continue;
                Seq<PlanetMesh> seen = new Seq<>();
                Seq<FadeLeaf> leaves = new Seq<>();
                collectFadeLeaves(p.mesh, true, field, seen, leaves);
                collectFadeLeaves(p.cloudMesh, false, field, seen, leaves);
                if(leaves.isEmpty()) continue;
                list.add(new FadePlanet(p, leaves));
            }

            if(list.isEmpty()){
                disposeFadeShaders();
                planetFadeTried = true;
                return false;
            }
            planetFades = list;
            planetFadeActive = true;
            planetFadeFinishing = false;
            planetFadeAlpha = 1f;
            return true;
        }

        private void finishPlanetFade(){
            if(disposed || !planetFadeActive) return;
            restorePlanetFade();
            planetFadeActive = false;
            planetFadeFinishing = false;
            planetFadeTried = true;
            planetFadeAlpha = 1f;
            applyMenuScene();
        }

        /** 恢复杂项星球的原始 shader/大气层颜色；调用方决定之后是否 applyMenuScene 隐藏它们。 */
        private void clearPlanetFade(){
            if(planetFades != null) restorePlanetFade();
            planetFadeActive = false;
            planetFadeFinishing = false;
            planetFadeAlpha = 1f;
        }

        private void restorePlanetFade(){
            if(planetFades == null) return;
            Field field = planetMeshShaderField();
            for(FadePlanet fp : planetFades){
                for(FadeLeaf leaf : fp.leaves){
                    try{
                        if(field != null) field.set(leaf.mesh, leaf.original);
                    }catch(Throwable t){
                        Log.err("IronworksBetterMenu: 恢复星球 shader 失败: " + fp.planet.name, t);
                    }
                }
                fp.planet.atmosphereColor.set(fp.atmosphere);
            }
            planetFades = null;
            disposeFadeShaders();
        }

        private void disposeFadeShaders(){
            if(planetFadeShader != null){
                planetFadeShader.dispose();
                planetFadeShader = null;
            }
            if(unlitFadeShader != null){
                unlitFadeShader.dispose();
                unlitFadeShader = null;
            }
            if(cloudFadeShader != null){
                cloudFadeShader.dispose();
                cloudFadeShader = null;
            }
        }

        private static Field planetMeshShaderField(){
            if(planetMeshShaderField == null){
                try{
                    Field f = PlanetMesh.class.getDeclaredField("shader");
                    f.setAccessible(true);
                    planetMeshShaderField = f;
                }catch(Throwable t){
                    Log.err("IronworksBetterMenu: 读取 PlanetMesh.shader 失败", t);
                }
            }
            return planetMeshShaderField;
        }

        private static Field multiMeshField(){
            if(multiMeshField == null){
                try{
                    Field f = MultiMesh.class.getDeclaredField("meshes");
                    f.setAccessible(true);
                    multiMeshField = f;
                }catch(Throwable t){
                    Log.err("IronworksBetterMenu: 读取 MultiMesh.meshes 失败", t);
                }
            }
            return multiMeshField;
        }

        private static Field matMeshField(){
            if(matMeshField == null){
                try{
                    Field f = MatMesh.class.getDeclaredField("mesh");
                    f.setAccessible(true);
                    matMeshField = f;
                }catch(Throwable t){
                    Log.err("IronworksBetterMenu: 读取 MatMesh.mesh 失败", t);
                }
            }
            return matMeshField;
        }

        private void beginReturnCamera(){
            if(disposed) return;
            returnOverlayHold = false;
            overlayFading = false;
            params.drawUi = false;
            params.renderer = null;
            params.planet = menuCamera;
            overlayAlpha = 0f;
            params.uiAlpha = 0f;

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
            if(ui.planet == null || !ui.planet.isShown()){
                Core.app.post(() -> startNativeLayer(true));
            }else{
                updateMenuState();
            }
        }

        private void beginSkybox(){
            if(disposed || campaignActive || !state.isMenu() || ui.planet == null || ui.planet.planets == null) return;
            // 只给天空盒这一次绘制临时用 60°；保存实际值，绘制后原样恢复，绝不把星球层留在窄 FOV。
            skyboxPrevFov = ui.planet.planets.cam.fov;
            skyboxWide = true;
            ui.planet.planets.cam.fov = CAMPAIGN_FOV;
            ui.planet.planets.cam.update();
        }

        private void endSkybox(){
            if(!skyboxWide) return;
            skyboxWide = false;
            if(ui.planet != null && ui.planet.planets != null){
                ui.planet.planets.cam.fov = skyboxPrevFov;
                ui.planet.planets.cam.update();
            }
        }

        /**
         * 返回过渡按“屏幕角尺寸”插值，而不是直接线性插值 zoom。
         * 直接 lerp(menuZoom, returnZoom, center) 在 fov 已收窄、zoom 仍接近 1 时会把
         * 星球和太阳系整体放大；这里保证起点是战役当前角尺寸、终点是菜单角尺寸。
         */
        private float transitionZoom(float menuZoom, float fov, float radius, float planetRadius){
            float startSize = angularSize(returnZoom, CAMPAIGN_FOV, radius, planetRadius);
            float menuSize = angularSize(menuZoom, fov, radius, planetRadius);
            float size = Mathf.lerp(menuSize, startSize, center);
            float halfTan = (float)Math.tan(size * Math.tan(fov * Mathf.degRad * 0.5f));
            if(halfTan <= 0.0001f) return menuZoom;
            return planetRadius / halfTan / (radius * 2f) - 1f;
        }

        /** 星球半径占屏幕纵向的比例；用于在 FOV/zoom 同时变化时保持视觉尺寸连续。 */
        private float angularSize(float zoom, float fov, float radius, float planetRadius){
            float distance = radius * PlanetRenderer.camLength + (zoom - 1f) * radius * 2f;
            if(distance <= 0.0001f) return 0f;
            return (float)(Math.atan2(planetRadius, distance) / Math.tan(fov * Mathf.degRad * 0.5f));
        }

        private void applyAtmosphereAlpha(){
            if(baseAtmosphere == null || menuPlanet == null) return;
            menuPlanet.atmosphereColor.set(
                baseAtmosphere.r * atmosphereAlpha,
                baseAtmosphere.g * atmosphereAlpha,
                baseAtmosphere.b * atmosphereAlpha,
                baseAtmosphere.a
            );
        }

        /** 主菜单和战役共用 ui.planet.mainView 这一层；这里只改同一份相机状态，不创建第二层。 */
        private void updateMenuState(){
            if(disposed || campaignActive || ui.planet == null) return;
            ensureMenuPlanet();
            // 如果星球切换没有走 onPlanetHidden（例如外部设置变化），主菜单也跟随 lastplanet。
            if(!animating && !returningToMenu){
                Planet preferred = settingPlanet();
                if(preferred != menuPlanet){
                    selectMenuPlanet(preferred);
                    applyMenuScene();
                }
            }

            // 从游戏/战役返回主菜单时，原生 PlanetDialog 可能已经被 hide；重新挂回同一层。
            if(!ui.planet.isShown() && !reopenPending && (ui.loadfrag == null || !ui.loadfrag.shown())){
                reopenPending = true;
                boolean returning = returningToMenu;
                Core.app.post(() -> {
                    reopenPending = false;
                    startNativeLayer(returning);
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
            float radius = menuPlanet.radius + menuPlanet.camRadius;
            float campaignLength = radius * PlanetRenderer.camLength;
            float targetLength = campaignLength
                * (float)(Math.tan(CAMPAIGN_FOV * Mathf.degRad * 0.5f) / Math.tan(fov * Mathf.degRad * 0.5f));
            float menuZoom = 1f + (targetLength - campaignLength) / (radius * 2f);
            float zoom = returningToMenu ? transitionZoom(menuZoom, fov, radius, menuPlanet.radius) : menuZoom;

            if(ui.planet != null && ui.planet.planets != null) ui.planet.planets.cam.fov = fov;
            params.planet = menuCamera;
            params.drawUi = false;
            params.drawSkybox = true;
            params.uiAlpha = 0f;
            params.zoom = zoom;
            params.viewW = -1;
            params.viewH = -1;
            params.renderer = null;
            params.otherCamPos = null;
            params.otherCamAlpha = 1f;
            if(ui.planet != null) ui.planet.zoom = zoom;

            // 相机直接看向目标星球左侧一点，使选中星球在菜单落在右侧；
            // 星空由同一个 PlanetRenderer 铺满全屏，不做整张画面平移。
            float aspect = Core.graphics.getWidth() / (float)Math.max(1, Core.graphics.getHeight());
            float d = (1f - center) * 0.45f * targetLength
                * (float)Math.tan(fov * Mathf.degRad * 0.5f) * aspect;

            tmpDir.set(params.camPos).nor();
            tmpRight.set(Vec3.Y).crs(tmpDir).nor();
            menuCamera.position.set(menuPlanet.position).sub(tmpRight.scl(d));

            // 镜头回落的后段开始渐隐其它星球；这样无论它们是否被挡住、是否能飞出屏幕，
            // 都不会在动画结束后直接消失。alpha 归零后才真正 applyMenuScene() 隐藏。
            if(returningToMenu && center < 0.35f && !planetFadeActive && !planetFadeTried){
                if(!beginPlanetFade()) applyMenuScene();
            }

            if(returningToMenu && center < 0.005f){
                returningToMenu = false;
                animating = false;
                overlayFading = false;
                overlayAlpha = 0f;
                params.uiAlpha = 0f;
                atmosphereTarget = 1f;
                dialogHideSuppressed = false;
                if(!planetFadeActive) applyMenuScene();
            }
        }

        private void startNativeLayer(){
            startNativeLayer(false);
        }

        private void startNativeLayer(boolean returning){
            if(disposed || ui.planet == null || !state.isMenu() || (ui.loadfrag != null && ui.loadfrag.shown())) return;
            if(!returning){
                dialogHideSuppressed = false;
                clearPlanetFade();
                planetFadeTried = false;
                selectMenuPlanet(settingPlanet());
            }else{
                ensureMenuPlanet();
            }

            // setup() 必须用真实的目标星球建 UI；之后菜单周期再把 state.planet 换成相机目标。
            params.planet = menuPlanet;
            ui.planet.state = params;
            ui.planet.color.a = 1f;
            configurePlanetDialog();
            // 退出战役时 Dialog.hide() 的默认淡出还在跑，此时 isShown() 仍为 true；
            // 只判断 isShown() 会跳过 show，等淡出动作把对话框移出场景后就是一段黑屏。
            // 因此：已经彻底隐藏才 show；仍在隐藏动画中就只清掉动作并恢复不透明，
            // 不重复触发 PlanetDialog.shown() 的 setup()，相机和返回过渡保持连续。
            if(!ui.planet.isShown()){
                if(returning){
                    // 返回菜单时不要走 Dialog.show()：它会 pack/center 并重新触发 setup()，
                    // 在主界面已经存在时可能让这一层短暂按不同尺寸重排，录屏中就是几帧横向挤压。
                    // 这层在战役里刚显示过，直接按屏幕尺寸挂回即可，状态和子控件都还在。
                    ui.planet.color.a = 1f;
                    ui.planet.setSize(Core.graphics.getWidth(), Core.graphics.getHeight());
                    ui.planet.setPosition(0f, 0f);
                    Core.scene.add(ui.planet);
                    // hide() 在移除前装了触摸拦截器；直接挂回时也要把它摘掉。
                    cancelDialogHide();
                }else{
                    ui.planet.show(Core.scene, null);
                }
            }else{
                cancelDialogHide();
            }
            menuLayerActive = true;
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
            if(returning && returnOverlayHold){
                // 先保持战役视角，在原星球上把区块层/包裹层交叉淡化；结束后再开始向右的相机动画。
                params.planet = menuPlanet;
                params.drawUi = true;
                params.drawSkybox = true;
                params.uiAlpha = overlayAlpha;
                params.zoom = returnZoom;
                params.viewW = -1;
                params.viewH = -1;
                params.renderer = ui.planet;
                params.otherCamPos = null;
                params.otherCamAlpha = 1f;
                if(ui.planet != null){
                    ui.planet.state = params;
                    ui.planet.zoom = returnZoom;
                    ui.planet.color.a = 1f;
                }
                if(root != null) root.visible = false;
                return;
            }

            if(!returning) applyMenuScene();
            // 标题用原版 logo 贴图；若首次安装时图集还没就绪，等同一层挂回来时再补建。
            if(titleLayer == null) buildTitle();
            fadeTitleIn(0.55f);
            updateMenuState();
        }

        /** 自定义地图/读档等路径不会主动调 ui.planet.hide()，进入游戏后在这里兜底清掉菜单背景层。 */
        private void guardGameLayer(){
            if(disposed || !menuLayerActive || !state.isGame()) return;
            dialogHideSuppressed = false;
            clearPlanetFade();
            planetFadeTried = false;
            menuLayerActive = false;
            returningToMenu = false;
            animating = false;
            campaignActive = false;
            returnOverlayHold = false;
            overlayFading = false;
            overlayAlpha = 0f;
            returnZoom = 1f;
            params.uiAlpha = 0f;
            atmosphereTarget = 1f;
            center = 0f;
            centerTarget = 0f;
            yaw = menuYaw;
            pitch = menuPitch;
            if(root != null) root.visible = false;
            if(ui.planet != null && ui.planet.isShown()){
                // 进入游戏不再需要菜单星球背景，立即移除，不播放 0.4 秒 Dialog 淡出。
                ui.planet.hide(Actions.remove());
            }
            restoreMenuScene();
            restoreAtmosphereBase();
            restoreNativePlanet();
        }

        /** 取消 Dialog.hide() 的淡出，同时摘掉 hide 临时添加的触摸拦截器。 */
        private void cancelDialogHide(){
            if(ui.planet == null) return;
            ui.planet.clearActions();
            ui.planet.color.a = 1f;
            try{
                Field field = Dialog.class.getDeclaredField("ignoreTouchDown");
                field.setAccessible(true);
                Object listener = field.get(ui.planet);
                if(listener instanceof EventListener){
                    ui.planet.removeCaptureListener((EventListener)listener);
                }
            }catch(Throwable ignored){
            }
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
            // 任何路径直接重排场景前，先撤掉仍在使用的淡出 shader，避免把自定义 shader 保存在隐藏快照里。
            clearPlanetFade();
            if(menuSceneHidden || menuPlanet == null) return;
            if(menuRoot == null) menuRoot = menuPlanet.solarSystem;
            if(menuRoot == null) menuRoot = menuPlanet;

            menuPlanetWasVisible = menuPlanet.visible;
            menuPlanet.visible = true;
            savedShells = new Seq<>();
            hiddenPlanets = new Seq<>();

            // 目标星球可能在卫星/行星链的末端：祖先必须保持 visible，渲染器才会递归到它；
            // 但祖先自身的球体、云和大气层要暂时藏掉，只留下通向目标星球的结构。
            Seq<Planet> chain = new Seq<>();
            for(Planet p = menuPlanet; p != null; p = p.parent){
                chain.add(p);
                if(p == menuRoot) break;
            }
            if(!chain.contains(menuRoot, true)) chain.add(menuRoot);

            for(Planet p : chain){
                if(p == menuPlanet) continue;
                SavedShell shell = new SavedShell(p);
                savedShells.add(shell);
                p.mesh = new EmptyMesh();
                p.cloudMesh = null;
                p.hasAtmosphere = false;
                p.visible = true;
            }

            for(Planet p : content.planets()){
                if(p != menuPlanet && !chain.contains(p, true) && p.visible){
                    p.visible = false;
                    hiddenPlanets.add(p);
                }
            }

            menuSceneHidden = true;
        }

        private void restoreMenuScene(){
            if(!menuSceneHidden) return;
            if(savedShells != null){
                for(SavedShell shell : savedShells) shell.restore();
                savedShells = null;
            }
            if(hiddenPlanets != null){
                for(Planet p : hiddenPlanets) p.visible = true;
                hiddenPlanets = null;
            }
            if(menuPlanet != null) menuPlanet.visible = menuPlanetWasVisible;
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
            // 反向过渡：进入战役时先把被菜单隐藏的其它行星/太阳系恢复出来，
            // 镜头从菜单窄 FOV 拉回战役视角时它们自然飞回，而不是等 openCampaign 后瞬间出现。
            restoreMenuScene();
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

            // 若玩家在返回菜单的星球淡出期间又点进战役，先把星球恢复为不透明再开始飞入。
            dialogHideSuppressed = false;
            clearPlanetFade();
            planetFadeTried = false;
            // 菜单和战役是同一个 ui.planet.mainView：这里只切参数/UI，不 show/hide 对话框，
            // 也没有第二层星球画面，所以不会出现两层画面对齐造成的错位或拉伸。
            center = 1f;
            returningToMenu = false;
            returnOverlayHold = false;
            overlayFading = true;
            overlayAlpha = 0f;
            overlayTarget = 1f;
            returnZoom = 1f;
            atmosphereAlpha = 0f;
            atmosphereTarget = 1f;
            applyAtmosphereAlpha();
            params.planet = menuPlanet;
            if(ui.planet != null && ui.planet.planets != null) ui.planet.planets.cam.fov = CAMPAIGN_FOV;
            params.drawUi = true;
            params.drawSkybox = true;
            params.uiAlpha = 0f;
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
            menuLayerActive = true;
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
            dialogHideSuppressed = false;
            clearPlanetFade();
            planetFadeTried = false;
            returningToMenu = false;
            campaignActive = false;
            returnOverlayHold = false;
            overlayFading = false;
            overlayAlpha = 0f;
            returnZoom = 1f;
            centerTarget = 0f;
            center = 0f;
            atmosphereTarget = 1f;
            ensureMenuPlanet();
            params.planet = menuCamera;
            params.renderer = null;
            params.drawUi = false;
            params.uiAlpha = 0f;
            params.zoom = 0.9f;
            if(ui.planet != null){
                ui.planet.color.a = 1f;
                ui.planet.zoom = 0.9f;
            }
            applyMenuScene();
            setNativeCampaignUi(false);
            menuLayerActive = true;
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
            if(titleLayer != null){
                root.removeChild(titleLayer);
                titleLayer = null;
                buildTitle();
                if(titleLayer != null) titleLayer.color.a = 1f;
            }else{
                buildTitle();
            }
        }

        void onPlanetHidden(){
            if(disposed) return;

            // 启动地图时 loadAnd() 会先显示加载层再调用 PlanetDialog.hide()，此时状态仍是菜单；
            // 不能把它当成“返回主菜单”，否则会把背景层重新 show 出来并残留到游戏里。
            boolean enteringGame = ui.loadfrag != null && ui.loadfrag.shown();
            boolean backToMenu = state.isMenu() && !enteringGame;
            dragging = false;
            pointer = -1;
            currentMenu = null;

            if(backToMenu){
                // 从战役返回菜单：沿用同一个 mainView，从战役视角连续拉回右侧菜单视角。
                dialogHideSuppressed = true;
                if(ui.planet != null) ui.planet.color.a = 1f;
                clearPlanetFade();
                planetFadeTried = false;
                campaignActive = false;
                returningToMenu = true;
                animating = true;
                menuLayerActive = true;
                returnOverlayHold = true;
                overlayFading = true;
                overlayAlpha = params.uiAlpha;
                overlayTarget = 0f;
                atmosphereTarget = 0f;
                center = 1f;
                centerTarget = 0f;
                returnZoom = params.zoom;

                float camLen = params.camPos.len();
                if(camLen > 0.0001f){
                    yaw = (float)Math.atan2(params.camPos.z, params.camPos.x);
                    pitch = (float)Math.asin(Mathf.clamp(params.camPos.y / camLen, -1f, 1f));
                }

                // 战役里可能已经点选/切换到埃里克尔或模组星球；返回菜单时沿用当前星球。
                Planet returned = (params.planet != null && params.planet != menuCamera) ? params.planet : settingPlanet();
                selectMenuPlanet(returned);
                // 返回保持阶段就是战役视角；显式锁回 60°，避免上一帧菜单/天空盒的窄 FOV 残留放大星球和星空。
                if(ui.planet != null && ui.planet.planets != null){
                    ui.planet.planets.cam.fov = CAMPAIGN_FOV;
                    ui.planet.planets.cam.update();
                }

                // 先不切换相机：保持战役视角，把 uiAlpha 从 1 降到 0，让区块层淡出、
                // HexSky 包裹层淡入；淡完再由 beginReturnCamera() 启动向右的镜头动画。
                params.planet = menuPlanet;
                params.renderer = ui.planet;
                params.drawUi = true;
                params.drawSkybox = true;
                params.uiAlpha = overlayAlpha;
                params.zoom = returnZoom;
                params.otherCamPos = null;
                params.otherCamAlpha = 1f;
                params.viewW = -1;
                params.viewH = -1;
                if(ui.planet != null){
                    ui.planet.state = params;
                    ui.planet.color.a = 1f;
                    ui.planet.zoom = returnZoom;
                }
                if(root != null) root.visible = false;

                Core.app.post(() -> startNativeLayer(true));
                return;
            }

            // 真正进入游戏：不要把 PlanetDialog 再挂回来。
            dialogHideSuppressed = false;
            clearPlanetFade();
            planetFadeTried = false;
            returningToMenu = false;
            animating = false;
            campaignActive = false;
            menuLayerActive = false;
            returnOverlayHold = false;
            overlayFading = false;
            overlayAlpha = 0f;
            returnZoom = 1f;
            atmosphereTarget = 1f;
            centerTarget = 0f;
            center = 0f;
            yaw = menuYaw;
            pitch = menuPitch;
            params.planet = menuCamera;
            params.renderer = null;
            params.drawUi = false;
            params.uiAlpha = 0f;
            params.zoom = 0.9f;
            if(ui.planet != null){
                ui.planet.color.a = 1f;
                ui.planet.zoom = 0.9f;
            }
            restoreMenuScene();
            restoreAtmosphereBase();
            if(root != null) root.visible = false;
            if(ui.planet != null){
                resetGui(ui.planet.buttons);
                resetGui(ui.planet.sectorTop);
                resetGui(ui.planet.expandTable);
            }
            restoreNativePlanet();
        }

        private interface AlphaFade{
            void setAlpha(float alpha);
        }

        private static final class FadeLeaf{
            final PlanetMesh mesh;
            final Shader original;
            final AlphaFade fade;

            FadeLeaf(PlanetMesh mesh, Shader original, AlphaFade fade){
                this.mesh = mesh;
                this.original = original;
                this.fade = fade;
            }
        }

        private static final class FadePlanet{
            final Planet planet;
            final Seq<FadeLeaf> leaves;
            final Color atmosphere;

            FadePlanet(Planet planet, Seq<FadeLeaf> leaves){
                this.planet = planet;
                this.leaves = leaves;
                this.atmosphere = new Color(planet.atmosphereColor);
            }
        }

        /** 和原版 planet.frag 输出一致，只额外乘一个透明度；星球本体/太阳共用同一对 shader。 */
        private abstract static class FadeShader extends Shader implements AlphaFade{
            private static final String alphaFrag =
                "varying vec4 v_col;\n" +
                "uniform float u_alpha;\n" +
                "void main(){\n" +
                "    gl_FragColor = v_col;\n" +
                "    gl_FragColor.a *= u_alpha;\n" +
                "}";
            float alpha = 1f;

            FadeShader(String vertex){
                super(shaderSource(vertex), alphaFrag);
            }

            @Override
            public void apply(){
                setUniformf("u_alpha", alpha);
            }

            @Override
            public void setAlpha(float alpha){
                this.alpha = alpha;
            }
        }

        /** 普通星球：HexMesh.preRender() 已经把光照写进 Shaders.planet，这里直接复用，避免每个星球编译一份。 */
        private static final class PlanetFadeShader extends FadeShader{
            PlanetFadeShader(){
                super("planet.vert");
            }

            @Override
            public void apply(){
                super.apply();
                setUniformf("u_lightdir", Shaders.planet.lightDir);
                setUniformf("u_ambientColor", Shaders.planet.ambientColor);
                setUniformf("u_campos", renderer.planets.cam.position);
                setUniformf("u_emissive", Shaders.planet.emissive ? 1f : 0f);
            }
        }

        /** 太阳等使用 unlit 的星球：只保留顶点色和 alpha。 */
        private static final class UnlitFadeShader extends FadeShader{
            UnlitFadeShader(){
                super("unlit.vert");
            }
        }

        /** 云层/包裹层：clouds.vert 已把 u_alpha 写进顶点色，这里复用 planet.frag 输出。 */
        private static final class CloudFadeShader extends Shader implements AlphaFade{
            float alpha = 1f;

            CloudFadeShader(){
                super(shaderSource("clouds.vert"), shaderSource("planet.frag"));
            }

            @Override
            public void apply(){
                setUniformf("u_alpha", alpha);
                setUniformf("u_emissive", 0f);
                setUniformf("u_lightdir", Shaders.clouds.lightDir);
                setUniformf("u_ambientColor", Shaders.clouds.ambientColor.r, Shaders.clouds.ambientColor.g, Shaders.clouds.ambientColor.b);
            }

            @Override
            public void setAlpha(float alpha){
                this.alpha = alpha;
            }
        }

        private static String shaderSource(String name){
            return Core.files.internal("shaders/" + name).readString();
        }

        private void resetGui(Table t){
            if(t == null) return;
            t.clearActions();
            t.translation.set(0f, 0f);
            t.color.a = 1f;
        }

        void dispose(){
            disposed = true;
            dialogHideSuppressed = false;
            clearPlanetFade();
            planetFadeTried = false;
            menuLayerActive = false;
            returnOverlayHold = false;
            overlayFading = false;
            overlayAlpha = 0f;
            restoreMenuScene();
            restoreAtmosphereBase();
            if(root != null && root.parent != null) root.parent.removeChild(root);
            setNativeCampaignUi(true);
            if(ui.planet != null && ui.planet.isShown()){
                // 不能用 hide() 后再 clearActions()：clearActions 会取消 hide 的移除动作，
                // 留下一个仍挂在场景里的静止星球层。Actions.remove() 立即移除，并照常触发 hidden。
                ui.planet.clearActions();
                ui.planet.color.a = 1f;
                ui.planet.hide(Actions.remove());
            }
            restoreNativePlanet();
        }
    }
}
