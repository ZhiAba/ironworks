package ironworks.unitmodule;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.TextureRegion;
import arc.math.Mathf;
import mindustry.Vars;
import mindustry.gen.Groups;
import mindustry.gen.Unit;

/** Draws module visuals into the unit layer through Draw's z-sorted batch. */
public class ModuleVisualRenderer{
    public static void drawAll(){
        if(Vars.headless || Core.atlas == null || Vars.player == null) return;

        for(Unit unit : Groups.unit){
            if(unit == null || unit.dead || !unit.isAdded()) continue;
            UnitModuleState state = UnitModuleRegistry.get(unit);
            if(state == null || state.nodes.isEmpty()) continue;
            if(unit.inFogTo(Vars.player.team())) continue;

            float baseZ = unit.isFlying()
                ? unit.type.flyingLayer
                : unit.type.groundLayer + Mathf.clamp(unit.hitSize / 4000f, 0f, 0.01f);

            drawNode(unit, state, unit, "root", baseZ, 0);
        }
    }

    private static void drawNode(Unit unit, UnitModuleState state, Object parent, String parentId, float baseZ, int depth){
        if(depth > 8) return;
        for(ModuleNode node : state.children(parentId)){
            ModuleSpec spec = ModuleCatalog.get(node.moduleId);
            if(spec == null) continue;
            ModuleContext ctx = new ModuleContext(unit, state, node, spec);
            float scale = ModuleTransforms.scale(state, node);

            for(ModuleVisual visual : spec.collectVisuals(ctx)){
                String regionName = node.regionOverride != null ? node.regionOverride : visual.regionName;
                TextureRegion region = visual.region != null ? visual.region : Core.atlas.find(regionName);
                if(region == null || region.found() == false){
                    continue;
                }

                float[] world = ModuleTransforms.world(unit, state, node, visual.x, visual.y);
                float x = world[0];
                float y = world[1];
                float rotation = ModuleTransforms.worldRotation(unit, state, node) + visual.rotation;
                float z = baseZ + node.z + visual.z;
                float sx = visual.scaleX * scale, sy = visual.scaleY * scale;

                Draw.draw(z, () -> {
                    if(visual.drawer != null){
                        visual.drawer.draw(unit, node, visual, x, y, rotation);
                        return;
                    }
                    if(visual.teamColor){
                        Draw.color(unit.team.color);
                    }else if(node.tint != -1){
                        Draw.color(arc.util.Tmp.c1.set(node.tint));
                    }else if(visual.tint != null){
                        Draw.color(visual.tint);
                    }else{
                        Draw.color(Color.white);
                    }
                    Draw.alpha(visual.alpha);
                    if(visual.mirror || node.mirror){
                        float old = Draw.xscl;
                        Draw.xscl = -old;
                        Draw.rect(region, x, y, region.width * sx, region.height * sy, rotation);
                        Draw.xscl = old;
                    }else{
                        Draw.rect(region, x, y, region.width * sx, region.height * sy, rotation);
                    }
                    Draw.reset();
                });
            }

            drawNode(unit, state, node, node.instanceId, baseZ, depth + 1);
        }
    }
}