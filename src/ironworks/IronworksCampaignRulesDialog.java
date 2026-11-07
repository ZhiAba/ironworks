package ironworks;

import arc.Core;
import arc.func.Boolc;
import arc.func.Boolp;
import arc.math.Mathf;
import arc.scene.ui.Image;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.layout.Table;
import arc.util.Log;
import arc.util.Time;
import mindustry.Vars;
import mindustry.game.CampaignRules;
import mindustry.gen.Call;
import mindustry.gen.Tex;
import mindustry.type.Planet;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.ui.dialogs.CampaignRulesDialog;

import java.lang.reflect.Field;

/**
 * 战役星球界面的“难度”页。
 *
 * <p>原版只提供休闲/简单/正常/困难/毁灭五档，本页把难度选择替换成铁流工业的
 * T0-T7 协议，同时保留原版其它战役选项。原版 CampaignRules.difficulty
 * 被重置为 normal，避免原版难度与 T0-T7 双重生效。</p>
 */
public class IronworksCampaignRulesDialog extends CampaignRulesDialog{
    private static final Field SUPER_PLANET;

    static{
        Field field = null;
        try{
            field = CampaignRulesDialog.class.getDeclaredField("planet");
            field.setAccessible(true);
        }catch(Throwable first){
            // R8 可能改字段名；按类型兜底查找。
            for(Field candidate : CampaignRulesDialog.class.getDeclaredFields()){
                if(candidate.getType() == Planet.class){
                    candidate.setAccessible(true);
                    field = candidate;
                    break;
                }
            }
            if(field == null){
                Log.err("IronworksCampaignRulesDialog: 无法访问原版 planet 字段", first);
            }
        }
        SUPER_PLANET = field;
    }

    private static final float PROMPT_COOLDOWN = Time.toSeconds;
    private Planet myPlanet;
    private Table currentTable;
    private boolean prompting;
    private float lastPromptAt = -9999f;

    public IronworksCampaignRulesDialog(){
        super();
        // 原版在 resize 时会重建自己的五档难度页；这里在它之后重建 T0-T7 页。
        onResize(() -> {
            if(myPlanet != null) rebuildCustom();
        });
    }

    @Override
    public void show(Planet planet){
        if(isShown()) return;
        this.myPlanet = planet;
        prompting = false;
        lastPromptAt = -9999f;
        Log.info("[IRONWORKS] campaign difficulty show @", planet == null ? "null" : planet.name);
        if(planet != null){
            // 原版难度不再参与缩放；T0-T7 是唯一难度来源。
            planet.campaignRules.difficulty = mindustry.game.Difficulty.normal;
        }
        setSuperPlanet(planet);
        rebuildCustom();
        super.show();
    }

    @Override
    public void hide(){
        Planet planet = myPlanet;
        super.hide();
        if(planet == null) return;

        planet.saveRules();
        if(Vars.state.isGame() && Vars.state.isCampaign() && Vars.state.getPlanet() == planet){
            // 原版 hidden 回调会先应用 normal；这里最后再压上 T0-T7 规则。
            planet.campaignRules.apply(planet, Vars.state.rules);
            Difficulty.applyRules();
            Call.setRules(Vars.state.rules);
        }
    }

    private void setSuperPlanet(Planet planet){
        if(SUPER_PLANET == null) return;
        try{
            SUPER_PLANET.set(this, planet);
        }catch(Throwable t){
            Log.err("IronworksCampaignRulesDialog: 写入原版 planet 字段失败", t);
        }
    }

    private void rebuildCustom(){
        if(myPlanet == null) return;
        CampaignRules rules = myPlanet.campaignRules;
        rules.difficulty = mindustry.game.Difficulty.normal;

        cont.clear();
        cont.top().pane(inner -> {
            inner.top().left().defaults().fillX().left().pad(5);
            currentTable = inner;

            inner.add(Core.bundle.get("ironworks.difficulty.campaign.title",
                "铁流工业难度协议 T0 - T7")).left().pad(4f).row();

            inner.table(Tex.button, t -> {
                t.margin(10f);
                Label current = new Label("", Styles.outlineLabel);
                Image colorBar = new Image(Tex.whiteui);
                t.add(current).left().row();
                t.add(colorBar).growX().height(4f).padBottom(4f).row();
                current.update(() -> {
                    int tier = Difficulty.tier();
                    String text = Difficulty.tierName(tier);
                    if(tier == Difficulty.maxTier && IronworksGlitch.active()) text = IronworksGlitch.garble(text);
                    current.setText(text);
                    current.setColor(Difficulty.tierColor(tier));
                    colorBar.setColor(Difficulty.tierColor(tier));
                });

                Slider slider = new Slider(0f, Difficulty.maxTier, 1f, false);
                float[] stops = new float[Difficulty.maxTier + 1];
                for(int i = 0; i < stops.length; i++) stops[i] = i;
                slider.setSnapToValues(stops, 0.30f);
                slider.setValue(Difficulty.tier());
                slider.moved(v -> {
                    int target = Mathf.clamp((int)Math.round(v), 0, Difficulty.maxTier);
                    if(target == Difficulty.tier()) return;
                    if(prompting || Time.time - lastPromptAt < PROMPT_COOLDOWN){
                        slider.setValue(Difficulty.tier());
                        return;
                    }
                    lastPromptAt = Time.time;
                    requestTier(target);
                    // 被受袭拦截或等待确认时，滑块回到当前实际档位。
                    if(Difficulty.tier() != target) slider.setValue(Difficulty.tier());
                });
                t.add(slider).growX().padTop(6f).row();
            }).left().fillX().expand(false, false).row();

            Label detail = new Label("", Styles.outlineLabel);
            detail.setWrap(true);
            inner.add(detail).width(560f).left().pad(6f).row();
            detail.update(() -> detail.setText(Difficulty.describe(Difficulty.tier())));

            if(myPlanet.allowSectorInvasion){
                ruleCheck("@rules.invasions", b -> rules.sectorInvasion = b, () -> rules.sectorInvasion);
            }
            ruleCheck("@rules.fog", b -> rules.fog = b, () -> rules.fog);
            ruleCheck("@rules.hidespawns", b -> rules.hideSpawns = b, () -> rules.hideSpawns);
            ruleCheck("@rules.randomwaveai", b -> rules.randomWaveAI = b, () -> rules.randomWaveAI);
            ruleCheck("@rules.pauseDisabled", b -> rules.pauseDisabled = b, () -> rules.pauseDisabled);

            if(myPlanet.showRtsAIRule){
                ruleCheck("@rules.rtsai.campaign", b -> rules.rtsAI = b, () -> rules.rtsAI);
            }
            if(!myPlanet.clearSectorOnLose){
                ruleCheck("@rules.clearsectoronloss", b -> rules.clearSectorOnLose = b, () -> rules.clearSectorOnLose);
            }

            inner.button(Core.bundle.get("ironworks.difficulty.t7.edit", "编辑 T7 词条"),
                Styles.defaultt, IronworksDifficultyDialog::show).width(180f).left().padTop(10f).row();
        }).growY();
    }

    private void ruleCheck(String text, Boolc cons, Boolp prov){
        String infoText = text.substring(1) + ".info";
        var cell = currentTable.check(text, cons).checked(prov.get());
        if(Core.bundle.has(infoText)){
            cell.tooltip(text + ".info");
        }
        cell.get().left();
        currentTable.row();
    }

    private void requestTier(int target){
        if(target == Difficulty.tier() || prompting) return;
        if(Difficulty.hasActiveAttackedSector()){
            prompting = true;
            BaseDialog blocked = new BaseDialog(Core.bundle.get("ironworks.difficulty.blocked.title", "无法切换难度"));
            blocked.cont.add(Core.bundle.get("ironworks.difficulty.blocked.text",
                "当前区块正在遭遇袭击。请先结束当前战斗，再切换难度。")).wrap().width(480f).pad(16f).row();
            blocked.buttons.button(Core.bundle.get("ok", "确定"), Styles.defaultt, blocked::hide).width(120f);
            blocked.hidden(() -> { prompting = false; lastPromptAt = Time.time; });
            blocked.show();
            return;
        }
        if(Difficulty.hasAttackedSector()){
            prompting = true;
            BaseDialog confirm = new BaseDialog(Core.bundle.get("ironworks.difficulty.confirm.title", "难度切换警告"));
            confirm.cont.add(Core.bundle.get("ironworks.difficulty.confirm.text",
                "此操作会自动放弃所有正在遭遇袭击中的区块，是否继续操作？"))
                .wrap().width(480f).pad(16f).row();
            confirm.buttons.button(Core.bundle.get("cancel", "取消"), Styles.defaultt, confirm::hide).width(120f);
            confirm.buttons.button(Core.bundle.get("ironworks.difficulty.confirm.continue", "继续（放弃受袭区块）"),
                Styles.defaultt, () -> {
                    Difficulty.abandonAttackedSectors();
                    applyTier(target);
                    confirm.hide();
                }).width(220f);
            confirm.hidden(() -> { prompting = false; lastPromptAt = Time.time; });
            confirm.show();
            return;
        }
        applyTier(target);
    }

    private void applyTier(int target){
        Difficulty.setTier(target);
        if(myPlanet != null){
            myPlanet.campaignRules.difficulty = mindustry.game.Difficulty.normal;
            myPlanet.saveRules();
        }
        // 触摸事件分发期间清空/重建控件会让当前按钮失去 scene，触发 addTouchFocus NPE。
        Core.app.post(() -> {
            if(isShown()) rebuildCustom();
        });
    }
}
