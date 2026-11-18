package aaaa0ggmc.ulmodpack.world;

import aaaa0ggmc.ulmodpack.UlModpack;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.NoiseGeneratorSimplex;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.IEventListener;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * AbyssalCraft Darklands 主世界地形/地表边缘平滑补丁。
 *
 * <p>问题根因（对照 AbyssalCraft 源码）：</p>
 * <ol>
 *   <li>{@code AbyssalCraftEventHooks.populateChunk} 在 {@code PopulateChunkEvent.Pre} 中
 *       对所有 {@code y >= 64}（{@code storage.getYLocation() >= 60}）且精确处于
 *       {@code abyssalcraft:darklands_mountains} 群系内的方块，无噪声地把全部石头硬替换为
 *       {@code darkstone}，导致群系边界出现垂直一刀切、{@code y=64} 出现水平一刀切。</li>
 *   <li>{@code BiomeDarklandsMountains} 的 {@code topBlock/fillerBlock} 硬设为
 *       {@code darkstone}，在原版/BOP 地形生成后群系边缘草地与暗黑石直接 1 格硬切。</li>
 * </ol>
 *
 * <p>本补丁在 {@code postInit} 摘除原 {@code AbyssalCraftEventHooks.populateChunk} 监听器
 * （保留 AbyssalCraft 其他所有事件处理），并用基于 {@link NoiseGeneratorSimplex} 与邻域
 * 群系权重的羽化过渡取而代之：仅在主世界（{@code dim == 0}）运行，对 {@code y=54..70}
 * 起始高度与群系边缘内外 6 格同时做连续噪声羽化。</p>
 */
public final class DarklandsTerrainPatch {

    private static final int MARGIN = 6;
    private static final int GRID_SIZE = 16 + MARGIN * 2; // 28

    private static final int[][] SAMPLE_OFFSETS = {
            {-3, 0}, {3, 0}, {0, -3}, {0, 3},
            {-6, 0}, {6, 0}, {0, -6}, {0, 6},
            {-4, -4}, {4, -4}, {-4, 4}, {4, 4}
    };

    private static Biome darklandsMountains;
    private static IBlockState darkstoneState;
    private static Block darkstoneBlock;
    private static Block abyssalStoneBlock;

    private final Map<World, NoiseGeneratorSimplex> noiseByWorld = new WeakHashMap<>();

    private DarklandsTerrainPatch() {}

    public static void register() {
        if (!Loader.isModLoaded("abyssalcraft")) {
            return;
        }
        darklandsMountains = Biome.REGISTRY.getObject(new ResourceLocation("abyssalcraft", "darklands_mountains"));
        darkstoneBlock = Block.REGISTRY.getObject(new ResourceLocation("abyssalcraft", "darkstone"));
        abyssalStoneBlock = Block.REGISTRY.getObject(new ResourceLocation("abyssalcraft", "stone"));

        if (darklandsMountains == null || darkstoneBlock == null || darkstoneBlock == Blocks.AIR) {
            UlModpack.LOGGER.warn("[ul-modpack] DarklandsTerrainPatch skipped: missing AbyssalCraft biome/block.");
            return;
        }
        darkstoneState = darkstoneBlock.getDefaultState();

        int removed = unregisterAbyssalCraftPopulateHook();
        MinecraftForge.EVENT_BUS.register(new DarklandsTerrainPatch());
        UlModpack.LOGGER.info("[ul-modpack] DarklandsTerrainPatch active (replaced {} AbyssalCraft populateChunk hook).", removed);
    }

    private static int unregisterAbyssalCraftPopulateHook() {
        PopulateChunkEvent.Pre dummy = new PopulateChunkEvent.Pre(null, null, null, 0, 0, false);
        IEventListener[] listeners = dummy.getListenerList().getListeners(0);
        int removed = 0;
        for (IEventListener listener : listeners) {
            String desc = listener.toString();
            if (desc.contains("AbyssalCraftEventHooks") && desc.contains("populateChunk")) {
                dummy.getListenerList().unregister(0, listener);
                removed++;
            }
        }
        return removed;
    }

    @SubscribeEvent
    public void onPopulateChunkPre(PopulateChunkEvent.Pre event) {
        World world = event.getWorld();
        if (world == null || world.provider.getDimension() != 0) {
            return;
        }

        int baseX = event.getChunkX() << 4;
        int baseZ = event.getChunkZ() << 4;

        Biome[] biomes = world.getBiomeProvider().getBiomes(null, baseX - MARGIN, baseZ - MARGIN, GRID_SIZE, GRID_SIZE, false);
        boolean hasDarklandsMountains = false;
        for (Biome b : biomes) {
            if (b == darklandsMountains) {
                hasDarklandsMountains = true;
                break;
            }
        }
        if (!hasDarklandsMountains) {
            return;
        }

        NoiseGeneratorSimplex simplex = getNoise(world);
        Chunk chunk = world.getChunk(event.getChunkX(), event.getChunkZ());
        ExtendedBlockStorage[] storages = chunk.getBlockStorageArray();

        IBlockState stoneState = Blocks.STONE.getDefaultState();
        IBlockState grassState = Blocks.GRASS.getDefaultState();
        IBlockState dirtState = Blocks.DIRT.getDefaultState();

        for (int x = 0; x < 16; x++) {
            int gx = x + MARGIN;
            int wx = baseX + x;
            for (int z = 0; z < 16; z++) {
                int gz = z + MARGIN;
                int wz = baseZ + z;

                Biome centerBiome = biomes[gz * GRID_SIZE + gx];
                float borderFactor = computeBorderFactor(biomes, gx, gz, centerBiome == darklandsMountains);
                if (borderFactor <= 0.01F && centerBiome != darklandsMountains) {
                    continue;
                }

                double nLow = simplex.getValue(wx * 0.035D, wz * 0.035D);
                double nDetail = simplex.getValue(wx * 0.12D + 137.0D, wz * 0.12D - 91.0D);
                double combined = nLow * 0.7D + nDetail * 0.3D;

                int minY = clamp((int) Math.round(62.0D + combined * 8.0D), 52, 74);
                double score = borderFactor + combined * 0.35D;
                boolean useDarkstone = score > 0.45D;

                // 1) 地下/山体石头与暗黑石之间的噪声羽化过渡
                for (ExtendedBlockStorage storage : storages) {
                    if (storage == null) {
                        continue;
                    }
                    int baseY = storage.getYLocation();
                    if (baseY + 15 < minY) {
                        continue;
                    }
                    int startLocalY = Math.max(0, minY - baseY);
                    for (int ly = startLocalY; ly < 16; ly++) {
                        Block block = storage.get(x, ly, z).getBlock();
                        if (useDarkstone) {
                            if (block == Blocks.STONE || (abyssalStoneBlock != null && block == abyssalStoneBlock)) {
                                storage.set(x, ly, z, darkstoneState);
                            }
                        } else if (centerBiome == darklandsMountains && block == darkstoneBlock) {
                            storage.set(x, ly, z, stoneState);
                        }
                    }
                }

                // 2) 地表边缘草/泥土/暗黑石平滑过渡（消除群系 1 格硬切边）
                if (centerBiome == darklandsMountains && !useDarkstone) {
                    smoothMountainEdgeSurface(storages, x, z, grassState, dirtState);
                }
            }
        }
    }

    private static void smoothMountainEdgeSurface(ExtendedBlockStorage[] storages, int x, int z,
                                                  IBlockState grassState, IBlockState dirtState) {
        int topY = -1;
        for (int sec = storages.length - 1; sec >= 0; sec--) {
            ExtendedBlockStorage storage = storages[sec];
            if (storage == null) {
                continue;
            }
            for (int ly = 15; ly >= 0; ly--) {
                IBlockState state = storage.get(x, ly, z);
                Block b = state.getBlock();
                if (b != Blocks.AIR && !state.getMaterial().isLiquid()) {
                    topY = storage.getYLocation() + ly;
                    break;
                }
            }
            if (topY != -1) {
                break;
            }
        }
        if (topY < 60) {
            return;
        }
        for (int dy = 0; dy < 4; dy++) {
            int y = topY - dy;
            if (y < 0) {
                break;
            }
            ExtendedBlockStorage storage = storages[y >> 4];
            if (storage == null) {
                continue;
            }
            int ly = y & 15;
            if (storage.get(x, ly, z).getBlock() == darkstoneBlock) {
                storage.set(x, ly, z, dy == 0 ? grassState : dirtState);
            }
        }
    }

    private static float computeBorderFactor(Biome[] biomes, int gx, int gz, boolean isCenterMountain) {
        int hits = 0;
        for (int[] off : SAMPLE_OFFSETS) {
            Biome neighbor = biomes[(gz + off[1]) * GRID_SIZE + (gx + off[0])];
            if (neighbor == darklandsMountains) {
                hits++;
            }
        }
        float ratio = (float) hits / SAMPLE_OFFSETS.length;
        return isCenterMountain ? (0.35F + 0.65F * ratio) : (0.55F * ratio);
    }

    private NoiseGeneratorSimplex getNoise(World world) {
        synchronized (noiseByWorld) {
            return noiseByWorld.computeIfAbsent(world, w -> new NoiseGeneratorSimplex(new Random(w.getSeed() ^ 0x4441524B4C414E44L)));
        }
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}
