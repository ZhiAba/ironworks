package ironworks;

import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.gen.Building;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;
import mindustry.world.Block;
import mindustry.world.blocks.storage.StorageBlock;
import mindustry.world.blocks.environment.OreBlock;
import ironworks.multicraft.MultiCrafter;
import ironworks.multicraft.Recipe;

import static mindustry.type.ItemStack.with;

/**
 * 核工业全套：铀矿 → 黄饼 → 浓缩铀 → 燃料棒 → RBMK → 乏燃料 → 后处理 → 固化 → 干式储存。
 *
 * <p>全部复用现有基础设施：
 * <ul>
 *   <li>加工环节都是 {@link MultiCrafter}（只写配方，不写逻辑）；</li>
 *   <li>浸出/浓缩/后处理都用现有的「酸」液体，不新增液体；</li>
 *   <li>矿脉复用 {@link OreBlock} + 我们自己的塞普罗撒矿补丁；</li>
 *   <li>干式储存复用原版 {@link StorageBlock}，只收固化废物体。</li>
 * </ul>
 */
public class IronworksNuclear{

    // ---- 物品：燃料前段 ----
    /** 铀矿石：矿脉直接挖。 */
    public static Item uraniumOre;
    /** 黄饼：酸浸后的铀浓缩物（U3O8）。 */
    public static Item yellowcake;
    /** 浓缩铀：可做燃料的富集产物。 */
    public static Item enrichedUranium;
    /** 燃料棒：装进 RBMK 的成品。 */
    public static Item fuelRod;

    // ---- 物品：后段 ----
    /** 乏燃料棒：反应堆烧完吐出来的。 */
    public static Item depletedRod;
    /** 冷却乏燃料棒：在乏燃料池里放够了才能后处理。 */
    public static Item cooledRod;
    /** 钚：后处理产物，可再进燃料线（本模组里当高级材料）。 */
    public static Item plutonium;
    /** 高放废料：后处理的另一半，必须固化。 */
    public static Item nuclearWaste;
    /** 固化废物体：干式储存库的最终形态。 */
    public static Item vitrifiedWaste;

    // ---- 方块 ----
    public static Block uraniumOreBlock;
    public static Block leachPlant, enrichmentPlant, fuelFactory;
    public static Block spentFuelPool, reprocessor, vitrifier, dryStorage;
    public static Block coolingTower, corium;

    public static void load(){
        loadItems();
        loadBlocks();
    }

    static void loadItems(){
        uraniumOre = new Item("uranium-ore", Color.valueOf("4f8f4a")){{
            cost = 1.5f;
            hardness = 4;
        }};
        yellowcake = new Item("yellowcake", Color.valueOf("ffd34d")){{
            cost = 2.0f;
            hardness = 2;
        }};
        enrichedUranium = new Item("enriched-uranium", Color.valueOf("a6ff8a")){{
            cost = 4.0f;
            hardness = 3;
        }};
        fuelRod = new Item("fuel-rod", Color.valueOf("9fe8b0")){{
            cost = 6.0f;
            hardness = 3;
        }};
        depletedRod = new Item("depleted-rod", Color.valueOf("5f7a68")){{
            cost = 2.0f;
            hardness = 3;
        }};
        cooledRod = new Item("cooled-rod", Color.valueOf("8fa39a")){{
            cost = 2.2f;
            hardness = 3;
        }};
        plutonium = new Item("plutonium", Color.valueOf("c46bd8")){{
            cost = 7.0f;
            hardness = 4;
        }};
        nuclearWaste = new Item("nuclear-waste", Color.valueOf("c9e04a")){{
            cost = 1.0f;
            hardness = 1;
        }};
        vitrifiedWaste = new Item("vitrified-waste", Color.valueOf("6f7d8a")){{
            cost = 1.2f;
            hardness = 2;
        }};
    }

    static void loadBlocks(){
        // 铀矿脉（实际撒矿在 IronworksOre 里做）
        uraniumOreBlock = new OreBlock("uranium-ore", uraniumOre);
        uraniumOreBlock.alwaysUnlocked = true;

        // —— 铀浸出槽：铀矿石 + 酸 → 黄饼 ——
        leachPlant = new MultiCrafter("leach-plant"){{
            requirements(Category.crafting,
                with(IronworksItems.steelPlate, 60, Items.titanium, 50, Items.metaglass, 40, IronworksMaterials.siliconCarbide, 10));
            size = 3;
            itemCapacity = 20;
            liquidCapacity = 60f;
            health = 480;
            recipes = new Recipe[]{
                new Recipe("yellowcake", 90f)
                    .items(with(uraniumOre, 3))
                    .liquids(new LiquidStack(IronworksLiquids.acid, 6f))
                    .power(3f)
                    .output(with(yellowcake, 1)),
                new Recipe("boric", 60f)
                    .liquids(new LiquidStack(IronworksLiquids.acid, 6f),
                             new LiquidStack(Liquids.water, 6f))
                    .power(2f)
                    .outputLiquid(new LiquidStack(IronworksLiquids.boricAcid, 6f))
            };
        }};

        // —— 气体离心浓缩厂：黄饼 + 酸 + 电 → 浓缩铀 ——
        enrichmentPlant = new MultiCrafter("enrichment-plant"){{
            requirements(Category.crafting,
                with(IronworksItems.steelPlate, 120, IronworksMaterials.siliconCarbide, 40,
                     IronworksAluminum.aviationAluminum, 30, Items.silicon, 180));
            size = 3;
            itemCapacity = 20;
            liquidCapacity = 60f;
            health = 620;
            recipes = new Recipe[]{
                new Recipe("enriched", 240f)
                    .items(with(yellowcake, 2))
                    .liquids(new LiquidStack(IronworksLiquids.acid, 4f))
                    .power(12f)
                    .output(with(enrichedUranium, 1))
            };
        }};

        // —— 燃料元件厂：浓缩铀 + 钢板 + 碳化硅 → 燃料棒 ——
        fuelFactory = new MultiCrafter("fuel-factory"){{
            requirements(Category.crafting,
                with(IronworksItems.steelPlate, 100, IronworksMaterials.siliconCarbide, 40,
                     Items.silicon, 140, Items.metaglass, 80));
            size = 3;
            itemCapacity = 20;
            liquidCapacity = 20f;
            health = 560;
            recipes = new Recipe[]{
                new Recipe("fuelrod", 120f)
                    .items(with(enrichedUranium, 1, IronworksItems.steelPlate, 4, IronworksMaterials.siliconCarbide, 2))
                    .power(4f)
                    .output(with(fuelRod, 2))
            };
        }};

        // —— 乏燃料冷却池：乏燃料棒 + 水 → 冷却乏燃料棒（放够时间才安全） ——
        spentFuelPool = new MultiCrafter("spent-fuel-pool"){{
            requirements(Category.crafting,
                with(IronworksItems.steelPlate, 90, Items.metaglass, 100, Items.lead, 60, Items.titanium, 60));
            size = 3;
            itemCapacity = 20;
            liquidCapacity = 120f;
            health = 520;
            recipes = new Recipe[]{
                new Recipe("coolrod", 300f)
                    .items(with(depletedRod, 1))
                    .liquids(new LiquidStack(Liquids.water, 2f))
                    .power(1f)
                    .output(with(cooledRod, 1))
            };
        }};

        // —— 后处理厂：冷却乏燃料棒 + 酸 → 钚 + 高放废料 ——
        reprocessor = new MultiCrafter("reprocessor"){{
            requirements(Category.crafting,
                with(IronworksSilicon.quantumCore, 2, IronworksMaterials.siliconCarbide, 80,
                     IronworksItems.steelPlate, 200, Items.silicon, 250));
            size = 4;
            itemCapacity = 30;
            liquidCapacity = 120f;
            health = 900;
            recipes = new Recipe[]{
                new Recipe("reprocess", 240f)
                    .items(with(cooledRod, 1))
                    .liquids(new LiquidStack(IronworksLiquids.acid, 8f))
                    .power(8f)
                    .output(with(plutonium, 1, nuclearWaste, 1))
            };
        }};

        // —— 固化车间：高放废料 + 沙 + 石墨 → 固化废物体 ——
        vitrifier = new MultiCrafter("vitrifier"){{
            requirements(Category.crafting,
                with(IronworksItems.steelPlate, 100, IronworksMaterials.siliconCarbide, 30,
                     Items.silicon, 120, Items.graphite, 80));
            size = 3;
            itemCapacity = 20;
            liquidCapacity = 20f;
            health = 540;
            recipes = new Recipe[]{
                new Recipe("vitrify", 120f)
                    .items(with(nuclearWaste, 1, Items.sand, 2, Items.graphite, 2))
                    .power(5f)
                    .output(with(vitrifiedWaste, 1))
            };
        }};

        // —— 冷却塔：温水 → 水（把汽轮机的回水做成独立闭环） ——
        coolingTower = new MultiCrafter("cooling-tower"){{
            requirements(Category.crafting,
                with(IronworksItems.steelPlate, 60, Items.metaglass, 60, Items.lead, 40, Items.titanium, 30));
            size = 3;
            itemCapacity = 10;
            liquidCapacity = 120f;
            health = 460;
            recipes = new Recipe[]{
                new Recipe("cool", 30f)
                    .liquids(new LiquidStack(IronworksLiquids.hotWater, 6f))
                    .power(1f)
                    .outputLiquid(new LiquidStack(Liquids.water, 6f))
            };
        }};

        // —— 堆芯熔融物：熔毁后留在原地的辐射源 ——
        corium = new Corium("corium");

        // —— 干式储存库：只收固化废物体 ——
        dryStorage = new WasteStorage("dry-storage"){{
            requirements(Category.effect,
                with(IronworksItems.steelPlate, 120, Items.lead, 100, Items.metaglass, 60));
            size = 3;
            itemCapacity = 400;
            health = 700;
        }};
    }

    /**
     * 熔毁后在原地留下 1~5 格堆芯熔融物：持续辐射、熔穿周围建筑、几十秒后凝固消失。
     * 这是「辐射剂量场可视化」的载体（自带红色虚线危险圈）。
     */
    public static class Corium extends Block{
        /** 辐射半径（格）。 */
        public static final float radRadius = 7f * 8f;

        public Corium(String name){
            super(name);
            size = 1;
            solid = true;
            update = true;
            health = 900;
            armor = 4f;
            buildVisibility = mindustry.world.meta.BuildVisibility.hidden;
            inEditor = false;
            alwaysUnlocked = false;
            buildType = CoriumBuild::new;
        }

        @Override
        public boolean isHidden(){
            return true;
        }

        public class CoriumBuild extends Building{
            /** 剩余寿命（tick）。 */
            public float life = 180f * 60f;
            float tick;

            @Override
            public void updateTile(){
                tick += delta();
                if(tick >= 15f){
                    tick = 0f;
                    // 辐射场
                    for(mindustry.gen.Unit u : mindustry.gen.Groups.unit){
                        if(u.dst2(x, y) < radRadius * radRadius){
                            u.apply(IronworksStatus.radiation, 240f);
                        }
                    }
                    // 熔穿周围建筑（自己的四邻）
                    for(Building other : proximity){
                        if(other != null && other.block != corium && Math.random() < 0.10){
                            other.damage(18f);
                        }
                    }
                }

                // 岩浆自损，最后凝固消失
                life -= delta();
                if(life <= 0f) tile.remove();
            }

            @Override
            public void draw(){
                super.draw();
                // 自发光 + 危险圈
                Draw.color(Color.valueOf("ff6a2b"), 0.5f + 0.3f * arc.math.Mathf.absin(arc.util.Time.time, 4f, 1f));
                Draw.rect(arc.Core.atlas.white(), x, y, 10f, 10f);
                Draw.color();
                mindustry.graphics.Drawf.dashCircle(x, y, radRadius, Color.valueOf("ff6a2b"));
            }
        }
    }

    /** 在给定格子周围撒 corium（熔毁时调用）。 */
    public static void spawnCorium(mindustry.world.Tile center, mindustry.game.Team team){
        if(center == null || corium == null) return;
        int n = 1 + (int)(Math.random() * 5);
        center.setBlock(corium, team);
        for(int i = 0; i < n; i++){
            int dx = (int)(Math.random() * 5) - 2, dy = (int)(Math.random() * 5) - 2;
            mindustry.world.Tile t = center.nearby(dx, dy);
            if(t != null && t.block().isAir()) t.setBlock(corium, team);
        }
    }

    /** 固化废物体的干式储存：只收它，别的东西进不来。 */
    public static class WasteStorage extends StorageBlock{
        public WasteStorage(String name){
            super(name);
            solid = true;
            update = false;
            coreMerge = false;
        }

        @Override
        public boolean outputsItems(){
            return false;
        }

        public class WasteStorageBuild extends StorageBuild{
            @Override
            public boolean acceptItem(Building source, Item item){
                return item == vitrifiedWaste && items.get(item) < itemCapacity;
            }
        }
    }
}
