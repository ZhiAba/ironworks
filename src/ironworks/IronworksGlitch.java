package ironworks;

import arc.math.Mathf;
import arc.util.Time;

/** T7 难度名的随机乱码计时器：每 2-4 秒触发一次，持续 1-2 秒。 */
public final class IronworksGlitch{
    private static float nextAt = -1f;
    private static float until = -1f;

    private IronworksGlitch(){
    }

    public static boolean active(){
        float now = Time.time;
        if(nextAt < 0f) nextAt = now + Mathf.random(2f, 4f);
        if(until > now) return true;
        if(until >= 0f){
            until = -1f;
            nextAt = now + Mathf.random(2f, 4f);
        }
        if(now >= nextAt){
            until = now + Mathf.random(1f, 2f);
            nextAt = until + Mathf.random(2f, 4f);
            return true;
        }
        return false;
    }

    public static String garble(String source){
        char[] glitch = {'#', '%', '&', '?', '@', '!', '0', '1', '█', '▓'};
        StringBuilder out = new StringBuilder(source.length());
        for(int i = 0; i < source.length(); i++){
            char c = source.charAt(i);
            if(c == ' ' || Mathf.chance(0.35)) out.append(c);
            else out.append(glitch[Mathf.random(glitch.length - 1)]);
        }
        return out.toString();
    }
}
