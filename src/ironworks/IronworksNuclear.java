package ironworks;

import arc.graphics.Color;
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
                    .output(with(yellowcake, 1))
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

        // —— 干式储存库：只收固化废物体 ——
        dryStorage = new WasteStorage("dry-storage"){{
            requirements(Category.effect,
                with(IronworksItems.steelPlate, 120, Items.lead, 100, Items.metaglass, 60));
            size = 3;
            itemCapacity = 400;
            health = 700;
        }};
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
