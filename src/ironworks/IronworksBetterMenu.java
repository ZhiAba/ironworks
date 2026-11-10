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
import mindustry.type.Planet;
import arc.input.KeyCode;
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
import mindustry.ui.dialogs.PlanetDialog;
import mindustry.ui.Styles;
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
        private Table eggArc;
        private ImageButton eggButton;
        private Button currentMenu;
        private PlanetRenderer planets;
        private FrameBuffer planetBuffer;
        private final PlanetParams params = new PlanetParams();
        private final Vec3 lastCamPos = new Vec3();
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
            params.drawSkybox = true;
            params.zoom = 0.9f;
            root = new WidgetGroup();
            root.setFillParent(true);
            root.touchable = Touchable.childrenOnly;
            root.visible(() -> state.isMenu() && !ui.editor.isShown() && !campaignActive);
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
            Table arc = new Table();
            arc.setFillParent(true);
            arc.touchable = Touchable.childrenOnly;
            arc.visible(() -> !campaignActive);
            arc.fill((x, y, w, h) -> drawEggArc(y, h));
            eggArc = arc;
            root.addChild(arc);

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

        /** 离开战役后把原生对话框状态/样式还回去，避免影响游戏内再次打开星球界面。 */
        private void restoreNativePlanet(){
            if(ui.planet == null) return;
            if(nativePlanetState != null) ui.planet.state = nativePlanetState;
            if(nativeDialogStyle != null) ui.planet.setStyle(nativeDialogStyle);
        }

        /** 主菜单只画 Serpulo 本身：不画太阳/其它行星，避免右侧星球左侧残留黄色光斑。 */
        private void renderMenuPlanet(PlanetParams params, int w, int h){
            Planet planet = params.planet;
            Draw.flush();

            Gl.depthMask(true);
            Gl.clear(Gl.depthBufferBit);
            Gl.enable(Gl.depthTest);
            Gl.enable(Gl.cullFace);
            Gl.cullFace(Gl.back);

            planets.bloom.blending = !params.drawSkybox;
            planets.cam.up.set(Vec3.Y);
            planets.cam.resize(w, h);
            params.camPos.setLength((planet.radius + planet.camRadius) * PlanetRenderer.camLength
                + (params.zoom - 1f) * (planet.radius + planet.camRadius) * 2f);

            planets.cam.position.set(planet.position).add(params.camPos);
            planets.cam.lookAt(planet.position);
            planets.cam.update();
            params.camUp.set(planets.cam.up);
            params.camDir.set(planets.cam.direction);

            // 镜头光轴横移，而不是把整张 FBO 往右搬：星空仍然铺满全屏，
            // 星球在主菜单落在右侧，菜单 UI 仍是独立的左对齐层。
            float shift = (1f - center) * 0.45f;
            lastCamPos.set(planets.cam.position);

            if(planets.skybox != null && params.drawSkybox){
                planets.cam.position.setZero();
                planets.cam.update();
                shiftCamera(shift);
                Gl.depthMask(false);
                planets.skybox.render(planets.cam.combined);
                Gl.depthMask(true);
                planets.cam.position.set(lastCamPos);
                planets.cam.update();
            }

            shiftCamera(shift);
            planets.projector.proj(planets.cam.combined);
            planets.batch.proj(planets.cam.combined);

            // 不能调用 PlanetRenderer.renderPlanet()：它内部会 cam.update()，会把刚做的光轴偏移冲掉。
            // 主菜单只画 Serpulo，直接按当前投影绘制星球本体/云层/大气即可。
            planet.draw(params, planets.cam.combined, planet.getTransform(planets.mat));
            planet.drawClouds(params, planets.cam.combined, planet.getTransform(planets.mat));
            if(planet.parent != null && planet.hasAtmosphere
                && (params.alwaysDrawAtmosphere || Core.settings.getBool("atmosphere"))){
                planet.drawAtmosphere(planets.atmosphere, planets.cam);
            }
            planets.batch.proj(planets.cam.combined);

            Gl.enable(Gl.blend);
            Gl.disable(Gl.cullFace);
            Gl.disable(Gl.depthTest);
            planets.cam.update();
        }

        /** 横移投影矩阵的 x 行：clip.x += amount * clip.w（等效镜头光轴偏移，不改相机朝向）。 */
        private void shiftCamera(float amount){
            if(amount == 0f || planets == null) return;
            float[] v = planets.cam.combined.val;
            v[0] += amount * v[3];
            v[4] += amount * v[7];
            v[8] += amount * v[11];
            v[12] += amount * v[15];
        }

        private void renderPlanet(float w, float h){
            // 时间单位：Time.delta 是帧（60/秒），所以过渡系数直接乘 Time.delta。
            center += (centerTarget - center) * Mathf.clamp(Time.delta * 0.1f, 0f, 1f);
            if(!campaignActive && !dragging && !animating) yaw += Time.delta * ROTATION_SPEED;

            Draw.color(0.008f, 0.010f, 0.020f, 1f);
            Fill.crect(0f, 0f, w, h);
            if(planets == null || planets.skybox == null) drawStars(w, h);

            if(planets != null && params.planet != null){
                // 固定使用全屏 FBO，动画只改变绘制中心，避免过渡期间每帧重建纹理。
                int fw = Math.max(1, (int)w);
                int fh = Math.max(1, (int)h);

                if(!campaignActive){
                    float cp = (float)Math.cos(pitch);
                    params.camPos.set((float)Math.cos(yaw) * cp, (float)Math.sin(pitch), (float)Math.sin(yaw) * cp);
                    params.zoom = Mathf.lerp(0.9f, 1f, center);
                    // 主菜单不画区块边界；进入战役后由原生 PlanetDialog 直接显示，不做边界淡入。
                    params.drawUi = false;
                    params.uiAlpha = 1f;
                }
                params.viewW = fw;
                params.viewH = fh;

                try{
                    if(planetBuffer == null) planetBuffer = new FrameBuffer(2, 2, true);
                    if(planetBuffer.getWidth() != fw || planetBuffer.getHeight() != fh){
                        planetBuffer.resize(fw, fh);
                    }

                    // PlanetRenderer 的 bloom 会重置 GL viewport，所以不能直接 Gl.viewport 偏右；
                    // 固定全屏 FBO 只承载真实场景；星球右移由镜头光轴偏移完成，避免左侧出现未渲染黑边。
                    Draw.flush();
                    boolean begun = false;
                    try{
                        planetBuffer.begin(Color.clear);
                        begun = true;
                        renderMenuPlanet(params, fw, fh);
                    }catch(Throwable t){
                        Log.err("IronworksBetterMenu: 星球渲染失败，回退纯色背景", t);
                        planets = null;
                    }finally{
                        if(begun) planetBuffer.end();
                    }
                    Gl.viewport(0, 0, Core.graphics.getBackBufferWidth(), Core.graphics.getBackBufferHeight());

                    if(planets != null){
                        Draw.color();
                        Draw.rect(Draw.wrap(planetBuffer.getTexture()), w / 2f, h / 2f, fw, -fh);
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

            // 不创建第二个 Screen/PlanetRenderer：菜单背景和战役界面共用同一个 PlanetRenderer/PlanetParams。
            // 战役里保留原生 PlanetDialog.mainView，由它继续渲染星球并处理区块点击；菜单 FBO 在此刻隐藏。
            center = 1f;
            params.planet = Planets.serpulo;
            params.drawUi = true;
            params.drawSkybox = true;
            params.uiAlpha = 1f;
            params.zoom = 1f;
            params.viewW = -1;
            params.viewH = -1;
            params.renderer = ui.planet;

            Vec3 saved = new Vec3(params.camPos);
            ui.planet.state = params;
            ui.planet.color.a = 1f;
            configurePlanetDialog();
            // 不用 Dialog 默认的整屏 alpha 动画：那会把星球和 UI 一起从黑色淡入，并走全屏缓冲导致一次拉伸。
            ui.planet.show(Core.scene, null);
            ui.planet.clearActions();
            ui.planet.color.a = 1f;

            if(!ui.planet.isShown()){
                cancelTransition();
                return;
            }

            campaignActive = true;
            if(root != null) root.visible = false;
            // show() 内部会 lookAt 最后的区块；恢复菜单里已经渲染到的相机，避免视觉重载。
            params.camPos.set(saved);
            params.otherCamPos = null;
            params.otherCamAlpha = 1f;
            params.zoom = 1f;
            ui.planet.zoom = 1f;
            params.drawUi = true;
            params.uiAlpha = 1f;
            params.renderer = ui.planet;

            // 只让原生 UI 层淡入；星球/FBO 画面保持完全不透明，不做全屏 alpha。
            fadeCampaignUi(ui.planet.buttons);
            fadeCampaignUi(ui.planet.sectorTop);
            fadeCampaignUi(ui.planet.expandTable);
        }

        /** 只对 UI Table 做透明度淡入，绝不改 translation/位置，避免与原生布局叠加产生抖动。 */
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
            params.renderer = null;
            params.drawUi = false;
            if(ui.planet != null) ui.planet.color.a = 1f;
            restoreNativePlanet();
            if(root != null) root.visible = true;
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
            restoreNativePlanet();
            if(root != null) root.visible = true;

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
