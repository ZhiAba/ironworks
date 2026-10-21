package ironworks;

import arc.graphics.Color;
import mindustry.type.Item;

public class IronworksItems{

    public static Item ironConcentrate, ironIngot, steelIngot, steelPlate;

    public static void load(){
        // 铁精矿：磁选富集产物，还不是金属
        ironConcentrate = new Item("iron-concentrate", Color.valueOf("6f6a60")){{
            cost = 1.0f;
            hardness = 1;
        }};

        // 铁锭：氢还原得到海绵铁/生铁，含碳量高、脆
        ironIngot = new Item("iron-ingot", Color.valueOf("b9c2cd")){{
            cost = 1.2f;
            hardness = 2;
        }};

        // 钢锭：转炉脱碳后的精炼产物，比铁锭高一级
        steelIngot = new Item("steel-ingot", Color.valueOf("93a3b8")){{
            cost = 1.4f;
            hardness = 3;
        }};

        // 钢板：钢锭轧制加工而成
        steelPlate = new Item("steel-plate", Color.valueOf("7f93ad")){{
            cost = 1.5f;
            hardness = 3;
        }};
    }
}
