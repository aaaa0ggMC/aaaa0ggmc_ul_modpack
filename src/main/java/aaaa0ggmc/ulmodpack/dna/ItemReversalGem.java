package aaaa0ggmc.ulmodpack.dna;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 回溯宝石（Reversal Gem）：放入 DNA 重组机中可将可成长生物的重组结果回溯为幼年体（Baby）。
 */
public class ItemReversalGem extends Item {

    public ItemReversalGem() {
        setRegistryName(UlModpack.MODID, "reversal_gem");
        setTranslationKey(UlModpack.MODID + ".reversal_gem");
        setCreativeTab(CommonProxy.TAB);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.reversal_gem.lore1"));
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.reversal_gem.lore2"));
    }
}
