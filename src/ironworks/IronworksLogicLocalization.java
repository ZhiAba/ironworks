package ironworks;

import arc.Core;

/**
 * 逻辑块编辑界面汉化开关（纯显示层）。
 *
 * <p>Mindustry 160.x 的逻辑编辑器本身支持 {@code logiclocalization} 设置：
 * 它只把指令标题、分类和枚举名称交给 bundle 翻译，玩家输入的变量名、
 * 关键字、内容引用和存档里的逻辑文本都不参与替换。这里只负责：</p>
 * <ul>
 *   <li>首次运行时按游戏语言决定默认值（中文默认开，其它语言默认关）；</li>
 *   <li>把玩家选择持久化到 {@link #KEY}，之后不再被默认值覆盖；</li>
 *   <li>把选择同步到原版的 {@code logiclocalization} 显示开关。</li>
 * </ul>
 *
 * <p>不修改 LStatement/LExecutor，也不改写任何逻辑存储内容。</p>
 */
public final class IronworksLogicLocalization{
    public static final String KEY = "ironworks.logic-localization";

    private IronworksLogicLocalization(){
    }

    public static void init(){
        if(Core.settings == null) return;

        boolean chinese = isChinese();
        // 只在第一次安装/首次运行时写默认值；玩家之后关掉不会被重新打开。
        if(!Core.settings.has(KEY)){
            Core.settings.put(KEY, chinese);
        }
        apply(Core.settings.getBool(KEY, chinese));
    }

    public static boolean enabled(){
        return Core.settings != null && Core.settings.getBool(KEY, isChinese());
    }

    public static void setEnabled(boolean enabled){
        if(Core.settings == null) return;
        Core.settings.put(KEY, enabled);
        apply(enabled);
    }

    /** 真正生效的只有原版显示开关，不触碰逻辑内容。 */
    private static void apply(boolean enabled){
        if(Core.settings != null) Core.settings.put("logiclocalization", enabled);
    }

    private static boolean isChinese(){
        try{
            String lang = Core.bundle == null ? null : Core.bundle.getLocale().getLanguage();
            return lang != null && (lang.startsWith("zh"));
        }catch(Throwable ignored){
            return false;
        }
    }
}
