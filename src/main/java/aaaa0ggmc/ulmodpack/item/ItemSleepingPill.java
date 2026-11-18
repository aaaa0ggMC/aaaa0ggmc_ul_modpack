package aaaa0ggmc.ulmodpack.item;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.UlModpackContainer;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 安眠药：通用种族回撤道具。
 * 处于变身状态下服下后获得「安眠」效果；效果期间躺上床或睡袋即可退回原本的玩家种族。
 */
public class ItemSleepingPill extends Item {

    public ItemSleepingPill() {
        setRegistryName(UlModpack.MODID, "sleeping_pill");
        setTranslationKey(UlModpack.MODID + ".sleeping_pill");
        setCreativeTab(CommonProxy.TAB);
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 20;
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.EAT;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        IMorphing morphing = Morphing.get(player);
        if (morphing == null || !morphing.isMorphed()) {
            if (!world.isRemote) {
                player.sendStatusMessage(new TextComponentTranslation("ul.sleeping_pill.already_human"), true);
            }
            return new ActionResult<>(EnumActionResult.FAIL, stack);
        }
        player.setActiveHand(hand);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public ItemStack onItemUseFinish(ItemStack stack, World world, EntityLivingBase entity) {
        if (!world.isRemote && entity instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) entity;
            IMorphing morphing = Morphing.get(player);
            if (morphing == null || !morphing.isMorphed()) {
                player.sendStatusMessage(new TextComponentTranslation("ul.sleeping_pill.already_human"), true);
                return stack;
            }
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
            int duration = Math.max(20, UlModpackContainer.instance().strengthService()
                    .integer("race_revert.deep_sleep_ticks", 1200));
            player.addPotionEffect(new PotionEffect(CommonProxy.POTION_DEEP_SLEEP, duration, 0, false, true));
            player.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, duration, 0, false, true));
            player.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, duration, 0, false, true));
            player.sendStatusMessage(new TextComponentTranslation("ul.sleeping_pill.taken"), true);
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.8F, 0.85F);
        }
        return stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.sleeping_pill.lore1"));
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.sleeping_pill.lore2"));
    }
}
