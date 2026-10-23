package ironworks;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Angles;
import arc.math.Mathf;
import arc.scene.ui.Label;
import arc.scene.ui.TextButton;
import arc.scene.ui.layout.Table;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.Time;
import arc.util.io.Reads;
import arc.util.io.Writes;
import mindustry.Vars;
import mindustry.content.Items;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.game.EventType;
import mindustry.gen.Unit;
import mindustry.graphics.Drawf;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.type.Category;
import mindustry.ui.Styles;
import mindustry.world.Block;
import mindustry.world.blocks.defense.turrets.Turret;
import mindustry.world.meta.Stat;
import mindustry.world.meta.StatValue;

import static mindustry.type.ItemStack.with;

/**
 * 自动雷达：3×3，索敌范围 103×103 格（以自身为中心）。
 *
 * <p>点击方块打开面板，选择索敌方式（血量上限最高 / 距离最近 / 最密集 / 当前血量最低），
 * 然后点击炮塔把它加入链接（最多 10 个）。雷达每 12 tick 重新选一次目标，
 * 并把目标直接写进链接炮塔的 {@code target}；炮塔自身的索敌计时器会被推到很远的未来，
 * 所以它们不会再自己挑目标。
 *
 * <p>炮塔**不会**因此获得超射程：原版 {@code TurretBuild.updateTile()} 开火前仍有
 * {@code within(target, range())} 判定，超出自己射程只会瞄准、不会开火。
 *
 * <p>性能：只在 12 tick 间隔扫描 {@code Groups.unit}；最密集模式用 8×8 格的计数网格
 * （复用 int[1024]），不搞 O(n²)；标记每帧只画 4 个三角形。
 */
public class IronworksRadar{

    public static Radar radar;

    /** 所有在跑的雷达（全局画标记用；按帧遍历的只有雷达，不是全图建筑）。 */
    static final Seq<Radar.RadarBuild> active = new Seq<>();

    /** 103×103 格的半宽（格）。 */
    public static final float rangeTiles = 51.5f;
    public static final float rangePx = rangeTiles * 8f;
    /** 可链接炮塔的最大距离（格）。 */
    public static final float linkRangeTiles = 30f;
    public static final int maxLinks = 10;
    /** 重新选目标的间隔（tick）。 */
    public static final float retargetTicks = 12f;

    public static final int modeMaxHp = 0, modeNearest = 1, modeDense = 2, modeLowHp = 3;
    public static final int configClear = -10;

    public static final Color markColor = Color.valueOf("ff2b2b");
    public static final Color rangeColor = Color.valueOf("ff5c5c");
    public static final Color linkColor = Color.valueOf("ff8a8a");

    static final String[] modeKeys = {"maxhp", "nearest", "dense", "lowhp"};

    public static void load(){
        radar = new Radar("auto-radar");
        radar.requirements(Category.effect,
            with(IronworksSilicon.quantumCore, 2, IronworksMaterials.siliconCarbide, 30,
                 Items.silicon, 200, Items.metaglass, 120));
        radar.health = 1800;

        // 标记必须画在目标身上，而不是只在雷达上屏时才画：用全局 draw 事件遍历少量雷达。
        Events.run(EventType.Trigger.draw, () -> {
            for(int i = 0; i < active.size; i++){
                Radar.RadarBuild b = active.get(i);
                if(b != null && b.isValid()) b.drawMarker();
            }
        });
        Events.on(EventType.ResetEvent.class, e -> active.clear());
    }

    public static class Radar extends Block{
        public Radar(String name){
            super(name);
            size = 3;
            solid = true;
            update = true;
            hasItems = false;
            hasLiquids = false;
            configurable = true;
            buildType = RadarBuild::new;

            config(Integer.class, (RadarBuild b, Integer v) -> b.applyConfig(v));
        }

        @Override
        public void load(){
            super.load();
            uiIcon = fullIcon = Core.atlas.find(name + "-ui", region);
        }

        @Override
        public void setStats(){
            super.setStats();

            stats.add(new Stat("ironworks-radar-specs"), (StatValue)table -> {
                IronworksStats.specRow(table, Icon.map,
                    Core.bundle.get("radar.spec.range"),
                    "103×103", Pal.accent);
                IronworksStats.specRow(table, Icon.link,
                    Core.bundle.get("radar.spec.links"),
                    String.valueOf(maxLinks), Pal.accent);
                IronworksStats.specRow(table, Icon.eye,
                    Core.bundle.get("radar.spec.modes"),
                    Core.bundle.get("radar.spec.modes.value"), Color.valueOf("ff8a8a"));
            });

            stats.add(new Stat("ironworks-radar-notes"), (StatValue)table ->
                IronworksStats.notes(table, Color.valueOf("ffb066"),
                    Core.bundle.get("radar.note.1"),
                    Core.bundle.get("radar.note.2")));
        }

        public class RadarBuild extends Building{
            /** 索敌方式，见 IronworksRadar.mode*。 */
            public int mode = modeMaxHp;
            /** 已链接炮塔的 pos，最多 maxLinks 个。 */
            public IntSeq links = new IntSeq();
            /** 当前目标（不存档，读档后自动重选）。 */
            public Unit target;
            /** 距离下次重选还有多少 tick。 */
            public float retargetTimer;
            /** 配置面板打开时画 103×103 框和连线。 */
            public boolean showRange;

            /** 8×8 格密度网格：地图 256 格 = 32×32 个格子。 */
            final int[] denseCells = new int[32 * 32];

            public void applyConfig(int v){
                if(v == configClear){
                    releaseAll();
                    return;
                }
                if(v <= -1 && v >= -4){
                    mode = -v - 1;
                    target = null;
                    retargetTimer = 0f;
                    return;
                }
                if(v >= 0){
                    toggleLink(Vars.world.build(v));
                }
            }

            /** 测试/UI 用：加入或移除一个炮塔。 */
            public void toggleLink(Building b){
                if(!(b instanceof Turret.TurretBuild t)) return;
                if(b == this || !b.within(this, linkRangeTiles * 8f)) return;

                int pos = b.pos();
                if(links.contains(pos)){
                    links.removeValue(pos);
                    release(t);
                    return;
                }
                // 新链接只收友军；已经链接的炮塔之后被改成别的队伍也保持链接（不自动断开）
                if(t.team != team) return;
                if(links.size >= maxLinks) return;
                links.add(pos);
            }

            void release(Turret.TurretBuild t){
                t.target = null;
                // 解除接管：让 elapsed 超过 targetInterval，下一 tick 炮塔恢复自己索敌
                t.timer().getTimes()[((Turret)t.block).timerTarget] = Time.time - 1000f;
            }

            void releaseAll(){
                for(int i = 0; i < links.size; i++){
                    Building b = Vars.world.build(links.get(i));
                    if(b instanceof Turret.TurretBuild t) release(t);
                }
                links.clear();
            }

            @Override
            public void updateTile(){
                if(!active.contains(this)) active.add(this);
                retargetTimer -= delta();

                if(target != null && !candidate(target)){
                    target = null;
                    retargetTimer = 0f;
                }

                if(retargetTimer <= 0f){
                    target = findTarget();
                    // 错开不同雷达的扫描帧，避免同 tick 一起扫全图
                    retargetTimer = retargetTicks + (pos() % 5);
                }

                updateLinks();
            }

            boolean candidate(Unit u){
                return u != null && u.isValid() && !u.dead() && u.team != team
                    && Math.abs(u.x - x) <= rangePx && Math.abs(u.y - y) <= rangePx;
            }

            int cellIndex(Unit u){
                int cx = Mathf.clamp(u.tileX() >> 3, 0, 31);
                int cy = Mathf.clamp(u.tileY() >> 3, 0, 31);
                return cx + cy * 32;
            }

            Unit findTarget(){
                if(mode == modeDense){
                    java.util.Arrays.fill(denseCells, 0);
                    for(Unit u : Groups.unit){
                        if(candidate(u)) denseCells[cellIndex(u)]++;
                    }
                }

                Unit best = null;
                float bestScore = -Float.MAX_VALUE;

                for(Unit u : Groups.unit){
                    if(!candidate(u)) continue;

                    float score;
                    switch(mode){
                        case modeNearest -> score = -u.dst2(x, y);
                        case modeLowHp -> score = -u.health;
                        case modeDense -> score = denseCells[cellIndex(u)] * 1e7f - u.dst2(x, y);
                        default -> score = u.maxHealth;
                    }

                    if(score > bestScore){
                        bestScore = score;
                        best = u;
                    }
                }
                return best;
            }

            void updateLinks(){
                for(int i = links.size - 1; i >= 0; i--){
                    Building b = Vars.world.build(links.get(i));
                    if(!(b instanceof Turret.TurretBuild t) || t.dead()){
                        links.removeIndex(i);
                        continue;
                    }

                    Turret turret = (Turret)t.block;
                    // 雷达目标超出炮塔的空中/地面过滤器时，炮塔不接这个目标
                    boolean canHit = target != null
                        && (target.isFlying() ? turret.targetAir : turret.targetGround);
                    if(canHit){
                        var ammo = t.peekAmmo();
                        if(ammo != null) canHit = target.isFlying() ? ammo.collidesAir : ammo.collidesGround;
                    }
                    t.target = canHit ? target : null;

                    // 每 tick 把炮塔的索敌计时器按到“刚刚”：elapsed=0 < targetInterval，它不会自己找目标。
                    // 绝不能设成未来——Interval.check 会把“未来”判成时钟回拨并立刻触发（实测会抢目标）。
                    t.timer().getTimes()[((Turret)t.block).timerTarget] = Time.time;
                }
            }

            @Override
            public boolean onConfigureBuildTapped(Building other){
                if(other != null && other.block instanceof Turret){
                    toggleLink(other);
                    return false; // 面板保持打开，方便连续链接
                }
                return true;
            }

            // ---------------- UI ----------------

            @Override
            public void buildConfiguration(Table table){
                showRange = true;
                table.defaults().pad(3f);

                table.add(Core.bundle.get("radar.panel.title")).color(Pal.accent).padBottom(4f).row();

                Table modes = new Table();
                for(int i = 0; i < 4; i++){
                    final int m = i;
                    TextButton b = new TextButton(Core.bundle.get("radar.mode." + modeKeys[i]), Styles.flatTogglet);
                    b.clicked(() -> configure(-1 - m));
                    b.update(() -> b.setChecked(mode == m));
                    modes.add(b).size(116f, 38f).pad(2f);
                    if(i % 2 == 1) modes.row();
                }
                table.add(modes).row();

                Label linkLabel = new Label("");
                linkLabel.update(() -> linkLabel.setText(
                    Core.bundle.format("radar.links", links.size, maxLinks)));
                table.add(linkLabel).padTop(5f).row();

                table.add(Core.bundle.get("radar.hint")).wrap().width(244f)
                    .color(Color.lightGray).left().row();

                table.button(Core.bundle.get("radar.clear"), () -> configure(configClear))
                    .size(244f, 34f).padTop(4f).row();
            }

            @Override
            public void onConfigureClosed(){
                showRange = false;
                super.onConfigureClosed();
            }

            // ---------------- 绘制 ----------------

            @Override
            public void draw(){
                super.draw();

                if(showRange){
                    Draw.z(Layer.effect);
                    Drawf.dashSquare(rangeColor, x, y, rangePx * 2f);

                    Lines.stroke(1f, linkColor);
                    for(int i = 0; i < links.size; i++){
                        Building b = Vars.world.build(links.get(i));
                        if(b != null && b.isValid()) Lines.line(x, y, b.x, b.y);
                    }
                    Lines.stroke(1f);
                }
            }

            /** 目标标记：全局 draw 事件调用，所以雷达不在屏幕里、目标在屏幕里时也能看到。 */
            void drawMarker(){
                Unit t = target;
                if(t == null || !t.isValid() || t.dead()) return;

                // 300 tick 转一圈；心跳约 0.7 秒一次
                float rot = (float)((Vars.state.tick % 300.0) / 300.0 * 360.0);
                float pulse = 1f + Mathf.absin(Time.time, 0.7f, 0.18f);
                float r = 13f * pulse, len = 9f * pulse, wid = 7f * pulse;

                Draw.z(Layer.flyingUnit + 1f);
                Draw.color(markColor, 0.95f);
                for(int i = 0; i < 4; i++){
                    float a = rot + i * 90f;
                    Drawf.tri(t.x + Angles.trnsx(a, r), t.y + Angles.trnsy(a, r), wid, len, a + 180f);
                }
                Draw.color();
            }

            @Override
            public void onDestroyed(){
                active.remove(this);
                releaseAll();
                super.onDestroyed();
            }

            @Override
            public void onDeconstructed(Unit unit){
                active.remove(this);
                releaseAll();
                super.onDeconstructed(unit);
            }

            // ---------------- 存档 ----------------

            @Override
            public byte version(){
                return 1;
            }

            @Override
            public void write(Writes write){
                super.write(write);
                write.i(mode);
                write.i(links.size);
                for(int i = 0; i < links.size; i++){
                    write.i(links.get(i));
                }
            }

            @Override
            public void read(Reads read, byte revision){
                super.read(read, revision);
                if(revision >= 1){
                    mode = Mathf.clamp(read.i(), 0, 3);
                    links.clear();
                    int n = Mathf.clamp(read.i(), 0, maxLinks);
                    for(int i = 0; i < n; i++){
                        links.add(read.i());
                    }
                    target = null;
                    retargetTimer = 0f;
                }
            }
        }
    }
}
