package ironworks;

import mindustry.content.Items;
import mindustry.type.Category;
import mindustry.world.Block;
import ironworks.multicraft.MultiCrafter;
import ironworks.multicraft.Recipe;

import static mindustry.type.ItemStack.with;

/** 弹药生产线。弹头压制机与弹药组装机使用自研 MultiCrafter。 */
public class IronworksAmmoLine{

    public static Block press, ammoAssembler;

    public static void load(){
        // 冲压机：弹壳与各种弹头都是"冲压成形"，合并成一台机器
        press = new MultiCrafter("press"){{
            requirements(Category.crafting,
                with(Items.copper, 50, Items.lead, 30, Items.titanium, 30, Items.graphite, 20));
            size = 2;
            itemCapacity = 30;
            recipes = new Recipe[]{
                new Recipe("casing", 30f)
                    .items(with(IronworksMaterials.copperPlate, 1))
                    .power(1f)
                    .output(with(IronworksMaterials.casing, 4)),
                new Recipe("copper-head", 30f)
                    .items(with(Items.copper, 1)).power(1f)
                    .output(with(IronworksMaterials.copperHead, 2)),
                new Recipe("lead-head", 30f)
                    .items(with(Items.lead, 1)).power(1f)
                    .output(with(IronworksMaterials.leadHead, 2)),
                new Recipe("titanium-head", 45f)
                    .items(with(Items.titanium, 1)).power(2f)
                    .output(with(IronworksMaterials.titaniumHead, 2)),
                new Recipe("alloy-head", 60f)
                    .items(with(Items.surgeAlloy, 1)).power(3f)
                    .output(with(IronworksMaterials.alloyHead, 2)),
                new Recipe("graphite-electrode", 70f)
                    .items(with(Items.graphite, 4)).power(3f)
                    .output(with(IronworksMaterials.graphiteElectrode, 1))
            };
        }};

        // 弹药组装机：7 配方（含凝固汽油弹灌装）
        ammoAssembler = new MultiCrafter("ammo-assembler"){{
            requirements(Category.crafting,
                with(Items.titanium, 120, Items.silicon, 80, Items.metaglass, 60));
            size = 3;
            itemCapacity = 40;
            recipes = new Recipe[]{
                new Recipe("copper-ammo", 45f)
                    .items(with(IronworksMaterials.casing, 1, Items.copper, 2,
                                IronworksMaterials.blackPowder, 1))
                    .power(2f)
                    .output(with(IronworksAmmo.copperAmmo, 6)),

                new Recipe("heavy-ammo", 75f)
                    .items(with(IronworksMaterials.casing, 1, IronworksMaterials.leadHead, 2,
                                IronworksMaterials.blackPowder, 2))
                    .power(2f)
                    .output(with(IronworksAmmo.heavyAmmo, 2)),

                new Recipe("lead-core-ammo", 75f)
                    .items(with(IronworksMaterials.casing, 1, IronworksMaterials.leadHead, 1,
                                IronworksMaterials.pureLead, 1, IronworksMaterials.blackPowder, 1))
                    .power(3f)
                    .output(with(IronworksAmmo.leadCoreAmmo, 2)),

                new Recipe("ti-pierce-ammo", 90f)
                    .items(with(IronworksMaterials.casing, 1, IronworksMaterials.titaniumHead, 1,
                                IronworksMaterials.blackPowder, 1))
                    .power(3f)
                    .output(with(IronworksAmmo.tiPierceAmmo, 1)),

                new Recipe("ti-overpressure-ammo", 120f)
                    .items(with(IronworksMaterials.casing, 1, IronworksMaterials.alloyHead, 1,
                                IronworksMaterials.blackPowder, 2))
                    .power(4f)
                    .output(with(IronworksAmmo.tiOverpressureAmmo, 1)),

                new Recipe("corrosive-ammo", 100f)
                    .items(with(IronworksMaterials.casing, 1, IronworksMaterials.titaniumPlate, 1))
                    .liquids(new mindustry.type.LiquidStack(IronworksLiquids.acid, 4f))
                    .power(3f)
                    .output(with(IronworksAmmo.corrosiveAmmo, 3)),

                // 灌装：石油桶的下游。桶类属 2 级材料，可作为原料继续加工
                new Recipe("napalm-ammo", 90f)
                    .items(with(IronworksMaterials.oilBarrel, 1, IronworksMaterials.titaniumPlate, 1, Items.silicon, 1))
                    .power(2f)
                    .output(with(IronworksAmmo.napalmAmmo, 2))
            };
        }};
    }
}
