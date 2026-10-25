package ironworks;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.g2d.TextureRegion;
import arc.scene.style.Drawable;
import arc.scene.style.TextureRegionDrawable;
import arc.util.Log;
import mindustry.game.EventType;
import mindustry.gen.Call;
import mindustry.gen.Tex;
import mindustry.graphics.Pal;
import mindustry.mod.Mods;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static mindustry.Vars.*;

/**
 * 设置里新增「铁流工业」分类：多人暂停、界面显示与彩蛋开关。
 *
 * <p>分隔线/按钮用匿名 {@link SettingsMenuDialog.SettingsTable.Setting} 注册，
 * 这样「恢复默认设置 / 切换分类」重建设置表时不会被删掉。
 */
public class IronworksSettings{

    private static boolean added;

    private IronworksSettings(){
    }

    public static void init(){
        register();
        Events.on(EventType.ClientLoadEvent.class, e -> register());
    }

    static void register(){
        if(added || headless || ui == null || ui.settings == null) return;
        added = true;

        try{
            Mods.LoadedMod mod = mods.getMod(IronworksMod.class);
            Drawable icon = (mod != null && mod.iconTexture != null)
                ? new TextureRegionDrawable(new TextureRegion(mod.iconTexture)) : Styles.black6;

            ui.settings.addCategory("@settings.ironworks.category.name", icon, st -> {
                // —— 多人暂停 ——
                st.sliderPref("ironworks.pause.mode", 0, 0, 2, 1,
                    i -> Core.bundle.get("setting.ironworks.pause.mode.value." + i, String.valueOf(i)),
                    i -> {
                        IronworksPause.mode = i;
                        if(net.client()) Call.serverPacketReliable("ironworks-pause-policy", String.valueOf(i));
                    });
                st.checkPref("ironworks.pause.request", true);
                custom(st, t -> t.button(
                    Core.bundle.get("setting.ironworks.pause.whitelist.name"), Styles.defaultt,
                    IronworksPause::showWhitelistDialog).width(200f).padTop(6f));

                custom(st, t -> t.image(Tex.whiteui).growX().height(2f)
                    .color(Pal.gray).padTop(8f).padBottom(8f));

                // —— 界面显示 / 彩蛋 ——
                st.checkPref(IronworksSidebar.key, true);
                st.checkPref("ironworks.damage.numbers", true, v -> DamageNumbers.enabled = v);
                custom(st, t -> t.button(Core.bundle.get("ironworks.combat.settings"), Styles.defaultt,
                    IronworksCombatDialog::show).width(240f).padTop(4f));
                st.checkPref("ironworks.rbmk.meme", true);
            });
        }catch(Throwable t){
            Log.err("IronworksSettings: 注册设置分类失败", t);
        }
    }

    /** 插入一个不参与“恢复默认”的自定义设置行。 */
    private static void custom(SettingsMenuDialog.SettingsTable table, Cons<SettingsMenuDialog.SettingsTable> content){
        table.pref(new SettingsMenuDialog.SettingsTable.Setting(null){
            @Override
            public void add(SettingsMenuDialog.SettingsTable target){
                content.get(target);
                target.row();
            }
        });
    }
}
