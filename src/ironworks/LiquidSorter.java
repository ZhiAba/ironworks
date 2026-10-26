package ironworks;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.gen.Building;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.blocks.ItemSelection;
import mindustry.world.meta.BlockGroup;

import static mindustry.Vars.content;
import static mindustry.Vars.tilesize;
import static mindustry.Vars.headless;
import static mindustry.Vars.renderer;

/**
 * 液体分类器 / 反向分类器。
 *
 * <p>工作方式对应原版物品分类器：设定一种液体后，匹配液体沿进液方向直通，
 * 不匹配液体转向左右两侧；反向分类器反过来，把设定液体分流到两侧、放行其余液体。
 * 液体本身不是“瞬时物品”，所以这里通过 {@link #getLiquidDestination} 把上游
 * 液体直接导向目标，同时保留 {@link #acceptLiquid}/{@link #handleLiquid} 作为
 * 直接注液（泵、液体源）时的兼容入口。</p>
 */
public class LiquidSorter extends Block{
    /** 未配置时显示的叉号；复用原版图集。 */
    public TextureRegion cross;
    /** true = 反向分类器：设定液体去两侧，其余液体直通。 */
    public boolean invert;

    public LiquidSorter(String name){
        super(name);
        update = false;
        destructible = true;
        solid = true;
        underBullets = true;
        group = BlockGroup.liquids;
        configurable = true;
        saveConfig = true;
        clearOnDoubleTap = true;
        drawCached = true;
        drawDynamic = false;
        noUpdateDisabled = true;
        liquidCapacity = 20f;
        hasLiquids = true;
        outputsLiquid = true;

        cross = Core.atlas == null ? null : Core.atlas.find("cross-full");

        config(Liquid.class, (LiquidSorterBuild build, Liquid liquid) -> build.sortLiquid = liquid);
        configClear((LiquidSorterBuild build) -> build.sortLiquid = null);
    }

    @Override
    public TextureRegion[] icons(){
        return new TextureRegion[]{region};
    }

    @Override
    public int minimapColor(mindustry.world.Tile tile){
        LiquidSorterBuild build = tile.build instanceof LiquidSorterBuild b ? b : null;
        return build == null || build.sortLiquid == null ? 0 : build.sortLiquid.color.rgba();
    }

    public class LiquidSorterBuild extends Building{
        public Liquid sortLiquid;

        @Override
        public void configured(mindustry.gen.Unit player, Object value){
            super.configured(player, value);
            if(!headless && renderer != null) renderer.minimap.update(tile);
        }

        @Override
        public void draw(){
            if(sortLiquid == null){
                if(cross != null) Draw.rect(cross, x, y);
            }else{
                Draw.color(sortLiquid.color);
                Fill.square(x, y, tilesize / 2f - 0.00001f);
                Draw.color(Color.white);
            }
            super.draw();
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            Building target = destination(source, liquid, false);
            return target != null && target != this && target.team == team && target.acceptLiquid(this, liquid);
        }

        @Override
        public void handleLiquid(Building source, Liquid liquid, float amount){
            Building target = destination(source, liquid, true);
            if(target != null && target != this && target.team == team && target.acceptLiquid(this, liquid)){
                target.handleLiquid(this, liquid, amount);
            }else{
                // 直接注液且暂时无处可去时先存住，下一 tick 再按当前方向尝试。
                liquids.add(liquid, Math.min(amount, liquidCapacity - liquids.get(liquid)));
            }
        }

        @Override
        public Building getLiquidDestination(Building source, Liquid liquid){
            Building target = destination(source, liquid, false);
            return target == null ? this : target;
        }

        @Override
        public void updateTile(){
            // 正常管道会在 getLiquidDestination 阶段直通；这里只处理直接注液留下的暂存。
            if(liquids.currentAmount() > 0.01f){
                Liquid liquid = liquids.current();
                Building a = destination(this, liquid, true);
                if(a != null && a != this) transferLiquid(a, Math.min(liquids.get(liquid), 20f), liquid);
            }
        }

        /** 根据进液侧和过滤条件选择出口；flip 只用于左右出口的确定性轮换。 */
        private Building destination(Building source, Liquid liquid, boolean flip){
            if(source == null || liquid == null || !enabled) return this;
            int dir = source.relativeTo(tile.x, tile.y);
            if(dir < 0) return this;

            boolean matches = sortLiquid != null && liquid == sortLiquid;
            boolean straight = (matches != invert);
            if(straight){
                Building ahead = nearby(dir);
                if(valid(ahead, liquid)) return ahead;
            }

            Building left = nearby(Mathf.mod(dir - 1, 4));
            Building right = nearby(Mathf.mod(dir + 1, 4));
            boolean lc = valid(left, liquid);
            boolean rc = valid(right, liquid);
            if(lc && !rc) return left;
            if(rc && !lc) return right;
            if(!lc && !rc) return this;
            Building chosen = (rotation & (1 << dir)) == 0 ? left : right;
            if(flip) rotation ^= (1 << dir);
            return chosen;
        }

        private boolean valid(Building target, Liquid liquid){
            return target != null && target != this && target.team == team
                && target.block.hasLiquids && target.acceptLiquid(this, liquid);
        }

        @Override
        public Liquid config(){
            return sortLiquid;
        }

        @Override
        public void buildConfiguration(Table table){
            ItemSelection.buildTable(LiquidSorter.this, table, content.liquids(),
                () -> sortLiquid, value -> configure(value), 5, 4);
        }

        @Override
        public byte version(){
            return 1;
        }

        @Override
        public void write(Writes write){
            super.write(write);
            write.s(sortLiquid == null ? -1 : sortLiquid.id);
        }

        @Override
        public void read(Reads read, byte revision){
            super.read(read, revision);
            sortLiquid = content.liquid(read.s());
        }
    }
}
