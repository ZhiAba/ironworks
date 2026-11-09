package ironworks;

import arc.Core;
import arc.graphics.Color;
import arc.math.Mathf;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import mindustry.gen.Tex;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;

/**
 * 难度协议界面：阶梯吸附滑块 + 难度色 + T7 乱码 + 词条池。
 * 只做客户端 UI；实际规则由 {@link Difficulty} 应用。
 */
public class IronworksDifficultyDialog{
    private IronworksDifficultyDialog(){
    }

    public static void show(){
        BaseDialog dialog = new BaseDialog(Core.bundle.get("ironworks.difficulty.title", "难度协议"));
        dialog.closeOnBack();

        int[] preview = {Difficulty.tier()};
        Label tierLabel = new Label("", Styles.outlineLabel);
        Label detail = new Label("", Styles.outlineLabel);
        detail.setWrap(true);

        dialog.cont.pane(root -> {
            root.left();

            root.add(tierLabel).left().pad(8f).row();
            Image colorBar = new Image(Tex.whiteui);
            colorBar.setColor(Difficulty.tierColor(preview[0]));
            colorBar.update(() -> colorBar.setColor(Difficulty.tierColor(preview[0])));
            root.add(colorBar).growX().height(4f).padBottom(6f).row();

            Slider slider = new Slider(0f, Difficulty.maxTier, 1f, false);
            slider.setValue(preview[0]);
            // 真正吸附到 8 个整点，而不是只在提交时取整。
            float[] stops = new float[Difficulty.maxTier + 1];
            for(int i = 0; i < stops.length; i++) stops[i] = i;
            slider.setSnapToValues(stops, 0.30f);
            slider.moved(v -> preview[0] = Mathf.clamp((int)Math.round(v), 0, Difficulty.maxTier));
            root.add(slider).growX().pad(8f).row();
            // 设置页外面的难度按钮/战役规则页改了档位时，滑块和预览要跟着实际档位走；
            // 只跟踪“实际档位变化”，避免点击 T 按钮时被旧档位立刻回写。
            int[] lastActualTier = {Difficulty.tier()};
            slider.update(() -> {
                if(slider.isDragging()) return;
                int actual = Difficulty.tier();
                if(actual != lastActualTier[0]){
                    lastActualTier[0] = actual;
                    preview[0] = actual;
                    slider.setValue(actual);
                }
            });

            Table ticks = new Table();
            for(int i = 0; i <= Difficulty.maxTier; i++){
                final int value = i;
                ticks.button("T" + i, Styles.defaultt, () -> {
                    preview[0] = value;
                    slider.setValue(value);
                }).width(42f).pad(1f);
            }
            root.add(ticks).growX().row();

            root.add(detail).left().wrap().width(560f).pad(8f).row();

            // T7 词条池：每个等级都是一个独立按钮，不用加号连点。
            root.table(mods -> {
                mods.left();
                mods.add(Core.bundle.get("ironworks.difficulty.mods.title", "T7 词条池（无上限叠加）"))
                    .left().pad(8f).row();
                Table list = new Table();
                list.left();
                Label summary = new Label("", Styles.outlineLabel);
                summary.setWrap(true);

                list.update(() -> {
                    if(Difficulty.tier() == Difficulty.maxTier && summary.getText().length() == 0){
                        summary.setText(Difficulty.modifierSummary());
                    }
                });

                mods.add(list).growX().row();
                mods.add(summary).left().wrap().width(520f).pad(4f).row();
                buildModifierRows(list, summary);
            }).left().growX().row();

            root.add(Core.bundle.get("ironworks.difficulty.warning",
                "切换难度会在有受袭区块时触发拦截；游戏中的受袭区块必须先清场。"))
                .color(Color.lightGray).left().wrap().width(560f).pad(8f).row();
        }).grow();

        tierLabel.update(() -> {
            int value = preview[0];
            Color color = Difficulty.tierColor(value);
            String text = Difficulty.tierName(value);
            if(value == Difficulty.maxTier && IronworksGlitch.active()){
                text = IronworksGlitch.garble(text);
            }
            if(!text.equals(tierLabel.getText().toString())){
                tierLabel.setText(text);
            }
            tierLabel.setColor(color);
        });

        detail.update(() -> {
            String text = Difficulty.describe(preview[0]);
            if(!text.equals(detail.getText().toString())){
                detail.setText(text);
            }
        });

        dialog.buttons.button(Core.bundle.get("ironworks.difficulty.apply", "应用"), Styles.defaultt, () -> {
            requestApply(preview[0], dialog);
        }).width(140f);
        dialog.buttons.button(Core.bundle.get("cancel", "取消"), Styles.defaultt, dialog::hide).width(120f);
        dialog.show();
    }

    private static void buildModifierRows(Table list, Label summary){
        list.clearChildren();
        if(Difficulty.tier() != Difficulty.maxTier){
            list.add(Core.bundle.get("ironworks.difficulty.mods.locked",
                "只有当前难度为 T7 时才能编辑词条；先应用 T7。")).left().wrap().width(520f).row();
            return;
        }

        for(String id : Difficulty.modIds){
            Table group = new Table();
            group.left();

            // 各词条上限不同：资源匮乏/精良装甲/精尖弹药到 IV，精锐部队到 II。
            Table levels = new Table();
            levels.left();
            int maxLevel = Difficulty.modifierMaxLevel(id);
            for(int level = 1; level <= maxLevel; level++){
                final int value = level;
                boolean selected = Difficulty.hasModifierLevel(id, level);
                String label = Difficulty.modName(id) + roman(level) + (selected ? "●" : "○");
                levels.button(label, Styles.defaultt, () -> {
                    Difficulty.toggleModifierLevel(id, value);
                    summary.setText(Difficulty.modifierSummary());
                    postModifierRows(list, summary);
                }).width(150f).pad(2f);
                if(level % 2 == 0) levels.row();
            }

            Table header = new Table();
            header.left();
            header.add(levels).left().growX();
            if(Difficulty.benefitControlsVisible() && Difficulty.hasBenefit(id)){
                boolean enabled = Difficulty.benefitEnabled(id);
                header.button(Core.bundle.get(enabled ? "ironworks.difficulty.benefit.off" : "ironworks.difficulty.benefit.on",
                        enabled ? "关闭收益" : "恢复收益"), Styles.defaultt, () -> {
                    Difficulty.setBenefitEnabled(id, !enabled);
                    summary.setText(Difficulty.modifierSummary());
                    postModifierRows(list, summary);
                }).width(112f).pad(2f).right();
            }
            group.add(header).growX().row();
            group.add(Difficulty.modDesc(id)).left().wrap().width(520f)
                .color(Color.lightGray).padTop(2f).row();
            list.add(group).growX().pad(4f).row();
        }

        Table footer = new Table();
        footer.left();
        footer.button(Core.bundle.get("ironworks.difficulty.benefit.close", "关闭词条收益○"),
            Styles.defaultt, () -> showBenefitConfirm(list, summary)).width(190f).pad(2f);
        footer.button(Core.bundle.get("ironworks.difficulty.mods.clear", "清空词条"),
            Styles.defaultt, () -> {
                Difficulty.clearModifiers();
                summary.setText(Difficulty.modifierSummary());
                postModifierRows(list, summary);
            }).width(120f).pad(2f);
        list.add(footer).left().padTop(8f).row();
    }

    private static void showBenefitConfirm(Table list, Label summary){
        BaseDialog confirm = new BaseDialog(Core.bundle.get("ironworks.difficulty.benefit.title", "关闭词条收益"));
        confirm.cont.add(Core.bundle.get("ironworks.difficulty.benefit.text",
            "开启此选项后可关闭词条带来的副收益，可能带来不可预知的平衡性灾难，确认开启吗？"))
            .wrap().width(500f).pad(16f).row();
        confirm.buttons.button(Core.bundle.get("cancel", "取消"), Styles.defaultt, confirm::hide).width(120f);
        confirm.buttons.button(Core.bundle.get("ironworks.difficulty.benefit.confirm", "确认开启"),
            Styles.defaultt, () -> {
                Difficulty.disableAllBenefits();
                summary.setText(Difficulty.modifierSummary());
                Core.app.post(() -> {
                    confirm.hide();
                    postModifierRows(list, summary);
                });
            }).width(160f);
        confirm.show();
    }

    private static void postModifierRows(Table list, Label summary){
        // 触摸事件分发期间清空/重建控件会让当前按钮失去 scene，触发 addTouchFocus NPE。
        Core.app.post(() -> {
            if(list.getScene() != null) buildModifierRows(list, summary);
        });
    }

    private static String roman(int value){
        int[] values = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] symbols = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder out = new StringBuilder();
        int left = Math.max(1, value);
        for(int i = 0; i < values.length && left > 0; i++){
            while(left >= values[i]){
                out.append(symbols[i]);
                left -= values[i];
            }
        }
        return out.toString();
    }

    private static void requestApply(int target, BaseDialog owner){
        if(target == Difficulty.tier()){
            Core.app.post(owner::hide);
            return;
        }
        if(Difficulty.hasActiveAttackedSector()){
            BaseDialog blocked = new BaseDialog(Core.bundle.get("ironworks.difficulty.blocked.title", "无法切换难度"));
            blocked.cont.add(Core.bundle.get("ironworks.difficulty.blocked.text",
                "当前区块正在遭遇袭击。请先结束当前战斗，再切换难度。")).wrap().width(480f).pad(16f).row();
            blocked.buttons.button(Core.bundle.get("ok", "确定"), Styles.defaultt, blocked::hide).width(120f);
            blocked.show();
            return;
        }
        if(Difficulty.hasAttackedSector()){
            BaseDialog confirm = new BaseDialog(Core.bundle.get("ironworks.difficulty.confirm.title", "难度切换警告"));
            confirm.cont.add(Core.bundle.get("ironworks.difficulty.confirm.text",
                "此操作会自动放弃所有正在遭遇袭击中的区块，是否继续操作？"))
                .wrap().width(480f).pad(16f).row();
            confirm.buttons.button(Core.bundle.get("cancel", "取消"), Styles.defaultt, confirm::hide).width(120f);
            confirm.buttons.button(Core.bundle.get("ironworks.difficulty.confirm.continue", "继续（放弃受袭区块）"),
                Styles.defaultt, () -> {
                    Difficulty.abandonAttackedSectors();
                    Difficulty.setTier(target);
                    Core.app.post(() -> {
                        confirm.hide();
                        owner.hide();
                        reopenForModifiers(target);
                    });
                }).width(220f);
            confirm.show();
            return;
        }
        Difficulty.setTier(target);
        Core.app.post(() -> {
            owner.hide();
            reopenForModifiers(target);
        });
    }

    private static void reopenForModifiers(int target){
        if(target == Difficulty.maxTier){
            Core.app.post(IronworksDifficultyDialog::show);
        }
    }


}
