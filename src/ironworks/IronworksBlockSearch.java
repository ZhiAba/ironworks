package ironworks;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.input.KeyCode;
import arc.scene.Element;
import arc.scene.event.ClickListener;
import arc.scene.event.InputEvent;
import arc.scene.event.Touchable;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ImageButton;
import arc.scene.ui.ScrollPane;
import arc.scene.ui.TextField;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.struct.Seq;
import arc.util.Log;
import mindustry.game.EventType;
import mindustry.gen.Building;
import mindustry.gen.Icon;
import mindustry.gen.Tex;
import mindustry.type.Category;
import mindustry.ui.Styles;
import mindustry.ui.fragments.PlacementFragment;
import mindustry.world.Block;

import java.lang.reflect.Field;

import static mindustry.Vars.*;

/**
 * 建造菜单搜索（独立实现）。
 *
 * <p>只做三件事：按内部名/显示名过滤当前合法方块、把结果放回原版方块格、
 * 清空搜索后让原版菜单重建。实现不再依赖 utility-pack 的拼音表、历史记录或设置项。
 *
 * <p>原版 PlacementFragment 会在地图加载/解锁时重建，所以这里只反射它已经存在的
 * 私有表；每次 update 检查搜索栏是否还在场景里，不在就重新插回。
 */
public class IronworksBlockSearch{

    private static final String ROW = "ironworks-search-row";
    private static final String RESULT = "ironworks-search-result";

    private static Field catTableF, blockTableF, blockPaneF, togglerF, selectedF, hoverF;
    private static Table searchRow, lastToggler;
    private static TextField field;
    private static boolean filtering;
    private static final Seq<Block> found = new Seq<>();

    private IronworksBlockSearch(){
    }

    public static void init(){
        if(headless) return;

        try{
            catTableF = PlacementFragment.class.getDeclaredField("blockCatTable");
            blockTableF = PlacementFragment.class.getDeclaredField("blockTable");
            blockPaneF = PlacementFragment.class.getDeclaredField("blockPane");
            togglerF = PlacementFragment.class.getDeclaredField("toggler");
            selectedF = PlacementFragment.class.getDeclaredField("selectedBlocks");
            hoverF = PlacementFragment.class.getDeclaredField("menuHoverBlock");
            for(Field f : new Field[]{catTableF, blockTableF, blockPaneF, togglerF, selectedF, hoverF}) f.setAccessible(true);
        }catch(Throwable t){
            Log.err("IronworksBlockSearch: 建造菜单字段不可用，搜索已禁用。", t);
            return;
        }

        Events.on(EventType.WorldLoadEvent.class, e -> Core.app.post(() -> Core.app.post(IronworksBlockSearch::inject)));
        Events.on(EventType.UnlockEvent.class, e -> {
            if(e.content instanceof Block) Core.app.post(() -> Core.app.post(IronworksBlockSearch::inject));
        });
        Events.run(EventType.Trigger.update, IronworksBlockSearch::tick);
    }

    private static PlacementFragment fragment(){
        return ui == null || ui.hudfrag == null ? null : ui.hudfrag.blockfrag;
    }

    private static <T> T field(Field f, PlacementFragment frag){
        try{
            return (T)f.get(frag);
        }catch(Exception e){
            return null;
        }
    }

    private static void tick(){
        PlacementFragment frag = fragment();
        if(frag == null) return;

        if(searchRow == null || searchRow.getScene() == null){
            inject();
            return;
        }

        if(field == null || field.getText().trim().isEmpty()){
            if(filtering) restore();
            return;
        }

        Table table = field(blockTableF, frag);
        if(filtering && table != null && table.find(RESULT) == null){
            apply(field.getText());
        }
    }

    private static void inject(){
        PlacementFragment frag = fragment();
        if(frag == null) return;

        Table cats = field(catTableF, frag);
        if(cats == null || cats.getChildren().size < 2) return;

        if(searchRow != null && searchRow.parent != null) searchRow.remove();
        if(cats.getChildren().size == 3 && ROW.equals(cats.getChildren().first().name)){
            cats.getChildren().first().remove();
        }
        if(cats.getChildren().size != 2) return;

        searchRow = new Table(Tex.pane2);
        searchRow.name = ROW;
        searchRow.left().margin(5f);
        searchRow.image(Icon.zoom).padRight(7f);
        field = searchRow.field("", IronworksBlockSearch::changed).growX().height(38f)
            .maxTextLength(64).name("ironworks-search-field").get();
        field.setMessageText(Core.bundle.get("ironworks.search.hint"));
        searchRow.button(Icon.cancel, Styles.clearNoneTogglei, IronworksBlockSearch::clear)
            .size(38f).padLeft(6f);

        Element blocks = cats.getChildren().get(0);
        Element categories = cats.getChildren().get(1);
        cats.clearChildren();
        cats.add(searchRow).colspan(2).growX().row();
        cats.add(blocks).fillY().bottom().touchable(Touchable.enabled);
        cats.add(categories).fillY().bottom().touchable(Touchable.enabled);

        // 点菜单空白处释放输入焦点，否则原版建造快捷键会被搜索框吃掉。
        Table toggler = field(togglerF, frag);
        if(toggler != null && toggler != lastToggler){
            lastToggler = toggler;
            toggler.addListener(new ClickListener(){
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                    if(field != null && Core.scene.getKeyboardFocus() == field){
                        Element hit = Core.scene.hit(x, y, true);
                        if(hit == null || !hit.isDescendantOf(searchRow)){
                            Core.scene.setKeyboardFocus(null);
                        }
                    }
                    return super.touchDown(event, x, y, pointer, button);
                }
            });
        }
    }

    private static void changed(String text){
        if(text == null || text.trim().isEmpty()) restore();
        else apply(text);
    }

    private static void apply(String text){
        PlacementFragment frag = fragment();
        if(frag == null) return;

        Table table = field(blockTableF, frag);
        if(table == null) return;

        String query = text.trim().toLowerCase();
        if(query.isEmpty()){
            restore();
            return;
        }

        filtering = true;
        found.clear();
        for(Block block : content.blocks()){
            if(!block.isVisible() || !legal(block)) continue;
            if(matches(block, query)) found.add(block);
        }
        found.sort((a, b) -> {
            int byCategory = Integer.compare(a.category.ordinal(), b.category.ordinal());
            if(byCategory != 0) return byCategory;
            return a.localizedName.compareTo(b.localizedName);
        });

        table.clear();
        table.top().left().margin(6f);

        if(found.isEmpty()){
            table.add(Core.bundle.get("ironworks.search.noresults")).name(RESULT)
                .color(Color.lightGray).pad(12f).left();
            table.act(0f);
            return;
        }

        int column = 0;
        for(Block block : found){
            if(column++ % 4 == 0) table.row();
            ImageButton button = table.button(new TextureRegionDrawable(block.uiIcon), Styles.selecti,
                () -> choose(frag, block)).size(48f).name(RESULT).get();
            button.resizeImage(iconMed);
            button.update(() -> {
                Building core = player.core();
                boolean affordable = state.rules.infiniteResources
                    || (core != null && core.items.has(block.requirements, state.rules.buildCostMultiplier));
                Color tint = affordable && player.isBuilder() ? Color.white : Color.gray;
                if(!block.isPlaceable()) tint = Color.darkGray;
                for(Element child : button.getChildren()) child.setColor(tint);
                button.setChecked(control.input.block == block);
            });
            button.hovered(() -> setHover(frag, block));
            button.exited(() -> {
                if(getHover(frag) == block) setHover(frag, null);
            });
        }

        while(column++ % 4 != 0) table.add().size(48f);
        table.act(0f);

        ScrollPane pane = field(blockPaneF, frag);
        if(pane != null) pane.setScrollYForce(0f);
    }

    private static boolean matches(Block block, String query){
        return block.name.toLowerCase().contains(query)
            || block.localizedName.toLowerCase().contains(query);
    }

    private static boolean legal(Block block){
        return block.unlockedNowHost() && block.placeablePlayer
            && block.environmentBuildable() && block.supportsEnv(state.rules.env);
    }

    private static void choose(PlacementFragment frag, Block block){
        control.input.block = control.input.block == block ? null : block;
        ObjectMap<Category, Block> selected = field(selectedF, frag);
        if(selected != null) selected.put(block.category, control.input.block);
        frag.currentCategory = block.category;
        clear();
    }

    private static void clear(){
        if(field != null) field.setText("");
        if(Core.scene != null) Core.scene.setKeyboardFocus(null);
        restore();
    }

    private static void restore(){
        if(!filtering) return;
        filtering = false;
        PlacementFragment frag = fragment();
        if(frag == null) return;
        Core.app.post(() -> {
            if(!filtering && ui != null && ui.hudfrag != null && ui.hudfrag.blockfrag != null){
                ui.hudfrag.blockfrag.rebuild();
            }
        });
    }

    private static void setHover(PlacementFragment frag, Block block){
        try{
            hoverF.set(frag, block);
        }catch(Exception ignored){
        }
    }

    private static Block getHover(PlacementFragment frag){
        return field(hoverF, frag);
    }
}
