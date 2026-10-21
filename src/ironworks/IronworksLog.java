package ironworks;

import arc.Core;
import arc.files.Fi;
import arc.util.Log;
import arc.util.Time;

/** 全量日志：每 tick 抓状态、所有关键事件，缓冲后写文件。 */
public class IronworksLog{

    public static boolean active;
    private static final StringBuilder buffer = new StringBuilder();
    private static Fi file;
    private static long written;
    private static final long MAX_BYTES = 24L * 1024 * 1024;

    public static void event(String message){
        if(!active) return;
        line("[EVENT] t=" + fmt(Time.time) + " " + message);
    }

    public static void line(String message){
        if(!active) return;
        buffer.append(message).append('\n');
        if(buffer.length() > 8192) flush();
    }

    public static void flush(){
        if(buffer.length() == 0) return;
        if(written > MAX_BYTES){
            buffer.setLength(0);
            return;
        }
        String text = buffer.toString();
        buffer.setLength(0);
        written += text.length();
        write(text);
    }

    private static void write(String text){
        try{
            if(file == null) file = Core.files.absolute("/sdcard/ironworks-tick.log");
            file.writeString(text, true);
        }catch(Throwable t){
            try{
                Fi f2 = Core.settings.getDataDirectory().child("ironworks-tick.log");
                f2.writeString(text, true);
            }catch(Throwable ignored){
            }
        }
    }

    public static String fmt(float v){
        return String.format("%.4f", v);
    }
}
