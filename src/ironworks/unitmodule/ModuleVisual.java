package ironworks.unitmodule;

import arc.graphics.Color;
import arc.graphics.g2d.TextureRegion;

/** A drawable layer contributed by a module. Coordinates are local to its parent. */
public class ModuleVisual{
    public String regionName;
    public TextureRegion region;
    public float x, y;
    public float rotation;
    public float scaleX = 1f, scaleY = 1f;
    /** Added to the unit's normal draw layer; positive draws above the body. */
    public float z;
    public Color tint;
    public boolean teamColor;
    public boolean mirror;
    public float alpha = 1f;
    public ModuleDrawer drawer;

    public ModuleVisual(String regionName){
        this.regionName = regionName;
    }

    public ModuleVisual at(float x, float y){
        this.x = x;
        this.y = y;
        return this;
    }

    public ModuleVisual rotate(float rotation){
        this.rotation = rotation;
        return this;
    }

    public ModuleVisual scale(float scale){
        this.scaleX = this.scaleY = scale;
        return this;
    }

    public ModuleVisual z(float z){
        this.z = z;
        return this;
    }

    public ModuleVisual tint(Color tint){
        this.tint = tint;
        return this;
    }

    public ModuleVisual teamColor(boolean teamColor){
        this.teamColor = teamColor;
        return this;
    }

    public ModuleVisual mirror(boolean mirror){
        this.mirror = mirror;
        return this;
    }

    public ModuleVisual alpha(float alpha){
        this.alpha = alpha;
        return this;
    }

    public ModuleVisual drawer(ModuleDrawer drawer){
        this.drawer = drawer;
        return this;
    }

    public ModuleVisual copy(){
        ModuleVisual v = new ModuleVisual(regionName);
        v.region = region;
        v.x = x; v.y = y; v.rotation = rotation;
        v.scaleX = scaleX; v.scaleY = scaleY;
        v.z = z; v.tint = tint; v.teamColor = teamColor;
        v.mirror = mirror; v.alpha = alpha; v.drawer = drawer;
        return v;
    }
}