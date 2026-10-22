package ironworks;

import arc.Core;
import arc.func.Intc2;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.struct.IntSet;
import arc.util.Eachable;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.entities.units.BuildPlan;
import mindustry.game.Team;
import mindustry.gen.Building;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.type.Liquid;
import mindustry.ui.Bar;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.defense.Wall;
import mindustry.world.consumers.ConsumeLiquid;
import mindustry.world.consumers.ConsumePowerDynamic;
import mindustry.world.meta.BlockGroup;
import mindustry.world.meta.BuildVisibility;
import mindustry.world.meta.Stat;

import static mindustry.type.ItemStack.with;

/**
 * 全域算力集群：区块级超速装置。
 *
 * 矩形占地方案（0.62.0 起，参考 NewHorizon 的 BasicMultiBlock）：
 * 引擎的 Block.size 只能是正方形，所以 4×6 / 2×3 由**核心块 + 链接块**拼出来：
 * <ul>
 *   <li>主集群：4×4 核心 + 底排两个 2×2 链接块 → 占地 4×6；</li>
 *   <li>高级模块：2×2 核心 + 底排两个 1×1 通风块 → 占地 2×3。</li>
 * </ul>
 * 链接块是隐藏方块：不能建造、不能拆除、会把液体转发给核心；核心被拆/被毁时一起清掉，
 * 读档后按相对坐标重新绑定核心（相对坐标可随蓝图一起平移）。
 *
 * 挂载规则：
 * - 模块必须有**至少一条完整的边**贴合主集群（含链接块）的包围盒；
 * - 贴合后模块按方向更换/绘制接口效果（0=上 1=右 2=下 3=左，指主集群相对模块的方向）；
 * - 加成与功率由主集群统计，加成总上限 +200%。
 */
public class IronworksCompute{

    public static Block cluster;
    public static ComputeModule micro, basic, advanced, quantum;
    public static LinkBlock clusterLink, moduleLink;

    public static final float basePower = 32460f;
    public static final float moduleCap = 2.0f;
    /** 基础超速 50%。 */
    public static final float baseBoost = 0.5f;
    /** 全局最大超速 250%。 */
    public static final float maxBoost = 2.5f;
    /** 4/tick = 240/秒。 */
    public static final float coolantUse = 4f;

    /** 4×6：核心 4×4 + 底排两个 2×2（相对核心锚点）。 */
    public static final int[][] CLUSTER_LAYOUT = {{-1, -3, 2}, {1, -3, 2}};
    /** 2×3：核心 2×2 + 底排两个 1×1。 */
    public static final int[][] ADVANCED_LAYOUT = {{0, -1, 1}, {1, -1, 1}};

    public static void load(){
        clusterLink = new LinkBlock("compute-cluster-link", 2);
        moduleLink = new LinkBlock("compute-advanced-link", 1);

        micro = new ComputeModule("compute-micro", 1, 0.0135f, 930f, null, null);
        micro.requirements(Category.effect, with(IronworksSilicon.basicChip, 2, Items.silicon, 20, Items.metaglass, 10));
        micro.health = 200;

        basic = new ComputeModule("compute-basic", 3, 0.07f, 1950f, null, null);
        basic.requirements(Category.effect, with(IronworksSilicon.basicChip, 6, IronworksItems.steelPlate, 30,
            Items.silicon, 50, Items.metaglass, 30));
        basic.health = 900;

        advanced = new ComputeModule("compute-advanced", 2, 0.14f, 6825f, ADVANCED_LAYOUT, moduleLink);
        advanced.requirements(Category.effect, with(IronworksSilicon.advancedChip, 4, IronworksItems.steelPlate, 80,
            IronworksMaterials.siliconCarbide, 30, Items.silicon, 100));
        advanced.health = 2200;

        quantum = new ComputeModule("compute-quantum", 2, 0.20f, 12420f, null, null);
        quantum.requirements(Category.effect, with(IronworksSilicon.quantumCore, 2, IronworksMaterials.siliconCarbide, 50,
            IronworksAluminum.aviationAluminum, 20, Items.silicon, 150));
        quantum.health = 1500;

        cluster = new ComputeCluster("compute-cluster", CLUSTER_LAYOUT, clusterLink){{
            requirements(Category.effect,
                with(IronworksSilicon.quantumCore, 10, IronworksMaterials.siliconCarbide, 200,
                     IronworksItems.steelPlate, 800, Items.silicon, 1200,
                     IronworksAluminum.aviationAluminum, 120));

            size = 4;
            health = 12000;
            armor = 12f;
            solid = true;
            update = true;
            hasLiquids = true;
            liquidCapacity = 2000f;
            hasPower = true;
            //冷却液 240/秒 → 热冷却液；热槽满时效率归零（不再白烧冷却液）
            consume(new ConsumeLiquid(IronworksLiquids.grapheneCoolant, coolantUse){
                @Override
                public float efficiency(Building build){
                    ComputeClusterBuild c = (ComputeClusterBuild)build;
                    return c.hotFull() ? 0f : super.efficiency(build);
                }
            });
            consume(new ConsumePowerDynamic(b -> {
                ComputeClusterBuild c = (ComputeClusterBuild)b;
                return (basePower + c.modulePower) / 60f;
            }));
        }};

        clusterLink.hostBlock = cluster;
        moduleLink.hostBlock = advanced;
    }

    // ------------------------------------------------------------------
    // 几何：包围盒 / 整边贴合
    // ------------------------------------------------------------------

    /** 方块（含链接块）在 tile 坐标下的包围盒，写入 out = {x1, y1, x2, y2}。 */
    public static void rectOf(Building b, int[] out){
        unionRect(b.tileX(), b.tileY(), b.block.size, layoutOf(b.block), out);
    }

    /** 按锚点 tile 计算含链接块的包围盒（不需要 Building 实例，画幽灵/蓝图时用）。 */
    public static void unionRect(int tx, int ty, int size, int[][] layout, int[] out){
        int off = (size - 1) / 2;
        int x1 = tx - off, y1 = ty - off;
        int x2 = x1 + size - 1, y2 = y1 + size - 1;

        if(layout != null){
            for(int[] l : layout){
                int ls = l[2], lo = (ls - 1) / 2;
                int ax = tx + l[0] - lo, ay = ty + l[1] - lo;
                x1 = Math.min(x1, ax);
                y1 = Math.min(y1, ay);
                x2 = Math.max(x2, ax + ls - 1);
                y2 = Math.max(y2, ay + ls - 1);
            }
        }
        out[0] = x1;
        out[1] = y1;
        out[2] = x2;
        out[3] = y2;
    }

    /** 包围盒中心的 world 坐标（Mindustry 的 tile 中心就在 x*8 / y*8）。 */
    public static float centerX(int[] r){ return (r[0] + r[2]) / 2f * 8f; }
    public static float centerY(int[] r){ return (r[1] + r[3]) / 2f * 8f; }

    public static int[][] layoutOf(Block b){
        if(b instanceof ComputeCluster c) return c.linkLayout;
        if(b instanceof ComputeModule m) return m.linkLayout;
        return null;
    }

    /**
     * 模块相对主集群的贴合方向（指主集群相对模块的方向：0=上 1=右 2=下 3=左），-1 表示没有整边贴合。
     * 整边贴合 = 模块的整条接触边完全落在主集群那条边的范围内。
     */
    public static int contactDir(int[] h, int[] m){
        //完全重叠不算贴合（正常放置不会出现）
        if(m[0] <= h[2] && m[2] >= h[0] && m[1] <= h[3] && m[3] >= h[1]) return -1;

        //模块在右 → 主集群在左（3）
        if(m[0] == h[2] + 1 && m[1] >= h[1] && m[3] <= h[3]) return 3;
        //模块在左 → 主集群在右（1）
        if(m[2] == h[0] - 1 && m[1] >= h[1] && m[3] <= h[3]) return 1;
        //模块在上 → 主集群在下（2）
        if(m[1] == h[3] + 1 && m[0] >= h[0] && m[2] <= h[2]) return 2;
        //模块在下 → 主集群在上（0）
        if(m[3] == h[1] - 1 && m[0] >= h[0] && m[2] <= h[2]) return 0;
        return -1;
    }

    /** 遍历包围盒外圈的所有 tile。 */
    public static void forEachBorder(int[] r, Intc2 cons){
        for(int x = r[0]; x <= r[2]; x++){
            cons.get(x, r[1] - 1);
            cons.get(x, r[3] + 1);
        }
        for(int y = r[1]; y <= r[3]; y++){
            cons.get(r[0] - 1, y);
            cons.get(r[2] + 1, y);
        }
    }

    /** 链接块占用的 tile 是否都空着（放置前校验）。 */
    public static boolean fitsExtra(int tx, int ty, int[][] layout){
        if(layout == null) return true;
        for(int[] l : layout){
            int ls = l[2], lo = (ls - 1) / 2;
            int ax = tx + l[0] - lo, ay = ty + l[1] - lo;
            for(int x = ax; x < ax + ls; x++){
                for(int y = ay; y < ay + ls; y++){
                    Tile t = Vars.world.tile(x, y);
                    if(t == null) return false;
                    Block b = t.block();
                    if(!b.isAir() && !b.alwaysReplace) return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 链接块维护
    // ------------------------------------------------------------------

    /** 缺失的链接块补上；被别的建筑占住的位置就跳过。 */
    public static void ensureLinks(Building host, int[][] layout, Block linkBlock){
        if(layout == null || linkBlock == null || Vars.net.client()) return;

        for(int[] l : layout){
            int ax = host.tileX() + l[0], ay = host.tileY() + l[1];
            Tile t = Vars.world.tile(ax, ay);
            if(t == null) continue;
            if(t.block() == linkBlock) continue;

            Block there = t.block();
            if(!there.isAir() && !there.alwaysReplace) continue;

            t.setBlock(linkBlock, host.team, 0);
            if(t.build instanceof LinkBlock.LinkBuild link){
                link.relX = host.tileX() - ax;
                link.relY = host.tileY() - ay;
                link.host = host;
            }
        }
    }

    /** 主块消失时清掉自己的链接块。 */
    public static void removeLinks(Building host, int[][] layout, Block linkBlock){
        if(layout == null || linkBlock == null || Vars.net.client()) return;

        for(int[] l : layout){
            Tile t = Vars.world.tile(host.tileX() + l[0], host.tileY() + l[1]);
            if(t != null && t.block() == linkBlock){
                t.remove();
            }
        }
    }

    /** 某个相邻建筑如果是主集群（或它的链接块），返回主集群本体。 */
    static Building clusterOf(Building b){
        if(b.block == cluster) return b;
        if(b.block == clusterLink && b instanceof LinkBlock.LinkBuild link) return link.host;
        return null;
    }

    /** 某个相邻建筑如果算力模块（或它的链接块），返回模块本体。 */
    static Building moduleOf(Building b){
        if(b.block instanceof ComputeModule) return b;
        if(b.block == moduleLink && b instanceof LinkBlock.LinkBuild link) return link.host;
        return null;
    }

    // ------------------------------------------------------------------
    // 链接块
    // ------------------------------------------------------------------

    /**
     * 隐藏的链接块：把占地补成矩形。
     * 不参与建造、不能拆除；液体转发给核心；核心没了就自己消失。
     */
    public static class LinkBlock extends Block{
        /** 这个链接块属于哪种主块，读档时用它校验目标。 */
        public Block hostBlock;

        public LinkBlock(String name, int size){
            super(name);
            this.size = size;
            update = true;
            solid = true;
            fillsTile = true;
            rotate = false;
            health = 1200;
            armor = 8f;
            group = BlockGroup.walls;
            buildVisibility = BuildVisibility.hidden;
            alwaysUnlocked = false;
            hasLiquids = true;
            liquidCapacity = 60f;
            buildType = LinkBuild::new;
        }

        @Override
        public void setBars(){
            //隐藏块不显示任何条
        }

        public class LinkBuild extends Building{
            public Building host;
            /** 相对核心的偏移（随蓝图平移也能重新绑定） */
            public int relX, relY;
            float timer;

            public void resolve(){
                Building b = Vars.world.build(tile.x + relX, tile.y + relY);
                host = (b != null && b != this && b.block == hostBlock) ? b : null;
            }

            @Override
            public void updateTile(){
                timer += delta();
                if(timer < 10f) return;
                timer = 0f;

                if(host == null || host.dead || !host.isValid()) resolve();
                if(host == null && !Vars.net.client()) tile.remove();
            }

            //液体从链接块旁进，直接进核心的液体池
            @Override
            public boolean acceptLiquid(Building source, Liquid liquid){
                return host != null && host.acceptLiquid(this, liquid);
            }

            @Override
            public void handleLiquid(Building source, Liquid liquid, float amount){
                if(host != null) host.handleLiquid(this, liquid, amount);
            }

            //整张机器贴图由主块一体绘制，链接块不画任何东西（和新视界一致）
            @Override
            public void draw(){
            }

            @Override
            public void drawSelect(){
                if(host != null) host.drawSelect();
            }

            @Override
            public float handleDamage(float amount){
                return host != null ? host.handleDamage(amount) : 0f;
            }

            @Override
            public void damage(float amount){
                if(host != null) host.damage(amount);
            }

            @Override
            public byte version(){
                return 1;
            }

            @Override
            public void write(Writes write){
                super.write(write);
                write.i(relX);
                write.i(relY);
            }

            @Override
            public void read(Reads read, byte revision){
                super.read(read, revision);
                if(revision >= 1){
                    relX = read.i();
                    relY = read.i();
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // 算力模块
    // ------------------------------------------------------------------

    public static class ComputeModule extends Wall{
        public float bonus, power;
        public final int[][] linkLayout;
        public final Block linkBlock;
        public final boolean hasVariant;
        public TextureRegion variant;

        public ComputeModule(String name, int size, float bonus, float power, int[][] linkLayout, Block linkBlock){
            super(name);
            this.size = size;
            this.bonus = bonus;
            this.power = power;
            this.linkLayout = linkLayout;
            this.linkBlock = linkBlock;
            this.hasVariant = linkLayout == null;
            update = true;
            rotate = false;
            buildType = ComputeModuleBuild::new;
        }

        @Override
        public void load(){
            super.load();
            if(hasVariant) variant = Core.atlas.find(name + "-2");
            uiIcon = fullIcon = Core.atlas.find(name + "-ui", region);
        }

        @Override
        public void drawPlace(int x, int y, int rotation, boolean valid){
            if(linkLayout == null){
                super.drawPlace(x, y, rotation, valid);
                return;
            }
            drawPotentialLinks(x, y);
            int[] r = new int[4];
            unionRect(x, y, size, linkLayout, r);
            Draw.color(valid ? Color.white : Pal.remove);
            Draw.rect(region, centerX(r), centerY(r));
            Draw.color();
        }

        @Override
        public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list){
            int[] r = new int[4];
            unionRect(plan.x, plan.y, size, linkLayout, r);
            Draw.rect(region, centerX(r), centerY(r));
        }

        @Override
        public boolean canPlaceOn(Tile tile, Team team, int rotation){
            return super.canPlaceOn(tile, team, rotation) && fitsExtra(tile.x, tile.y, linkLayout);
        }

        public class ComputeModuleBuild extends WallBuild{
            public boolean attached;
            /** 主集群相对本模块的方向：0=上 1=右 2=下 3=左，-1=未贴合 */
            public int dir = -1;
            float timer;

            @Override
            public void updateTile(){
                timer += delta();
                if(timer < 10f) return;
                timer = 0f;

                ensureLinks(this, linkLayout, linkBlock);
                checkAttach();
            }

            void checkAttach(){
                attached = false;
                dir = -1;

                int[] mr = new int[4];
                rectOf(this, mr);

                forEachBorder(mr, (x, y) -> {
                    if(dir >= 0) return;
                    Building b = Vars.world.build(x, y);
                    if(b == null) return;
                    Building host = clusterOf(b);
                    if(host == null) return;

                    int[] hr = new int[4];
                    rectOf(host, hr);
                    int d = contactDir(hr, mr);
                    if(d >= 0){
                        attached = true;
                        dir = d;
                    }
                });
            }

            @Override
            public void draw(){
                if(hasVariant && attached && variant != null && variant.found()){
                    Draw.rect(variant, x, y, variant.width / 4f, variant.height / 4f, -90f * dir);
                }else if(linkLayout != null){
                    int[] r = new int[4];
                    rectOf(this, r);
                    Draw.rect(region, centerX(r), centerY(r));
                    if(attached) drawContactGlow();
                }else{
                    super.draw();
                }
            }

            /** 矩形模块不能靠旋转贴图表达朝向，改成在贴合边上画一条呼吸光带。 */
            void drawContactGlow(){
                int[] mr = new int[4];
                rectOf(this, mr);

                float x1 = mr[0] * 8f - 4f, y1 = mr[1] * 8f - 4f;
                float x2 = mr[2] * 8f + 4f, y2 = mr[3] * 8f + 4f;
                float t = 2f;

                Draw.color(Color.valueOf("b6a8ff"), 0.4f + 0.25f * Mathf.absin(Time.time, 5f, 1f));
                TextureRegion white = Core.atlas.white();
                switch(dir){
                    case 0 -> Draw.rect(white, (x1 + x2) / 2f, y2 - t / 2f, x2 - x1 - 4f, t);
                    case 2 -> Draw.rect(white, (x1 + x2) / 2f, y1 + t / 2f, x2 - x1 - 4f, t);
                    case 1 -> Draw.rect(white, x2 - t / 2f, (y1 + y2) / 2f, t, y2 - y1 - 4f);
                    case 3 -> Draw.rect(white, x1 + t / 2f, (y1 + y2) / 2f, t, y2 - y1 - 4f);
                    default -> {}
                }
                Draw.color();
            }

            @Override
            public void onRemoved(){
                super.onRemoved();
                removeLinks(this, linkLayout, linkBlock);
            }
        }
    }

    // ------------------------------------------------------------------
    // 主集群
    // ------------------------------------------------------------------

    public static class ComputeCluster extends Block{
        public final int[][] linkLayout;
        public final Block linkBlock;

        public ComputeCluster(String name, int[][] linkLayout, Block linkBlock){
            super(name);
            this.linkLayout = linkLayout;
            this.linkBlock = linkBlock;
            buildType = ComputeClusterBuild::new;
            rotate = false;
            update = true;
        }

        @Override
        public void load(){
            super.load();
            uiIcon = fullIcon = Core.atlas.find(name + "-ui", region);
        }

        @Override
        public boolean canPlaceOn(Tile tile, Team team, int rotation){
            return super.canPlaceOn(tile, team, rotation) && fitsExtra(tile.x, tile.y, linkLayout);
        }

        @Override
        public void drawPlace(int x, int y, int rotation, boolean valid){
            drawPotentialLinks(x, y);
            int[] r = new int[4];
            unionRect(x, y, size, linkLayout, r);
            Draw.color(valid ? Color.white : Pal.remove);
            Draw.rect(region, centerX(r), centerY(r));
            Draw.color();
        }

        @Override
        public void drawPlanRegion(BuildPlan plan, Eachable<BuildPlan> list){
            int[] r = new int[4];
            unionRect(plan.x, plan.y, size, linkLayout, r);
            Draw.rect(region, centerX(r), centerY(r));
        }

        @Override
        public void setStats(){
            super.setStats();
            stats.remove(Stat.size);
            stats.add(Stat.size, "4×6");
            stats.add(new Stat("ironworks-compute-base"), "基础 50%，全局上限 250%");
            stats.add(new Stat("ironworks-compute-modules"), "模块加成计入上限（整边贴合）");
            stats.add(new Stat("ironworks-compute-coolant"), "石墨烯冷却液 240/秒 → 热冷却液（存量 2000）");
        }

        @Override
        public void setBars(){
            super.setBars();
            addBar("compute-boost", (ComputeClusterBuild e) -> new Bar(
                () -> Core.bundle.format("bar.ironworks-compute-boost", Mathf.round(e.appliedBoost * 100f)),
                () -> Pal.accent,
                () -> Mathf.clamp(e.appliedBoost / maxBoost)
            ));
            addBar("compute-modules", (ComputeClusterBuild e) -> new Bar(
                () -> Core.bundle.format("bar.ironworks-compute-modules", e.moduleCount,
                    Mathf.round(e.moduleBonus * 100f)),
                () -> arc.graphics.Color.valueOf("b6a8ff"),
                () -> Mathf.clamp(e.moduleBonus / moduleCap)
            ));
            addBar("compute-coolant", (ComputeClusterBuild e) -> e.liquids.get(IronworksLiquids.grapheneCoolant) <= 0.01f ? null : new Bar(
                () -> Core.bundle.format("bar.ironworks-compute-coolant", Mathf.round(e.liquids.get(IronworksLiquids.grapheneCoolant))),
                () -> IronworksLiquids.grapheneCoolant.color,
                () -> Mathf.clamp(e.liquids.get(IronworksLiquids.grapheneCoolant) / e.block.liquidCapacity)
            ));
            addBar("compute-hot", (ComputeClusterBuild e) -> e.liquids.get(IronworksLiquids.hotCoolant) <= 0.01f ? null : new Bar(
                () -> Core.bundle.format("bar.ironworks-compute-hot", Mathf.round(e.liquids.get(IronworksLiquids.hotCoolant))),
                () -> IronworksLiquids.hotCoolant.color,
                () -> Mathf.clamp(e.liquids.get(IronworksLiquids.hotCoolant) / e.block.liquidCapacity)
            ));
        }

        public class ComputeClusterBuild extends Building{
            public int moduleCount;
            public float moduleBonus, modulePower;
            public float appliedBoost;
            float applyTimer, tickTimer;

            void recalcModules(){
                int[] hr = new int[4];
                rectOf(this, hr);

                IntSet seen = new IntSet();
                int[] count = new int[1];
                float[] sum = new float[2];

                forEachBorder(hr, (x, y) -> {
                    Building b = Vars.world.build(x, y);
                    if(b == null) return;
                    Building mod = moduleOf(b);
                    if(mod == null || !seen.add(mod.id)) return;

                    int[] mr = new int[4];
                    rectOf(mod, mr);
                    if(contactDir(hr, mr) < 0) return;

                    ComputeModule m = (ComputeModule)mod.block;
                    count[0]++;
                    sum[0] += m.bonus;
                    sum[1] += m.power;
                });

                moduleCount = count[0];
                moduleBonus = Math.min(sum[0], moduleCap);
                modulePower = sum[1];
            }

            @Override
            public void updateTile(){
                tickTimer += delta();
                if(tickTimer >= 10f){
                    tickTimer = 0f;
                    ensureLinks(this, linkLayout, linkBlock);
                    recalcModules();
                }

                boolean working = efficiency > 0.001f;
                appliedBoost = working ? Math.min(baseBoost + moduleBonus, maxBoost) : 0f;

                //冷却液转热冷却液，与消耗同步（efficiency 为 0 时 edelta 也是 0）
                float flow = coolantUse * edelta();
                if(flow > 0f){
                    float space = block.liquidCapacity - liquids.get(IronworksLiquids.hotCoolant);
                    if(space > 0.0001f){
                        liquids.add(IronworksLiquids.hotCoolant, Math.min(flow, space));
                    }
                }
                if(liquids.get(IronworksLiquids.hotCoolant) > 0.01f){
                    dumpLiquid(IronworksLiquids.hotCoolant);
                }

                if(appliedBoost > 0.001f){
                    applyTimer += delta();
                    if(applyTimer >= 4f){
                        applyTimer = 0f;
                        float boost = appliedBoost;
                        for(Building b : mindustry.gen.Groups.build){
                            if(b == this || b.dead) continue;
                            if(b.block instanceof ComputeCluster || b.block instanceof ComputeModule
                                || b.block instanceof LinkBlock) continue;
                            b.applyBoost(boost, 8f);
                        }
                    }
                }
            }

            /** 热冷却液存满了就停机，不再继续消耗冷却液。 */
            public boolean hotFull(){
                return liquids.get(IronworksLiquids.hotCoolant) >= block.liquidCapacity - 0.001f;
            }

            @Override
            public void draw(){
                int[] r = new int[4];
                rectOf(this, r);
                Draw.rect(block.region, centerX(r), centerY(r));
            }

            @Override
            public void onRemoved(){
                super.onRemoved();
                removeLinks(this, linkLayout, linkBlock);
            }

            @Override
            public byte version(){
                return 1;
            }

            @Override
            public void write(Writes write){
                super.write(write);
                write.f(moduleBonus);
            }

            @Override
            public void read(Reads read, byte revision){
                super.read(read, revision);
                if(revision >= 1) moduleBonus = read.f();
            }
        }
    }
}
