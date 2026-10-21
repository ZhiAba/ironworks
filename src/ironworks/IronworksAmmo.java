package ironworks;

import arc.graphics.Color;
import mindustry.type.Item;

/**
 * 弹药大类：只被炮塔消耗，不参与任何合成配方。
 * 这里只做物品定义，弹道参数在炮塔的 ammoTypes 里绑定。
 */
public class IronworksAmmo{

    public static Item copperAmmo, heavyAmmo, leadCoreAmmo, tiPierceAmmo, tiOverpressureAmmo, napalmAmmo, corrosiveAmmo;

    public static void load(){
        copperAmmo        = new Item("copper-ammo",        Color.valueOf("c87c4a")){{ cost = 1.5f; }};
        heavyAmmo         = new Item("heavy-ammo",         Color.valueOf("787c84")){{ cost = 2.0f; }};
        leadCoreAmmo      = new Item("lead-core-ammo",     Color.valueOf("808492")){{ cost = 2.0f; }};
        tiPierceAmmo      = new Item("ti-pierce-ammo",     Color.valueOf("92b0c8")){{ cost = 2.5f; }};
        tiOverpressureAmmo= new Item("ti-overpressure-ammo",Color.valueOf("d2be8c")){{ cost = 3.0f; }};
        napalmAmmo        = new Item("napalm-ammo",         Color.valueOf("ff8a3c")){{ cost = 3.0f; }};
        corrosiveAmmo     = new Item("corrosive-ammo",      Color.valueOf("8fbf5a")){{ cost = 3.0f; }};
    }
}
