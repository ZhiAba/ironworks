package ironworks;

import arc.Core;
import arc.graphics.Color;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.content.Fx;
import mindustry.content.Items;
import mindustry.entities.Units;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.type.Category;
import mindustry.type.Item;
import mindustry.type.StatusEffect;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatUnit;

import static mindustry.type.ItemStack.with;

/**
 * 单位外挂装甲。
 *
 * 设计：装甲是**独立于单位**的一件装备，由装甲坞装到单位身上，装上后：
 *   1. 叠加一层护甲值（走 statusArmor()，即动态状态的 armorOverride）；
 *   2. 附带该装甲专属的状态效果。
 * 一个单位同时只能穿一件外挂装甲（靠状态互斥判断），装上后不会自然脱落。
 */
public class IronworksArmor{

    public static Item reactivePlate, compositePlate, nanitePlate;
    public static Block armorPress, armorDock;

    /** 一种外挂装甲：消耗的物品 / 附带的护甲 / 附带的状态。 */
    public static class ArmorType{
        public final Item item;
        public final StatusEffect status;
        public final float armorBonus;

        public ArmorType(Item item, StatusEffect status, float armorBonus){
            this.item = item;
            this.status = status;
            this.armorBonus = armorBonus;
        }
    }

    /** 装甲坞：把装甲块装到范围内的友军单位上。 */
    public static class ArmorDock extends Block{
        public ArmorType[] armors = {};
        /** 作用范围（世界单位）。 */
        public float range = 72f;
        /** 电费结算周期（tick）。 */
        public float useTime = 60f;

        public ArmorDock(String name){
            super(name);
            update = true;
            solid = true;
            hasPower = true;
            hasItems = true;
            itemCapacity = 12;
            configurable = true;
            saveConfig = true;
            sync = true;
        }

        @Override
        public void setStats(){
            super.setStats();
            stats.add(Stat.range, range / Vars.tilesize, StatUnit.blocks);
        }

        public class ArmorDockBuild extends Building{
            public int armorIndex;
            protected float useProgress;
            /** 测试用：累计装上去的件数。 */
            public int equipped;

            public ArmorType type(){
                return armors[arc.math.Mathf.clamp(armorIndex, 0, armors.length - 1)];
            }

            public boolean hasArmor(Unit unit){
                for(ArmorType t : armors){
                    if(unit.hasEffect(t.status)) return true;
                }
                return false;
            }

            @Override
            public void updateTile(){
                if(efficiency <= 0f) return;

                useProgress += delta();
                if(useProgress >= useTime){
                    useProgress %= useTime;
                    consume();
                }

                ArmorType t = type();
                if(items.get(t.item) < 1) return;

                Units.nearby(team, x, y, range, u -> equip(u, t));
            }

            protected void equip(Unit u, ArmorType t){
                if(u.dead || !u.isValid() || hasArmor(u)) return;
                if(items.get(t.item) < 1) return;

                items.remove(t.item, 1);
                u.apply(t.status, Float.POSITIVE_INFINITY);
                // 独立护甲层：在单位自身护甲上叠加，而不是替换
                u.statusArmor(Math.max(0f, u.armor()) + t.armorBonus);
                equipped++;

                if(!Vars.headless) Fx.shieldBreak.at(u.x, u.y);
            }

            @Override
            public boolean acceptItem(Building source, Item item){
                if(items.get(item) >= itemCapacity) return false;
                for(ArmorType t : armors){
                    if(t.item == item) return true;
                }
                return false;
            }

            @Override
            public void buildConfiguration(Table table){
                table.defaults().pad(3f);
                for(int i = 0; i < armors.length; i++){
                    int idx = i;
                    ArmorType t = armors[i];
                    TextButton b = new TextButton(t.item.localizedName, Styles.flatTogglet);
                    b.clicked(() -> configure(idx));
                    b.update(() -> b.setChecked(armorIndex == idx));
                    table.add(b).size(160f, 40f).padRight(6f);
                }
            }

            @Override
            public Object config(){
                return armorIndex;
            }

            @Override
            public byte version(){
                return 1;
            }

            @Override
            public void write(arc.util.io.Writes write){
                super.write(write);
                write.i(armorIndex);
            }

            @Override
            public void read(arc.util.io.Reads read, byte revision){
                super.read(read, revision);
                if(revision >= 1){
                    armorIndex = arc.math.Mathf.clamp(read.i(), 0, Math.max(armors.length - 1, 0));
                }
            }
        }
    }

    public static void load(){
        // ── 装甲块（装备类物品，不是合成原料）──
        reactivePlate  = new Item("reactive-plate",  Color.valueOf("9aa86f")){{ cost = 1.5f; }};
        compositePlate = new Item("composite-plate", Color.valueOf("c8b48a")){{ cost = 2.0f; }};
        nanitePlate    = new Item("nanite-plate",    Color.valueOf("62d6c0")){{ cost = 2.5f; }};

        // ── 装甲压制机：三种装甲块 ──
        armorPress = new ironworks.multicraft.MultiCrafter("armor-press"){{
            requirements(Category.crafting,
                with(Items.titanium, 70, Items.silicon, 40, Items.lead, 40, IronworksMaterials.castIron, 25));
            size = 2;
            itemCapacity = 20;
            recipes = new ironworks.multicraft.Recipe[]{
                new ironworks.multicraft.Recipe("reactive-plate", 90f)
                    .items(with(IronworksItems.steelPlate, 2, IronworksMaterials.leadPlate, 1))
                    .power(2f)
                    .output(with(reactivePlate, 1)),
                new ironworks.multicraft.Recipe("composite-plate", 110f)
                    .items(with(IronworksMaterials.titaniumPlate, 1, IronworksItems.steelPlate, 1, IronworksMaterials.leadPlate, 1))
                    .power(3f)
                    .output(with(compositePlate, 1)),
                new ironworks.multicraft.Recipe("nanite-plate", 110f)
                    .items(with(IronworksItems.steelPlate, 1, Items.silicon, 1, Items.copper, 1))
                    .power(3f)
                    .output(with(nanitePlate, 1))
            };
        }};

        // ── 外挂装甲坞 ──
        armorDock = new ArmorDock("armor-dock"){{
            requirements(Category.effect,
                with(Items.silicon, 90, Items.titanium, 60, IronworksItems.steelPlate, 40, IronworksMaterials.castIron, 35));
            size = 3;
            range = 72f;
            itemCapacity = 12;
            consumePower(6f);
            config(Integer.class, (ArmorDockBuild build, Integer index) -> {
                build.armorIndex = arc.math.Mathf.clamp(index, 0, 2);
            });
            configClear((ArmorDockBuild build) -> build.armorIndex = 0);

            armors = new ArmorType[]{
                new ArmorType(reactivePlate,  IronworksStatus.reactiveArmor,  8f),
                new ArmorType(compositePlate, IronworksStatus.compositeArmor, 6f),
                new ArmorType(nanitePlate,    IronworksStatus.naniteArmor,    3f)
            };
        }};
    }
}
