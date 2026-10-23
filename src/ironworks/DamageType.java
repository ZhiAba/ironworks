package ironworks;

import arc.Core;
import arc.graphics.Color;
import mindustry.type.StatusEffect;

/**
 * 属性伤害定义（v2.0.0.0）。
 *
 * 一个属性 = 一套统一的结算规则：
 *  - armorMult：护甲倍率。1 = 正常护甲，0 = 完全无视护甲，0.5 = 只吃一半护甲。
 *  - status：命中附加的状态（必中，statusChance 目前都是 1）。
 *  - bonusStatus / bonusMult：目标身上有某种状态时，伤害倍率。
 *
 * 以后加新属性只在这里加一行，再在 Dmg.load() 里注册归属即可。
 * 颜色是详情页文字用的专属色号，不要在这里做任何绘制。
 */
public enum DamageType{
    //      id         中文  色号      元素?  护甲倍率 状态        持续    概率  克制状态   倍率
    physical("physical", "物理", "c8c8c8", false, 1.00f, null,       0f,   1f,  null,      1.00f),
    thunder ("thunder",  "雷霆", "b6a8ff", true,  0.50f, "shocked",  90f,  1f,  "wet",     1.50f),
    water   ("water",    "水",   "5aa9ff", true,  0.90f, "wet",      240f, 1f,  null,      1.00f),
    ice     ("ice",      "冰",   "9fe8ff", true,  0.70f, "freezing", 120f, 1f,  "wet",     1.25f),
    energy  ("energy",   "能量", "d370d3", true,  0.35f, "electrified", 60f, 1f, "shocked", 1.25f),
    fire    ("fire",     "火",   "ff8a3c", true,  0.80f, "burning",  240f, 1f,  null,      1.00f),
    corrosive("corrosive","腐蚀","8fbf5a", true,  0.60f, "corrosion",300f, 1f,  null,      1.00f);

    public final String id;
    /** 中文名，作为 bundle 缺失时的兜底。 */
    public final String cn;
    public final String hex;
    public final boolean elemental;
    public final float armorMult;
    public final String statusName;
    public final float statusDuration;
    public final float statusChance;
    public final String bonusStatusName;
    public final float bonusMult;

    /** 由 Dmg.load() 在内容加载完成后解析，避免枚举初始化时碰 StatusEffects。 */
    public StatusEffect status, bonusStatus;

    DamageType(String id, String cn, String hex, boolean elemental, float armorMult,
               String statusName, float statusDuration, float statusChance,
               String bonusStatusName, float bonusMult){
        this.id = id;
        this.cn = cn;
        this.hex = hex;
        this.elemental = elemental;
        this.armorMult = armorMult;
        this.statusName = statusName;
        this.statusDuration = statusDuration;
        this.statusChance = statusChance;
        this.bonusStatusName = bonusStatusName;
        this.bonusMult = bonusMult;
    }

    public Color color(){
        return Color.valueOf(hex);
    }

    public String localizedName(){
        return bundle("damage-type." + id + ".name", cn);
    }

    /** 详情页用：带颜色的属性名，末尾已经闭合颜色标记，后面的文字会回到默认色。 */
    public String coloredName(){
        return "[#" + hex + "]" + localizedName() + "[]";
    }

    /** 中文直接拼接（水元素伤害），拉丁文字需要空格（Water element damage）。 */
    public String separator(){
        try{
            String lang = Core.bundle == null ? "zh" : Core.bundle.getLocale().getLanguage();
            return lang != null && (lang.startsWith("zh") || lang.startsWith("ja") || lang.startsWith("ko")) ? "" : " ";
        }catch(Throwable ignored){
            return "";
        }
    }

    public String damageSuffix(){
        return bundle(elemental ? "damage-type.element-suffix" : "damage-type.physical-suffix",
            elemental ? "元素伤害" : "伤害");
    }

    public String areaSuffix(){
        return bundle(elemental ? "damage-type.element-area" : "damage-type.physical-area",
            elemental ? "元素范围伤害" : "范围伤害");
    }

    public static DamageType byId(String id){
        for(DamageType t : values()){
            if(t.id.equals(id)) return t;
        }
        return physical;
    }

    static String bundle(String key, String def){
        try{
            return Core.bundle == null ? def : Core.bundle.get(key, def);
        }catch(Throwable ignored){
            return def;
        }
    }
}
