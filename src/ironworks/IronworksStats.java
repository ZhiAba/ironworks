package ironworks;

import arc.Core;
import arc.scene.ui.Label;
import arc.scene.ui.layout.Table;
import arc.util.Scaling;
import arc.util.Strings;
import arc.graphics.g2d.TextureRegion;
import mindustry.graphics.Pal;
import mindustry.type.Liquid;
import mindustry.ui.Styles;
import mindustry.world.meta.StatUnit;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatValue;

/**
 * 液体加速的详情页条目：和原版强化条目同样的灰底面板 + 40px 液体图标，
 * 左侧液体名与消耗速率，右侧黄色大号倍率。
 */
public class IronworksStats{

    /** 自定义统计项：最大射速 / 命中效果 */
    public static final Stat maxFire = new Stat("ironworks-max-fire");
    public static final Stat onHit = new Stat("ironworks-on-hit");

    /**
     * @param actionKey 文案 key（如开火速率 / 传送速度）
     * @param entries  三元组：Liquid, Float 倍率, Float 每秒消耗（<0 表示不消耗）
     */
    /**
     * 带"上限"的液体强化条目：四元组 Liquid, 倍率, 每秒消耗, 加成上限百分比（<0 不显示）。
     * 详情页右侧显示两行：倍率 + 上限。
     */
    public static StatValue liquidBoostsCapped(String actionKey, Object... entries){
        return table -> {
            table.row();
            table.table(c -> {
                for(int i = 0; i + 3 < entries.length; i += 4){
                    Liquid liquid = (Liquid)entries[i];
                    float mult = (Float)entries[i + 1];
                    float usage = (Float)entries[i + 2];
                    float cap = (Float)entries[i + 3];

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
                            bt.add(value).right().row();
                            if(cap >= 0f){
                                Label capLabel = new Label(Core.bundle.format("stat.ironworks-cap", (int)cap), Styles.defaultLabel);
                                capLabel.setColor(Pal.accent);
                                capLabel.setFontScale(0.95f);
                                bt.add(capLabel).right().padTop(2f);
                            }
                        }).right().grow().padRight(8f);
                    }).growX().pad(5f).row();
                }
            }).growX().colspan(table.getColumns());
            table.row();
        };
    }

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

    /**
     * 详情页「规格行」：灰底一行，左边图标 + 名称，右边带颜色的数值。
     * 参数多的方块（RBMK 之类）用它排版，比一行一个 add(name, value) 整齐得多。
     */
    public static void specRow(Table parent, arc.scene.style.Drawable icon, String label, String value, arc.graphics.Color valueColor){
        parent.table(Styles.grayPanel, row -> {
            row.image(icon).size(30f).pad(8f).left().scaling(Scaling.fit);
            row.add(label).left().color(arc.graphics.Color.lightGray).growX();
            row.add(value).right().color(valueColor).padLeft(10f).padRight(10f);
        }).growX().padBottom(3f);
        parent.row();
    }

    /** 规格行的贴图重载（液体 uiIcon 之类）。 */
    public static void specRow(Table parent, TextureRegion icon, String label, String value, arc.graphics.Color valueColor){
        specRow(parent, new arc.scene.style.TextureRegionDrawable(icon), label, value, valueColor);
    }

    /** 详情页「说明/风险」块：一行一句，整块灰底，统一文字颜色。 */
    public static void notes(Table parent, arc.graphics.Color color, String... lines){
        parent.table(Styles.grayPanel, c -> {
            for(String line : lines){
                if(line == null || line.isEmpty()) continue;
                c.add(line).left().color(color).wrap().growX().pad(6f).row();
            }
        }).growX();
        parent.row();
    }
}
