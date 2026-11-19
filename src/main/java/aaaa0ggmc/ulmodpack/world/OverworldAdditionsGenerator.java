package aaaa0ggmc.ulmodpack.world;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.IChunkGenerator;
import net.minecraft.world.gen.feature.WorldGenMinable;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.fml.common.IWorldGenerator;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 主世界新增生成：
 * <ol>
 *   <li>甘苦矿（{@code bittersweet_ore}）：仅主世界生成，y=12..36（略高于钻石），每区块 4 簇、每簇最多 4 格；</li>
 *   <li>碎屑书架（{@code shelf_of_pieces}）：在生成村庄的区块中替换图书馆内 1–2 个原版书架。</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = UlModpack.MODID)
public class OverworldAdditionsGenerator implements IWorldGenerator {

    private static final int ORE_VEINS_PER_CHUNK = 4;
    private static final int ORE_MAX_VEIN_SIZE = 4;
    private static final int ORE_MIN_Y = 12;
    private static final int ORE_MAX_Y = 36;

    private final WorldGenMinable bittersweetGen = new WorldGenMinable(
            CommonProxy.BITTERSWEET_ORE.getDefaultState(), ORE_MAX_VEIN_SIZE);

    @Override
    public void generate(Random random, int chunkX, int chunkZ, World world,
                         IChunkGenerator chunkGenerator, IChunkProvider chunkProvider) {
        if (world.provider.getDimension() != 0) {
            return;
        }
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        for (int i = 0; i < ORE_VEINS_PER_CHUNK; i++) {
            int x = baseX + random.nextInt(16);
            int y = ORE_MIN_Y + random.nextInt(ORE_MAX_Y - ORE_MIN_Y + 1);
            int z = baseZ + random.nextInt(16);
            bittersweetGen.generate(world, random, new BlockPos(x, y, z));
        }
    }

    @SubscribeEvent
    public static void onPopulateChunkPost(PopulateChunkEvent.Post event) {
        World world = event.getWorld();
        if (world.isRemote || world.provider.getDimension() != 0 || !event.isHasVillageGenerated()) {
            return;
        }
        int baseX = event.getChunkX() * 16;
        int baseZ = event.getChunkZ() * 16;
        List<BlockPos> shelves = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                for (int y = 50; y <= 120; y++) {
                    cursor.setPos(baseX + dx, y, baseZ + dz);
                    if (world.getBlockState(cursor).getBlock() == Blocks.BOOKSHELF) {
                        shelves.add(cursor.toImmutable());
                    }
                }
            }
        }
        if (shelves.isEmpty()) {
            return;
        }
        Random rand = event.getRand();
        Collections.shuffle(shelves, rand);
        int replaceCount = Math.min(shelves.size(), 1 + rand.nextInt(2));
        for (int i = 0; i < replaceCount; i++) {
            world.setBlockState(shelves.get(i), CommonProxy.SHELF_OF_PIECES.getDefaultState(), 2);
        }
    }
}
