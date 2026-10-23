import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** 算力集群：主块一张整图（4x6 / 2x3），链接块不绘制；另出方形 UI 图标。 */
public class Gen{
    static final Color OUT  = new Color(0x12141c);
    static final Color DARK = new Color(0x222632);
    static final Color MID  = new Color(0x2c303e);
    static final Color LIT  = new Color(0x3a4052);
    static final Color GREY = new Color(0x828ca6);
    static final Color PUR  = new Color(0x9678ff);
    static final Color PURD = new Color(0x6f5db7);
    static final Color PURL = new Color(0xc4b2ff);
    static final Color PURI = new Color(0xdccdff);

    static Graphics2D g;

    static BufferedImage img(int w, int h){
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        rect(0, 0, w, h, DARK);
        frame(w, h);
        return img;
    }
    static void rect(int x, int y, int w, int h, Color c){ g.setColor(c); g.fillRect(x, y, w, h); }
    static void frame(int w, int h){
        rect(0, 0, w, h, OUT);
        rect(1, 1, w - 2, h - 2, MID);
        rect(3, 3, w - 6, h - 6, OUT);
        rect(5, 5, w - 10, h - 10, DARK);
    }
    static void dot(int x, int y, Color c){ rect(x, y, 3, 3, c); }
    static void ring(int cx, int cy, int r, Color c){
        g.setColor(c);
        for(int y = -r; y <= r; y++){
            int dx = (int)Math.round(Math.sqrt((double)r * r - (double)y * y));
            g.drawLine(cx - dx, cy + y, cx + dx, cy + y);
        }
    }

    /** 顶部状态栏 */
    static void banner(int w){
        rect(8, 8, w - 16, 20, LIT);
        rect(8, 8, w - 16, 2, GREY);
        for(int i = 0; i < 3; i++) rect(14 + i * 10, 14, 4, 4, i == 0 ? PURL : PURD);
        rect(50, 13, w - 78, 4, GREY);
        rect(50, 19, 32, 3, MID);
        rect(w - 20, 12, 8, 8, OUT);
        rect(w - 18, 14, 4, 4, PUR);
    }

    /** 两侧机架列 */
    static void racks(int x, int y, int h){
        rect(x, y, 22, h, OUT);
        rect(x + 1, y + 1, 20, h - 2, MID);
        int rows = h / 11;
        for(int row = 0; row < rows; row++){
            for(int col = 0; col < 3; col++){
                boolean on = (row + col) % 3 != 1;
                dot(x + 3 + col * 6, y + 4 + row * 11, on ? PUR : PURD);
            }
            rect(x + 2, y + 9 + row * 11, 18, 1, DARK);
        }
    }

    /** 中央算力环 */
    static void core(int cx, int cy, int r){
        ring(cx, cy, r, OUT);
        ring(cx, cy, r - 2, PURD);
        ring(cx, cy, r - 5, MID);
        ring(cx, cy, r - 8, PUR);
        ring(cx, cy, r - 11, DARK);
        int pw = Math.max(6, r / 3), ph = Math.max(20, r);
        rect(cx - pw / 2 - 2, cy - ph / 2 - 2, pw + 4, ph + 4, OUT);
        rect(cx - pw / 2, cy - ph / 2, pw, ph, PURL);
        rect(cx - pw / 4, cy - ph / 2 + 4, Math.max(2, pw / 2), ph - 8, PURI);
    }

    /** 横向冷却歧管 */
    static void manifold(int x, int y, int w, int phase){
        rect(x, y, w, 9, OUT);
        rect(x + 1, y + 1, w - 2, 7, LIT);
        rect(x + 1, y + 3, w - 2, 3, MID);
        for(int i = 0; i < w / 18; i++){
            rect(x + 6 + i * 18, y + 3, 5, 3, (i + phase) % 2 == 0 ? PUR : PURD);
        }
    }

    /** 底部散热口 */
    static void vents(int x, int y, int w, int v){
        int step = (w - 4) / v;
        for(int i = 0; i < v; i++){
            rect(x + 2 + i * step, y, step - 3, 12, OUT);
            rect(x + 3 + i * step, y + 1, step - 5, 10, MID);
            rect(x + 4 + i * step, y + 3, step - 7, 6, DARK);
        }
    }

    /** 4x6 全域算力集群：整张一体图 */
    static BufferedImage clusterFull(){
        BufferedImage im = img(128, 192);
        banner(128);
        racks(10, 36, 86);
        racks(96, 36, 86);
        core(64, 80, 32);
        manifold(8, 132, 112, 0);
        manifold(8, 146, 112, 1);
        vents(10, 164, 108, 4);
        for(int i = 0; i < 4; i++) rect(96 + i * 6, 182, 3, 3, i % 2 == 0 ? PUR : PURD);
        return im;
    }

    /** 4x6 的方形 UI 图标 */
    static BufferedImage clusterIcon(){
        BufferedImage im = img(128, 128);
        banner(128);
        racks(8, 34, 62);
        racks(98, 34, 62);
        core(64, 66, 26);
        vents(10, 108, 108, 4);
        return im;
    }

    /** 2x3 高级算力模块：整张一体图 */
    static BufferedImage advFull(){
        BufferedImage im = img(64, 96);
        for(int cx = 0; cx < 2; cx++) for(int cy = 0; cy < 2; cy++){
            rect(7 + cx * 46, 7 + cy * 46, 6, 6, OUT);
            rect(8 + cx * 46, 8 + cy * 46, 4, 4, GREY);
            rect(9 + cx * 46, 9 + cy * 46, 2, 2, LIT);
        }
        core(32, 32, 22);
        for(int i = 0; i < 3; i++){
            dot(54, 24 + i * 7, i == 1 ? PURD : PUR);
            dot(7, 24 + i * 7, i == 2 ? PURD : PUR);
        }
        manifold(8, 66, 48, 0);
        vents(10, 80, 44, 2);
        return im;
    }

    /** 2x3 的方形 UI 图标 */
    static BufferedImage advIcon(){
        BufferedImage im = img(64, 64);
        core(32, 32, 20);
        for(int cx = 0; cx < 2; cx++) for(int cy = 0; cy < 2; cy++){
            rect(7 + cx * 46, 7 + cy * 46, 6, 6, OUT);
            rect(8 + cx * 46, 8 + cy * 46, 4, 4, GREY);
        }
        return im;
    }

    /** 链接块贴图（实际不绘制，仅保证 atlas 有图） */
    static BufferedImage linkTile(int size, int cells){
        BufferedImage im = img(size, size);
        int step = size / cells;
        for(int i = 0; i < cells; i++) vents(6 + i * step, size / 2 - 6, step - 4, 1);
        return im;
    }


    /** 1x1 跃迁节点：暗色基座 + 中央菱形光环（物品=青绿，液体=蓝） */
    static BufferedImage node(boolean liquid){
        BufferedImage im = img(32, 32);
        Color accent = liquid ? new Color(0x7fb6ff) : new Color(0x7ff0d0);
        Color accentDim = liquid ? new Color(0x3d6fa8) : new Color(0x3f9e88);

        // 四角刻线
        for(int cx = 0; cx < 2; cx++) for(int cy = 0; cy < 2; cy++){
            rect(5 + cx * 20, 5 + cy * 20, 3, 3, GREY);
        }

        ring(16, 16, 11, OUT);
        ring(16, 16, 9, accentDim);
        ring(16, 16, 7, DARK);
        ring(16, 16, 6, accent);

        // 菱形
        g.setColor(accent);
        for(int dy = -4; dy <= 4; dy++){
            int w = 4 - Math.abs(dy);
            g.drawLine(16 - w, 16 + dy, 16 + w, 16 + dy);
        }
        g.setColor(OUT);
        for(int dy = -2; dy <= 2; dy++){
            int w = 2 - Math.abs(dy);
            g.drawLine(16 - w, 16 + dy, 16 + w, 16 + dy);
        }
        rect(15, 15, 2, 2, accent);

        // 底部指示灯
        rect(11, 27, 10, 2, OUT);
        rect(12, 27, 3, 2, accent);
        return im;
    }

    static void save(BufferedImage img, String path) throws Exception{
        ImageIO.write(img, "png", new File(path));
        System.out.println("wrote " + path + " " + img.getWidth() + "x" + img.getHeight());
    }


    // ==================================================================
    // RBMK 核电站（5×5 反应堆 + 3×3 汽轮机）
    // ==================================================================

    /** 石墨堆：一堆带斜纹的方块 */
    static void graphiteStack(int x, int y, int w, int h, int cell){
        for(int gx = 0; gx * cell < w; gx++){
            for(int gy = 0; gy * cell < h; gy++){
                int cx = x + gx * cell, cy = y + gy * cell;
                rect(cx, cy, cell - 2, cell - 2, OUT);
                rect(cx + 1, cy + 1, cell - 4, cell - 4, MID);
                for(int i = 0; i < (cell - 6) / 3; i++){
                    rect(cx + 3 + i * 3, cy + 2, 1, cell - 6, DARK);
                }
            }
        }
    }

    /** 5×5 RBMK 反应堆：石墨堆 + 中央燃料通道 + 顶部控制棒 + 两侧水路 */
    static BufferedImage rbmkFull(){
        BufferedImage im = img(160, 160);

        // 石墨堆体
        graphiteStack(14, 20, 132, 118, 22);

        // 中央燃料通道（竖排，炽热）
        rect(62, 20, 36, 118, OUT);
        rect(64, 22, 32, 114, DARK);
        for(int i = 0; i < 6; i++){
            int x = 68 + i * 5;
            rect(x, 28, 3, 104, i % 2 == 0 ? new Color(0xff8a3c) : new Color(0xffc247));
        }
        // 通道上下端盖
        rect(60, 16, 40, 6, GREY);
        rect(60, 136, 40, 6, GREY);

        // 顶部 8 根控制棒
        for(int i = 0; i < 8; i++){
            int x = 18 + i * 17;
            rect(x, 8, 9, 5, OUT);
            rect(x + 1, 9, 7, 3, GREY);
            rect(x + 3, 13, 3, 7, LIT);
        }

        // 两侧冷却水管路
        rect(4, 34, 9, 92, OUT);
        rect(5, 35, 7, 90, new Color(0x33606e));
        rect(5, 45, 7, 3, new Color(0x4f8fa3));
        rect(5, 110, 7, 3, new Color(0x4f8fa3));
        rect(147, 34, 9, 92, OUT);
        rect(148, 35, 7, 90, new Color(0x33606e));
        rect(148, 45, 7, 3, new Color(0x4f8fa3));
        rect(148, 110, 7, 3, new Color(0x4f8fa3));

        // 底部警示条
        rect(14, 144, 132, 10, new Color(0x1a1c22));
        for(int i = 0; i < 11; i++){
            rect(16 + i * 12, 146, 7, 6, i % 2 == 0 ? new Color(0xe8c33a) : OUT);
        }

        // 中央辉光点
        rect(76, 74, 8, 8, new Color(0xfff2c4));
        return im;
    }

    /** 5×5 反应堆的方形 UI 图标 */
    static BufferedImage rbmkIcon(){
        BufferedImage im = img(128, 128);
        graphiteStack(12, 16, 104, 96, 20);
        rect(50, 16, 28, 96, OUT);
        rect(52, 18, 24, 92, DARK);
        for(int i = 0; i < 5; i++) rect(55 + i * 4, 24, 2, 80, i % 2 == 0 ? new Color(0xff8a3c) : new Color(0xffc247));
        for(int i = 0; i < 7; i++){
            rect(14 + i * 15, 6, 8, 4, OUT);
            rect(15 + i * 15, 7, 6, 2, GREY);
        }
        rect(54, 72, 8, 8, new Color(0xfff2c4));
        return im;
    }

    /** 3×3 汽轮机：机壳 + 转子 + 蒸汽管 */
    static BufferedImage turbineFull(){
        BufferedImage im = img(96, 96);

        // 机壳
        rect(10, 14, 76, 68, OUT);
        rect(12, 16, 72, 64, MID);
        rect(14, 18, 68, 60, DARK);

        // 转子
        ring(48, 48, 26, OUT);
        ring(48, 48, 24, GREY);
        ring(48, 48, 18, LIT);
        for(int i = 0; i < 12; i++){
            double a = Math.PI * 2 * i / 12;
            int x1 = 48 + (int)Math.round(Math.cos(a) * 8), y1 = 48 + (int)Math.round(Math.sin(a) * 8);
            int x2 = 48 + (int)Math.round(Math.cos(a) * 22), y2 = 48 + (int)Math.round(Math.sin(a) * 22);
            g.setColor(i % 2 == 0 ? GREY : LIT);
            g.drawLine(x1, y1, x2, y2);
        }
        rect(44, 44, 8, 8, new Color(0x4f8fa3));

        // 顶部蒸汽管 + 底部冷凝
        rect(20, 4, 56, 10, OUT);
        rect(22, 6, 52, 6, new Color(0x33606e));
        rect(26, 8, 44, 2, new Color(0x7fd4ff));
        rect(20, 82, 56, 10, OUT);
        rect(22, 84, 52, 6, new Color(0x2a4a55));
        rect(26, 86, 44, 2, new Color(0x4f8fa3));
        return im;
    }

    /** 3×3 汽轮机的方形 UI 图标 */
    static BufferedImage turbineIcon(){
        BufferedImage im = img(96, 96);
        rect(12, 14, 72, 68, OUT);
        rect(14, 16, 68, 64, MID);
        ring(48, 48, 24, OUT);
        ring(48, 48, 22, GREY);
        ring(48, 48, 16, LIT);
        for(int i = 0; i < 10; i++){
            double a = Math.PI * 2 * i / 10;
            g.setColor(GREY);
            g.drawLine(48 + (int)Math.round(Math.cos(a) * 7), 48 + (int)Math.round(Math.sin(a) * 7),
                       48 + (int)Math.round(Math.cos(a) * 20), 48 + (int)Math.round(Math.sin(a) * 20));
        }
        rect(44, 44, 8, 8, new Color(0x4f8fa3));
        return im;
    }

    public static void main(String[] args) throws Exception{
        String dir = "/sdcard/dsh/mindustry-mod/assets/sprites/";
        save(clusterFull(), dir + "compute-cluster.png");
        save(clusterIcon(), dir + "compute-cluster-ui.png");
        save(advFull(), dir + "compute-advanced.png");
        save(advIcon(), dir + "compute-advanced-ui.png");
        save(linkTile(64, 2), dir + "compute-cluster-link.png");
        save(linkTile(32, 1), dir + "compute-advanced-link.png");
        save(node(false), dir + "mct-item-node.png");
        save(node(true), dir + "mct-liquid-node.png");
        save(rbmkFull(), dir + "rbmk-reactor.png");
        save(rbmkIcon(), dir + "rbmk-reactor-ui.png");
        save(turbineFull(), dir + "steam-turbine.png");
        save(turbineIcon(), dir + "steam-turbine-ui.png");
    }
}
