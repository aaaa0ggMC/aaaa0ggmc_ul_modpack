package aaaa0ggmc.ulmodpack.item;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.UlModpackContainer;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 碗装孟婆汤（Lethe Soup）：64 堆叠，2s 快速饮用。
 * 立即回复 6 点生命，同时叠加 4s 缓慢 I 与 4s 虚弱 I（连续饮用时叠加持续时间）。
 */
public class ItemLetheSoup extends Item {

    public ItemLetheSoup() {
        setRegistryName(UlModpack.MODID, "lethe_soup");
        setTranslationKey(UlModpack.MODID + ".lethe_soup");
        setMaxStackSize(64);
        setCreativeTab(CommonProxy.TAB);
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 16; // 0.8s（原版喝药水为 32 tick = 1.6s，减半实现快速饮用）
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.DRINK;
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        player.setActiveHand(hand);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public ItemStack onItemUseFinish(ItemStack stack, World world, EntityLivingBase entity) {
        if (!world.isRemote) {
            float heal = (float) Math.max(0.0D, UlModpackContainer.instance().strengthService()
                    .num("lethe.soup_heal", 6.0D));
            int debuffTicks = Math.max(20, UlModpackContainer.instance().strengthService()
                    .integer("lethe.soup_debuff_ticks", 80));

            if (heal > 0.0F) {
                entity.heal(heal);
            }
            addStackingEffect(entity, MobEffects.SLOWNESS, debuffTicks, 0);
            addStackingEffect(entity, MobEffects.WEAKNESS, debuffTicks, 0);

            world.playSound(null, entity.posX, entity.posY, entity.posZ,
                    SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.85F, 1.05F);
        }

        if (entity instanceof EntityPlayer) {
            EntityPlayer player = (EntityPlayer) entity;
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
                ItemStack bowl = new ItemStack(Items.BOWL);
                if (stack.isEmpty()) {
                    return bowl;
                }
                if (!player.inventory.addItemStackToInventory(bowl)) {
                    player.dropItem(bowl, false);
                }
            }
        } else {
            stack.shrink(1);
        }
        return stack;
    }

    private static void addStackingEffect(EntityLivingBase entity, Potion potion, int addTicks, int amplifier) {
        PotionEffect existing = entity.getActivePotionEffect(potion);
        int newDuration = (existing != null ? existing.getDuration() : 0) + addTicks;
        int newAmplifier = existing != null ? Math.max(existing.getAmplifier(), amplifier) : amplifier;
        entity.addPotionEffect(new PotionEffect(potion, newDuration, newAmplifier, false, true));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.lethe_soup.lore1"));
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.lethe_soup.lore2"));
    }
}
