package ironworks;

import mindustry.content.Blocks;
import mindustry.content.Items;
import mindustry.content.Liquids;
import mindustry.content.Planets;
import mindustry.type.StatusEffect;
import mindustry.type.Liquid;
import mindustry.content.TechTree.TechNode;
import mindustry.ctype.UnlockableContent;
import mindustry.game.Objectives.Produce;
import mindustry.type.ItemStack;

import static mindustry.type.ItemStack.with;

/**
 * 科技树。
 *
 * 设计原则（0.24.0 修正）：
 * 1. **不再挂到原版等级节点下**。挂上去就意味着必须先研究那个原版节点，
 *    哪怕我们的内容本该更早、或与原版同级。现在两条线都直接挂在基础核心下面，
 *    解锁时期由**研究所需的资源数量**决定，而不是被别人的链子拖着走。
 * 2. 物品线与建筑线相互独立：材料线只依赖材料，建筑线只依赖建筑与它真正用到的材料。
 * 3. 每个节点都有明确的研究成本（物品默认是免费的，必须显式写数量）。
 */
public class IronworksTech{

    static TechNode node(TechNode parent, UnlockableContent content, ItemStack... cost){
        return new TechNode(parent, content, cost);
    }

    /** 物品节点：不给成本。物品是"获得即解锁"，研究成本只用在建筑/单位上。 */
    static TechNode produce(TechNode parent, UnlockableContent content){
        TechNode node = new TechNode(parent, content, ItemStack.empty);
        node.objectives.add(new Produce(content));
        return node;
    }

    public static void load(){
        TechNode core = Blocks.coreShard.techNode;

        // ══════════════════ 一、材料线 ══════════════════
        // 早期：铜/铅/煤 级
        TechNode iron = produce(core, IronworksItems.ironConcentrate);
        TechNode copperPlate = produce(iron, IronworksMaterials.copperPlate);
        TechNode leadPlate = produce(iron, IronworksMaterials.leadPlate);
        TechNode barrel = produce(iron, IronworksMaterials.waterBarrel);
        produce(barrel, IronworksMaterials.oilBarrel);

        // 中期：硅/钛/玻璃 级
        TechNode ironIngot = produce(iron, IronworksItems.ironIngot);
        TechNode titaniumPlate = produce(iron, IronworksMaterials.titaniumPlate);
        produce(iron, IronworksMaterials.pureCopper);
        produce(iron, IronworksMaterials.pureLead);

        // 铝
        produce(iron, IronworksAluminum.aluminum);
        produce(iron, IronworksAluminum.aluminumAlloy);
        produce(iron, IronworksAluminum.aviationAluminum);
        // 铝全量中间品
        TechNode bauxite = produce(iron, IronworksAluminum.bauxite);
        produce(bauxite, IronworksAluminum.alumina);
        produce(bauxite, IronworksAluminum.cryolite);
        produce(bauxite, IronworksAluminum.redMud);
        produce(bauxite, IronworksAluminum.aluminumSheet);
        // 桶装液体物品
        produce(barrel, IronworksMaterials.moltenCopperBarrel);
        produce(barrel, IronworksMaterials.moltenLeadBarrel);
        produce(barrel, IronworksMaterials.moltenTitaniumBarrel);
        produce(barrel, IronworksMaterials.moltenSurgeBarrel);
        produce(barrel, IronworksMaterials.moltenAluminumBarrel);
        produce(barrel, IronworksMaterials.gasolineBarrel);
        produce(barrel, IronworksMaterials.heavyOilBarrel);
        produce(iron, IronworksPetro.rubber);
        produce(iron, IronworksPetro.plastic);
        produce(iron, IronworksPetro.petcoke);

        // 硅基时代
        produce(iron, IronworksSilicon.silver);
        produce(iron, IronworksSilicon.gold);
        produce(iron, IronworksSilicon.titaniumAlloy);
        produce(iron, IronworksSilicon.basicChip);
        produce(iron, IronworksSilicon.advancedChip);
        produce(iron, IronworksSilicon.quantumCore);

        // 石墨烯与石墨深加工
        produce(iron, IronworksMaterials.graphene);
        produce(iron, IronworksMaterials.siliconCarbide);
        produce(iron, IronworksMaterials.graphiteElectrode);
        produce(iron, IronworksMaterials.moltenIronBarrel);
        produce(iron, IronworksMaterials.moltenSteelBarrel);

        // 铁板：基础工业部件（铁锭轧制）
        produce(ironIngot, IronworksMaterials.ironPlate);

        // 铸铁件：铁水的第二条下游
        produce(ironIngot, IronworksMaterials.castIron);

        TechNode steel = produce(ironIngot, IronworksItems.steelIngot);
        TechNode steelPlate = produce(steel, IronworksItems.steelPlate);

        // 火药与弹药
        TechNode sulfur = produce(iron, IronworksMaterials.sulfur);
        TechNode powder = produce(sulfur, IronworksMaterials.blackPowder);
        TechNode casing = produce(powder, IronworksMaterials.casing);
        TechNode copperHead = produce(casing, IronworksMaterials.copperHead);
        TechNode leadHead = produce(casing, IronworksMaterials.leadHead);
        produce(casing, IronworksMaterials.titaniumHead);
        produce(casing, IronworksMaterials.alloyHead);

        TechNode ammo = produce(casing, IronworksAmmo.copperAmmo);
        produce(ammo, IronworksAmmo.heavyAmmo);
        produce(ammo, IronworksAmmo.leadCoreAmmo);
        produce(ammo, IronworksAmmo.tiPierceAmmo);
        produce(ammo, IronworksAmmo.tiOverpressureAmmo);
        produce(ammo, IronworksAmmo.corrosiveAmmo);
        produce(ammo, IronworksAmmo.napalmAmmo);

        // 大口径炮弹
        produce(ammo, IronworksShells.heShell);
        produce(ammo, IronworksShells.apShell);
        produce(ammo, IronworksShells.incendiaryShell);
        produce(ammo, IronworksShells.fragShell);

        // 外挂装甲块（材料线里，和钢板/钛板同级）
        produce(steelPlate, IronworksArmor.reactivePlate);
        produce(steelPlate, IronworksArmor.compositePlate);
        produce(steelPlate, IronworksArmor.nanitePlate);

        // ══════════════════ 二、建筑线 ══════════════════
        TechNode sep = node(core, IronworksBlocks.separator, with(Items.copper, 200, Items.lead, 100));
        TechNode roll = node(sep, IronworksIndustry.rollingMill, with(Items.copper, 300, Items.lead, 200, Items.titanium, 100));
        TechNode elec = node(sep, IronworksIndustry.electrolysisPlant,
            with(Items.titanium, 300, Items.silicon, 200, Items.metaglass, 150));
        node(elec, IronworksIndustry.airSeparator, with(Items.silicon, 300, Items.titanium, 200, Items.lead, 150));
        TechNode reducer = node(elec, IronworksBlocks.reducer, with(Items.titanium, 300, Items.metaglass, 200, Items.silicon, 150));
        node(reducer, IronworksIndustry.arcFurnace, with(Items.titanium, 500, Items.silicon, 400, Items.metaglass, 300));

        TechNode blast = node(sep, IronworksIndustry.blastFurnace, with(Items.copper, 400, Items.lead, 300, Items.graphite, 250));
        TechNode conv = node(blast, IronworksIndustry.converter, with(Items.titanium, 400, Items.silicon, 300, Items.metaglass, 200));
        node(conv, IronworksIndustry.caster, with(Items.copper, 300, Items.lead, 300, Items.titanium, 300, Items.silicon, 200));


        // 循环冷却液
        TechNode gforge = node(blast, IronworksCoolant.grapheneForge,
            with(Items.titanium, 400, Items.silicon, 250, Items.graphite, 300));
        node(gforge, IronworksCoolant.fluidizer,
            with(Items.titanium, 400, Items.silicon, 300, Items.metaglass, 250));
        node(gforge, IronworksCoolant.coolantPlant,
            with(Items.titanium, 400, Items.silicon, 300, Items.metaglass, 250, Items.lead, 200));

        node(sep, IronworksIndustry.desulfurizer, with(Items.titanium, 300, Items.metaglass, 200));

        // 酸 → 耐高温管道 → 石墨坩埚
        TechNode acid = node(sep, IronworksHighTemp.acidPlant,
            with(Items.titanium, 300, Items.silicon, 200, Items.metaglass, 150));
        TechNode hotPipe = node(acid, IronworksHighTemp.hotPipe,
            with(IronworksMaterials.siliconCarbide, 100, Items.graphite, 150, Items.silicon, 100));

        TechNode leach = node(acid, IronworksSilicon.preciousLeach,
            with(IronworksItems.steelPlate, 200, Items.silicon, 250, Items.metaglass, 200));
        TechNode chip = node(leach, IronworksSilicon.chipFab,
            with(IronworksItems.steelPlate, 400, Items.silicon, 500, Items.metaglass, 300, IronworksMaterials.ironPlate, 200));
        // 铝线已并入老机器：电解车间(溶出/焙烧/冰晶石/铝电解)、电弧炉(铝热/再生铝)、
        // 连铸机(铸铝锭/铸板)、轧机(铝板)、合金炉(铝合金/航空铝)，不再有铝专用方块。
        // 这里只补物品的产出节点。
        produce(iron, IronworksMaterials.slag);

        // 全域算力集群（0.60.0）
        TechNode clusterNode = node(chip, IronworksCompute.cluster,
            with(IronworksSilicon.quantumCore, 5, IronworksMaterials.siliconCarbide, 150,
                 IronworksItems.steelPlate, 600, Items.silicon, 800));
        node(clusterNode, IronworksCompute.quantum,
            with(IronworksSilicon.quantumCore, 2, IronworksMaterials.siliconCarbide, 40, Items.silicon, 120));
        node(clusterNode, IronworksCompute.advanced,
            with(IronworksSilicon.advancedChip, 4, IronworksItems.steelPlate, 60, Items.silicon, 80));
        TechNode basicNode = node(clusterNode, IronworksCompute.basic,
            with(IronworksSilicon.basicChip, 6, IronworksItems.steelPlate, 30, Items.silicon, 50));
        node(basicNode, IronworksCompute.micro,
            with(IronworksSilicon.basicChip, 2, Items.silicon, 20, Items.metaglass, 10));

        // RBMK 核电站（0.66.0.0）：反应堆 → 汽轮机
        TechNode rbmk = node(clusterNode, IronworksRBMK.reactor,
            with(IronworksMaterials.siliconCarbide, 120, IronworksItems.steelPlate, 400,
                 IronworksSilicon.quantumCore, 4, Items.silicon, 500));
        node(rbmk, IronworksRBMK.turbine,
            with(IronworksItems.steelPlate, 150, IronworksSilicon.quantumCore, 1,
                 Items.silicon, 200, Items.metaglass, 120));

        // 石油化工：炼油厂 → 聚合车间
        TechNode refinery = node(acid, IronworksPetro.refinery,
            with(IronworksItems.steelPlate, 250, Items.silicon, 300, Items.metaglass, 200,
                 IronworksMaterials.titaniumPlate, 80));
        node(refinery, IronworksPetro.polymerWorks,
            with(IronworksItems.steelPlate, 200, Items.silicon, 200, Items.titanium, 150));

        // ══════════════════ 原版材料闭环 ══════════════════
        TechNode siliconFurnace = node(sep, IronworksVanillaLoops.siliconFurnace,
            with(Items.copper, 150, Items.lead, 120, Items.silicon, 100));
        node(siliconFurnace, IronworksVanillaLoops.glassKiln,
            with(Items.copper, 120, Items.lead, 150, Items.graphite, 60));
        TechNode graphitizer = node(sep, IronworksVanillaLoops.graphitizer,
            with(Items.copper, 200, Items.graphite, 80, Items.silicon, 80));
        node(graphitizer, IronworksVanillaLoops.pyroWorks,
            with(Items.copper, 150, Items.lead, 150, Items.graphite, 80));
        node(sep, IronworksVanillaLoops.polymerizer,
            with(Items.titanium, 250, Items.silicon, 200, Items.metaglass, 150));
        node(sep, IronworksVanillaLoops.alloyFurnace,
            with(IronworksItems.steelPlate, 150, Items.silicon, 250, Items.titanium, 200));
        TechNode phaseWeaver = node(sep, IronworksVanillaLoops.phaseWeaver,
            with(Items.titanium, 300, Items.silicon, 300, Items.thorium, 150));
        node(chip, IronworksVanillaLoops.scrapWorks,
            with(IronworksItems.steelPlate, 200, Items.silicon, 250, Items.metaglass, 120));
        TechNode arcTower = node(chip, IronworksSilicon.arcTower,
            with(IronworksSilicon.basicChip, 20, IronworksItems.steelPlate, 300, Items.silicon, 400, Items.titanium, 300));
        // 天罚：四炮口升级版电弧塔
        node(arcTower, IronworksTianfa.tianfa,
            with(IronworksSilicon.advancedChip, 10, IronworksItems.steelPlate, 500,
                 Items.silicon, 400, IronworksAluminum.aviationAluminum, 40,
                 IronworksSilicon.titaniumAlloy, 60));
        node(chip, IronworksSilicon.railTower,
            with(IronworksSilicon.advancedChip, 30, IronworksItems.steelPlate, 500, Items.silicon, 600, IronworksSilicon.titaniumAlloy, 80));
        node(chip, IronworksSilicon.singularityTower,
            with(IronworksSilicon.quantumCore, 20, IronworksItems.steelPlate, 700, Items.silicon, 800, IronworksMaterials.siliconCarbide, 200));
        node(hotPipe, IronworksHighTemp.hotRouter,
            with(IronworksMaterials.siliconCarbide, 60, Items.graphite, 100));
        node(hotPipe, IronworksHighTemp.hotJunction,
            with(IronworksMaterials.siliconCarbide, 80, Items.graphite, 120));
        node(hotPipe, IronworksHighTemp.hotBridge,
            with(IronworksMaterials.siliconCarbide, 100, Items.graphite, 150));
        TechNode beltNode = node(hotPipe, IronworksBelts.belt,
            with(IronworksItems.steelPlate, 150, Items.silicon, 150));
        node(beltNode, IronworksBelts.router,
            with(IronworksItems.steelPlate, 100, Items.silicon, 100));
        node(beltNode, IronworksBelts.junction,
            with(IronworksItems.steelPlate, 100, Items.silicon, 100));
        node(beltNode, IronworksBelts.bridge,
            with(IronworksItems.steelPlate, 150, Items.silicon, 150));
        TechNode beltAdvNode = node(hotPipe, IronworksBelts.beltAdv,
            with(IronworksMaterials.siliconCarbide, 120, IronworksMaterials.graphiteElectrode, 60, Items.silicon, 200));
        node(beltAdvNode, IronworksBelts.routerAdv,
            with(IronworksMaterials.siliconCarbide, 100, IronworksMaterials.graphiteElectrode, 50));
        node(beltAdvNode, IronworksBelts.junctionAdv,
            with(IronworksMaterials.siliconCarbide, 100, IronworksMaterials.graphiteElectrode, 50));
        node(beltAdvNode, IronworksBelts.bridgeAdv,
            with(IronworksMaterials.siliconCarbide, 150, IronworksMaterials.graphiteElectrode, 80));
        node(hotPipe, IronworksHighTemp.coolantPipe,
            with(IronworksItems.steelPlate, 200, Items.silicon, 200, Items.metaglass, 150));
        node(hotPipe, IronworksHighTemp.crucible,
            with(IronworksMaterials.siliconCarbide, 300, IronworksMaterials.graphiteElectrode, 100, Items.graphite, 300));
        TechNode powderMixer = node(sep, IronworksIndustry.powderMixer, with(Items.copper, 250, Items.lead, 200, Items.graphite, 150));
        TechNode press = node(sep, IronworksAmmoLine.press,
            with(Items.copper, 300, Items.lead, 200, Items.titanium, 150));
        TechNode asm = node(press, IronworksAmmoLine.ammoAssembler,
            with(Items.titanium, 400, Items.silicon, 300, Items.metaglass, 200));
        node(asm, IronworksTurrets.rapidCannon, with(Items.copper, 400, Items.lead, 300, IronworksMaterials.copperPlate, 100));
        node(asm, IronworksTurrets.piercer, with(IronworksItems.steelPlate, 250, Items.titanium, 400, Items.silicon, 300));
        node(asm, IronworksBlocks.steelWall, with(IronworksItems.steelPlate, 100, Items.copper, 200));

        // 大口径炮弹组装室 + 迫击炮
        TechNode shellAsm = node(asm, IronworksShells.shellAssembly,
            with(Items.titanium, 400, Items.silicon, 300, Items.metaglass, 250, IronworksMaterials.ironPlate, 100));
        node(shellAsm, IronworksShells.mortar,
            with(IronworksItems.steelPlate, 300, IronworksMaterials.ironPlate, 250, Items.titanium, 400, Items.silicon, 300));

        node(sep, IronworksBarrels.barrelFiller, with(Items.copper, 250, Items.lead, 150, Items.titanium, 100));
        node(sep, IronworksBarrels.barrelEmptier, with(Items.copper, 250, Items.lead, 150));

        TechNode armorPress = node(sep, IronworksArmor.armorPress,
            with(Items.titanium, 400, Items.silicon, 300, Items.lead, 200));
        node(armorPress, IronworksArmor.armorDock,
            with(Items.silicon, 400, Items.titanium, 300, IronworksItems.steelPlate, 200));

        node(sep, IronworksSupport.nanoField,
            with(Items.lead, 300, Items.silicon, 300, Items.titanium, 300, IronworksItems.steelPlate, 150));
        node(sep, IronworksSupport.jadeBeacon,
            with(Items.silicon, 300, Items.titanium, 200, IronworksItems.steelPlate, 150));
        node(sep, IronworksSupport.criticalDevice,
            with(Items.silicon, 300, IronworksMaterials.pureCopper, 250, IronworksItems.steelPlate, 150));

        // ══════════════════ 三、行星标签 ══════════════════
        // 数据库按「行星标签」过滤内容：没有标签的物品/液体会在数据库里整页消失
        // （物品不会像方块那样自动从建造材料推断行星，所以必须手动打标签）。
        iron.addPlanet(Planets.serpulo);
        sep.addPlanet(Planets.serpulo);

        for(Liquid liquid : new Liquid[]{IronworksLiquids.oxygen, IronworksLiquids.moltenIron, IronworksLiquids.moltenSteel, IronworksLiquids.acid, IronworksLiquids.grapheneCoolant, IronworksLiquids.hotCoolant, IronworksLiquids.sodiumAluminate, IronworksLiquids.moltenAluminum}){
            liquid.shownPlanets.add(Planets.serpulo);
        }
        for(StatusEffect effect : new StatusEffect[]{IronworksStatus.corrosion, IronworksStatus.nanoArmor,
            IronworksStatus.jadeShatter, IronworksStatus.critical, IronworksStatus.reactiveArmor,
            IronworksStatus.compositeArmor, IronworksStatus.naniteArmor}){
            effect.shownPlanets.add(Planets.serpulo);
        }
    }
}
