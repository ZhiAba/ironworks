package ironworks;

import arc.graphics.Color;
import mindustry.type.Item;

/**
 * 铝全量：只保留物品定义。
 *
 * 原来自带 5 台铝专用厂（溶出罐/焙烧炉/电解槽/铝热炉/铝厂），
 * 和已有机器功能重复，现已删除，配方并入老机器：
 * - 溶出 / 焙烧 / 电解铝 → 电解车间（electrolysis-plant）
 * - 铝热炼铁 / 再生铝合金 → 电弧炉（arc-furnace）
 * - 铸造铝锭 / 应急烧结 → 连铸机（caster）
 * - 轧制铝板 → 轧机（rolling-mill）
 * - 铝合金 / 航空铝 → 合金炉（alloy-furnace）
 */
public class IronworksAluminum{

    public static Item bauxite, alumina, cryolite, redMud;
    public static Item aluminum, aluminumSheet, aluminumAlloy, aviationAluminum;

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
    }
}
