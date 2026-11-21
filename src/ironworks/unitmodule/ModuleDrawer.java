package ironworks.unitmodule;

import mindustry.gen.Unit;

/** Custom draw hook; coordinates are already converted to world space. */
public interface ModuleDrawer{
    void draw(Unit unit, ModuleNode node, ModuleVisual visual, float x, float y, float rotation);
}