package ironworks.multicraft;

import arc.Core;
import arc.graphics.g2d.TextureRegion;
import arc.scene.ui.Image;
import mindustry.gen.Icon;
import mindustry.graphics.Pal;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;
import mindustry.Vars;
import mindustry.world.meta.StatValue;
import mindustry.world.meta.StatValues;
import mindustry.ui.Styles;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Time;
import arc.math.Interp;
import mindustry.content.Fx;
import mindustry.entities.Effect;
import mindustry.gen.Building;
import mindustry.type.Item;
import mindustry.type.Liquid;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.consumers.ConsumeItemDynamic;
import mindustry.world.consumers.ConsumeLiquidsDynamic;
import mindustry.world.consumers.ConsumePowerDynamic;
import mindustry.world.draw.DrawBlock;
import mindustry.world.draw.DrawDefault;

/**
 * 单方块多配方工厂。
 *
 * 设计要点（也是相对旧库的修正）：
 * 1. 功耗走 ConsumePowerDynamic，读的是**当前配方**的 power —— 电网每帧都会收到真实需求；
 * 2. 输入物品/液体同样用 Dynamic 消耗器，切配方后自动换消耗表；
 * 3. 配方索引通过 Block.config 注册，存档由 TypeIO 自动持久化（SaveVersion 会写 block.config）。
 */
public class MultiCrafter extends Block{

    /** 自定义统计项：详情页的「配方」栏。Stat 是普通类，可自行实例化；本地化键为 stat.recipes */
    public static final mindustry.world.meta.Stat recipeStat = new mindustry.world.meta.Stat("recipes");

    public Recipe[] recipes = {};
    public DrawBlock drawer = new DrawDefault();
    public float warmupSpeed = 0.019f;
    public Effect craftEffect = Fx.none;
    /**
     * 每种产出液体的输出方向（0=右 1=上 2=左 3=下，-1=四面）。
     * 索引对应 recipe().liquidOutputs 的下标。
     * 多液体产出必须分开，否则两种液体会从同一侧流进同一根管道/罐子 → 混料。
     */
    public int[] liquidOutputDirections;

    public MultiCrafter(String name){
        super(name);
        update = true;
        solid = true;
        hasItems = true;
        configurable = true;
        saveConfig = true;
        sync = true;

        itemCapacity = 20;
        liquidCapacity = 30f;
        hasLiquids = true;
        ambientSoundVolume = 0.04f;
        drawArrow = false;

        // 默认外观 + 液体输出方向箭头（放置预览与建成后都显示）
        drawer = new mindustry.world.draw.DrawMulti(
            new mindustry.world.draw.DrawDefault(),
            new DrawLiquidOutputArrows(b -> ((MultiCrafterBuild)b).recipe().liquidOutputs)
        );

        // 三类动态消耗器：都由当前配方决定
        consume(new ConsumeItemDynamic(b -> ((MultiCrafterBuild)b).recipe().inputs));
        // 每 craft 一次性扣液体（不是每 tick 连续扣）：
        // 连续扣会在输入断供时把半截进度里的液体白白消耗掉，闭环冷却看起来像"液体蒸发"。
        consume(new ConsumeLiquidsCraft(b -> ((MultiCrafterBuild)b).recipe().liquidInputs));

        // 动态电力：配方不需要电时（power<=0）不能因为没有电网就把效率判成 0
        consume(new ConsumePowerDynamic(b -> ((MultiCrafterBuild)b).recipe().power){
            @Override
            public float efficiency(mindustry.gen.Building build){
                return requestedPower(build) <= 0f ? 1f : super.efficiency(build);
            }
        });

        config(Integer.class, (MultiCrafterBuild tile, Integer index) -> {
            tile.recipeIndex = Mathf.clamp(index, 0, recipes.length - 1);
            tile.progress = 0f;
            // 旧配方残留的液体不属于新配方：先尽量排给邻居，再清掉，避免隐形占容量
            for(Liquid l : mindustry.Vars.content.liquids()){
                float amt = tile.liquids.get(l);
                if(amt <= 0.001f) continue;
                boolean used = false;
                for(LiquidStack s : tile.recipe().liquidInputs) if(s.liquid == l){ used = true; break; }
                if(!used) for(LiquidStack s : tile.recipe().liquidOutputs) if(s.liquid == l){ used = true; break; }
                if(used) continue;
                tile.dumpLiquid(l, 20f);
                tile.liquids.remove(l, tile.liquids.get(l));
            }
        });
        configClear((MultiCrafterBuild tile) -> {
            tile.recipeIndex = 0;
            tile.progress = 0f;
        });
    }

    /**
     * 详情页配方表。结构对齐 MultiCrafterLib 的 buildStats()：
     * 深色行底 + 左输入 / 中工时条 / 右输出，并额外显示配方名。
     */
    @Override
    public void setStats(){
        super.setStats();
        stats.add(recipeStat, (StatValue)this::buildStats);
    }

    public void buildStats(Table stat){
        if(recipes.length == 0) return;

        for(Recipe r : recipes){
            String name = Core.bundle.get("recipe.ironworks." + r.name, r.name);
            stat.add("[accent]" + name + "[]").left().padTop(8f).row();

            Table row = new Table();
            row.background(mindustry.gen.Tex.whiteui);
            row.setColor(Pal.darkestGray);

            // 输入
            buildIOEntry(row, r, true);

            // 工时条（循环填充，纯视觉，对应原库的 Bar）
            Table time = new Table();
            final float[] dur = {0f};
            float ct = Math.max(r.craftTime, 1f);
            String ctLabel = String.format("%.2f", r.craftTime / 60f);
            time.update(() -> {
                dur[0] += Time.delta;
                if(dur[0] > ct) dur[0] = 0f;
            });
            time.add(new mindustry.ui.Bar(() -> ctLabel, () -> Pal.accent,
                () -> arc.math.Interp.smooth.apply(dur[0] / ct)))
                .height(30f).width(Vars.mobile ? 80f : 130f);
            row.add(time).pad(6f);

            // 输出
            buildIOEntry(row, r, false);

            stat.add(row).pad(8f).growX().left().row();
        }
    }

    /**
     * 输入/输出列。
     * 每个物品单独一行：贴图 + 数量 + **名字**。
     * 之前用 StatValues.stack 只画贴图，动态配方里几种弹药物品长得像，靠贴图分不出来。
     */
    protected void buildIOEntry(Table table, Recipe r, boolean input){
        Table t = new Table();
        t.top().left();

        for(ItemStack stack : (input ? r.inputs : r.outputs)){
            t.add(StatValues.displayItem(stack.item, stack.amount)).left().pad(1f).row();
        }
        for(LiquidStack stack : (input ? r.liquidInputs : r.liquidOutputs)){
            t.add(StatValues.displayLiquid(stack.liquid, stack.amount, false)).left().pad(1f).row();
        }
        if(input && r.power > 0){
            Table p = new Table();
            p.left();
            p.image(Icon.power).size(24f).color(Pal.power).padRight(4f);
            p.add("[lightgray]" + String.format("%.0f", r.power * 60f) + " " + StatUnit.powerSecond.localized());
            t.add(p).left().pad(1f).row();
        }

        table.add(t).pad(5f).top().width(Vars.mobile ? 195f : 220f);
    }

    /**
     * 默认的液体条是"动态条"（显示 liquids.current()），多种液体轮流当最大值时会来回跳。
     * 这里改成按每种液体各一条独立液体条，各显示各的存量。
     */
    @Override
    public void setBars(){
        super.setBars();
        removeBar("liquid");

        arc.struct.ObjectSet<Liquid> seen = new arc.struct.ObjectSet<>();
        for(Recipe r : recipes){
            for(LiquidStack stack : r.liquidInputs) seen.add(stack.liquid);
            for(LiquidStack stack : r.liquidOutputs) seen.add(stack.liquid);
        }
        // 只显示「当前配方」涉及的液体：切到别的配方时，其余液体条直接不出现
        for(Liquid liquid : seen){
            addBar("liquid-" + liquid.name, (MultiCrafterBuild e) -> {
                boolean used = false;
                for(LiquidStack stack : e.recipe().liquidInputs) if(stack.liquid == liquid){ used = true; break; }
                if(!used) for(LiquidStack stack : e.recipe().liquidOutputs) if(stack.liquid == liquid){ used = true; break; }
                if(!used) return null;
                return new mindustry.ui.Bar(
                    () -> liquid.localizedName + " " + (int)e.liquids.get(liquid) + "/" + (int)liquidCapacity,
                    () -> liquid.barColor,
                    () -> Mathf.clamp(e.liquids.get(liquid) / Math.max(liquidCapacity, 1f))
                );
            });
        }
    }

    @Override
    public void drawPlanRegion(mindustry.entities.units.BuildPlan plan, arc.util.Eachable<mindustry.entities.units.BuildPlan> list){
        drawer.drawPlan(this, plan, list);
    }

    @Override
    protected TextureRegion[] icons(){
        return new TextureRegion[]{region};
    }

    public class MultiCrafterBuild extends Building{

        public int recipeIndex = 0;
        public float progress, warmup, totalProgress;

        public Recipe recipe(){
            return recipes[Mathf.clamp(recipeIndex, 0, recipes.length - 1)];
        }

        @Override
        public void updateTile(){
            if(efficiency > 0f){
                // 与原版一致：进度按效率缩放，缺料/缺电时会变慢而不是照常推进
                progress += edelta() * Math.min(efficiency, 1f) / Math.max(recipe().craftTime, 1f);
                warmup = Mathf.approachDelta(warmup, 1f, warmupSpeed);
            }else{
                warmup = Mathf.approachDelta(warmup, 0f, warmupSpeed);
            }
            totalProgress += warmup * Time.delta;

            if(progress >= 1f){
                craft();
            }
            dumpOutputs();
        }

        public void craft(){
            // 原料不足时不出货：否则 remove 会被钳到 0，产物却照常生成 → 凭空造物
            for(ItemStack in : recipe().inputs){
                if(items.get(in.item) < in.amount) return;
            }
            for(LiquidStack in : recipe().liquidInputs){
                if(liquids.get(in.liquid) < in.amount - 0.0001f) return;
            }

            // 触发所有 Consume：扣物品、扣液体；电力由电网按 requestedPower 结算
            consume();

            for(ItemStack out : recipe().outputs){
                for(int i = 0; i < out.amount; i++) offload(out.item);
            }
            for(LiquidStack out : recipe().liquidOutputs){
                handleLiquid(this, out.liquid, out.amount);
            }

            progress %= 1f;
            if(wasVisible) craftEffect.at(x, y);
        }

        public void dumpOutputs(){
            for(ItemStack out : recipe().outputs){
                if(items.has(out.item)) dump(out.item);
            }
            LiquidStack[] outs = recipe().liquidOutputs;
            for(int i = 0; i < outs.length; i++){
                int dir = liquidOutputDirections != null && liquidOutputDirections.length > i
                    ? liquidOutputDirections[i] : -1;
                if(dir == -1){
                    dumpLiquid(outs[i].liquid, 2f);
                }else{
                    dumpLiquid(outs[i].liquid, 2f, dir);
                }
            }
        }

        @Override
        public boolean shouldConsume(){
            // 输出放不下就停工，避免产物被吞
            for(ItemStack out : recipe().outputs){
                if(items.get(out.item) + out.amount > itemCapacity) return false;
            }
            // 液体输出同理：看这种液体自己还能不能装下
            // （不能按总量算：输入液体也占容量，会把正在生产的机器卡死）
            for(LiquidStack out : recipe().liquidOutputs){
                if(liquids.get(out.liquid) + out.amount > block.liquidCapacity) return false;
            }
            return enabled;
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            if(liquids.get(liquid) >= block.liquidCapacity - 0.001f) return false;
            // 只接受当前配方用得到的液体，避免下游把产物倒回上游、把罐子占满
            for(LiquidStack in : recipe().liquidInputs){
                if(in.liquid == liquid) return true;
            }
            return false;
        }

        @Override
        public boolean acceptItem(Building source, Item item){
            if(items.get(item) >= itemCapacity) return false;
            for(ItemStack in : recipe().inputs){
                if(in.item == item) return true;
            }
            return false;
        }

        @Override
        public Object config(){
            return recipeIndex;
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(arc.util.io.Writes write){
            super.write(write);
            write.i(recipeIndex);
        }

        @Override
        public void read(arc.util.io.Reads read, byte revision){
            super.read(read, revision);
            if(revision >= 1){
                recipeIndex = Mathf.clamp(read.i(), 0, Math.max(recipes.length - 1, 0));
            }
        }

        @Override
        public void buildConfiguration(Table table){
            if(recipes.length <= 1) return;

            Table list = new Table();
            list.defaults().pad(3f);
            for(int i = 0; i < recipes.length; i++){
                int idx = i;
                String label = arc.Core.bundle.get("recipe.ironworks." + recipes[i].name, recipes[i].name);
                arc.scene.ui.TextButton b = new arc.scene.ui.TextButton(label, Styles.flatTogglet);
                b.clicked(() -> configure(idx));
                // 高亮当前配方
                b.setChecked(recipeIndex == idx);
                b.update(() -> b.setChecked(recipeIndex == idx));
                list.add(b).size(150f, 36f).row();
            }

            // 配方太多时限制可见高度（隐藏距离）并加滚动条
            if(recipes.length <= 5){
                table.add(list);
            }else{
                arc.scene.ui.ScrollPane pane = new arc.scene.ui.ScrollPane(list);
                pane.setFadeScrollBars(false);
                pane.setScrollingDisabled(true, false);
                pane.setOverscroll(false, false);
                table.add(pane).width(176f).maxHeight(5f * 42f + 6f);
            }
        }

        @Override
        public void draw(){
            drawer.draw(this);
        }

        @Override
        public void drawLight(){
            super.drawLight();
            drawer.drawLight(this);
        }
    }

    /** 在方块边缘画出每种产出液体的出口箭头；放置预览同样显示。 */
    public static class DrawLiquidOutputArrows extends DrawBlock{
        private final arc.func.Func<Building, LiquidStack[]> outputs;

        public DrawLiquidOutputArrows(arc.func.Func<Building, LiquidStack[]> outputs){
            this.outputs = outputs;
        }

        @Override
        public void draw(Building build){
            MultiCrafter crafter = (MultiCrafter)build.block;
            drawArrows(build.x, build.y, crafter.size, outputs.get(build), crafter.liquidOutputDirections);
        }

        @Override
        public void drawPlan(Block block, mindustry.entities.units.BuildPlan plan, arc.util.Eachable<mindustry.entities.units.BuildPlan> list){
            MultiCrafter crafter = (MultiCrafter)block;
            LiquidStack[] outs = crafter.recipes.length > 0 ? crafter.recipes[0].liquidOutputs : null;
            drawArrows(plan.drawx(), plan.drawy(), crafter.size, outs, crafter.liquidOutputDirections);
        }

        void drawArrows(float x, float y, int size, LiquidStack[] outs, int[] dirs){
            if(outs == null || dirs == null) return;

            float half = size * Vars.tilesize / 2f;
            for(int i = 0; i < outs.length; i++){
                int dir = i < dirs.length ? dirs[i] : -1;
                if(dir < 0) continue;

                float dx = arc.math.geom.Geometry.d4x[dir], dy = arc.math.geom.Geometry.d4y[dir];
                float tipX = x + dx * (half + 9f), tipY = y + dy * (half + 9f);
                float baseX = x + dx * (half - 1f), baseY = y + dy * (half - 1f);

                arc.graphics.Color c = outs[i].liquid.color;
                arc.graphics.g2d.Draw.color(c.r, c.g, c.b, 0.9f);
                arc.graphics.g2d.Lines.stroke(Math.max(2f, size));

                // 箭杆
                arc.graphics.g2d.Lines.line(baseX, baseY, tipX, tipY);
                // 箭头两翼
                float wing = Math.max(4f, size * 2.4f);
                arc.graphics.g2d.Lines.line(tipX, tipY, tipX - dx * wing - dy * wing * 0.8f, tipY - dy * wing + dx * wing * 0.8f);
                arc.graphics.g2d.Lines.line(tipX, tipY, tipX - dx * wing + dy * wing * 0.8f, tipY - dy * wing - dx * wing * 0.8f);
                arc.graphics.g2d.Draw.reset();
            }
        }
    }
}
