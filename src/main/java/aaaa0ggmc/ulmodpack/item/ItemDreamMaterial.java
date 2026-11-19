package aaaa0ggmc.ulmodpack.item;

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
 * 梦境核心原料：美梦（Sweet Dream）与噩梦（Nightmare）。
 * 由 8 个 Pieces 包裹甜蜜素 / 苦味剂合成，用于制作 DNA 重组机等变形机器。
 */
public class ItemDreamMaterial extends Item {

    private final String id;

    public ItemDreamMaterial(String id) {
        this.id = id;
        setRegistryName(UlModpack.MODID, id);
        setTranslationKey(UlModpack.MODID + "." + id);
        setCreativeTab(CommonProxy.TAB);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack." + id + ".lore1"));
    }
}
