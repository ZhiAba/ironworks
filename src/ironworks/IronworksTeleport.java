package ironworks;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import ironworks.multicraft.MultiCrafter;
import ironworks.multicraft.Recipe;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.Liquid;
import mindustry.type.LiquidStack;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.blocks.production.Drill;
import mindustry.world.blocks.production.GenericCrafter;
import mindustry.world.blocks.production.Pump;
import mindustry.world.blocks.production.Separator;
import mindustry.world.blocks.production.SolidPump;
import mindustry.world.blocks.sandbox.LiquidSource;
import mindustry.world.meta.Stat;

import static mindustry.type.ItemStack.with;

/**
 * MCT 跃迁节点：物品 / 液体两种。
 *
 * 交互参考电力节点：选中节点后，点一下目标建筑即可连接（单目标、有距离限制）。
 * 节点自身不储存：传送模式下，传送带/管道推进来的东西当场转给连接处；
 * 吸取模式下，只抽取连接建筑**产出**的物品/液体，并直接向自己四向分发。
 *
 * 耗电 1080/秒（18/tick）。
 */
public class IronworksTeleport{

    public static TeleportNode itemNode, liquidNode;

    public static final float powerPerSecond = 1080f;
    public static final float linkRangeTiles = 12f;

    /** 配置值：模式与断开（真实连接用建筑 pos，pos 恒 >= 0）。 */
    public static final int configSend = -1, configPull = -2, configClear = -3;
    public static final int modeSend = 0, modePull = 1;

    public static void load(){
        itemNode = new TeleportNode("mct-item-node", true){{
            requirements(Category.effect, with(IronworksSilicon.quantumCore, 2, IronworksMaterials.siliconCarbide, 20,
                Items.silicon, 120, Items.metaglass, 60));
            hasItems = true;
            itemCapacity = 1;
            buildType = ItemNodeBuild::new;
        }};

        liquidNode = new TeleportNode("mct-liquid-node", false){{
            requirements(Category.effect, with(IronworksSilicon.quantumCore, 2, IronworksMaterials.siliconCarbide, 20,
                Items.silicon, 120, Items.metaglass, 60));
            hasLiquids = true;
            liquidCapacity = 100f;
            buildType = LiquidNodeBuild::new;
        }};
    }

    // ------------------------------------------------------------------
    // 输出识别：只抽“产出”，不抽原料
    // ------------------------------------------------------------------

    public static boolean isOutputItem(Building b, Item item){
        if(b == null || b.block == null) return false;
        Block bl = b.block;

        if(bl instanceof MultiCrafter mc){
            for(Recipe r : mc.recipes){
                for(ItemStack st : r.outputs){
                    if(st.item == item) return true;
                }
            }
            return false;
        }

        if(bl instanceof GenericCrafter gc){
            if(gc.outputItem != null && gc.outputItem.item == item) return true;
            if(gc.outputItems != null){
                for(ItemStack st : gc.outputItems){
                    if(st.item == item) return true;
                }
            }
            return false;
        }

        if(bl instanceof Separator sp){
            for(ItemStack st : sp.results){
                if(st.item == item) return true;
            }
            return false;
        }

        //钻头只会存自己挖的矿，直接视为产出
        if(bl instanceof Drill) return true;

        return false;
    }

    public static boolean isOutputLiquid(Building b, Liquid liquid){
        if(b == null || b.block == null) return false;
        Block bl = b.block;

        if(bl instanceof MultiCrafter mc){
            for(Recipe r : mc.recipes){
                for(LiquidStack st : r.liquidOutputs){
                    if(st.liquid == liquid) return true;
                }
            }
            return false;
        }

        if(bl instanceof GenericCrafter gc){
            if(gc.outputLiquid != null && gc.outputLiquid.liquid == liquid) return true;
            if(gc.outputLiquids != null){
                for(LiquidStack st : gc.outputLiquids){
                    if(st.liquid == liquid) return true;
                }
            }
            return false;
        }

        if(bl instanceof SolidPump sp) return sp.result == liquid;
        if(b instanceof Pump.PumpBuild p) return p.liquidDrop == liquid;
        if(b instanceof LiquidSource.LiquidSourceBuild src) return src.source == liquid;

        return false;
    }

    // ------------------------------------------------------------------
    // 方块
    // ------------------------------------------------------------------

    public static class TeleportNode extends Block{
        public final boolean itemMode;
        public TextureRegion laser, laserEnd;

        public TeleportNode(String name, boolean itemMode){
            super(name);
            this.itemMode = itemMode;
            size = 1;
            health = 900;
            armor = 4f;
            solid = true;
            update = true;
            hasPower = true;
            configurable = true;
            saveConfig = false;
            rotate = false;
            canOverdrive = false;
            envEnabled = mindustry.world.meta.Env.any;
            consumePower(powerPerSecond / 60f);

            config(Integer.class, (Building b, Integer v) -> ((NodeBuild)b).applyConfig(v));
        }

        @Override
        public void load(){
            super.load();
            laser = Core.atlas.find("laser");
            laserEnd = Core.atlas.find("laser-end");
        }

        @Override
        public void setStats(){
            super.setStats();
            stats.add(new Stat("mct-link-range"), (int)linkRangeTiles + " 格");
        }
    }

    // ------------------------------------------------------------------
    // 建筑基类
    // ------------------------------------------------------------------

    public static abstract class NodeBuild extends Building{
        public int mode = modeSend;
        public int linkPos = -1;
        float flushTimer;

        public TeleportNode node(){
            return (TeleportNode)block;
        }

        public Building target(){
            if(linkPos < 0) return null;
            Building b = Vars.world.build(linkPos);
            return (b != null && b.isValid() && b.team == team && b != this) ? b : null;
        }

        /** 供连接与测试使用：直接指向某个建筑，传 null 断开。 */
        public void linkTo(Building b){
            linkPos = b == null ? -1 : b.pos();
        }

        public void setMode(int m){
            mode = Mathf.clamp(m, modeSend, modePull);
        }

        public void applyConfig(int v){
            if(v == configSend) mode = modeSend;
            else if(v == configPull) mode = modePull;
            else if(v == configClear) linkPos = -1;
            else linkPos = v;
        }

        // ---------------- 交互 ----------------

        @Override
        public boolean onConfigureBuildTapped(Building other){
            if(other != this && other != null && other.team == team
                && other.within(this, linkRangeTiles * 8f)){
                configure(other.pos());
            }
            return false;
        }

        @Override
        public void buildConfiguration(Table table){
            table.defaults().pad(3f);

            TextButton send = new TextButton(Core.bundle.get("mct.mode.send"), Styles.flatTogglet);
            send.clicked(() -> configure(configSend));
            send.update(() -> send.setChecked(mode == modeSend));
            table.add(send).size(168f, 40f).row();

            TextButton pull = new TextButton(Core.bundle.get("mct.mode.pull"), Styles.flatTogglet);
            pull.clicked(() -> configure(configPull));
            pull.update(() -> pull.setChecked(mode == modePull));
            table.add(pull).size(168f, 40f).row();

            arc.scene.ui.Label linkLabel = new arc.scene.ui.Label("");
            linkLabel.update(() -> {
                Building t = target();
                String name = t == null ? Core.bundle.get("mct.link.none") : t.block.localizedName;
                linkLabel.setText(Core.bundle.format("mct.link", name));
            });
            table.add(linkLabel).padTop(6f).row();
            table.add(Core.bundle.get("mct.hint")).wrap().width(240f).padTop(2f).row();

            table.button(Core.bundle.get("mct.disconnect"), () -> configure(configClear)).size(168f, 32f).padTop(4f).row();
        }

        // ---------------- 每帧 ----------------

        @Override
        public void updateTile(){
            flushTimer += delta();
            if(flushTimer >= 2f){
                flushTimer = 0f;
                flushHeld();
            }

            if(efficiency <= 0.0001f) return;
            if(mode == modePull) pullOutputs();
        }

        /** 取出连接建筑的产出并直接分发给四邻。 */
        protected void pullOutputs(){}

        /** 保险：极端情况下手里有东西没送出去，下一tick再试。 */
        protected void flushHeld(){}

        // ---------------- 绘制与存档 ----------------

        @Override
        public void draw(){
            super.draw();

            Building t = target();
            if(t != null){
                float pulse = 0.7f + Mathf.absin(Time.time, 7f, 0.3f);
                Draw.z(Layer.power);
                Draw.color(node().itemMode ? Color.valueOf("7ff0d0") : Color.valueOf("7fb6ff"));
                Drawf.laser(node().laser, node().laserEnd, x, y, t.x, t.y, 0.3f * pulse);
                Draw.color();
                Draw.z(Layer.block);
            }
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.i(linkPos);
            write.b((byte)mode);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 1){
                linkPos = read.i();
                mode = read.b();
            }
        }
    }

    // ------------------------------------------------------------------
    // 物品节点
    // ------------------------------------------------------------------

    public static class ItemNodeBuild extends NodeBuild{
        @Override
        public boolean acceptItem(Building source, Item item){
            if(mode != modeSend) return false;
            Building t = target();
            return efficiency > 0.0001f && t != null && t.acceptItem(this, item);
        }

        @Override
        public void handleItem(Building source, Item item){
            Building t = target();
            if(t != null && t.acceptItem(this, item)){
                t.handleItem(this, item);
            }else{
                items.add(item, 1);
            }
        }

        @Override
        protected void flushHeld(){
            if(items.total() <= 0) return;
            Building t = target();
            for(int i = 0; i < Vars.content.items().size; i++){
                Item item = Vars.content.items().get(i);
                if(items.get(item) <= 0) continue;
                if(t != null && t.acceptItem(this, item)){
                    t.handleItem(this, item);
                    items.remove(item, 1);
                }else{
                    for(Building b : proximity){
                        if(b == this || b == t || b.dead) continue;
                        if(b.acceptItem(this, item)){
                            b.handleItem(this, item);
                            items.remove(item, 1);
                            break;
                        }
                    }
                }
                return;
            }
        }

        @Override
        protected void pullOutputs(){
            Building t = target();
            if(t == null || !t.block.hasItems || t.items == null) return;

            for(int i = 0; i < Vars.content.items().size; i++){
                Item item = Vars.content.items().get(i);
                if(t.items.get(item) <= 0 || !isOutputItem(t, item)) continue;

                for(Building b : proximity){
                    if(b == this || b == t || b.dead) continue;
                    if(b.acceptItem(this, item)){
                        t.items.remove(item, 1);
                        b.handleItem(this, item);
                        return;
                    }
                }
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // 液体节点
    // ------------------------------------------------------------------

    public static class LiquidNodeBuild extends NodeBuild{
        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            if(mode != modeSend) return false;
            Building t = target();
            return efficiency > 0.0001f && t != null && t.acceptLiquid(this, liquid)
                && t.liquids.get(liquid) < t.block.liquidCapacity - 0.001f;
        }

        @Override
        public void handleLiquid(Building source, Liquid liquid, float amount){
            Building t = target();
            if(t != null){
                float space = t.block.liquidCapacity - t.liquids.get(liquid);
                if(space > 0.001f){
                    t.handleLiquid(this, liquid, Math.min(amount, space));
                    return;
                }
            }
            liquids.add(liquid, amount);
        }

        @Override
        protected void flushHeld(){
            if(liquids.currentAmount() <= 0.01f) return;
            Building t = target();
            Liquid liq = liquids.current();
            if(liq == null) return;

            if(t != null){
                float space = t.block.liquidCapacity - t.liquids.get(liq);
                if(space > 0.001f && t.acceptLiquid(this, liq)){
                    float flow = Math.min(liquids.get(liq), space);
                    t.handleLiquid(this, liq, flow);
                    liquids.remove(liq, flow);
                    return;
                }
            }

            for(Building b : proximity){
                if(b == this || b == t || b.dead) continue;
                if(!b.block.hasLiquids || b.liquids == null) continue;
                if(!b.acceptLiquid(this, liq)) continue;
                float space = b.block.liquidCapacity - b.liquids.get(liq);
                if(space <= 0.001f) continue;
                float flow = Math.min(liquids.get(liq), space);
                b.handleLiquid(this, liq, flow);
                liquids.remove(liq, flow);
                return;
            }
        }

        @Override
        protected void pullOutputs(){
            Building t = target();
            if(t == null || !t.block.hasLiquids || t.liquids == null) return;

            for(int i = 0; i < Vars.content.liquids().size; i++){
                Liquid liq = Vars.content.liquids().get(i);
                float amount = t.liquids.get(liq);
                if(amount <= 0.1f || !isOutputLiquid(t, liq)) continue;

                for(Building b : proximity){
                    if(b == this || b == t || b.dead) continue;
                    if(!b.block.hasLiquids || b.liquids == null) continue;
                    if(!b.acceptLiquid(this, liq)) continue;

                    float space = b.block.liquidCapacity - b.liquids.get(liq);
                    if(space <= 0.001f) continue;

                    float flow = Math.min(Math.min(amount, space), 20f);
                    b.handleLiquid(this, liq, flow);
                    t.liquids.remove(liq, flow);
                    return;
                }
                return;
            }
        }
    }
}
