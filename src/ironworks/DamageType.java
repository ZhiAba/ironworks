package ironworks;

import arc.Core;
import arc.graphics.Color;
import mindustry.type.StatusEffect;

/**
 * 属性伤害定义（v2.6 战斗体系）。
 *
 * <p>一个属性 = 元素归属 + 默认穿甲曲线 + 命中附着状态。具体弹种可以在
 * {@link Dmg} 里覆盖穿甲曲线；Combo 倍率由 {@link ElementCombo} 统一管理。</p>
 *
 * <p>颜色只用于详情页/飘字，不在枚举里做绘制。</p>
 */
public enum DamageType{
    //      id         中文  色号      元素?  形态          额定穿甲 阈值  每点衰减 状态        持续   概率
    physical("physical", "物理", "c8c8c8", false, DamageForm.KINETIC,   0.65f, 18f, 0.30f, null,       0f,   1f),
    thunder ("thunder",  "雷霆", "b6a8ff", true,  DamageForm.KINETIC,   0.50f, 15f, 0.30f, "shocked",  90f,  1f),
    water   ("water",    "水",   "5aa9ff", true,  DamageForm.ENERGY,    0.30f, 12f, 0.35f, "wet",      240f, 1f),
    ice     ("ice",      "冰",   "9fe8ff", true,  DamageForm.ENERGY,    0.40f, 15f, 0.35f, "freezing", 120f, 1f),
    fire    ("fire",     "火",   "ff8a3c", true,  DamageForm.EXPLOSIVE, 0.25f, 10f, 0.45f, "burning",  240f, 1f),
    corrosive("corrosive","腐蚀","8fbf5a", true,  DamageForm.KINETIC,   0.80f, 25f, 0.20f, "corrosion",300f, 1f),
    /** 无属性：无元素归属，也不参与动能/元素 Combo；可单独指定能量等形态。 */
    typeless("typeless",  "无属性", "ffffff", false, DamageForm.TYPELESS,  0.65f, 18f, 0.30f, null,       0f,   1f),
    /** 湮灭：只受“受到最终伤害降低”影响；不跳弹、不过穿、不吃护甲/抗性/护盾/Combo。 */
    annihilation("annihilation", "湮灭", "5b1a7a", false, DamageForm.ANNIHILATION, 1f, 1000000f, 0f, null, 0f, 1f);

    public final String id;
    /** 中文名，作为 bundle 缺失时的兜底。 */
    public final String cn;
    public final String hex;
    public final boolean elemental;
    /** 默认形态；具体弹种可覆盖。 */
    public final DamageForm defaultForm;
    /** 默认穿甲曲线；具体弹种可覆盖。 */
    public final ArmorPen pen;
    public final String statusName;
    public final float statusDuration;
    public final float statusChance;

    /** 由 Dmg.load() 在内容加载完成后解析，避免枚举初始化时碰 StatusEffects。 */
    public StatusEffect status;

    DamageType(String id, String cn, String hex, boolean elemental, DamageForm defaultForm,
               float rated, float threshold, float decay,
               String statusName, float statusDuration, float statusChance){
        this.id = id;
        this.cn = cn;
        this.hex = hex;
        this.elemental = elemental;
        this.defaultForm = defaultForm;
        this.pen = new ArmorPen(rated, threshold, decay);
        this.statusName = statusName;
        this.statusDuration = statusDuration;
        this.statusChance = statusChance;
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
        if(this == typeless) return bundle("damage-type.typeless-suffix", "无属性伤害");
        if(this == annihilation) return bundle("damage-type.annihilation-suffix", "湮灭伤害");
        return bundle(elemental ? "damage-type.element-suffix" : "damage-type.physical-suffix",
            elemental ? "元素伤害" : "伤害");
    }

    public String areaSuffix(){
        if(this == typeless) return bundle("damage-type.typeless-area", "无属性范围伤害");
        if(this == annihilation) return bundle("damage-type.annihilation-area", "湮灭范围伤害");
        return bundle(elemental ? "damage-type.element-area" : "damage-type.physical-area",
            elemental ? "元素范围伤害" : "范围伤害");
    }

    /** 详情页：该属性可触发的 Combo。 */
    public String comboSummary(){
        return ElementCombo.summary(this);
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
