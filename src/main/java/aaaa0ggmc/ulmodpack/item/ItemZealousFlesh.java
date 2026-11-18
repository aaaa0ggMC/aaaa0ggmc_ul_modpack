package aaaa0ggmc.ulmodpack.item;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.UlModpackContainer;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import aaaa0ggmc.ulmodpack.zombie.ZombieForms;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 热忱的腐肉：泰坦王冠的合成材料；在僵尸/不死者（{@code ul:undead}）形态下
 * 也可直接食用，回复固定生命值（默认 10.0 点 / 5 颗心，走 {@code zombie_tide.zealous_flesh.heal_amount}）。
 *
 * 随从被铁傀儡打死时由尸潮系统掉落（{@code HordeManager.onFollowerDrops}）。
 */
public class ItemZealousFlesh extends Item {

    public ItemZealousFlesh() {
        setRegistryName(UlModpack.MODID, "zealous_flesh");
        setTranslationKey(UlModpack.MODID + ".zealous_flesh");
        setCreativeTab(CommonProxy.TAB);
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 32; // 进食时长（同原版食物）
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.EAT;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (world.isRemote) {
            IMorphing morphing = Morphing.get(player);
            if (morphing == null || !morphing.isMorphed() || player.getHealth() >= player.getMaxHealth()) {
                return new ActionResult<>(EnumActionResult.FAIL, stack);
            }
            player.setActiveHand(hand);
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        if (player instanceof EntityPlayerMP) {
            EntityPlayerMP mp = (EntityPlayerMP) player;
            String failure = checkEatFailure(mp);
            if (failure != null) {
                mp.sendStatusMessage(new TextComponentTranslation(failure), true);
                return new ActionResult<>(EnumActionResult.FAIL, stack);
            }
            mp.setActiveHand(hand);
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        return new ActionResult<>(EnumActionResult.FAIL, stack);
    }

    @Override
    public ItemStack onItemUseFinish(ItemStack stack, World world, EntityLivingBase entity) {
        if (!world.isRemote && entity instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) entity;
            String failure = checkEatFailure(player);
            if (failure != null) {
                player.sendStatusMessage(new TextComponentTranslation(failure), true);
                return stack;
            }
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
            double heal = UlModpackContainer.instance().strengthService()
                    .num("zombie_tide.zealous_flesh.heal_amount", 10.0D);
            float healAmount = (float) Math.max(0.0D, heal);
            player.heal(healAmount);
            if (player.world instanceof WorldServer) {
                ((WorldServer) player.world).spawnParticle(
                        EnumParticleTypes.HEART, player.posX, player.posY + 1.2D, player.posZ,
                        6, 0.4D, 0.4D, 0.4D, 0.05D);
                player.world.playSound(null, player.posX, player.posY, player.posZ,
                        SoundEvents.ENTITY_PLAYER_BURP, SoundCategory.PLAYERS, 0.7F, 0.9F);
            }
        }
        return stack;
    }

    @Nullable
    private static String checkEatFailure(EntityPlayerMP player) {
        if (!ZombieForms.isUndead(player)) {
            return "ul.zealous_flesh.fail.not_undead";
        }
        if (player.getHealth() >= player.getMaxHealth()) {
            return "ul.zealous_flesh.fail.full";
        }
        return null;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.zealous_flesh.lore1"));
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.zealous_flesh.lore2"));
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.zealous_flesh.lore3"));
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.zealous_flesh.lore4"));
    }
}
