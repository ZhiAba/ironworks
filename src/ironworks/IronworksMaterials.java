package ironworks;

import arc.graphics.Color;
import mindustry.type.Item;

/** 二级合成材料与弹药物料。 */
public class IronworksMaterials{

    public static Item copperPlate, leadPlate, pureCopper, pureLead;
    public static Item sulfur, blackPowder;
    public static Item casing, copperHead, leadHead, titaniumHead, alloyHead;
    public static Item castIron, ironPlate, graphene;
    public static Item titaniumPlate;
    public static Item waterBarrel, oilBarrel, moltenIronBarrel, moltenSteelBarrel,
        moltenCopperBarrel, moltenLeadBarrel, moltenTitaniumBarrel, moltenSurgeBarrel, moltenAluminumBarrel;
    public static Item slag;
    public static Item siliconCarbide, graphiteElectrode;

    public static void load(){
        // 板材：轧制产物
        copperPlate = new Item("copper-plate", Color.valueOf("c47846")){{ cost = 1.3f; hardness = 2; }};
        leadPlate   = new Item("lead-plate",   Color.valueOf("7a7e8c")){{ cost = 1.3f; hardness = 2; }};

        // 高纯金属：电解精炼产物
        pureCopper = new Item("pure-copper", Color.valueOf("e08c56")){{ cost = 1.8f; hardness = 3; }};
        pureLead   = new Item("pure-lead",   Color.valueOf("a4aaba")){{ cost = 1.8f; hardness = 3; }};

        // 火药原料与成品
        sulfur      = new Item("sulfur",       Color.valueOf("d6c456")){{ cost = 1.1f; }};
        blackPowder = new Item("black-powder", Color.valueOf("403c3a")){{ cost = 1.6f; explosiveness = 0.4f; flammability = 0.6f; }};

        // 弹药零件
        casing      = new Item("casing",       Color.valueOf("c69648")){{ cost = 1.4f; }};
        copperHead  = new Item("copper-head",  Color.valueOf("c87c4a")){{ cost = 1.2f; }};
        leadHead    = new Item("lead-head",    Color.valueOf("808492")){{ cost = 1.2f; hardness = 2; }};
        titaniumHead= new Item("titanium-head",Color.valueOf("92b0c8")){{ cost = 1.6f; hardness = 3; }};
        alloyHead   = new Item("alloy-head",   Color.valueOf("bace7c")){{ cost = 2.0f; hardness = 4; }};

        // 钛板：2 级合成材料（钛轧制而成），凝固汽油弹的载体材料
        titaniumPlate = new Item("titanium-plate", Color.valueOf("9fbdd4")){{ cost = 1.6f; hardness = 3; }};

        // 桶装液体
        waterBarrel = new Item("water-barrel", Color.valueOf("969ea9")){{ cost = 1.5f; }};
        oilBarrel   = new Item("oil-barrel",   Color.valueOf("78706a")){{ cost = 1.5f; }};
        moltenIronBarrel = new Item("molten-iron-barrel", Color.valueOf("ff8a3d")){{ cost = 2.6f; hardness = 3; }};
        moltenSteelBarrel= new Item("molten-steel-barrel",Color.valueOf("9fd8ff")){{ cost = 3.4f; hardness = 3; }};
        moltenCopperBarrel = new Item("molten-copper-barrel", Color.valueOf("ff9a4d")){{ cost = 2.8f; hardness = 3; }};
        moltenLeadBarrel   = new Item("molten-lead-barrel",   Color.valueOf("b9c2d4")){{ cost = 2.8f; hardness = 3; }};
        moltenTitaniumBarrel = new Item("molten-titanium-barrel", Color.valueOf("c8dcf0")){{ cost = 3.6f; hardness = 4; }};
        moltenSurgeBarrel  = new Item("molten-surge-barrel",  Color.valueOf("d6e77a")){{ cost = 4.0f; hardness = 4; }};
        moltenAluminumBarrel = new Item("molten-aluminum-barrel", Color.valueOf("dfe8f2")){{ cost = 3.0f; hardness = 3; }};
        // 炉渣：吹炼/熔炼副产物，废料分选厂可回收金属
        slag = new Item("slag", Color.valueOf("6b6560")){{ cost = 0.8f; }};
        castIron         = new Item("cast-iron",         Color.valueOf("9a9a96")){{ cost = 2.0f; }};
        ironPlate        = new Item("iron-plate",        Color.valueOf("a8aeb8")){{ cost = 1.4f; hardness = 2; }};
        graphene         = new Item("graphene",          Color.valueOf("6f8f88")){{ cost = 2.5f; hardness = 3; }};
        siliconCarbide   = new Item("silicon-carbide",   Color.valueOf("8f98a6")){{ cost = 3.2f; hardness = 3; }};
        graphiteElectrode= new Item("graphite-electrode",Color.valueOf("3d444a")){{ cost = 2.2f; hardness = 2; }};
    }
}
