package ironworks;

import arc.Core;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.type.UnitType;
import mindustry.type.Weapon;
import mindustry.entities.bullet.BulletType;
import mindustry.ui.Styles;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.meta.Stat;

/**
 * 详情页属性伤害着色与实时弹药数值（客户端）。
 *
 * <p>原版弹药统计由 StatValues.ammo 直接读取 BulletType.damage / splashDamage，
 * 没有按难度缩放的钩子。这里只在详情页 show() 期间临时替换弹体字段，让原版
 * 弹药面板本身显示当前实际伤害，返回后立刻恢复；穿甲收益则补在弹药面板末尾。</p>
 */
public class IronworksDamageUI extends IronworksContentInfoDialog{
    /** 详情页打开期间临时改写的弹体；show() 结束后必须恢复。 */
    private static final ObjectMap<BulletType, float[]> previewed = new ObjectMap<>();

    public static void init(){
        if(Vars.headless || Vars.ui == null) return;
        if(Vars.ui.content instanceof IronworksDamageUI) return;
        Vars.ui.content = new IronworksDamageUI();
    }

    @Override
    public void show(UnlockableContent content){
        Turret turret = content instanceof Turret t ? t : null;
        UnitType unit = content instanceof UnitType u ? u : null;
        if(turret != null) pushBulletPreview(turret);
        try{
            super.show(content);
            if(turret != null){
                decorate(cont, turret);
            }else if(unit != null){
                decorateUnit(cont, unit);
            }
        }finally{
            if(turret != null) popBulletPreview();
        }
    }

    private static void pushBulletPreview(Turret turret){
        previewed.clear();
        float multiplier = Difficulty.precisionDamageMultiplier();
        if(multiplier == 1f) return;

        for(BulletType bullet : Dmg.ammoTypes(turret)){
            if(bullet == null || previewed.containsKey(bullet)) continue;
            previewed.put(bullet, new float[]{bullet.damage, bullet.splashDamage});
            bullet.damage *= multiplier;
            bullet.splashDamage *= multiplier;
        }
    }

    private static void popBulletPreview(){
        for(ObjectMap.Entry<BulletType, float[]> entry : previewed){
            entry.key.damage = entry.value[0];
            entry.key.splashDamage = entry.value[1];
        }
        previewed.clear();
    }

    private static void decorate(Table root, Turret turret){
        Seq<BulletType> bullets = Dmg.ammoTypes(turret);
        if(bullets.isEmpty()) return;

        Table ammo = findAmmoSection(root);
        if(ammo == null) return;

        Seq<Table> panels = new Seq<>();
        collectPanels(ammo, panels);
        if(panels.isEmpty()) return;

        int index = 0;
        for(Table panel : panels){
            if(index >= bullets.size) break;
            BulletType bullet = bullets.get(index++);
            decoratePanel(panel, bullet, Dmg.typeOf(bullet, turret),
                Dmg.formOf(bullet, turret), Dmg.penOf(bullet, turret));
        }
    }

    /** 精确找到 Stat.ammo 那一节，避免把其它 grayPanel（物品/液体统计）当成弹药面板。 */
    private static Table findAmmoSection(Element element){
        String target = "[lightgray]" + Stat.ammo.localized() + ":[] ";
        if(element instanceof Label label){
            String text = label.getText().toString();
            if(text.equals(target) || text.startsWith(target)){
                return label.parent instanceof Table table ? table : null;
            }
        }
        if(element instanceof Group group){
            for(Element child : group.getChildren()){
                Table found = findAmmoSection(child);
                if(found != null) return found;
            }
        }
        return null;
    }

    private static void decoratePanel(Table panel, BulletType bullet, DamageType type, DamageForm form, ArmorPen pen){
        String areaWord = areaWord();
        String directSuffix = directSuffix();
        boolean changed = false;

        for(Label label : labels(panel)){
            String text = label.getText().toString();

            if(areaWord != null && text.contains(areaWord)){
                String next = text.replace(areaWord, type.coloredName() + type.separator() + type.areaSuffix());
                // 原版范围伤害按整数显示；这里换成临时缩放后的实际值，仍保留半径。
                if(bullet.splashDamage > 0f){
                    next = next.replaceFirst("\\[stat\\][^\\[]*", "[stat]" + trim(bullet.splashDamage));
                }
                label.setText(next);
                changed = true;
            }else if(directSuffix != null && text.contains(directSuffix)){
                label.setText(text.replace(directSuffix, "[lightgray] " + type.coloredName() + type.separator() + type.damageSuffix()));
                changed = true;
            }
        }

        // 0 伤害的弹药（例如纯状态弹）没有伤害行时，补一行属性说明
        if(!changed){
            panel.row();
            panel.add("[lightgray]" + type.coloredName() + type.separator() + type.damageSuffix()).left();
        }

        panel.row();
        panel.add("[lightgray]" + Core.bundle.get("ironworks.combat.form", "形态") + ":[] "
            + form.localizedName()).left();
        panel.row();
        panel.add("[lightgray]" + Core.bundle.format("ironworks.combat.pierce",
            Mathf.round(pen.rate * 100f), trim(pen.flat), trim(pen.decay), trim(pen.ricochet)))
            .left().wrap();
        panel.row();
        panel.add("[lightgray]" + Core.bundle.format("ironworks.combat.combo", type.comboSummary()))
            .left().wrap();
        if(form.kinetic){
            panel.row();
            panel.add("[lightgray]" + Core.bundle.get("ironworks.combat.overpen-short"))
                .left().wrap();
        }
        if(form.bypassShields){
            panel.row();
            panel.add("[lightgray]" + Core.bundle.get("ironworks.combat.shield-bypass"))
                .left().wrap();
        }else if(pen.ricochet > 0f){
            panel.row();
            panel.add("[lightgray]" + Core.bundle.get("ironworks.combat.ricochet-short"))
                .left().wrap();
        }
    }

    /** 单位没有 Stat.ammo 区块，武器统计由 StatValues.weapons 生成；按同样的面板样式逐武器着色。 */
    private static void decorateUnit(Table root, UnitType unit){
        if(unit.weapons == null || unit.weapons.isEmpty()) return;

        Seq<Weapon> visible = new Seq<>();
        for(Weapon weapon : unit.weapons){
            if(weapon == null || weapon.bullet == null || weapon.flipSprite || !weapon.hasStats(unit)) continue;
            visible.add(weapon);
        }
        if(visible.isEmpty()) return;

        Seq<Table> weaponPanels = new Seq<>();
        collectWeaponPanels(root, weaponPanels);
        int count = Math.min(visible.size, weaponPanels.size);
        for(int i = 0; i < count; i++){
            Weapon weapon = visible.get(i);
            Table outer = weaponPanels.get(i);
            Table ammo = findNestedAmmoPanel(outer);
            decoratePanel(ammo == null ? outer : ammo, weapon.bullet,
                Dmg.typeOf(weapon.bullet), Dmg.formOf(weapon.bullet), Dmg.penOf(weapon.bullet));
        }
    }

    /**
     * 顶层武器面板是 StatValues.weapons 的 grayPanel；它内部还会再套一层弹药 grayPanel。
     * 只取“父级不是 grayPanel、但直接子级是 grayPanel”的顶层面板，避免把弹药面板重复配对。
     */
    private static void collectWeaponPanels(Element element, Seq<Table> out){
        if(element instanceof Table table && table.getBackground() == Styles.grayPanel
            && !(table.parent instanceof Table parent && parent.getBackground() == Styles.grayPanel)){
            for(Element child : table.getChildren()){
                if(child instanceof Table nested && nested.getBackground() == Styles.grayPanel){
                    out.add(table);
                    break;
                }
            }
        }
        if(element instanceof Group group){
            for(Element child : group.getChildren()){
                collectWeaponPanels(child, out);
            }
        }
    }

    private static Table findNestedAmmoPanel(Table outer){
        for(Element child : outer.getChildren()){
            if(child instanceof Table nested && nested.getBackground() == Styles.grayPanel){
                return nested;
            }
        }
        return null;
    }

    private static String trim(float value){
        return value == (int)value ? String.valueOf((int)value) : String.format(java.util.Locale.US, "%.1f", value);
    }

    private static void collectPanels(Element element, Seq<Table> out){
        if(element instanceof Table table && table.getBackground() == Styles.grayPanel){
            out.add(table);
        }
        if(element instanceof Group group){
            for(Element child : group.getChildren()){
                collectPanels(child, out);
            }
        }
    }

    private static Seq<Label> labels(Table table){
        Seq<Label> out = new Seq<>();
        collectLabels(table, out);
        return out;
    }

    private static void collectLabels(Element element, Seq<Label> out){
        if(element instanceof Label label){
            out.add(label);
            return;
        }
        if(element instanceof Group group){
            for(Element child : group.getChildren()){
                collectLabels(child, out);
            }
        }
    }

    private static String directSuffix(){
        try{
            String template = Core.bundle.get("bullet.damage", "");
            int index = template.indexOf("{0}");
            return index < 0 ? null : template.substring(index + 3);
        }catch(Throwable ignored){
            return null;
        }
    }

    private static String areaWord(){
        try{
            String template = Core.bundle.get("bullet.splashdamage", "");
            return template.contains("范围伤害") ? "范围伤害" : (template.contains("area dmg") ? "area dmg" : null);
        }catch(Throwable ignored){
            return null;
        }
    }
}
