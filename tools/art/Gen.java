import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

/** 算力集群矩形化：生成 4x4 主块 + 2x2 链接块 + 2x2 高级模块 + 1x1 通风块。 */
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

    static void rect(int x, int y, int w, int h, Color c){
        g.setColor(c); g.fillRect(x, y, w, h);
    }
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

    /** 4x4 主块：顶栏 + 双侧机架 + 中央算力环。 */
    static BufferedImage cluster(){
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        rect(0, 0, 128, 128, DARK);
        frame(128, 128);

        // 顶栏
        rect(8, 8, 112, 18, LIT);
        rect(8, 8, 112, 2, GREY);
        for(int i = 0; i < 3; i++) rect(14 + i * 10, 14, 4, 4, i == 0 ? PURL : PURD);
        rect(50, 13, 52, 4, GREY);
        rect(50, 19, 32, 3, MID);
        rect(108, 13, 8, 8, OUT);
        rect(110, 15, 4, 4, PUR);

        // 两侧机架
        for(int side = 0; side < 2; side++){
            int bx = side == 0 ? 10 : 96;
            rect(bx, 34, 22, 62, OUT);
            rect(bx + 1, 35, 20, 60, MID);
            for(int row = 0; row < 6; row++){
                for(int col = 0; col < 3; col++){
                    boolean on = (row + col + side * 2) % 3 != 1;
                    dot(bx + 3 + col * 6, 38 + row * 10, on ? PUR : PURD);
                }
                rect(bx + 2, 43 + row * 10, 18, 1, DARK);
            }
        }

        // 中央环
        ring(64, 72, 32, OUT);
        ring(64, 72, 30, PURD);
        ring(64, 72, 27, MID);
        ring(64, 72, 24, PUR);
        ring(64, 72, 21, DARK);
        rect(58, 52, 12, 40, OUT);
        rect(60, 54, 8, 36, PURL);
        rect(62, 58, 4, 28, PURI);
        rect(64, 66, 2, 16, PURI);

        // 底部格栅
        rect(10, 100, 108, 2, MID);
        for(int i = 0; i < 9; i++) rect(10 + i * 12, 106, 8, 12, OUT);
        for(int i = 0; i < 9; i++) rect(11 + i * 12, 108, 6, 8, MID);
        for(int i = 0; i < 4; i++) rect(96 + i * 6, 121, 3, 3, i % 2 == 0 ? PUR : PURD);
        return img;
    }

    /** 2x2 链接块：同款机架延续 + 冷却歧管；镜像后左右两块可共用一张图。 */
    static BufferedImage clusterLink(){
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        rect(0, 0, 64, 64, DARK);
        frame(64, 64);

        // 与主块相连的接口柱
        for(int i = 0; i < 3; i++){
            rect(10 + i * 18, 6, 10, 10, OUT);
            rect(11 + i * 18, 7, 8, 9, LIT);
            rect(13 + i * 18, 7, 4, 3, PUR);
        }

        // 两道冷却歧管
        for(int k = 0; k < 2; k++){
            int y = 22 + k * 16;
            rect(8, y, 48, 8, OUT);
            rect(9, y + 1, 46, 6, LIT);
            rect(9, y + 3, 46, 2, MID);
            for(int i = 0; i < 5; i++) rect(12 + i * 9, y + 3, 4, 2, i % 2 == 0 ? PUR : PURD);
        }

        // 底部散热口
        for(int i = 0; i < 2; i++){
            rect(10 + i * 24, 52, 20, 6, OUT);
            rect(12 + i * 24, 53, 16, 4, MID);
            rect(14 + i * 24, 54, 12, 2, DARK);
        }
        return img;
    }

    /** 2x2 高级模块核心：小号算法环 + 四角螺栓。 */
    static BufferedImage advCore(){
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        rect(0, 0, 64, 64, DARK);
        frame(64, 64);

        for(int cx = 0; cx < 2; cx++) for(int cy = 0; cy < 2; cy++){
            rect(8 + cx * 44, 8 + cy * 44, 6, 6, OUT);
            rect(9 + cx * 44, 9 + cy * 44, 4, 4, GREY);
            rect(10 + cx * 44, 10 + cy * 44, 2, 2, LIT);
        }

        ring(32, 32, 23, OUT);
        ring(32, 32, 21, PURD);
        ring(32, 32, 18, MID);
        ring(32, 32, 15, PUR);
        ring(32, 32, 12, DARK);
        rect(27, 22, 10, 20, OUT);
        rect(29, 24, 6, 16, PURL);
        rect(31, 27, 2, 10, PURI);

        // 侧边状态灯
        for(int i = 0; i < 3; i++){
            dot(52, 26 + i * 6, i == 1 ? PURD : PUR);
            dot(9, 26 + i * 6, i == 2 ? PURD : PUR);
        }
        return img;
    }

    /** 1x1 通风块。 */
    static BufferedImage advVent(){
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        rect(0, 0, 32, 32, DARK);
        frame(32, 32);
        for(int i = 0; i < 3; i++){
            rect(6, 8 + i * 6, 20, 4, OUT);
            rect(7, 9 + i * 6, 18, 2, MID);
        }
        rect(13, 26, 6, 3, PURD);
        rect(15, 26, 2, 3, PUR);
        return img;
    }

    static void save(BufferedImage img, String path) throws Exception{
        ImageIO.write(img, "png", new File(path));
        System.out.println("wrote " + path + " " + img.getWidth() + "x" + img.getHeight());
    }

    public static void main(String[] args) throws Exception{
        String dir = "/sdcard/dsh/mindustry-mod/assets/sprites/";
        save(cluster(), dir + "compute-cluster.png");
        save(clusterLink(), dir + "compute-cluster-link.png");
        save(advCore(), dir + "compute-advanced.png");
        save(advVent(), dir + "compute-advanced-link.png");
    }
}
