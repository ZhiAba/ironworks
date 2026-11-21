package ironworks.unitmodule;

/**
 * Placement for one installed module.
 *
 * <p>Coordinates are local to the parent node (or to the unit when
 * {@code parentId == "root"}). If a socket is named, its local position is
 * added to the explicit coordinates.</p>
 */
public class ModulePlacement{
    public String parentId = "root";
    public String socketId;
    public float x, y;
    public float rotation;
    public float scale = 1f;
    public float z;
    public String region;
    public int tint = -1;
    public boolean mirror;

    public ModulePlacement(){}

    public ModulePlacement(float x, float y){
        this.x = x;
        this.y = y;
    }

    public ModulePlacement parent(String parentId){
        this.parentId = parentId;
        return this;
    }

    public ModulePlacement socket(String socketId){
        this.socketId = socketId;
        return this;
    }

    public ModulePlacement rotation(float rotation){
        this.rotation = rotation;
        return this;
    }

    public ModulePlacement scale(float scale){
        this.scale = scale;
        return this;
    }

    public ModulePlacement z(float z){
        this.z = z;
        return this;
    }

    public ModulePlacement region(String region){
        this.region = region;
        return this;
    }

    public ModulePlacement tint(int tint){
        this.tint = tint;
        return this;
    }

    public ModulePlacement mirror(boolean mirror){
        this.mirror = mirror;
        return this;
    }

    public ModulePlacement copy(){
        ModulePlacement p = new ModulePlacement(x, y);
        p.parentId = parentId;
        p.socketId = socketId;
        p.rotation = rotation;
        p.scale = scale;
        p.z = z;
        p.region = region;
        p.tint = tint;
        p.mirror = mirror;
        return p;
    }
}