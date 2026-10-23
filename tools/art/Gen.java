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


    // ==================================================================
    // 核工业（0.67.0.0）：物品图标 + 方块贴图
    // ==================================================================

    /** 透明底物品图标：kind 决定形状。 */
    static BufferedImage nukeItem(int kind, Color a, Color b){
        BufferedImage im = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        switch(kind){
            case 0 -> { // 矿石：几块碎石
                rect(7, 14, 9, 9, OUT); rect(8, 15, 7, 7, a); rect(9, 16, 3, 3, b);
                rect(17, 10, 8, 8, OUT); rect(18, 11, 6, 6, a); rect(19, 12, 2, 2, b);
                rect(12, 22, 7, 6, OUT); rect(13, 23, 5, 4, a);
            }
            case 1 -> { // 粉末堆（黄饼）
                rect(5, 20, 22, 3, OUT); rect(6, 21, 20, 2, a);
                rect(8, 15, 16, 6, OUT); rect(9, 16, 14, 4, a); rect(12, 17, 4, 2, b);
                dot(11, 22, b); dot(18, 22, b); dot(15, 23, a);
            }
            case 2 -> { // 浓缩铀：三颗小球
                ring(11, 12, 6, OUT); ring(11, 12, 5, a); rect(9, 10, 3, 3, b);
                ring(21, 17, 6, OUT); ring(21, 17, 5, a); rect(19, 15, 3, 3, b);
                ring(12, 23, 5, OUT); ring(12, 23, 4, a);
            }
            case 3, 4, 5 -> { // 燃料棒 / 乏燃料 / 冷却后：圆柱棒
                rect(9, 5, 14, 23, OUT);
                rect(10, 6, 12, 21, a);
                rect(12, 8, 3, 17, b);
                rect(17, 8, 3, 17, b);
                if(kind != 3){ // 乏燃料：裂纹 + 暗斑
                    rect(10, 14, 12, 2, OUT);
                    rect(12, 22, 4, 2, OUT);
                }
                if(kind == 5){ // 冷却后：淡蓝高光
                    rect(10, 6, 12, 3, new Color(0xbfe8ff));
                }
            }
            case 6 -> { // 钚：发光球
                ring(16, 16, 9, OUT); ring(16, 16, 8, a); ring(16, 16, 5, b);
                rect(15, 15, 2, 2, new Color(0xffffff));
            }
            case 7 -> { // 高放废料桶
                rect(9, 8, 14, 18, OUT);
                rect(10, 9, 12, 16, a);
                rect(10, 12, 12, 3, OUT);
                rect(13, 16, 6, 5, b);
                rect(14, 6, 4, 3, GREY);
            }
            case 8 -> { // 固化废物体：玻璃柱
                rect(10, 6, 12, 21, OUT);
                rect(11, 7, 10, 19, a);
                rect(13, 10, 6, 13, b);
                rect(11, 7, 10, 3, new Color(0xcfd8e2));
            }
        }
        g.dispose();
        return im;
    }

    /** 核工业方块贴图：kind 决定机组样式。 */
    static BufferedImage nukeBlock(int kind, Color accent){
        int s = 96;
        BufferedImage im = img(s, s);
        switch(kind){
            case 0 -> { // 浸出槽：大罐 + 液面 + 进出管
                rect(16, 20, 64, 58, OUT); rect(18, 22, 60, 54, MID);
                rect(22, 34, 52, 40, OUT); rect(24, 36, 48, 36, accent);
                rect(22, 30, 52, 6, LIT);
                for(int i = 0; i < 4; i++) rect(26 + i * 12, 40 + (i % 2) * 8, 6, 3, new Color(0x1f3a2a));
                rect(4, 44, 12, 8, OUT); rect(6, 46, 8, 4, accent);
                rect(80, 44, 12, 8, OUT); rect(82, 46, 8, 4, accent);
                rect(40, 12, 16, 8, OUT); rect(42, 14, 12, 4, GREY);
            }
            case 1 -> { // 离心浓缩：三根离心筒
                for(int i = 0; i < 3; i++){
                    int x = 14 + i * 24;
                    rect(x, 18, 20, 62, OUT);
                    rect(x + 2, 20, 16, 58, MID);
                    rect(x + 5, 24, 10, 50, accent);
                    for(int k = 0; k < 5; k++) rect(x + 5, 28 + k * 9, 10, 2, OUT);
                }
                rect(10, 82, 76, 6, LIT);
            }
            case 2 -> { // 燃料元件厂：压制机
                rect(14, 16, 68, 20, OUT); rect(16, 18, 64, 16, MID);
                rect(28, 36, 40, 22, OUT); rect(30, 38, 36, 18, accent);
                rect(36, 60, 24, 22, OUT); rect(38, 62, 20, 18, LIT);
                for(int i = 0; i < 3; i++) rect(41 + i * 7, 65, 4, 12, accent);
                rect(20, 60, 10, 22, OUT); rect(66, 60, 10, 22, OUT);
            }
            case 3 -> { // 乏燃料池：水池 + 棒架
                rect(12, 24, 72, 54, OUT); rect(14, 26, 68, 50, new Color(0x1d3a4a));
                rect(16, 30, 64, 42, new Color(0x2a6f8a));
                rect(16, 30, 64, 6, new Color(0x7fd4ff));
                for(int i = 0; i < 6; i++) rect(20 + i * 10, 40, 5, 26, new Color(0x9fe8b0));
                for(int i = 0; i < 6; i++) rect(20 + i * 10, 40, 5, 4, new Color(0x4f7a5c));
                rect(8, 46, 6, 16, OUT); rect(82, 46, 6, 16, OUT);
            }
            case 4 -> { // 后处理厂（4×4）：双塔 + 管路
                int s4 = 128;
                im = img(s4, s4);
                rect(14, 16, 42, 92, OUT); rect(16, 18, 38, 88, MID);
                rect(20, 26, 30, 74, accent);
                for(int k = 0; k < 6; k++) rect(20, 32 + k * 12, 30, 3, OUT);
                rect(70, 28, 42, 80, OUT); rect(72, 30, 38, 76, MID);
                rect(76, 38, 30, 62, LIT);
                rect(58, 48, 12, 6, OUT); rect(60, 50, 8, 2, accent);
                rect(8, 108, 112, 8, LIT);
            }
            case 5 -> { // 固化车间：炉体 + 出料
                rect(18, 26, 60, 50, OUT); rect(20, 28, 56, 46, MID);
                rect(26, 38, 44, 26, OUT); rect(28, 40, 40, 22, accent);
                rect(32, 46, 12, 10, new Color(0xffd34d));
                rect(52, 46, 12, 10, new Color(0xc9e04a));
                rect(30, 76, 36, 12, OUT); rect(32, 78, 32, 8, LIT);
            }
            case 6 -> { // 干式储存：混凝土桶阵列
                for(int c = 0; c < 2; c++){
                    for(int r = 0; r < 2; r++){
                        int x = 16 + c * 34, y = 16 + r * 34;
                        rect(x, y, 28, 28, OUT);
                        rect(x + 2, y + 2, 24, 24, new Color(0x6f7d8a));
                        rect(x + 5, y + 5, 18, 18, new Color(0x8b98a5));
                        rect(x + 10, y + 10, 8, 8, accent);
                    }
                }
                rect(8, 84, 80, 6, LIT);
            }
        }
        return im;
    }


    /** 冷却塔：塔体 + 蒸汽 */
    static BufferedImage coolingTower(){
        BufferedImage im = img(96, 96);
        // 塔身（梯形）
        for(int i = 0; i < 14; i++){
            int w = 46 - i * 2;
            rect(48 - w / 2, 22 + i * 4, w, 4, i % 2 == 0 ? MID : LIT);
        }
        rect(26, 80, 44, 6, OUT);
        rect(20, 72, 56, 8, LIT);
        // 蒸汽
        for(int i = 0; i < 5; i++) rect(34 + i * 7, 8 + (i % 2) * 4, 6, 4, new Color(0xbfd8e2));
        // 循环水管
        rect(6, 46, 14, 8, OUT); rect(8, 48, 10, 4, new Color(0x8fc8dd));
        rect(76, 46, 14, 8, OUT); rect(78, 48, 10, 4, new Color(0x8fc8dd));
        return im;
    }

    /** 堆芯熔融物：发光岩浆团 */
    static BufferedImage corium(){
        BufferedImage im = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        ring(16, 17, 13, new Color(0x2a1a12));
        ring(16, 17, 12, new Color(0xff6a2b));
        ring(16, 17, 9, new Color(0xff9a3c));
        ring(16, 17, 6, new Color(0xffd166));
        rect(14, 15, 4, 4, new Color(0xfff2c4));
        // 结壳
        rect(7, 20, 5, 3, new Color(0x3b2a20));
        rect(20, 9, 4, 3, new Color(0x3b2a20));
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
        save(nukeItem(0, new Color(0x4f8f4a), new Color(0x8fd47a)), dir + "uranium-ore.png");
        save(nukeItem(1, new Color(0xffd34d), new Color(0xe8a92a)), dir + "yellowcake.png");
        save(nukeItem(2, new Color(0xa6ff8a), new Color(0x63c94a)), dir + "enriched-uranium.png");
        save(nukeItem(3, new Color(0x9fe8b0), new Color(0x4f8f5c)), dir + "fuel-rod.png");
        save(nukeItem(4, new Color(0x5f7a68), new Color(0x3b4d40)), dir + "depleted-rod.png");
        save(nukeItem(5, new Color(0x8fa39a), new Color(0x5f7a6a)), dir + "cooled-rod.png");
        save(nukeItem(6, new Color(0xc46bd8), new Color(0xff9be8)), dir + "plutonium.png");
        save(nukeItem(7, new Color(0xc9e04a), new Color(0x7f8f2a)), dir + "nuclear-waste.png");
        save(nukeItem(8, new Color(0x6f7d8a), new Color(0x3f4a55)), dir + "vitrified-waste.png");
        save(nukeBlock(0, new Color(0x4f8f4a)), dir + "leach-plant.png");
        save(nukeBlock(1, new Color(0xa6ff8a)), dir + "enrichment-plant.png");
        save(nukeBlock(2, new Color(0x9fe8b0)), dir + "fuel-factory.png");
        save(nukeBlock(3, new Color(0x7fd4ff)), dir + "spent-fuel-pool.png");
        save(nukeBlock(4, new Color(0xc46bd8)), dir + "reprocessor.png");
        save(nukeBlock(5, new Color(0xc9e04a)), dir + "vitrifier.png");
        save(nukeBlock(6, new Color(0x6f7d8a)), dir + "dry-storage.png");
        save(coolingTower(), dir + "cooling-tower.png");
        save(corium(), dir + "corium.png");

    }
}
