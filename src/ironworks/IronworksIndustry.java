package ironworks;

import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.type.Category;
import mindustry.type.LiquidStack;
import mindustry.world.Block;
import mindustry.world.blocks.production.GenericCrafter;
import ironworks.multicraft.MultiCrafter;
import ironworks.multicraft.Recipe;

import static mindustry.type.ItemStack.with;

/**
 * 二级材料工厂。
 *
 * 合并原则：**同一种物理/化学操作只保留一台机器**，用配方切换区分产物。
 *   轧机     —— 铜/铅/钛/钢，四种板材都是压延成形
 *   电解车间 —— 电解水、电解精炼铜、电解精炼铅
 *
 * 钢铁两条路线（都不用电加热）：
 *   高炉 → 铁水 → 转炉(吹氧) → 钢水 → 连铸 → 钢锭     （焦炭路线，热量来自煤与熔体本身）
 *   氢还原 → 海绵铁 → 电弧炉(耗电) → 钢水 → 连铸 → 钢锭 （DRI 路线，电在这里才出场）
 */
public class IronworksIndustry{

    public static Block rollingMill, electrolysisPlant, airSeparator, desulfurizer, powderMixer,
        blastFurnace, converter, caster, arcFurnace;

    public static void load(){
        // —— 轧机：四种板材共用一套轧辊 ——
        rollingMill = new MultiCrafter("rolling-mill"){{
            requirements(Category.crafting,
                with(Items.copper, 60, Items.lead, 40, Items.titanium, 30));
            size = 2;
            itemCapacity = 30;
            recipes = new Recipe[]{
                new Recipe("copper-plate", 30f)
                    .items(with(Items.copper, 2)).power(1f)
                    .output(with(IronworksMaterials.copperPlate, 1)),
                new Recipe("lead-plate", 30f)
                    .items(with(Items.lead, 2)).power(1f)
                    .output(with(IronworksMaterials.leadPlate, 1)),
                new Recipe("titanium-plate", 45f)
                    .items(with(Items.titanium, 2)).power(2f)
                    .output(with(IronworksMaterials.titaniumPlate, 1)),
                new Recipe("steel-plate", 80f)
                    .items(with(IronworksItems.steelIngot, 1)).power(3f)
                    .output(with(IronworksItems.steelPlate, 2)),
                // 铁板放在最后：不打乱旧存档里的 recipeIndex（steel 一直是 3）
                new Recipe("iron-plate", 40f)
                    .items(with(IronworksItems.ironIngot, 2)).power(2f)
                    .output(with(IronworksMaterials.ironPlate, 1)),
                // 铝板轧制（原铝厂的轧制工序并入轧机）
                new Recipe("aluminum-sheet", 75f)
                    .items(with(IronworksAluminum.aluminum, 2)).power(3f)
                    .output(with(IronworksAluminum.aluminumSheet, 1))
            };
        }};

        // —— 电解车间：换电解液就换产物 ——
        electrolysisPlant = new MultiCrafter("electrolysis-plant"){{
            requirements(Category.crafting,
                with(Items.copper, 80, Items.lead, 50, Items.titanium, 60,
                     Items.silicon, 40, Items.metaglass, 30));
            size = 2;
            itemCapacity = 20;
            liquidCapacity = 60f;
            // 氢气走右侧、氧气走左侧：两种产物必须分口，否则会混进同一根管道
            liquidOutputDirections = new int[]{0, 2};
            recipes = new Recipe[]{
                new Recipe("water-split", 60f)
                    .liquids(new LiquidStack(Liquids.water, 6f))
                    .power(4f)
                    .outputLiquid(new LiquidStack(Liquids.hydrogen, 6f),
                                  new LiquidStack(IronworksLiquids.oxygen, 3f)),

                new Recipe("pure-copper", 75f)
                    .items(with(IronworksMaterials.copperPlate, 1))
                    .liquids(new LiquidStack(Liquids.water, 15f))
                    .power(3f)
                    .output(with(IronworksMaterials.pureCopper, 1)),

                new Recipe("pure-lead", 75f)
                    .items(with(IronworksMaterials.leadPlate, 1))
                    .liquids(new LiquidStack(Liquids.water, 15f))
                    .power(3f)
                    .output(with(IronworksMaterials.pureLead, 1)),

                // 拜耳法溶出：铝土矿 + 酸 → 铝酸钠溶液 + 赤泥
                new Recipe("bayer", 90f)
                    .items(with(IronworksAluminum.bauxite, 4))
                    .liquids(new LiquidStack(IronworksLiquids.acid, 8f))
                    .power(4f)
                    .output(with(IronworksAluminum.redMud, 1))
                    .outputLiquid(new LiquidStack(IronworksLiquids.sodiumAluminate, 8f)),

                // 焙烧分解：铝酸钠 → 氧化铝 + 回收酸（闭环）
                new Recipe("calcination", 90f)
                    .liquids(new LiquidStack(IronworksLiquids.sodiumAluminate, 8f))
                    .power(5f)
                    .output(with(IronworksAluminum.alumina, 4))
                    .outputLiquid(new LiquidStack(IronworksLiquids.acid, 6f)),

                // 冰晶石合成：铝土矿 + 硫 + 酸
                new Recipe("cryolite", 75f)
                    .items(with(IronworksAluminum.bauxite, 2, IronworksMaterials.sulfur, 2))
                    .liquids(new LiquidStack(IronworksLiquids.acid, 4f))
                    .power(2f)
                    .output(with(IronworksAluminum.cryolite, 1)),

                // 铝电解：氧化铝 + 冰晶石 + 石墨电极 → 熔融铝 + 氧气
                new Recipe("aluminum-electrolysis", 120f)
                    .items(with(IronworksAluminum.alumina, 4, IronworksAluminum.cryolite, 1,
                                 IronworksMaterials.graphiteElectrode, 1))
                    .power(16f)
                    .outputLiquid(new LiquidStack(IronworksLiquids.moltenAluminum, 8f),
                                  new LiquidStack(IronworksLiquids.oxygen, 4f))
            };
        }};

        // —— 空气分离机：深冷分离空气，得到氮气与氧气 ——
        // 原版只有埃里克尔有氮气，塞普罗没有来源；而黑火药需要氮。
        airSeparator = new MultiCrafter("air-separator"){{
            requirements(Category.crafting,
                with(Items.silicon, 60, Items.metaglass, 40, Items.titanium, 50, Items.lead, 40));
            size = 2;
            itemCapacity = 10;
            liquidCapacity = 60f;
            // 氮气走右侧、氧气走左侧
            liquidOutputDirections = new int[]{0, 2};
            recipes = new Recipe[]{
                new Recipe("air-separation", 60f)
                    .power(6f)
                    .outputLiquid(new LiquidStack(Liquids.nitrogen, 4f),
                                  new LiquidStack(IronworksLiquids.oxygen, 2f))
            };
        }};

        // —— 脱硫塔：石油 + 氢 → 硫 ——
        desulfurizer = new MultiCrafter("desulfurizer"){{
            requirements(Category.crafting,
                with(Items.titanium, 80, Items.metaglass, 40, Items.silicon, 30));
            size = 2;
            itemCapacity = 20;
            liquidCapacity = 60f;
            recipes = new Recipe[]{
                new Recipe("sulfur", 60f)
                    .liquids(new LiquidStack(Liquids.oil, 4f), new LiquidStack(Liquids.hydrogen, 2f))
                    .power(2f)
                    .output(with(IronworksMaterials.sulfur, 2))
            };
        }};

        // —— 高炉：焦炭还原，出铁水（真实高炉就是出铁水，不是铁锭）——
        blastFurnace = new MultiCrafter("blast-furnace"){{
            requirements(Category.crafting,
                with(Items.copper, 150, Items.lead, 100, Items.graphite, 60));
            size = 3;
            itemCapacity = 30;
            liquidCapacity = 60f;
            recipes = new Recipe[]{
                new Recipe("molten-iron", 120f)
                    .items(with(IronworksItems.ironConcentrate, 2, Items.coal, 2))
                    .power(1f)   // 鼓风机电费，不是加热电费
                    .outputLiquid(new LiquidStack(IronworksLiquids.moltenIron, 6f))
                ,
                // 碳化硅：高温还原，放在第二配方
                new Recipe("silicon-carbide", 60f)
                    .items(with(Items.sand, 3, Items.graphite, 2))
                    .output(with(IronworksMaterials.siliconCarbide, 2))
            };
        }};

        // —— 转炉：顶吹氧气脱碳。铁水自带显热，碳氧反应还放热，不需要外部热源 ——
        converter = new MultiCrafter("converter"){{
            requirements(Category.crafting,
                with(Items.titanium, 120, Items.silicon, 60, Items.metaglass, 50));
            size = 3;
            itemCapacity = 10;
            liquidCapacity = 60f;
            recipes = new Recipe[]{
                new Recipe("molten-steel", 120f)
                    .liquids(new LiquidStack(IronworksLiquids.moltenIron, 6f),
                             new LiquidStack(IronworksLiquids.oxygen, 3f))
                    .outputLiquid(new LiquidStack(IronworksLiquids.moltenSteel, 6f)),
                // 巨浪合金熔炼：铜铅钛三熔体 + 硅，吹氧出渣
                new Recipe("molten-surge", 120f)
                    .items(with(Items.silicon, 2))
                    .liquids(new LiquidStack(IronworksLiquids.moltenCopper, 2f),
                             new LiquidStack(IronworksLiquids.moltenLead, 3f),
                             new LiquidStack(IronworksLiquids.moltenTitanium, 2f),
                             new LiquidStack(IronworksLiquids.oxygen, 2f))
                    .power(6f)
                    .output(with(IronworksMaterials.slag, 1))
                    .outputLiquid(new LiquidStack(IronworksLiquids.moltenSurge, 6f))
            };
        }};

        // —— 连铸机：钢水 → 钢锭 ——
        caster = new MultiCrafter("caster"){{
            requirements(Category.crafting,
                with(Items.copper, 80, Items.lead, 60, Items.titanium, 60,
                     Items.silicon, 40, Items.metaglass, 30));
            size = 2;
            itemCapacity = 20;
            liquidCapacity = 60f;
            recipes = new Recipe[]{
                // 钢水 → 钢锭
                new Recipe("steel-ingot", 60f)
                    .liquids(new LiquidStack(IronworksLiquids.moltenSteel, 6f))
                    .power(2f)
                    .output(with(IronworksItems.steelIngot, 2)),
                // 铁水 → 生铁锭（铁水的第二种去向）
                new Recipe("cast-iron-ingot", 50f)
                    .liquids(new LiquidStack(IronworksLiquids.moltenIron, 6f))
                    .power(1f)
                    .output(with(IronworksItems.ironIngot, 2)),
                // 铁水 + 沙模 → 铸铁件
                new Recipe("cast-iron", 70f)
                    .items(with(Items.sand, 2))
                    .liquids(new LiquidStack(IronworksLiquids.moltenIron, 6f))
                    .power(2f)
                    .output(with(IronworksMaterials.castIron, 2)),
                // 熔融铝 → 铝锭
                new Recipe("aluminum", 60f)
                    .liquids(new LiquidStack(IronworksLiquids.moltenAluminum, 8f))
                    .power(1f)
                    .output(with(IronworksAluminum.aluminum, 4)),
                // 熔融金属直接铸板（比轧制省料，但要熔炼与物流）
                new Recipe("copper-plate-cast", 60f)
                    .liquids(new LiquidStack(IronworksLiquids.moltenCopper, 8f))
                    .power(2f)
                    .output(with(IronworksMaterials.copperPlate, 4)),
                new Recipe("lead-plate-cast", 60f)
                    .liquids(new LiquidStack(IronworksLiquids.moltenLead, 8f))
                    .power(2f)
                    .output(with(IronworksMaterials.leadPlate, 4)),
                new Recipe("titanium-plate-cast", 75f)
                    .liquids(new LiquidStack(IronworksLiquids.moltenTitanium, 6f))
                    .power(3f)
                    .output(with(IronworksMaterials.titaniumPlate, 2)),
                // 熔融巨浪合金 → 巨浪合金
                new Recipe("surge-alloy", 90f)
                    .liquids(new LiquidStack(IronworksLiquids.moltenSurge, 6f))
                    .power(3f)
                    .output(with(Items.surgeAlloy, 2)),
                // 应急烧结制铝（老存档没有铝土矿时起步）
                new Recipe("aluminum-sinter", 120f)
                    .items(with(Items.sand, 5, Items.coal, 2))
                    .power(4f)
                    .output(with(IronworksAluminum.aluminum, 2))
            };
        }};

        // —— 电弧炉：把海绵铁重熔并炼成钢。电在这里才出场（大后期）——
        arcFurnace = new MultiCrafter("arc-furnace"){{
            requirements(Category.crafting,
                with(Items.titanium, 150, Items.silicon, 100, Items.metaglass, 80, Items.lead, 60));
            size = 3;
            itemCapacity = 30;
            liquidCapacity = 60f;
            recipes = new Recipe[]{
                // 海绵铁重熔
                new Recipe("arc-steel", 120f)
                    .items(with(IronworksItems.ironIngot, 2))
                    .liquids(new LiquidStack(IronworksLiquids.oxygen, 2f))
                    .power(6f)
                    .outputLiquid(new LiquidStack(IronworksLiquids.moltenSteel, 6f)),
                // 铁水精炼（钢包精炼炉）：焦炭路线也能接电炉
                new Recipe("titanium-alloy", 90f)
                    .items(with(Items.titanium, 2, IronworksItems.steelIngot, 1))
                    .power(6f)
                    .output(with(IronworksSilicon.titaniumAlloy, 1)),
                new Recipe("ladle-steel", 100f)
                    .liquids(new LiquidStack(IronworksLiquids.moltenIron, 6f),
                             new LiquidStack(IronworksLiquids.oxygen, 2f))
                    .power(5f)
                    .outputLiquid(new LiquidStack(IronworksLiquids.moltenSteel, 6f)),
                // 有色金属熔炼：固体 → 熔体
                new Recipe("molten-copper", 60f)
                    .items(with(Items.copper, 4))
                    .power(4f)
                    .outputLiquid(new LiquidStack(IronworksLiquids.moltenCopper, 8f)),
                new Recipe("molten-lead", 60f)
                    .items(with(Items.lead, 4))
                    .power(3f)
                    .outputLiquid(new LiquidStack(IronworksLiquids.moltenLead, 8f)),
                new Recipe("molten-titanium", 75f)
                    .items(with(Items.titanium, 3))
                    .power(8f)
                    .outputLiquid(new LiquidStack(IronworksLiquids.moltenTitanium, 6f)),
                // 铝热炼铁：铝还原铁精矿，氧化铝渣回电解车间
                new Recipe("thermite-iron", 90f)
                    .items(with(IronworksAluminum.aluminum, 2, IronworksItems.ironConcentrate, 3))
                    .power(2f)
                    .output(with(IronworksAluminum.alumina, 2))
                    .outputLiquid(new LiquidStack(IronworksLiquids.moltenIron, 8f)),
                // 再生铝合金
                new Recipe("recycle-alloy", 75f)
                    .items(with(Items.scrap, 4, IronworksAluminum.aluminum, 2))
                    .power(3f)
                    .output(with(IronworksAluminum.aluminumAlloy, 2))
            };
        }};

        // —— 火药混合机：煤 + 硫 + 氮 → 黑火药 ——
        powderMixer = new MultiCrafter("powder-mixer"){{
            requirements(Category.crafting, with(Items.copper, 50, Items.lead, 30, Items.graphite, 20));
            size = 2;
            itemCapacity = 20;
            liquidCapacity = 30f;
            recipes = new Recipe[]{
                new Recipe("black-powder", 45f)
                    .items(with(Items.coal, 1, IronworksMaterials.sulfur, 1))
                    .liquids(new LiquidStack(Liquids.nitrogen, 3f))
                    .power(1f)
                    .output(with(IronworksMaterials.blackPowder, 3))
            };
        }};
    }
}
