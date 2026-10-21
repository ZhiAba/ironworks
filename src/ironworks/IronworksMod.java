package ironworks;

import mindustry.mod.Mod;

public class IronworksMod extends Mod{

    @Override
    public void loadContent(){
        IronworksLiquids.load();   // 液体先建
        IronworksItems.load();     // 钢铁线
        IronworksMaterials.load(); // 二级材料与弹药物料
        IronworksAluminum.load(); // 铝全量物品（工厂已并入老机器）
        IronworksStatus.load();    // 状态效果（被弹药与支援方块引用）
        IronworksAmmo.load();      // 弹药
        IronworksArmor.load();     // 外挂装甲（物品 + 压制机 + 装甲坞）
        IronworksIndustry.load(); // 二级材料工厂
        IronworksAmmoLine.load(); // 弹药生产线（自研多配方）
        IronworksBarrels.load();   // 桶装液体物流
        IronworksTurrets.load();   // 炮塔（弹药消费端）
        IronworksShells.load();    // 大口径炮弹 + 炮弹组装室 + 迫击炮
        IronworksCoolant.load();   // 循环冷却液（石墨烯 / 冷液 / 热液 / 冷却工厂）
        IronworksHighTemp.load();  // 酸、碳化硅、耐高温管道、石墨坩埚
        IronworksVanillaLoops.load(); // 原版材料闭环（硅/玻璃/石墨/塑钢/相织/合金/火工/废料回收）
        IronworksSilicon.load();   // 硅基时代：贵金属 / 钛合金 / 三级芯片 / 电弧塔
        IronworksBelts.load();     // 通液传送带全套（带 / 路由器 / 带桥 ×2 档）
        IronworksDebug.load();     // 调试：液体计量器
        IronworksSupport.load();   // 战术支援方块（状态光环形）
        IronworksBlocks.load();    // 方块要引用上面所有
        IronworksOre.load();       // 铁矿脉 + 塞普罗生成器补丁
        IronworksTech.load();

        // 普通管道（钢化玻璃）拒收熔融金属，必须用耐高温管道。
        // 放在 loadContent 末尾：Conduit.init() 只在 buildType 为 null 时才自行推断，
        // 这里先设好，init 就不会覆盖。
        IronworksHighTemp.patchVanillaPipes();
    }
}
