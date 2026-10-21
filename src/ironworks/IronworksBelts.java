package ironworks;

import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.world.Block;

import static mindustry.type.ItemStack.with;

/**
 * 通液传送带全套（基础 / 高级两档 + 路由器 / 交叉器 / 带桥）。
 *
 * 基础：只收水，9.5 物品/秒，带水 1.15×。
 * 高级：任意冷却液（水 1.15 / 冷冻液 1.25 / 石墨烯冷却液 1.55），16.5 物品/秒。
 * 全部受超速器影响（传送逻辑走 edelta()，含 timeScale）。
 */
public class IronworksBelts{

    public static Block belt, router, junction, bridge;
    public static Block beltAdv, routerAdv, junctionAdv, bridgeAdv;

    public static void load(){
        // ── 基础档：玻璃 1 / 石墨 1 / 铁板 1（路由与交叉器 ×4，带桥 ×6）──
        belt = new LiquidConveyor("liquid-conveyor"){{
            requirements(Category.distribution,
                with(Items.metaglass, 1, Items.graphite, 1, IronworksMaterials.ironPlate, 1));
            speed = 9.5f / 150f;
            displayedSpeed = 9.5f;
            liquidCapacity = 10f;
            liquidStep = 20f;
            anyCoolant = false;
        }};

        router = new LiquidBeltRouter("liquid-belt-router"){{
            requirements(Category.distribution,
                with(Items.metaglass, 4, Items.graphite, 4, IronworksMaterials.ironPlate, 4));
            liquidCapacity = 10f;
            liquidStep = 20f;
            anyCoolant = false;
        }};

        junction = new LiquidBeltJunction("liquid-belt-junction"){{
            requirements(Category.distribution,
                with(Items.metaglass, 4, Items.graphite, 4, IronworksMaterials.ironPlate, 4));
            liquidCapacity = 10f;
            liquidStep = 20f;
            anyCoolant = false;
        }};

        bridge = new LiquidBeltBridge("liquid-belt-bridge"){{
            requirements(Category.distribution,
                with(Items.metaglass, 6, Items.graphite, 6, IronworksMaterials.ironPlate, 6));
            range = 6;
            liquidCapacity = 10f;
            anyCoolant = false;
        }};

        // ── 高级档：碳化硅 1 / 石墨电极 1 / 石墨烯 1 / 钢板 2 ──
        beltAdv = new LiquidConveyor("liquid-conveyor-adv"){{
            requirements(Category.distribution,
                with(IronworksMaterials.siliconCarbide, 1, IronworksMaterials.graphiteElectrode, 1,
                     IronworksMaterials.graphene, 1, IronworksItems.steelPlate, 2));
            speed = 16.5f / 150f;
            displayedSpeed = 16.5f;
            liquidCapacity = 16f;
            liquidStep = 12f;
            anyCoolant = true;
        }};

        routerAdv = new LiquidBeltRouter("liquid-belt-router-adv"){{
            requirements(Category.distribution,
                with(IronworksMaterials.siliconCarbide, 4, IronworksMaterials.graphiteElectrode, 4,
                     IronworksMaterials.graphene, 4, IronworksItems.steelPlate, 8));
            liquidCapacity = 16f;
            liquidStep = 12f;
            anyCoolant = true;
        }};

        junctionAdv = new LiquidBeltJunction("liquid-belt-junction-adv"){{
            requirements(Category.distribution,
                with(IronworksMaterials.siliconCarbide, 4, IronworksMaterials.graphiteElectrode, 4,
                     IronworksMaterials.graphene, 4, IronworksItems.steelPlate, 8));
            liquidCapacity = 16f;
            liquidStep = 12f;
            anyCoolant = true;
        }};

        bridgeAdv = new LiquidBeltBridge("liquid-belt-bridge-adv"){{
            requirements(Category.distribution,
                with(IronworksMaterials.siliconCarbide, 6, IronworksMaterials.graphiteElectrode, 6,
                     IronworksMaterials.graphene, 6, IronworksItems.steelPlate, 12));
            range = 8;
            liquidCapacity = 16f;
            anyCoolant = true;
            liquidRate = 0.8f;
        }};
    }
}
