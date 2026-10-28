package ironworks;

import arc.Core;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import arc.scene.ui.layout.Table;
import arc.util.Eachable;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.entities.units.BuildPlan;
import mindustry.gen.Building;
import mindustry.gen.Unit;
import mindustry.type.Liquid;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.ItemSelection;
import mindustry.world.meta.BlockGroup;

import static mindustry.Vars.*;

/**
 * 液体分类器 / 反向分类器。
 *
 * <p>工作方式对应原版物品分类器：设定一种液体后，匹配液体沿进液方向直通，
 * 不匹配液体转向左右两侧；反向分类器反过来，把设定液体分流到两侧、放行其余液体。
 * 两侧都可用时通过 {@link #handleLiquid} 尽量均分，而不是只选一边。</p>
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
    public void drawPlanConfig(BuildPlan plan, Eachable<BuildPlan> list){
        drawPlanConfigCenter(plan, plan.config, "center", true);
    }

    @Override
    public int minimapColor(Tile tile){
        LiquidSorterBuild build = tile.build instanceof LiquidSorterBuild b ? b : null;
        return build == null || build.sortLiquid == null ? 0 : build.sortLiquid.color.rgba();
    }

    public class LiquidSorterBuild extends Building{
        public Liquid sortLiquid;

        @Override
        public void configured(Unit player, Object value){
            super.configured(player, value);
            if(!headless && renderer != null){
                recache();
                renderer.minimap.update(tile);
            }
        }

        @Override
        public void draw(){
            // 先画方块本体，再把液体颜色/图标盖在中心，配置变化时才能看见。
            super.draw();
            if(sortLiquid == null){
                if(cross != null) Draw.rect(cross, x, y);
            }else{
                Draw.color(sortLiquid.color);
                Fill.circle(x, y, 4.5f);
                Draw.color();
                if(sortLiquid.uiIcon != null && sortLiquid.uiIcon.found()){
                    Draw.rect(sortLiquid.uiIcon, x, y, 8f, 8f);
                }
            }
        }

        @Override
        public void drawSelect(){
            super.drawSelect();
            drawItemSelection(sortLiquid);
        }

        @Override
        public boolean acceptLiquid(Building source, Liquid liquid){
            if(source == null || liquid == null || !enabled) return false;
            if(straight(liquid)){
                Building ahead = straightTarget(source, liquid);
                return ahead != null && ahead.team == team && ahead.acceptLiquid(this, liquid);
            }
            Building[] sides = sideTargets(source, liquid);
            return sides[0] != null || sides[1] != null;
        }

        @Override
        public void handleLiquid(Building source, Liquid liquid, float amount){
            if(source == null || liquid == null || amount <= 0f) return;

            if(straight(liquid)){
                Building ahead = straightTarget(source, liquid);
                if(ahead != null) ahead.handleLiquid(this, liquid, amount);
                return;
            }

            // 两侧都通时先各送一半；如果一边满了，剩下的尽量补到另一边。
            Building[] sides = sideTargets(source, liquid);
            float sent = send(sides[0], liquid, amount / 2f);
            sent += send(sides[1], liquid, amount - sent);
            float remain = amount - sent;
            if(remain > 0.001f){
                send(sides[0], liquid, remain);
            }
        }

        @Override
        public Building getLiquidDestination(Building source, Liquid liquid){
            if(source == null || liquid == null || !enabled) return this;

            if(straight(liquid)){
                Building ahead = straightTarget(source, liquid);
                return ahead != null ? ahead : this;
            }

            // 侧向输出需要同时拿到左右两个目标，交给 handleLiquid 均分。
            return this;
        }

        private boolean straight(Liquid liquid){
            boolean matches = sortLiquid != null && liquid == sortLiquid;
            return matches != invert;
        }

        private Building straightTarget(Building source, Liquid liquid){
            int dir = source.relativeTo(tile.x, tile.y);
            if(dir < 0) return null;
            Building ahead = nearby(dir);
            return valid(ahead, liquid) ? ahead : null;
        }

        private Building[] sideTargets(Building source, Liquid liquid){
            Building[] out = new Building[2];
            int dir = source.relativeTo(tile.x, tile.y);
            if(dir < 0) return out;

            Building left = nearby(Mathf.mod(dir - 1, 4));
            Building right = nearby(Mathf.mod(dir + 1, 4));
            if(valid(left, liquid)) out[0] = left;
            if(valid(right, liquid)) out[1] = right;
            return out;
        }

        private boolean valid(Building target, Liquid liquid){
            return target != null && target != this && target.team == team
                && target.block.hasLiquids && target.acceptLiquid(this, liquid);
        }

        private float send(Building target, Liquid liquid, float amount){
            if(target == null || amount <= 0f) return 0f;

            float room = target.block.liquidCapacity - target.liquids.get(liquid);
            float take = Math.min(amount, Math.max(0f, room));
            if(take <= 0.001f || !target.acceptLiquid(this, liquid)) return 0f;

            target.handleLiquid(this, liquid, take);
            return take;
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
