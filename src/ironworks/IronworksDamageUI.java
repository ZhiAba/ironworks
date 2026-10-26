package ironworks;

import arc.Core;
import arc.math.Mathf;
import arc.scene.Element;
import arc.scene.Group;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.ctype.UnlockableContent;
import mindustry.entities.bullet.BulletType;
import mindustry.type.UnitType;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.ContentInfoDialog;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.meta.Stat;

/**
 * 详情页属性伤害着色（客户端）。
 *
 * 原版弹药统计由 StatValues.ammo 直接生成 "[stat]12[lightgray] 伤害"，
 * 没有按弹种改字体的钩子。这里替换掉 Vars.ui.content，
 * 在 dialog.show() 之后遍历弹药面板的 Label，把伤害行改成：
 *   [stat]12[lightgray] [#5aa9ff]水[]元素伤害
 * 同时处理范围伤害行。
 *
 * 这样原版海啸/蓝瑟/魔灵和模组炮塔都能统一显示，不需要为每门炮写 setStats。
 */
public class IronworksDamageUI extends ContentInfoDialog{

    public static void init(){
        if(Vars.headless || Vars.ui == null) return;
        if(Vars.ui.content instanceof IronworksDamageUI) return;
        Vars.ui.content = new IronworksDamageUI();
    }

    @Override
    public void show(UnlockableContent content){
        super.show(content);
        if(content instanceof Turret turret){
            decorate(cont, turret);
        }else if(content instanceof UnitType unit){
            decorateUnit(cont, unit);
        }
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
            decoratePanel(panel, bullet, Dmg.typeOf(bullet, turret), turret);
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

    private static void decoratePanel(Table panel, BulletType bullet, DamageType type, Turret turret){
        String areaWord = areaWord();
        String directSuffix = directSuffix();
        boolean changed = false;

        for(Label label : labels(panel)){
            String text = label.getText().toString();

            if(areaWord != null && text.contains(areaWord)){
                label.setText(text.replace(areaWord, type.coloredName() + type.separator() + type.areaSuffix()));
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

        ArmorPen pen = Dmg.penOf(bullet, turret);
        DamageForm form = Dmg.formOf(bullet, turret);
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

    private static void decorateUnit(Table root, UnitType unit){
        root.row();
        root.table(Styles.grayPanel, t -> {
            t.add("[lightgray]" + Core.bundle.get("ironworks.combat.armor", "护甲") + ":[] "
                + Mathf.round(unit.armor)).left();
            t.row();
            t.add("[lightgray]" + Core.bundle.get("ironworks.combat.material", "材质") + ":[] "
                + TargetMaterial.name(unit)).left();
            t.row();
            t.add("[lightgray]" + Core.bundle.get("ironworks.combat.weakness", "弱点") + ":[] "
                + ElementCombo.weaknessName(unit)).left().wrap();
            t.row();
            t.add("[lightgray]" + Core.bundle.get("ironworks.combat.resistance", "抗性") + ":[] "
                + Resistance.summary(unit)).left().wrap();
        }).growX().padTop(6f);
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
