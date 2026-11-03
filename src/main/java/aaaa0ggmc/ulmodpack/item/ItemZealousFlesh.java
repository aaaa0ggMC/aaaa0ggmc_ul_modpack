package aaaa0ggmc.ulmodpack.item;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import aaaa0ggmc.ulmodpack.zombie.titan.TitanFeast;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 热忱的腐肉：吃下变泰坦（{@code minecraft:giant}）的入口物品。
 *
 * 右键进食前预检（有尸群领导 + 通关过尸潮 + 头顶 12 格净空 + 不死者形态），不满足则
 * 提示且不消耗；满足才进入进食动画，进食完成扣 1 个并强制变泰坦（见 {@link TitanFeast}）。
 * 随从被铁傀儡打死时由尸潮系统掉落（{@code HordeManager.onFollowerDrops}）。
 *
 * 变身是服务端权威（{@link TitanFeast#transform}）；客户端只按「是否变身」预判是否播放
 * 进食动画，完整条件在服务端判。体型 patch 留待 morphext-patch-giant。
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
            // 客户端：仅按「是否变身」预判是否播放进食动画；技能/履历/空间在服务端判
            IMorphing morphing = Morphing.get(player);
            if (morphing == null || !morphing.isMorphed()) {
                return new ActionResult<>(EnumActionResult.FAIL, stack);
            }
            player.setActiveHand(hand);
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        if (player instanceof EntityPlayerMP) {
            String failure = TitanFeast.checkFailure((EntityPlayerMP) player);
            if (failure != null) {
                player.sendStatusMessage(new TextComponentTranslation(failure), true);
                return new ActionResult<>(EnumActionResult.FAIL, stack); // 不消耗
            }
            player.setActiveHand(hand);
            return new ActionResult<>(EnumActionResult.SUCCESS, stack);
        }
        return new ActionResult<>(EnumActionResult.FAIL, stack);
    }

    @Override
    public ItemStack onItemUseFinish(ItemStack stack, World world, EntityLivingBase entity) {
        if (!world.isRemote && entity instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) entity;
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1); // 消耗 1 个
            }
            TitanFeast.transform(player);
        }
        return stack;
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
