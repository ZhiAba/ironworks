package ironworks.multicraft;

import mindustry.type.ItemStack;
import mindustry.type.LiquidStack;

/** 一条配方：输入（物品/液体/电力）+ 输出（物品/液体）+ 工时。 */
public class Recipe{

    public String name;
    public ItemStack[] inputs = {};
    public LiquidStack[] liquidInputs = {};
    public float power = 0f;
    public ItemStack[] outputs = {};
    public LiquidStack[] liquidOutputs = {};
    public float craftTime = 60f;

    public Recipe(String name, float craftTime){
        this.name = name;
        this.craftTime = craftTime;
    }

    public Recipe items(ItemStack... in){
        inputs = in;
        return this;
    }

    public Recipe liquids(LiquidStack... in){
        liquidInputs = in;
        return this;
    }

    public Recipe power(float p){
        power = p;
        return this;
    }

    public Recipe output(ItemStack... out){
        outputs = out;
        return this;
    }

    public Recipe outputLiquid(LiquidStack... out){
        liquidOutputs = out;
        return this;
    }
}
