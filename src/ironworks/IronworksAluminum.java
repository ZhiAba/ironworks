package ironworks;

import arc.graphics.Color;
import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.LiquidStack;
import mindustry.world.Block;
import ironworks.multicraft.MultiCrafter;
import ironworks.multicraft.Recipe;

import static mindustry.type.ItemStack.with;

/**
 * 铝全量：从铝土矿到航空铝的完整冶金链。
 *
 * 闭环点（不是摆设，每一个都接回别的产线）：
 * 1. 酸：溶出罐吃掉酸，焙烧炉把酸回收回来 —— 酸在铝线里循环，只补损耗；
 * 2. 氧气：电解槽副产氧气，直接喂转炉/气化炉；
 * 3. 氧化铝：铝热炉还原铁精矿后把氧化铝吐回来，重新进电解槽；
 * 4. 赤泥：废料分选厂从赤泥里回收钍，尾渣不浪费；
 * 5. 石墨电极：被电解槽消耗，由石墨化炉用煤补齐。
 */
public class IronworksAluminum{

    public static Item bauxite, alumina, cryolite, redMud, aluminum, aluminumSheet,
        aluminumAlloy, aviationAluminum;

    public static Block aluminumPlant, digester, calciner, hallCell, thermiteFurnace;

    public static void load(){
        // —— 原料与中间品 ——
        bauxite = new Item("bauxite", Color.valueOf("b0785a")){{ cost = 1.6f; hardness = 1; }};
        alumina = new Item("alumina", Color.valueOf("e9e4da")){{ cost = 3.0f; hardness = 3; }};
        cryolite = new Item("cryolite", Color.valueOf("cfd8e6")){{ cost = 3.4f; hardness = 3; }};
        redMud = new Item("red-mud", Color.valueOf("a8503c")){{ cost = 1.0f; hardness = 1; }};

        // —— 产品 ——
        aluminum      = new Item("aluminum",          Color.valueOf("d8dde3")){{ cost = 2.6f; hardness = 2; }};
        aluminumSheet = new Item("aluminum-sheet",    Color.valueOf("cfd6dd")){{ cost = 3.4f; hardness = 4; }};
        aluminumAlloy = new Item("aluminum-alloy",    Color.valueOf("c2ccd8")){{ cost = 4.2f; hardness = 4; }};
        aviationAluminum = new Item("aviation-aluminum", Color.valueOf("eef3f8")){{ cost = 7.5f; hardness = 5; }};

        // —— 溶出罐：铝土矿 + 酸 → 铝酸钠溶液 + 赤泥 ——
        digester = new MultiCrafter("digester"){{
            requirements(Category.crafting,
                with(IronworksItems.steelPlate, 120, Items.silicon, 100, Items.copper, 150,
                     IronworksMaterials.graphiteElectrode, 20));
            size = 3;
            itemCapacity = 30;
            liquidCapacity = 50f;
            recipes = new Recipe[]{
                new Recipe("bayer", 90f)
                    .items(with(bauxite, 4))
                    .liquids(new LiquidStack(IronworksLiquids.acid, 8f))
                    .power(4f)
                    .output(with(redMud, 1))
                    .outputLiquid(new LiquidStack(IronworksLiquids.sodiumAluminate, 8f)),
                // 冰晶石：电解槽的助熔剂，本身也要开矿才拿得到（这里用铝土矿里的钠氟伴生）
                new Recipe("cryolite", 75f)
                    .items(with(bauxite, 2, IronworksMaterials.sulfur, 2))
                    .liquids(new LiquidStack(IronworksLiquids.acid, 4f))
                    .power(2f)
                    .output(with(cryolite, 1))
            };
        }};

        // —— 焙烧炉：铝酸钠溶液 → 氧化铝 + 回收酸（闭环） ——
        calciner = new MultiCrafter("calciner"){{
            requirements(Category.crafting,
                with(IronworksItems.steelPlate, 100, Items.silicon, 80, Items.graphite, 60, Items.lead, 100));
            size = 3;
            itemCapacity = 30;
            liquidCapacity = 50f;
            recipes = new Recipe[]{
                new Recipe("alumina", 90f)
                    .liquids(new LiquidStack(IronworksLiquids.sodiumAluminate, 8f))
                    .power(5f)
                    .output(with(alumina, 4))
                    .outputLiquid(new LiquidStack(IronworksLiquids.acid, 6f))
            };
        }};

        // —— 电解槽：霍尔-埃鲁法，熔融氧化铝电解 → 熔融铝 + 氧气 ——
        hallCell = new MultiCrafter("hall-cell"){{
            requirements(Category.crafting,
                with(IronworksItems.steelPlate, 150, Items.silicon, 200, Items.metaglass, 100,
                     IronworksMaterials.graphiteElectrode, 40, Items.titanium, 80));
            size = 3;
            itemCapacity = 30;
            liquidCapacity = 50f;
            // 熔融铝走右、氧气走左：两种产出必须分口
            liquidOutputDirections = new int[]{0, 2};
            recipes = new Recipe[]{
                new Recipe("molten-aluminum", 120f)
                    .items(with(alumina, 4, cryolite, 1, IronworksMaterials.graphiteElectrode, 1))
                    .power(16f)
                    .outputLiquid(new LiquidStack(IronworksLiquids.moltenAluminum, 8f),
                                  new LiquidStack(IronworksLiquids.oxygen, 4f))
            };
        }};

        // —— 铝热炉：铝还原铁精矿 → 铁水 + 氧化铝（回电解槽）；废料再生铝合金 ——
        thermiteFurnace = new MultiCrafter("thermite-furnace"){{
            requirements(Category.crafting,
                with(IronworksItems.steelPlate, 120, Items.silicon, 100, Items.graphite, 80, Items.titanium, 60));
            size = 3;
            itemCapacity = 30;
            liquidCapacity = 40f;
            recipes = new Recipe[]{
                new Recipe("thermite-iron", 90f)
                    .items(with(aluminum, 2, IronworksItems.ironConcentrate, 3))
                    .power(2f)
                    .output(with(alumina, 2))
                    .outputLiquid(new LiquidStack(IronworksLiquids.moltenIron, 8f)),
                new Recipe("recycle-alloy", 75f)
                    .items(with(Items.scrap, 4, aluminum, 2))
                    .power(3f)
                    .output(with(aluminumAlloy, 2))
            };
        }};

        // —— 铝厂：铸造 / 轧板 / 合金 / 航空铝 ——
        aluminumPlant = new MultiCrafter("aluminum-plant"){{
            requirements(Category.crafting,
                with(IronworksItems.steelPlate, 80, Items.silicon, 100, Items.metaglass, 60));
            size = 3;
            itemCapacity = 30;
            liquidCapacity = 40f;
            recipes = new Recipe[]{
                new Recipe("aluminum", 60f)
                    .liquids(new LiquidStack(IronworksLiquids.moltenAluminum, 8f))
                    .power(1f)
                    .output(with(aluminum, 4)),
                new Recipe("aluminum-sheet", 75f)
                    .items(with(aluminum, 2))
                    .power(3f)
                    .output(with(aluminumSheet, 1)),
                new Recipe("aluminum-alloy", 75f)
                    .items(with(aluminum, 2, Items.copper, 1))
                    .power(4f)
                    .output(with(aluminumAlloy, 1)),
                new Recipe("aviation-aluminum", 90f)
                    .items(with(aluminumAlloy, 2, Items.titanium, 1, IronworksMaterials.graphene, 1))
                    .power(6f)
                    .output(with(aviationAluminum, 1)),
                // 应急路线：没有铝土矿脉的老存档/低资源期用它起步，效率远低于正规铝线
                new Recipe("aluminum-sinter", 120f)
                    .items(with(Items.sand, 5, Items.coal, 2))
                    .power(4f)
                    .output(with(aluminum, 2))
            };
        }};
    }
}
