package ironworks;

import arc.Core;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.util.Scaling;
import arc.util.Strings;
import mindustry.graphics.Pal;
import mindustry.type.Liquid;
import mindustry.ui.Styles;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.StatValue;

/**
 * 液体加速的详情页条目：和原版强化条目同样的灰底面板 + 40px 液体图标，
 * 左侧液体名与消耗速率，右侧黄色大号倍率。
 */
public class IronworksStats{

    /**
     * @param actionKey 文案 key（如开火速率 / 传送速度）
     * @param entries  三元组：Liquid, Float 倍率, Float 每秒消耗（<0 表示不消耗）
     */
    public static StatValue liquidBoosts(String actionKey, Object... entries){
        return table -> {
            table.row();
            table.table(c -> {
                for(int i = 0; i + 2 < entries.length; i += 3){
                    Liquid liquid = (Liquid)entries[i];
                    float mult = (Float)entries[i + 1];
                    float usage = (Float)entries[i + 2];

                    c.table(Styles.grayPanel, b -> {
                        b.image(liquid.uiIcon).size(40f).pad(10f).left().scaling(Scaling.fit);

                        b.table(info -> {
                            info.add(liquid.localizedName).left().row();
                            if(usage >= 0f){
                                info.add(Strings.autoFixed(usage, 2) + StatUnit.perSecond.localized())
                                    .left().color(arc.graphics.Color.lightGray).padTop(2f);
                            }
                        }).left().growX().padLeft(4f);

                        b.table(bt -> {
                            Label value = new Label((int)(mult * 100f) + "% " + Core.bundle.get(actionKey), Styles.defaultLabel);
                            value.setColor(Pal.accent);
                            value.setFontScale(1.15f);
                            bt.add(value).right().pad(10f);
                        }).right().grow().padRight(8f);
                    }).growX().pad(5f).row();
                }
            }).growX().colspan(table.getColumns());
            table.row();
        };
    }
}
