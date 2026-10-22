package ironworks;

import arc.graphics.Color;
import mindustry.content.StatusEffects;
import mindustry.type.Liquid;

/**
 * 自定义液体。氢气直接复用原版 Liquids.hydrogen（避免同名重复内容），
 * 原版没有氧气与熔融金属，这里补上。
 */
public class IronworksLiquids{

    public static Liquid oxygen, moltenIron, moltenSteel, grapheneCoolant, hotCoolant, acid,
        sodiumAluminate, moltenAluminum, moltenCopper, moltenLead, moltenTitanium, moltenSurge, gasoline, heavyOil, chiller;

    public static void load(){
        oxygen = new Liquid("oxygen", Color.valueOf("8fd4ff")){{
            gas = true;
            heatCapacity = 0.7f;
            flammability = 0f;
            explosiveness = 0f;
        }};

        // 铁水：高炉出铁口出来的熔融生铁（约 1400℃）
        moltenIron = new Liquid("molten-iron", Color.valueOf("ff6a2b")){{
            temperature = 1f;
            viscosity = 0.8f;
            heatCapacity = 1f;
            effect = StatusEffects.melting;
            lightColor = Color.valueOf("f0511d").a(0.35f);
        }};

        // 钢水：转炉/电弧炉出钢后待连铸的熔钢
        moltenSteel = new Liquid("molten-steel", Color.valueOf("ffc247")){{
            temperature = 1f;
            viscosity = 0.75f;
            heatCapacity = 1f;
            effect = StatusEffects.melting;
            lightColor = Color.valueOf("ffd76a").a(0.4f);
        }};

        // 酸：基础化工液体，腐蚀弹与石墨烯的原料
        acid = new Liquid("acid", Color.valueOf("b6ff6a")){{
            temperature = 0.35f;
            heatCapacity = 0.9f;
            viscosity = 0.7f;
            flammability = 0f;
            effect = StatusEffects.corroded;
        }};

        // 石墨烯冷却液（冷液）：循环的起点
        grapheneCoolant = new Liquid("graphene-coolant", Color.valueOf("4fd6c0")){{
            temperature = 0.2f;
            heatCapacity = 1.6f;
            viscosity = 0.55f;
            flammability = 0f;
            // 关掉原版"冷热液体相遇反应"：引擎会在热液试图进入冷液管道时
            // 每 tick 销毁 0.7*delta 的液体，闭环里看起来就是液体凭空蒸发。
            blockReactive = false;
        }};

        // 铝酸钠溶液：拜耳法溶出铝土矿得到的中间液，焙烧后回收酸、留下氧化铝
        sodiumAluminate = new Liquid("sodium-aluminate", Color.valueOf("e8d38a")){{
            temperature = 0.45f;
            heatCapacity = 1.1f;
            viscosity = 0.85f;
            flammability = 0f;
            blockReactive = false; // 循环液：禁止冷热相遇销毁，避免闭环里"蒸发"
        }};

        // 熔融铝：霍尔-埃鲁电解槽出来的液态铝，送去铸造
        moltenAluminum = new Liquid("molten-aluminum", Color.valueOf("dfe8f2")){{
            temperature = 1f;
            viscosity = 0.6f;
            heatCapacity = 1f;
            effect = StatusEffects.melting;
            lightColor = Color.valueOf("cfe3ff").a(0.35f);
            blockReactive = false;
        }};

        // 熔融铜：铜熔炼炉出来的液态铜，铸造或合金用
        moltenCopper = new Liquid("molten-copper", Color.valueOf("ff9a4d")){{
            temperature = 1f; viscosity = 0.7f; heatCapacity = 1f;
            effect = StatusEffects.melting;
            lightColor = Color.valueOf("ff7a2e").a(0.3f);
            blockReactive = false;
        }};

        // 熔融铅
        moltenLead = new Liquid("molten-lead", Color.valueOf("b9c2d4")){{
            temperature = 0.95f; viscosity = 0.75f; heatCapacity = 1f;
            effect = StatusEffects.melting;
            blockReactive = false;
        }};

        // 熔融钛：熔点高，需要电弧炉
        moltenTitanium = new Liquid("molten-titanium", Color.valueOf("c8dcf0")){{
            temperature = 1f; viscosity = 0.65f; heatCapacity = 1.2f;
            effect = StatusEffects.melting;
            lightColor = Color.valueOf("cfe6ff").a(0.3f);
            blockReactive = false;
        }};

        // 巨浪合金熔体：铜铅钛硅四元合金
        moltenSurge = new Liquid("molten-surge", Color.valueOf("d6e77a")){{
            temperature = 1f; viscosity = 0.6f; heatCapacity = 1.2f;
            effect = StatusEffects.melting;
            lightColor = Color.valueOf("e8ff8a").a(0.35f);
            blockReactive = false;
        }};

        // 汽油：原油分馏的轻馏分，凝固汽油弹的提纯来源
        gasoline = new Liquid("gasoline", Color.valueOf("ffd166")){{
            temperature = 0.4f;
            viscosity = 0.35f;
            flammability = 1f;
            explosiveness = 0.25f;
            heatCapacity = 0.9f;
            blockReactive = false;
        }};

        // 重油：分馏塔底的渣油，去焦化或裂化
        heavyOil = new Liquid("heavy-oil", Color.valueOf("4a4340")){{
            temperature = 0.5f;
            viscosity = 0.95f;
            flammability = 0.55f;
            heatCapacity = 1.1f;
            blockReactive = false;
        }};

        // 冷冻液：水+塑料配的防冻液，天罚炮塔的最低冷却要求（不进水）
        chiller = new Liquid("chiller", Color.valueOf("7fd4ff")){{
            temperature = 0.12f;
            heatCapacity = 1.3f;
            viscosity = 0.5f;
            flammability = 0f;
            blockReactive = false;
        }};

        // 热冷却液（热液）：设备吸热后的状态
        hotCoolant = new Liquid("hot-coolant", Color.valueOf("ff8a5c")){{
            temperature = 0.95f;
            heatCapacity = 1.6f;
            viscosity = 0.55f;
            flammability = 0f;
            blockReactive = false; // 同上：避免和冷液在管道里"反应"蒸发
            effect = StatusEffects.melting;
        }};
    }
}
