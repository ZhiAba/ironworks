import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

public class Sample{
    public static void main(String[] a) throws Exception{
        for(String p : a){
            BufferedImage img = ImageIO.read(new File(p));
            Map<Integer,Integer> m = new HashMap<>();
            for(int y=0;y<img.getHeight();y++) for(int x=0;x<img.getWidth();x++){
                int c = img.getRGB(x,y);
                int al = (c>>>24)&0xff;
                if(al < 40) continue;
                m.merge(c & 0xffffff, 1, Integer::sum);
            }
            List<Map.Entry<Integer,Integer>> e = new ArrayList<>(m.entrySet());
            e.sort((q,r)->r.getValue()-q.getValue());
            System.out.println(p + " (" + img.getWidth()+"x"+img.getHeight()+")");
            for(int i=0;i<Math.min(10,e.size());i++){
                int c = e.get(i).getKey();
                System.out.printf("  #%06x  %d%n", c, e.get(i).getValue());
            }
        }
    }
}
