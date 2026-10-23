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
import arc.scene.ui.ButtonGroup;
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

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;

import static mindustry.Vars.*;

/**
 * 方块搜索（内嵌自 utility-pack 的 BlockSearch，已改名/改设置键，避免与 Silicon 等模组冲突）。
 *
 * <p>在原版建造菜单顶部插一条搜索栏：
 * <ul>
 *   <li>跨**所有分类**搜索当前合法的方块（判定规则与原版菜单一致）；</li>
 *   <li>支持内部名/显示名，中文名支持全拼与首字母模糊匹配（拼音表打进 jar：/pinyin.txt）；</li>
 *   <li>最近 4 条搜索记录可点回填。</li>
 * </ul>
 *
 * <p>原版 PlacementFragment 会在地图加载/解锁新方块时整体重建，
 * 所以这里反射拿它的私有表，并在每次重建后重新插回搜索栏（update 里自愈）。
 */
public class IronworksBlockSearch{

    private static final String RESULT_NAME = "ironworks-search-result";
    private static final String ROW_NAME = "ironworks-block-search";

    //PlacementFragment 的私有字段（160.1 / 160.3 字段名一致）
    private static Field blockCatTableF, blockTableF, blockPaneF, togglerF, selectedBlocksF, menuHoverBlockF;
    private static Table lastToggler;

    private static Table searchRow, historyList;
    private static Element historyButton;
    private static TextField field;
    private static boolean searching;
    private static final Seq<Block> results = new Seq<>();

    /** BMP 字符 → 无调拼音，懒加载 */
    private static String[] pinyin;
    private static final ObjectMap<Block, String[]> pinyinCache = new ObjectMap<>();

    /** 最近搜索，存 settings */
    private static Seq<String> history = new Seq<>();
    private static boolean historyLoaded;

    private IronworksBlockSearch(){
    }

    /** 在 mod 的 init() 里调用。 */
    public static void init(){
        if(headless) return;

        try{
            blockCatTableF = PlacementFragment.class.getDeclaredField("blockCatTable");
            blockTableF = PlacementFragment.class.getDeclaredField("blockTable");
            blockPaneF = PlacementFragment.class.getDeclaredField("blockPane");
            togglerF = PlacementFragment.class.getDeclaredField("toggler");
            selectedBlocksF = PlacementFragment.class.getDeclaredField("selectedBlocks");
            menuHoverBlockF = PlacementFragment.class.getDeclaredField("menuHoverBlock");
            for(Field f : new Field[]{blockCatTableF, blockTableF, blockPaneF, togglerF, selectedBlocksF, menuHoverBlockF}){
                f.setAccessible(true);
            }
        }catch(Throwable t){
            Log.err("IronworksBlockSearch: 无法访问建造菜单内部字段，方块搜索已禁用。", t);
            return;
        }

        //原版会在地图加载/解锁时重建整个菜单 —— 重建后再插回去
        Events.on(EventType.WorldLoadEvent.class, e -> Core.app.post(() -> Core.app.post(IronworksBlockSearch::inject)));
        Events.on(EventType.UnlockEvent.class, e -> {
            if(e.content instanceof Block) Core.app.post(() -> Core.app.post(IronworksBlockSearch::inject));
        });

        //自愈：菜单被重建后搜索栏还活着；搜索中被原版刷新覆盖则重新套用过滤
        Events.run(EventType.Trigger.update, IronworksBlockSearch::update);
    }

    static PlacementFragment fragment(){
        if(ui == null || ui.hudfrag == null) return null;
        return ui.hudfrag.blockfrag;
    }

    @SuppressWarnings("unchecked")
    static <T> T getField(Field f, PlacementFragment frag){
        try{
            return (T)f.get(frag);
        }catch(Exception e){
            return null;
        }
    }

    static void update(){
        PlacementFragment frag = fragment();
        if(frag == null) return;

        //原版重建后旧搜索栏会成为孤儿：还挂着 parent，但已经不在 stage 上 → 用 getScene() 判断
        if(searchRow == null || searchRow.getScene() == null){
            inject();
            return;
        }

        //「显示搜索历史」开关热更新
        if(historyButton != null && historyButton.visible != showHistoryEnabled()){
            historyButton.visible = showHistoryEnabled();
            if(!showHistoryEnabled()) closeHistory();
        }

        if(field != null && field.getText().trim().isEmpty()){
            if(searching) restore();
            return;
        }

        //搜索中被原版表格覆盖（例如搜索后用了快捷键）→ 重新套用
        if(searching && field != null){
            Table blockTable = getField(blockTableF, frag);
            if(blockTable != null && blockTable.find(RESULT_NAME) == null){
                applyFilter(field.getText());
            }
        }
    }

    static void inject(){
        PlacementFragment frag = fragment();
        if(frag == null) return;

        Table blockCatTable = getField(blockCatTableF, frag);
        if(blockCatTable == null) return;

        if(searchRow != null && searchRow.parent != null) searchRow.remove();
        if(blockCatTable.getChildren().size == 3 && ROW_NAME.equals(blockCatTable.getChildren().first().name)){
            blockCatTable.getChildren().first().remove();
        }
        if(blockCatTable.getChildren().size != 2) return;

        //搜索行：放大镜 + 输入框 + 清除 + 历史下拉
        searchRow = new Table(Tex.pane2);
        searchRow.name = ROW_NAME;
        searchRow.top().left().margin(4f);
        searchRow.image(Icon.zoom).padRight(8f);
        field = searchRow.field("", IronworksBlockSearch::onChanged).growX().height(38f)
            .name("ironworks-search-field").maxTextLength(64).get();
        field.setMessageText(Core.bundle.get("ironworks.search.hint"));
        searchRow.button(Icon.cancel, Styles.clearNoneTogglei, IronworksBlockSearch::clearSearch)
            .size(38f).padLeft(6f).name("ironworks-search-clear");
        historyButton = searchRow.button(Icon.downOpen, Styles.clearNonei, IronworksBlockSearch::toggleHistory)
            .size(38f).padLeft(4f).name("ironworks-search-history")
            .tooltip(Core.bundle.get("ironworks.search.history")).get();
        historyButton.visible = showHistoryEnabled();
        searchRow.row();

        historyList = new Table(Tex.pane2);
        historyList.name = "ironworks-search-history-list";
        historyList.top().left();
        historyList.visible = false;
        searchRow.add(historyList).colspan(4).growX().padTop(2f);

        //把原版的「方块格 / 分类」两张表重新挂到搜索栏下面
        Table blocksSelect = (Table)blockCatTable.getChildren().get(0);
        Table categories = (Table)blockCatTable.getChildren().get(1);
        blockCatTable.clearChildren();
        blockCatTable.add(searchRow).colspan(2).growX().row();
        blockCatTable.add(blocksSelect).fillY().bottom().touchable(Touchable.enabled);
        blockCatTable.add(categories).fillY().bottom().touchable(Touchable.enabled);

        //点搜索栏外面释放输入焦点，原版的建造快捷键才能继续用
        Table toggler = getField(togglerF, frag);
        if(toggler != null && toggler != lastToggler){
            lastToggler = toggler;
            toggler.addListener(new ClickListener(){
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                    Element target = Core.scene.hit(x, y, true);
                    if(target == null || !target.isDescendantOf(searchRow)){
                        closeHistory();
                        if(field != null && Core.scene.getKeyboardFocus() == field){
                            Core.scene.setKeyboardFocus(null);
                        }
                    }
                    return super.touchDown(event, x, y, pointer, button);
                }
            });
        }
    }

    static void onChanged(String text){
        if(text == null || text.trim().isEmpty()){
            restore();
            closeHistory();
        }else{
            closeHistory();
            applyFilter(text);
        }
    }

    static void applyFilter(String text){
        PlacementFragment frag = fragment();
        if(frag == null) return;

        Table blockTable = getField(blockTableF, frag);
        if(blockTable == null) return;

        String q = text.trim().replaceAll(" +", " ").toLowerCase();
        if(q.isEmpty()){
            restore();
            return;
        }

        searching = true;

        //跨分类收集所有合法方块（规则与原版菜单一致）
        results.clear();
        for(Block block : content.blocks()){
            if(!block.isVisible() || !isLegal(block)) continue;
            if(matches(block, q)) results.add(block);
        }
        results.sort((a, b) -> {
            int c = Integer.compare(a.category.ordinal(), b.category.ordinal());
            if(c != 0) return c;
            c = Boolean.compare(!a.isPlaceable(), !b.isPlaceable());
            if(c != 0) return c;
            return a.localizedName.compareTo(b.localizedName);
        });

        blockTable.clear();
        blockTable.top().margin(5);

        if(results.isEmpty()){
            blockTable.add(Core.bundle.get("ironworks.search.noresults")).color(Color.lightGray).pad(10f).left();
            blockTable.act(0f);
            return;
        }

        int index = 0;
        ButtonGroup<ImageButton> group = new ButtonGroup<>();
        group.setMinCheckCount(0);

        for(Block block : results){
            if(index++ % 4 == 0) blockTable.row();

            ImageButton button = blockTable.button(new TextureRegionDrawable(block.uiIcon), Styles.selecti,
                () -> select(frag, block)).size(46f).group(group).name(RESULT_NAME).get();
            button.resizeImage(iconMed);

            button.update(() -> {
                Building core = player.core();
                Color color = (state.rules.infiniteResources
                    || (core != null && core.items.has(block.requirements, state.rules.buildCostMultiplier)))
                    && player.isBuilder() ? Color.white : Color.gray;
                button.forEach(elem -> elem.setColor(color));
                button.setChecked(control.input.block == block);
                if(!block.isPlaceable()){
                    button.forEach(elem -> elem.setColor(Color.darkGray));
                }
            });

            button.hovered(() -> setMenuHover(frag, block));
            button.exited(() -> {
                if(getMenuHover(frag) == block) setMenuHover(frag, null);
            });
        }
        if(index < 4){
            for(int i = 0; i < 4 - index; i++) blockTable.add().size(46f);
        }
        blockTable.act(0f);

        ScrollPane pane = getField(blockPaneF, frag);
        if(pane != null) pane.setScrollYForce(0f);
    }

    static boolean matches(Block block, String q){
        if(block.name.toLowerCase().contains(q)) return true;
        if(block.localizedName.toLowerCase().contains(q)) return true;

        //中文名：全拼 / 首字母；2 字以上再放开子序列模糊
        String[] py = pinyinOf(block);
        if(py != null){
            if(py[0].contains(q) || py[1].contains(q)) return true;
            if(q.length() >= 2 && (isSubsequence(q, py[0]) || isSubsequence(q, py[1]))) return true;
        }
        return false;
    }

    /** 显示名的全拼与首字母；没有汉字时返回 null。 */
    static String[] pinyinOf(Block block){
        String[] cached = pinyinCache.get(block);
        if(cached != null) return cached;

        String[] table = pinyinMap();
        if(table == null) return null;

        StringBuilder full = new StringBuilder(), init = new StringBuilder();
        for(char c : block.localizedName.toCharArray()){
            String py = table[c];
            if(py == null) continue;
            full.append(py);
            init.append(py.charAt(0));
        }
        if(full.length() == 0) return null;

        String[] result = {full.toString(), init.toString()};
        pinyinCache.put(block, result);
        return result;
    }

    static boolean isSubsequence(String q, String s){
        int i = 0;
        for(int j = 0; j < s.length() && i < q.length(); j++){
            if(s.charAt(j) == q.charAt(i)) i++;
        }
        return i == q.length();
    }

    /** 拼音表：jar 内 /pinyin.txt，每行 "HEX 拼音"。 */
    static String[] pinyinMap(){
        if(pinyin == null){
            String[] table = new String[0x10000];
            try(InputStream in = IronworksBlockSearch.class.getResourceAsStream("/pinyin.txt")){
                if(in == null){
                    Log.warn("IronworksBlockSearch: 未找到拼音表，拼音搜索已禁用。");
                }else{
                    BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                    String line;
                    while((line = r.readLine()) != null){
                        int sp = line.indexOf(' ');
                        if(sp <= 0) continue;
                        try{
                            int cp = Integer.parseInt(line.substring(0, sp), 16);
                            if(cp < table.length) table[cp] = line.substring(sp + 1).trim();
                        }catch(Exception ignored){
                        }
                    }
                }
            }catch(Exception e){
                Log.err("IronworksBlockSearch: 拼音表加载失败", e);
            }
            pinyin = table;
        }
        return pinyin;
    }

    /** 与原版菜单同样的合法性判定。 */
    static boolean isLegal(Block block){
        return block.unlockedNowHost() && block.placeablePlayer && block.environmentBuildable() &&
            block.supportsEnv(state.rules.env);
    }

    static void select(PlacementFragment frag, Block block){
        control.input.block = control.input.block == block ? null : block;
        ObjectMap<Category, Block> selected = getField(selectedBlocksF, frag);
        if(selected != null) selected.put(block.category, control.input.block);
        frag.currentCategory = block.category; //搜索清空后，分类网格回到这个方块的分类

        //记住这次搜索词，下次能点历史回填
        if(field != null && !field.getText().trim().isEmpty()){
            addHistory(field.getText().trim());
        }

        if(clearOnSelectEnabled()){
            clearSearch();
        }
    }

    static void clearSearch(){
        if(field != null) field.setText("");
        closeHistory();
        if(Core.scene != null) Core.scene.setKeyboardFocus(null);
        restore();
    }

    /** 恢复原版分类网格（重建整个 fragment，下一帧执行）。 */
    static void restore(){
        if(!searching) return;
        searching = false;
        PlacementFragment frag = fragment();
        if(frag == null) return;
        Core.app.post(() -> {
            //如果重建排队的瞬间又开始了新的搜索（例如刚点了历史记录），就别把结果抹掉
            if(!searching && ui != null && ui.hudfrag != null && ui.hudfrag.blockfrag != null){
                ui.hudfrag.blockfrag.rebuild();
            }
        });
    }

    // ---- 设置项 ----

    static boolean showHistoryEnabled(){
        return Core.settings.getBool("ironworks.search.showHistory", true);
    }

    static boolean clearOnSelectEnabled(){
        return Core.settings.getBool("ironworks.search.clearOnSelect", true);
    }

    // ---- 最近搜索 ----

    static Seq<String> history(){
        if(!historyLoaded){
            historyLoaded = true;
            try{
                history = Core.settings.getJson("ironworks-search-history", Seq.class, String.class, Seq::new);
            }catch(Exception e){
                history = new Seq<>();
            }
            if(history == null) history = new Seq<>();
        }
        return history;
    }

    static void addHistory(String q){
        if(!showHistoryEnabled()) return;
        Seq<String> h = history();
        if(q.isEmpty()) return;
        h.remove(q);
        h.insert(0, q);
        while(h.size > 4) h.pop();
        try{
            Core.settings.putJson("ironworks-search-history", String.class, h);
        }catch(Exception ignored){
        }
        if(historyList != null && historyList.getChildren().size > 0) rebuildHistoryList();
    }

    static void toggleHistory(){
        if(historyList == null) return;
        if(!showHistoryEnabled()){
            closeHistory();
            return;
        }
        if(historyList.getChildren().size > 0){
            closeHistory();
        }else{
            rebuildHistoryList();
        }
    }

    static void closeHistory(){
        if(historyList != null){
            historyList.clearChildren();
            historyList.visible = false;
        }
    }

    static void rebuildHistoryList(){
        if(historyList == null) return;
        historyList.clearChildren();
        historyList.visible = true;
        historyList.top().left().margin(4f);

        Seq<String> h = history();
        if(h.isEmpty()){
            historyList.add(Core.bundle.get("ironworks.search.nohistory")).color(Color.lightGray).pad(6f);
            return;
        }

        for(String q : h){
            String fq = q;
            historyList.button(q, Styles.flatBordert, () -> {
                if(field != null){
                    field.setText(fq);
                    if(Core.scene != null) Core.scene.setKeyboardFocus(field);
                }
                applyFilter(fq);
                closeHistory();
            }).growX().pad(3f);
            historyList.row();
        }
    }

    static void setMenuHover(PlacementFragment frag, Block block){
        try{
            menuHoverBlockF.set(frag, block);
        }catch(Exception ignored){
        }
    }

    static Block getMenuHover(PlacementFragment frag){
        return getField(menuHoverBlockF, frag);
    }
}
