package ironworks;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.Gl;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.math.Interp;
import arc.math.Mathf;
import arc.scene.Group;
import arc.scene.actions.Actions;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ImageButton.ImageButtonStyle;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.scene.ui.layout.WidgetGroup;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.content.Planets;
import mindustry.core.Version;
import mindustry.game.EventType;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.graphics.g3d.PlanetParams;
import mindustry.graphics.g3d.PlanetRenderer;
import mindustry.ui.Styles;
import mindustry.ui.fragments.MenuFragment;

import static mindustry.Vars.*;

/**
 * 更好的主界面：真实渲染战役星球 + 可拖动旋转 + 进入战役的平滑过渡。
 *
 * <p>只替换 {@link UI#menufrag} 和 {@link UI#menuGroup} 的内容，不碰游戏内 HUD。
 * 由设置项 {@value #KEY} 控制；关闭时恢复原版菜单。无头端完全不加载 UI。</p>
 */
public final class IronworksBetterMenu{
    public static final String KEY = "ironworks.menu.better";

    private static MenuFragment vanilla;
    private static BetterMenuFragment custom;
    private static boolean installed;
    private static boolean planetHook;
    private static boolean failed;

    private IronworksBetterMenu(){
    }

    public static void init(){
        Core.settings.defaults(KEY, true);
        Events.on(EventType.ClientLoadEvent.class, e -> sync());
        Events.run(EventType.Trigger.update, IronworksBetterMenu::sync);
        if(!headless && ui != null) sync();
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
            ui.menuGroup.clear();
            custom = new BetterMenuFragment();
            ui.menufrag = custom;
            custom.build(ui.menuGroup);
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
            ui.menuGroup.clear();
            ui.menufrag = vanilla;
            vanilla.build(ui.menuGroup);
        }catch(Throwable t){
            Log.err("IronworksBetterMenu: 恢复原版主界面失败", t);
        }
        custom = null;
        installed = false;
    }

    private static final class BetterMenuFragment extends MenuFragment{
        private WidgetGroup root;
        private Table menu;
        private PlanetRenderer planets;
        private final PlanetParams params = new PlanetParams();

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
            params.zoom = 0.22f;

            root = new WidgetGroup();
            root.setFillParent(true);
            root.visible(() -> state.isMenu());
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
                    if(BetterMenuFragment.this.pointer != -1) return false;
                    BetterMenuFragment.this.pointer = pointer;
                    dragging = true;
                    lastX = x;
                    lastY = y;
                    return true;
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer){
                    if(pointer != BetterMenuFragment.this.pointer) return;
                    yaw -= (x - lastX) * 0.007f;
                    pitch = Mathf.clamp(pitch + (y - lastY) * 0.007f, -1.25f, 1.25f);
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

        private void buildMenu(){
            menu = new Table();
            menu.setFillParent(true);
            menu.touchable = Touchable.childrenOnly;
            menu.left().top();

            Table panel = new Table(Styles.black6);
            panel.margin(Scl.scl(16f));

            Label title = new Label(Core.bundle.get("ironworks.menu.title", "铁流工业"), Styles.outlineLabel);
            panel.add(title).left().padBottom(Scl.scl(4f)).row();
            panel.add(new Label(Core.bundle.get("ironworks.menu.subtitle", "IRONWORKS · CAMPAIGN"), Styles.outlineLabel))
                .left().padBottom(Scl.scl(18f)).row();

            menuButton(panel, Core.bundle.get("play", "开始游戏"), this::beginCampaign);
            menuButton(panel, Core.bundle.get("customgame", "自定义游戏"), () -> ui.custom.show());
            menuButton(panel, Core.bundle.get("joingame", "加入游戏"), () -> ui.join.show());
            menuButton(panel, Core.bundle.get("database", "数据库"), () -> ui.database.show());
            menuButton(panel, Core.bundle.get("editor", "地图编辑器"), () -> ui.editor.show());
            menuButton(panel, Core.bundle.get("mods", "模组"), () -> ui.mods.show());
            menuButton(panel, Core.bundle.get("settings", "设置"), () -> ui.settings.show());
            menuButton(panel, Core.bundle.get("quit", "退出"), () -> Core.app.exit());

            panel.add(new Label("v" + Version.combined(), Styles.outlineLabel))
                .left().padTop(Scl.scl(18f)).row();

            menu.add(panel).width(Scl.scl(300f)).padLeft(Scl.scl(42f)).padTop(Scl.scl(70f));
            root.addChild(menu);
        }

        private void menuButton(Table panel, String text, Runnable action){
            TextButton button = new TextButton(text, Styles.defaultt);
            button.clicked(() -> {
                if(!animating) action.run();
            });
            panel.add(button).growX().height(Scl.scl(42f)).pad(Scl.scl(3f)).row();
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
            egg.add(button).size(Scl.scl(32f)).padLeft(Scl.scl(4f));

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

        private void renderPlanet(float w, float h){
            center = Mathf.lerpDelta(center, centerTarget, 0.16f);
            if(!dragging) yaw += Time.delta * 0.012f;

            Draw.color(0.008f, 0.010f, 0.020f, 1f);
            Fill.crect(0f, 0f, w, h);
            drawStars(w, h);

            if(planets != null && params.planet != null){
                float frac = Mathf.lerp(0.62f, 1f, center);
                int vw = Math.max(1, (int)(w * frac));
                int vx = Math.max(0, (int)w - vw);
                int vh = Math.max(1, (int)h);

                float cp = (float)Math.cos(pitch);
                params.camPos.set((float)Math.cos(yaw) * cp, (float)Math.sin(pitch), (float)Math.sin(yaw) * cp);
                params.zoom = Mathf.lerp(0.22f, 0.08f, center);
                params.viewW = vw;
                params.viewH = vh;

                try{
                    Gl.viewport(vx, 0, vw, vh);
                    planets.render(params);
                }catch(Throwable t){
                    // 某些客户端/集显在菜单阶段没有完成星球 mesh 上传；不要每帧崩溃。
                    Log.err("IronworksBetterMenu: 星球渲染失败，回退纯色背景", t);
                    planets = null;
                }finally{
                    Gl.viewport(0, 0, (int)w, (int)h);
                }
            }

            Draw.color(0f, 0f, 0f, 0.20f);
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
            centerTarget = 1f;
            menu.addAction(Actions.parallel(
                Actions.fadeOut(0.36f, Interp.fade),
                Actions.translateBy(-Scl.scl(180f), 0f, 0.36f, Interp.pow3In)
            ));
            menu.addAction(Actions.sequence(
                Actions.delay(0.56f),
                Actions.run(this::openCampaign)
            ));
        }

        private void openCampaign(){
            if(!state.isMenu() || ui.planet == null) return;
            ui.planet.state.planet = Planets.serpulo;
            ui.planet.show();
            if(!ui.planet.isShown()){
                onPlanetHidden();
                return;
            }
            Core.app.post(() -> Core.app.post(this::animatePlanetDialog));
        }

        /** 战役 GUI 从屏幕边缘滑入；PlanetDialog 的 setup 在 show() 后重建控件，所以延迟两帧。 */
        private void animatePlanetDialog(){
            if(ui.planet == null || !ui.planet.isShown()) return;
            float w = Core.graphics.getWidth();
            float off = Math.max(Scl.scl(120f), w * 0.12f);

            if(ui.planet.buttons != null){
                ui.planet.buttons.translation.set(-off, -Scl.scl(20f));
                ui.planet.buttons.color.a = 0f;
                ui.planet.buttons.addAction(Actions.parallel(
                    Actions.translateBy(off, Scl.scl(20f), 0.45f, Interp.pow3Out),
                    Actions.fadeIn(0.45f, Interp.fade)
                ));
            }
            if(ui.planet.sectorTop != null){
                ui.planet.sectorTop.translation.set(off, 0f);
                ui.planet.sectorTop.color.a = 0f;
                ui.planet.sectorTop.addAction(Actions.parallel(
                    Actions.translateBy(-off, 0f, 0.45f, Interp.pow3Out),
                    Actions.fadeIn(0.45f, Interp.fade)
                ));
            }
            slideIn(ui.planet.notifs, off, 0f, 0.05f);
            slideIn(ui.planet.expandTable, off, 0f, 0.08f);
        }

        private void slideIn(Table table, float dx, float dy, float delay){
            if(table == null) return;
            table.translation.set(dx, dy);
            table.color.a = 0f;
            table.addAction(Actions.sequence(
                Actions.delay(delay),
                Actions.parallel(
                    Actions.translateBy(-dx, -dy, 0.45f, Interp.pow3Out),
                    Actions.fadeIn(0.45f, Interp.fade)
                )
            ));
        }

        void onPlanetHidden(){
            animating = false;
            centerTarget = 0f;
            if(menu != null){
                menu.clearActions();
                menu.translation.set(0f, 0f);
                menu.color.a = 1f;
                menu.visible = true;
            }
        }
    }
}
