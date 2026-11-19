package aaaa0ggmc.ulmodpack.block;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

/**
 * 甘苦矿（Bittersweet Ore）：
 * <ul>
 *   <li>铁镐挖掘等级（harvestLevel = 2），主世界 y=12..36 生成（团簇最多 4 格）；</li>
 *   <li>白天挖掘主要掉落「甜蜜素」（85% 甜蜜素 / 15% 苦味剂）；</li>
 *   <li>夜晚挖掘主要掉落「苦味剂」（85% 苦味剂 / 15% 甜蜜素）；</li>
 *   <li>精准采集掉落原矿自身。</li>
 * </ul>
 */
public class BlockBittersweetOre extends Block {

    private static final float PRIMARY_CHANCE = 0.85F;

    public BlockBittersweetOre() {
        super(Material.ROCK);
        setRegistryName(UlModpack.MODID, "bittersweet_ore");
        setTranslationKey(UlModpack.MODID + ".bittersweet_ore");
        setHardness(3.0F);
        setResistance(5.0F);
        setSoundType(SoundType.STONE);
        setHarvestLevel("pickaxe", 2);
        setCreativeTab(CommonProxy.TAB);
    }

    @Override
    protected boolean canSilkHarvest() {
        return true;
    }

    @Override
    public void getDrops(NonNullList<ItemStack> drops, IBlockAccess world, BlockPos pos,
                         IBlockState state, int fortune) {
        Random rand = world instanceof World ? ((World) world).rand : RANDOM;
        boolean daytime = !(world instanceof World) || ((World) world).isDaytime();

        int count = 1 + (fortune > 0 ? rand.nextInt(fortune + 1) : 0);
        int sweetCount = 0;
        int bitterCount = 0;
        for (int i = 0; i < count; i++) {
            boolean primary = rand.nextFloat() < PRIMARY_CHANCE;
            if (daytime == primary) {
                sweetCount++;
            } else {
                bitterCount++;
            }
        }
        if (sweetCount > 0) {
            drops.add(new ItemStack(CommonProxy.SWEETENER, sweetCount));
        }
        if (bitterCount > 0) {
            drops.add(new ItemStack(CommonProxy.BITTERANT, bitterCount));
        }
    }

    @Override
    public int getExpDrop(IBlockState state, IBlockAccess world, BlockPos pos, int fortune) {
        Random rand = world instanceof World ? ((World) world).rand : RANDOM;
        return MathHelper.getInt(rand, 2, 5);
    }
}
