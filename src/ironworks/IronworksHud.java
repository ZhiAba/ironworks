package ironworks;

import arc.Core;
import arc.Events;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Align;
import arc.util.Strings;
import arc.util.Time;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.ui.Styles;
import mindustry.world.blocks.power.PowerGraph;

import static mindustry.Vars.*;

/**
 * 客户端辅助 HUD（v2.1.1.0）。
 *
 * 内嵌两种对大型工厂最有用的信息：
 *   1. 电网总览：所有 PowerGraph 的发电/用电/收支/电池储量，缺电时标红；
 *   2. 物流监视：指向建筑时显示该建筑物品/液体的净流量（库存变化/秒）。
 *
 * 所有统计都只读，不改任何方块效率或传输速度；无头端不初始化。
 * 设置开关由 IronworksAuxSettings 的子设置界面管理。
 */
public class IronworksHud{
    public static final String powerKey = "ironworks.hud.power";
    public static final String flowKey = "ironworks.hud.flow";

    private static final float refreshInterval = 30f; // 0.5 秒刷新一次，避免每帧扫全图
    private static final float flowInterval = 60f;    // 净流量窗口 1 秒

    private static boolean initialized;
    private static Table root, panel;
    private static Label powerLabel, flowLabel;
    private static float refreshTimer;

    private static final ObjectSet<PowerGraph> graphs = new ObjectSet<>();
    private static final Seq<Flow> flows = new Seq<>();

    private static Building flowTarget;
    private static int lastLow;
    private static float flowLastSample;
    private static float[] itemPrev = new float[0], liquidPrev = new float[0];

    private IronworksHud(){
    }

    public static void init(){
        if(initialized || headless) return;
        initialized = true;

        Core.settings.defaults(powerKey, false);
        Core.settings.defaults(flowKey, false);

        Events.run(Trigger.update, IronworksHud::update);
        Events.on(ResetEvent.class, e -> reset());
        Events.on(WorldLoadEvent.class, e -> reset());
    }

    /** 供设置界面“全部关闭”使用。 */
    public static void reset(){
        flowTarget = null;
        flows.clear();
        refreshTimer = 0f;
        lastLow = 0;
        if(powerLabel != null) powerLabel.setText("");
        if(flowLabel != null){
            flowLabel.setText("");
            flowLabel.visible = false;
        }
        if(panel != null) panel.visible = false;
    }

    private static void update(){
        if(headless || ui == null || ui.hudGroup == null) return;
        if(!state.isGame() || ui.hudfrag == null || !ui.hudfrag.shown){
            hide();
            return;
        }

        boolean showPower = Core.settings.getBool(powerKey, false);
        boolean showFlow = Core.settings.getBool(flowKey, false);
        if(!showPower && !showFlow){
            hide();
            return;
        }

        ensure();

        refreshTimer += Time.delta;
        if(refreshTimer >= refreshInterval){
            refreshTimer = 0f;
            if(showPower) refreshPower(); else clearPower();
            if(showFlow) refreshFlow(); else clearFlow();
        }

        boolean hasPower = showPower && powerLabel != null && powerLabel.getText().length() > 0;
        boolean hasFlow = showFlow && flowLabel != null && flowLabel.visible && flowLabel.getText().length() > 0;
        panel.visible = hasPower || hasFlow;
    }

    private static void hide(){
        if(panel != null) panel.visible = false;
    }

    private static void ensure(){
        if(panel == null){
            panel = new Table(Tex.pane);
            panel.touchable = Touchable.disabled;
            panel.margin(7f);
            panel.visible = false;

            powerLabel = new Label("", Styles.outlineLabel);
            powerLabel.setWrap(true);
            powerLabel.setAlignment(Align.left);
            powerLabel.setColor(Pal.powerLight);
            panel.add(powerLabel).width(310f).left().row();

            flowLabel = new Label("", Styles.outlineLabel);
            flowLabel.setWrap(true);
            flowLabel.setAlignment(Align.left);
            flowLabel.setColor(Pal.accent);
            flowLabel.visible = false;
            panel.add(flowLabel).width(310f).left().padTop(4f);
        }

        if(root == null || root.parent == null){
            if(panel.parent != null) panel.remove();
            root = null;
            if(ui != null && ui.hudGroup != null){
                ui.hudGroup.fill(t -> {
                    root = t;
                    t.touchable = Touchable.childrenOnly;
                    t.right().add(panel).padRight(8f);
                });
            }
        }
    }

    private static void clearPower(){
        lastLow = 0;
        if(powerLabel == null) return;
        powerLabel.setText("");
    }

    private static void clearFlow(){
        flowTarget = null;
        flows.clear();
        if(flowLabel != null){
            flowLabel.setText("");
            flowLabel.visible = false;
        }
    }

    /** 汇总所有独立电网；功率值乘 60 换算为每秒。 */
    private static void refreshPower(){
        if(powerLabel == null) return;
        graphs.clear();
        for(Building b : Groups.build){
            if(b.power != null && b.power.graph != null){
                graphs.add(b.power.graph);
            }
        }

        if(graphs.isEmpty()){
            powerLabel.setText(Core.bundle.get("ironworks.hud.power.none"));
            powerLabel.setColor(Pal.lightishGray);
            return;
        }

        float produced = 0f, needed = 0f, balance = 0f, stored = 0f, capacity = 0f;
        int low = 0;
        for(PowerGraph g : graphs){
            produced += g.getLastPowerProduced() * 60f;
            needed += g.getLastPowerNeeded() * 60f;
            balance += g.getPowerBalance() * 60f;
            stored += g.getLastPowerStored();
            capacity += g.getTotalBatteryCapacity();
            if(g.getLastPowerNeeded() > 0.01f && g.getSatisfaction() < 0.995f) low++;
        }

        if(low > 0 && lastLow == 0){
            IronworksNotifications.warn(Core.bundle.format("ironworks.log.power.low", low));
        }else if(low == 0 && lastLow > 0){
            IronworksNotifications.info(Core.bundle.get("ironworks.log.power.ok"));
        }
        lastLow = low;

        int percent = capacity > 0.01f ? Math.max(0, Math.min(100, Math.round(stored / capacity * 100f))) : 0;
        String text = Core.bundle.format("ironworks.hud.power", graphs.size, rate(produced), rate(needed), signed(balance), percent);
        if(low > 0){
            text += "\n" + Core.bundle.format("ironworks.hud.power.low", low);
        }
        powerLabel.setText(text);
        powerLabel.setColor(low > 0 ? Pal.remove : Pal.powerLight);
    }

    /** 指向建筑时统计其库存净变化；不读取/修改传输逻辑。 */
    private static void refreshFlow(){
        if(flowLabel == null) return;

        Building target = world.buildWorld(Core.input.mouseWorldX(), Core.input.mouseWorldY());
        if(target == null || !target.isValid() || (target.items == null && target.liquids == null)){
            clearFlow();
            return;
        }
        if(target != flowTarget){
            flowTarget = target;
            flowLastSample = Time.time;
            snapshot(target);
            flows.clear();
            flowLabel.setText(Core.bundle.format("ironworks.hud.flow", target.block.localizedName)
                + "\n" + Core.bundle.get("ironworks.hud.flow.sampling"));
            flowLabel.visible = true;
            return;
        }

        float elapsed = Time.time - flowLastSample;
        if(elapsed < flowInterval) return;
        float seconds = elapsed / 60f;
        flowLastSample = Time.time;
        if(seconds <= 0f) return;
        if(itemPrev.length != content.items().size || liquidPrev.length != content.liquids().size){
            snapshot(target);
            return;
        }

        flows.clear();
        if(target.items != null){
            for(Item item : content.items()){
                float now = target.items.get(item);
                float rate = (now - itemPrev[item.id]) / seconds;
                itemPrev[item.id] = now;
                if(Math.abs(rate) >= 0.05f) flows.add(new Flow(item.localizedName, rate));
            }
        }
        if(target.liquids != null){
            for(Liquid liquid : content.liquids()){
                float now = target.liquids.get(liquid);
                float rate = (now - liquidPrev[liquid.id]) / seconds;
                liquidPrev[liquid.id] = now;
                if(Math.abs(rate) >= 0.05f) flows.add(new Flow(liquid.localizedName, rate));
            }
        }

        flows.sort((a, b) -> Float.compare(Math.abs(b.rate), Math.abs(a.rate)));
        int shown = Math.min(3, flows.size);
        StringBuilder text = new StringBuilder(Core.bundle.format("ironworks.hud.flow", target.block.localizedName));
        for(int i = 0; i < shown; i++){
            Flow f = flows.get(i);
            text.append('\n').append(Core.bundle.format("ironworks.hud.flow.entry", f.name, f.rate >= 0f ? "+" : "-", rate(Math.abs(f.rate))));
        }
        if(shown == 0){
            text.append('\n').append(Core.bundle.get("ironworks.hud.flow.stable"));
        }
        flowLabel.setText(text);
        flowLabel.visible = true;
    }

    private static void snapshot(Building target){
        itemPrev = new float[content.items().size];
        liquidPrev = new float[content.liquids().size];
        if(target.items != null){
            for(Item item : content.items()) itemPrev[item.id] = target.items.get(item);
        }
        if(target.liquids != null){
            for(Liquid liquid : content.liquids()) liquidPrev[liquid.id] = target.liquids.get(liquid);
        }
    }

    private static String rate(float value){
        float v = Math.abs(value);
        if(v >= 1000f) return Strings.autoFixed(v / 1000f, 1) + "k";
        return Strings.autoFixed(v, 1);
    }

    private static String signed(float value){
        return (value >= 0f ? "+" : "-") + rate(value);
    }

    static class Flow{
        final String name;
        final float rate;

        Flow(String name, float rate){
            this.name = name;
            this.rate = rate;
        }
    }
}
