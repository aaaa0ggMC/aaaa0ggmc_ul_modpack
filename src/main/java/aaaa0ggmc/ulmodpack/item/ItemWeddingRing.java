package aaaa0ggmc.ulmodpack.item;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import baubles.api.BaubleType;
import baubles.api.BaublesApi;
import baubles.api.IBauble;
import baubles.api.cap.IBaublesItemHandler;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 结婚戒指：Baubles RING 槽位。
 *
 * 佩戴时通过 {@link aaaa0ggmc.morphext.ability.TemporaryAbilityRegistry}
 * 临时授予玩家「繁殖」（{@code aaaa0ggmc_ul_modpack:breed}）主动技能。
 */
public class ItemWeddingRing extends Item implements IBauble {

    public ItemWeddingRing() {
        setRegistryName(UlModpack.MODID, "wedding_ring");
        setTranslationKey(UlModpack.MODID + ".wedding_ring");
        setMaxStackSize(1);
        setCreativeTab(CommonProxy.TAB);
    }

    @Override
    public BaubleType getBaubleType(ItemStack stack) {
        return BaubleType.RING;
    }

    /** 检查玩家是否在 Baubles 戒指槽中佩戴了结婚戒指。 */
    public static boolean isEquipped(@Nullable EntityPlayer player) {
        if (player == null) {
            return false;
        }
        IBaublesItemHandler handler = BaublesApi.getBaublesHandler(player);
        if (handler == null) {
            return false;
        }
        for (int slot : BaubleType.RING.getValidSlots()) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.getItem() == CommonProxy.WEDDING_RING) {
                return true;
            }
        }
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.wedding_ring.lore1"));
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.wedding_ring.lore2"));
    }
}
