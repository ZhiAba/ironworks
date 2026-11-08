package ironworks;

import arc.Events;
import arc.util.Log;
import mindustry.Vars;
import mindustry.game.EventType;

/**
 * 把原版战役星球界面的 CampaignRulesDialog 替换成铁流工业 T0-T7 页面。
 * 无头端不会创建 UI，也不会加载客户端对话框。
 */
public final class IronworksCampaignDifficulty{
    private static boolean installed;

    private IronworksCampaignDifficulty(){
    }

    public static void init(){
        Events.on(EventType.ClientLoadEvent.class, e -> install());
        // 某些客户端在 mod init 时 UI 还没创建、也不保证 ClientLoadEvent 已到；用 update 兜底。
        Events.run(EventType.Trigger.update, IronworksCampaignDifficulty::install);
        if(Vars.ui != null && !Vars.headless) install();
    }

    private static void install(){
        if(installed || Vars.headless || Vars.ui == null) return;
        try{
            Vars.ui.campaignRules = new IronworksCampaignRulesDialog();
            Log.info("[IRONWORKS] campaign difficulty page installed");
        }catch(Throwable t){
            Log.err("IronworksCampaignDifficulty: 安装战役难度页失败", t);
        }
        try{
            // 同一个子类同时负责属性伤害着色和难度实时属性；不要再让两个
            // ContentInfoDialog 子类互相覆盖（后安装的会丢掉前者的功能）。
            if(!(Vars.ui.content instanceof IronworksDamageUI)){
                Vars.ui.content = new IronworksDamageUI();
            }
            Log.info("[IRONWORKS] content detail UI installed (damage + difficulty stats)");
        }catch(Throwable t){
            Log.err("IronworksCampaignDifficulty: 安装建筑/单位详情 UI 失败", t);
        }
        installed = true;
    }
}
