package aaaa0ggmc.ulmodpack.block;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

/**
 * 碎屑书架（Shelf of Pieces）：
 * <ul>
 *   <li>自然生成于村庄图书馆中（替换 1–2 个原版书架），也可由 8 桶孟婆汤 + 1 个书架合成；</li>
 *   <li>附魔加成等同于 4 个普通书架（{@code getEnchantPowerBonus = 4.0F}）；</li>
 *   <li>精准采集掉落自身；非精准采集掉落 3–9 个 {@code Pieces}（时运提升下限，上限仍为 9）。</li>
 * </ul>
 */
public class BlockShelfOfPieces extends Block {

    public static final int MAX_PIECES_DROP = 9;

    public BlockShelfOfPieces() {
        super(Material.WOOD);
        setRegistryName(UlModpack.MODID, "shelf_of_pieces");
        setTranslationKey(UlModpack.MODID + ".shelf_of_pieces");
        setHardness(1.5F);
        setSoundType(SoundType.WOOD);
        setCreativeTab(CommonProxy.TAB);
    }

    @Override
    public float getEnchantPowerBonus(World world, BlockPos pos) {
        return 4.0F;
    }

    @Override
    protected boolean canSilkHarvest() {
        return true;
    }

    @Override
    public Item getItemDropped(IBlockState state, Random rand, int fortune) {
        return CommonProxy.PIECES;
    }

    @Override
    public int quantityDropped(Random random) {
        // 3..9 个
        return 3 + random.nextInt(7);
    }

    @Override
    public int quantityDroppedWithBonus(int fortune, Random random) {
        int base = quantityDropped(random);
        if (fortune > 0) {
            base += random.nextInt(fortune + 1);
        }
        return Math.min(MAX_PIECES_DROP, base);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.shelf_of_pieces.lore1"));
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.shelf_of_pieces.lore2"));
    }
}
