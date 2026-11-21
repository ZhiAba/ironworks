package ironworks.unitmodule;

import arc.func.Prov;
import mindustry.type.Weapon;

/** One physical weapon mount contributed by a module. */
public class MountSpec{
    public String id;
    public Prov<Weapon> factory;
    public float x, y;
    public float shootX, shootY;
    public boolean mirror;

    public MountSpec(String id, Prov<Weapon> factory, float x, float y){
        this.id = id;
        this.factory = factory;
        this.x = x;
        this.y = y;
    }

    public MountSpec shoot(float shootX, float shootY){
        this.shootX = shootX;
        this.shootY = shootY;
        return this;
    }

    public MountSpec mirror(boolean mirror){
        this.mirror = mirror;
        return this;
    }
}