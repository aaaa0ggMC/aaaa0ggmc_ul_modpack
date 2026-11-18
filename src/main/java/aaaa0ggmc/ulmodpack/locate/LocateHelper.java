package aaaa0ggmc.ulmodpack.locate;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.init.Biomes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeProvider;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * 改进原版 /locate 的分散遗迹（MapGenScatteredFeature）定位：
 *
 * <p>原版 1.12.2 把沙漠神殿、丛林神庙、女巫小屋、雪屋全部注册在同一个
 * {@code "Temple"} 结构名下，导致 {@code /locate Temple} 会把雪屋当成神殿返回。</p>
 *
 * <p>本类按原版 {@code MapGenScatteredFeature} 完全一致的网格参数
 * （spacing=32, separation=8, salt=14357617, maxRadius=100）螺旋搜索，
 * 按目标生物群系精确过滤：</p>
 * <ul>
 *   <li>{@code Temple}：仅匹配沙漠神殿（Desert / DesertHills）与丛林神庙（Jungle / JungleHills）</li>
 *   <li>{@code DesertPyramid} / {@code Desert_Pyramid} / {@code DesertTemple}：仅沙漠神殿</li>
 *   <li>{@code JunglePyramid} / {@code Jungle_Pyramid} / {@code JungleTemple}：仅丛林神庙</li>
 *   <li>{@code SwampHut} / {@code Swamp_Hut} / {@code WitchHut}：仅女巫小屋</li>
 *   <li>{@code Igloo}：仅雪屋</li>
 * </ul>
 */
public final class LocateHelper {

    private static final int SCATTERED_SPACING = 32;
    private static final int SCATTERED_SEPARATION = 8;
    private static final int SCATTERED_SALT = 14357617;
    private static final int SEARCH_RADIUS = 100;

    private static final String[] TAB_SUGGESTIONS = {
            "Stronghold", "Monument", "Village", "Mansion", "EndCity", "Fortress",
            "Temple", "Mineshaft", "DesertPyramid", "JunglePyramid", "SwampHut", "Igloo"
    };

    private static final Map<String, Set<Biome>> SCATTERED_TARGETS = new HashMap<>();

    static {
        Set<Biome> desert = new HashSet<>(Arrays.asList(Biomes.DESERT, Biomes.DESERT_HILLS));
        Set<Biome> jungle = new HashSet<>(Arrays.asList(Biomes.JUNGLE, Biomes.JUNGLE_HILLS));
        Set<Biome> temples = new HashSet<>();
        temples.addAll(desert);
        temples.addAll(jungle);
        Set<Biome> swamp = Collections.singleton(Biomes.SWAMPLAND);
        Set<Biome> igloo = new HashSet<>(Arrays.asList(Biomes.ICE_PLAINS, Biomes.COLD_TAIGA));

        SCATTERED_TARGETS.put("temple", temples);
        SCATTERED_TARGETS.put("desertpyramid", desert);
        SCATTERED_TARGETS.put("desert_pyramid", desert);
        SCATTERED_TARGETS.put("deserttemple", desert);
        SCATTERED_TARGETS.put("junglepyramid", jungle);
        SCATTERED_TARGETS.put("jungle_pyramid", jungle);
        SCATTERED_TARGETS.put("jungletemple", jungle);
        SCATTERED_TARGETS.put("swamphut", swamp);
        SCATTERED_TARGETS.put("swamp_hut", swamp);
        SCATTERED_TARGETS.put("witchhut", swamp);
        SCATTERED_TARGETS.put("igloo", igloo);
    }

    private LocateHelper() {}

    /**
     * 若 {@code name} 属于分散遗迹（或其细分类型），则由本类接管定位并返回 {@code true}；
     * 否则返回 {@code false} 交由原版逻辑处理。
     */
    public static boolean tryHandleLocate(ICommandSender sender, String name) throws CommandException {
        Set<Biome> targetBiomes = SCATTERED_TARGETS.get(name.toLowerCase(Locale.ROOT));
        if (targetBiomes == null) {
            return false;
        }

        World world = sender.getEntityWorld();
        if (world.provider.getDimension() != 0) {
            throw new CommandException("commands.locate.failure", name);
        }

        BlockPos pos = findNearestScattered(world, sender.getPosition(), targetBiomes);
        if (pos == null) {
            throw new CommandException("commands.locate.failure", name);
        }

        TextComponentTranslation msg = new TextComponentTranslation("commands.locate.success", name, pos.getX(), pos.getZ());
        msg.getStyle().setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                "/tp " + pos.getX() + " ~ " + pos.getZ()));
        sender.sendMessage(msg);
        return true;
    }

    public static List<String> getTabCompletions(String[] args) {
        if (args.length == 1) {
            return CommandBase.getListOfStringsMatchingLastWord(args, TAB_SUGGESTIONS);
        }
        return Collections.emptyList();
    }

    @Nullable
    private static BlockPos findNearestScattered(World world, BlockPos origin, Set<Biome> allowedBiomes) {
        BiomeProvider biomeProvider = world.getBiomeProvider();
        int chunkX = origin.getX() >> 4;
        int chunkZ = origin.getZ() >> 4;

        for (int radius = 0; radius <= SEARCH_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                boolean onEdgeX = (dx == -radius || dx == radius);
                for (int dz = -radius; dz <= radius; dz++) {
                    boolean onEdgeZ = (dz == -radius || dz == radius);
                    if (!onEdgeX && !onEdgeZ) {
                        continue;
                    }

                    int testChunkX = chunkX + SCATTERED_SPACING * dx;
                    int testChunkZ = chunkZ + SCATTERED_SPACING * dz;
                    if (testChunkX < 0) {
                        testChunkX -= SCATTERED_SPACING - 1;
                    }
                    if (testChunkZ < 0) {
                        testChunkZ -= SCATTERED_SPACING - 1;
                    }

                    int cellX = testChunkX / SCATTERED_SPACING;
                    int cellZ = testChunkZ / SCATTERED_SPACING;
                    Random rand = world.setRandomSeed(cellX, cellZ, SCATTERED_SALT);
                    int spawnChunkX = cellX * SCATTERED_SPACING + rand.nextInt(SCATTERED_SPACING - SCATTERED_SEPARATION);
                    int spawnChunkZ = cellZ * SCATTERED_SPACING + rand.nextInt(SCATTERED_SPACING - SCATTERED_SEPARATION);

                    Biome biome = biomeProvider.getBiome(new BlockPos(spawnChunkX * 16 + 8, 0, spawnChunkZ * 16 + 8));
                    if (allowedBiomes.contains(biome)) {
                        return new BlockPos((spawnChunkX << 4) + 8, 64, (spawnChunkZ << 4) + 8);
                    }

                    if (radius == 0) {
                        break;
                    }
                }
                if (radius == 0) {
                    break;
                }
            }
        }
        return null;
    }
}
