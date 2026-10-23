package ironworks;

import arc.util.noise.Simplex;
import mindustry.content.Blocks;
import mindustry.content.Planets;
import mindustry.maps.planet.SerpuloPlanetGenerator;
import mindustry.type.Sector;
import mindustry.world.Block;
import mindustry.world.Tiles;
import mindustry.world.WorldParams;
import mindustry.world.blocks.environment.OreBlock;

/**
 * 铁矿脉。
 *
 * 铁精矿原来只有磁选机（沙 -> 铁精矿）一个来源，这里加一条矿脉来源：
 * 直接在地图上生成铁矿，挖出来就是铁精矿。
 *
 * 原版塞普罗的矿石清单写死在 SerpuloPlanetGenerator.generate 里（局部变量），
 * 模组无法追加，所以用一个子类在生成完之后自己撒矿脉。
 */
public class IronworksOre{

    public static Block ironOre, bauxiteOre;

    public static void load(){
        ironOre = new OreBlock("iron-ore", IronworksItems.ironConcentrate);
        bauxiteOre = new OreBlock("bauxite-ore", IronworksAluminum.bauxite);

        // 只影响之后新生成的区块，已有存档不受影响
        Planets.serpulo.generator = new SerpuloPlanetGenerator(){
            @Override
            public void generate(Tiles tiles, Sector sec, WorldParams params){
                super.generate(tiles, sec, params);
                place(tiles, sec);
            }
        };
    }

    static void place(Tiles tiles, Sector sec){
        if(ironOre == null || tiles == null) return;

        int seed = sec == null ? 7919 : sec.id * 7919 + 131;
        int aluSeed = sec == null ? 4127 : sec.id * 4127 + 977;
        int urSeed = sec == null ? 6257 : sec.id * 6257 + 1493;
        tiles.eachTile(tile -> {
            if(tile == null) return;
            if(!tile.floor().hasSurface() || tile.overlay() != Blocks.air || tile.block() != Blocks.air) return;

            // 低频噪声成片，只铺一部分地
            double n = Simplex.noise2d(seed, 3, 0.55, 1.0 / 24.0, tile.x, tile.y);
            if(n > 0.58){
                tile.setOverlay(ironOre);
                return;
            }
            // 铝土矿：另一种噪声纹理，避开铁矿脉，成片落在另一片地上
            double a = Simplex.noise2d(aluSeed, 3, 0.62, 1.0 / 30.0, tile.x, tile.y);
            if(a > 0.60){ tile.setOverlay(bauxiteOre); return; }
            // 铀矿：更稀疏（阈值高、频率低），只在剩下的空地上撒
            double u = Simplex.noise2d(urSeed, 3, 0.70, 1.0 / 34.0, tile.x, tile.y);
            if(u > 0.63) tile.setOverlay(IronworksNuclear.uraniumOreBlock);
        });
    }
}
