package ironworks;

import arc.Core;
import arc.math.Mathf;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/** 设置里的「属性克制与穿甲说明」独立页。 */
public class IronworksCombatDialog{
    private IronworksCombatDialog(){
    }

    public static void show(){
        BaseDialog dialog = new BaseDialog(Core.bundle.get("ironworks.combat.title", "属性与穿甲"));
        dialog.cont.pane(t -> {
            t.left();
            t.add(Core.bundle.get("ironworks.combat.formula")).color(arc.graphics.Color.lightGray)
                .left().wrap().width(520f).pad(8f).row();
            t.add(Core.bundle.get("ironworks.combat.rules")).color(arc.graphics.Color.lightGray)
                .left().wrap().width(520f).pad(8f).row();
            t.add(Core.bundle.get("ironworks.combat.overpen")).color(arc.graphics.Color.lightGray)
                .left().wrap().width(520f).pad(8f).row();
            t.add(Core.bundle.get("ironworks.combat.form-note")).color(arc.graphics.Color.lightGray)
                .left().wrap().width(520f).pad(8f).row();

            for(DamageType type : DamageType.values()){
                t.table(row -> {
                    row.left();
                    row.add(type.coloredName()).width(72f).left();
                    row.add(type.defaultForm.localizedName()).width(64f).color(arc.graphics.Color.lightGray).left();
                    ArmorPen pen = type.pen;
                    row.add(Core.bundle.format("ironworks.combat.pierce",
                        Mathf.round(pen.rated * 100f), trim(pen.threshold), trim(pen.decay * 100f)))
                        .color(arc.graphics.Color.lightGray).left().wrap().width(240f);
                    row.row();
                    row.add("");
                    row.add(Core.bundle.format("ironworks.combat.combo", type.comboSummary()))
                        .color(arc.graphics.Color.lightGray).left().wrap().width(330f);
                }).growX().pad(5f).row();
            }
        }).grow();

        dialog.closeOnBack();
        dialog.addCloseButton();
        dialog.show();
    }

    private static String trim(float value){
        return value == (int)value ? String.valueOf((int)value) : String.format(java.util.Locale.US, "%.1f", value);
    }
}
