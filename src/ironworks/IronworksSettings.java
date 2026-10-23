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
 * 设置里新增「铁流工业」分类（方块搜索 + 多人暂停）。
 * 内嵌自 utility-pack 的设置注册方式：用 Setting 子类插入分隔线/按钮，
 * 这样「恢复默认设置 / 切换分类」重建设置表时它们不会被删掉。
 */
public class IronworksSettings{

    private static boolean added;

    private IronworksSettings(){
    }

    /** 在设置表里插入任意内容（分隔线、按钮）。name 传 null：恢复默认设置时不会被移除。 */
    public static class CustomSetting extends SettingsMenuDialog.SettingsTable.Setting{
        private final Cons<SettingsMenuDialog.SettingsTable> cons;

        public CustomSetting(Cons<SettingsMenuDialog.SettingsTable> cons){
            super(null);
            this.cons = cons;
        }

        @Override
        public void add(SettingsMenuDialog.SettingsTable table){
            cons.get(table);
            table.row();
        }
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
                // —— 方块搜索 ——
                st.checkPref("ironworks.search.showHistory", true);
                st.checkPref("ironworks.search.clearOnSelect", true);
                st.pref(new CustomSetting(t -> t.image(Tex.whiteui).growX().height(2f)
                    .color(Pal.gray).padTop(8f).padBottom(8f)));

                // —— 多人暂停 ——
                st.sliderPref("ironworks.pause.mode", 0, 0, 2, 1,
                    i -> Core.bundle.get("setting.ironworks.pause.mode.value." + i, String.valueOf(i)),
                    i -> {
                        IronworksPause.mode = i;
                        if(net.client()) Call.serverPacketReliable("ironworks-pause-mode", String.valueOf(i));
                    });
                st.checkPref("ironworks.pause.request", true);
                st.pref(new CustomSetting(t -> t.button(
                    Core.bundle.get("setting.ironworks.pause.whitelist.name"), Styles.defaultt,
                    IronworksPause::showWhitelistDialog).width(200f).padTop(6f)));
            });
        }catch(Throwable t){
            Log.err("IronworksSettings: 注册设置分类失败", t);
        }
    }
}
