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
        IronworksPetro.load();     // 石油化工（先于冷却厂：冷冻液配方要用塑料）
        IronworksCoolant.load();   // 循环冷却液（石墨烯 / 冷液 / 热液 / 冷却工厂）
        IronworksHighTemp.load();  // 酸、碳化硅、耐高温管道、石墨坩埚
        IronworksVanillaLoops.load(); // 原版材料闭环（硅/玻璃/石墨/塑钢/相织/合金/火工/废料回收）
        IronworksSilicon.load();   // 硅基时代：贵金属 / 钛合金 / 三级芯片 / 电弧塔
        IronworksTianfa.load();   // 天罚（四炮口电弧塔，依赖硅基时代物品）
        IronworksCompute.load();  // 全域算力集群 + 算力模块
        IronworksNuclear.load();  // 核工业全套（铀矿→黄饼→浓缩铀→燃料棒→乏燃料→后处理→固化）
        IronworksRBMK.load();     // RBMK 核电站（反应堆 + 汽轮机）
        IronworksTeleport.load(); // MCT 跃迁节点（物品/液体）
        IronworksBelts.load();     // 通液传送带全套（带 / 路由器 / 带桥 ×2 档）
        IronworksDebug.load();     // 调试：液体计量器
        IronworksSupport.load();   // 战术支援方块（状态光环形）
        IronworksRadar.load();     // 自动雷达（3×3，索敌 + 接管炮塔）
        IronworksBlocks.load();    // 方块要引用上面所有
        IronworksOre.load();       // 铁矿脉 + 塞普罗生成器补丁
        IronworksTech.load();

        // 普通管道（钢化玻璃）拒收熔融金属，必须用耐高温管道。
        // 放在 loadContent 末尾：Conduit.init() 只在 buildType 为 null 时才自行推断，
        // 这里先设好，init 就不会覆盖。
        IronworksHighTemp.patchVanillaPipes();

        // 属性伤害：解析状态、注册归属、把原版水/冰/雷/能量弹体换成带属性的同类。
        // 必须放在所有内容创建之后，供详情页和运行时查询。
        Dmg.load();
    }

    /**
     * 客户端功能（内嵌自 utility-pack：方块搜索 + 多人暂停 + 设置分类）。
     * 无头服务端上各自内部会 early-return，不会碰 UI。
     */
    @Override
    public void init(){
        IronworksBlockSearch.init(); // 建造菜单里的方块搜索（含拼音）
        IronworksPause.init();       // 多人暂停：网络包 + !pause 命令 + 暂停键请求
        IronworksRbmkChat.init();    // 彩蛋：聊天关键词按顺序说出 → 全图工作中的反应堆熔毁
        IronworksNotifications.init(); // 左侧日志数据源
        IronworksLogPanel.init();      // 动画日志面板
        IronworksNotificationPopups.init(); // 新警告弹出条
        IronworksSidebar.init();      // 左侧长条：点击打开日志
        IronworksHud.init();          // 辅助 HUD：电网总览 + 物流监视
        IronworksSettings.init();    // 设置 → 铁流工业
        IronworksDamageUI.init();    // 详情页弹药属性着色
    }
}
