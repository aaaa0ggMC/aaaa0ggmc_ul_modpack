package aaaa0ggmc.ulmodpack.item;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.UlModpackContainer;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 安眠药：通用种族回撤道具 & 孟婆汤原料。
 * <ul>
 *   <li>任意形态（含正常人类形态）均可服下，获得「安眠」效果；</li>
 *   <li>变身状态下在效果期间躺上床或睡袋即可退回原本的玩家种族；</li>
 *   <li>将 8 颗安眠药投入同一格水源方块，可将其转化为一格「孟婆汤」流体。</li>
 * </ul>
 */
public class ItemSleepingPill extends Item {

    public static final int PILLS_PER_LETHE_SOURCE = 8;

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
        player.setActiveHand(hand);
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public ItemStack onItemUseFinish(ItemStack stack, World world, EntityLivingBase entity) {
        if (!world.isRemote && entity instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) entity;
            if (!player.capabilities.isCreativeMode) {
                stack.shrink(1);
            }
            int duration = Math.max(20, UlModpackContainer.instance().strengthService()
                    .integer("race_revert.deep_sleep_ticks", 1200));
            player.addPotionEffect(new PotionEffect(CommonProxy.POTION_DEEP_SLEEP, duration, 0, false, true));
            player.sendStatusMessage(new TextComponentTranslation("ul.sleeping_pill.taken"), true);
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.PLAYERS, 0.8F, 0.85F);
        }
        return stack;
    }

    @Override
    public boolean onEntityItemUpdate(EntityItem entityItem) {
        World world = entityItem.world;
        if (world.isRemote || entityItem.isDead || entityItem.ticksExisted % 5 != 0) {
            return false;
        }
        BlockPos pos = new BlockPos(entityItem.posX, entityItem.posY + 0.1D, entityItem.posZ);
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != Blocks.WATER || state.getValue(BlockLiquid.LEVEL) != 0) {
            return false;
        }
        List<EntityItem> items = world.getEntitiesWithinAABB(EntityItem.class, new AxisAlignedBB(pos),
                e -> e != null && !e.isDead && !e.getItem().isEmpty() && e.getItem().getItem() == this);
        int total = 0;
        for (EntityItem e : items) {
            total += e.getItem().getCount();
        }
        if (total < PILLS_PER_LETHE_SOURCE) {
            return false;
        }
        int toConsume = PILLS_PER_LETHE_SOURCE;
        for (EntityItem e : items) {
            if (toConsume <= 0) {
                break;
            }
            ItemStack s = e.getItem();
            int take = Math.min(toConsume, s.getCount());
            s.shrink(take);
            toConsume -= take;
            if (s.isEmpty()) {
                e.setDead();
            } else {
                e.setItem(s);
            }
        }
        world.setBlockState(pos, CommonProxy.LETHE_WATER_BLOCK.getDefaultState(), 3);
        if (world instanceof WorldServer) {
            ((WorldServer) world).spawnParticle(EnumParticleTypes.END_ROD,
                    pos.getX() + 0.5D, pos.getY() + 0.9D, pos.getZ() + 0.5D,
                    18, 0.35D, 0.25D, 0.35D, 0.03D);
        }
        world.playSound(null, pos, SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.BLOCKS, 0.9F, 1.15F);
        return false;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<String> tooltip, ITooltipFlag flagIn) {
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.sleeping_pill.lore1"));
        tooltip.add(I18n.format("aaaa0ggmc_ul_modpack.sleeping_pill.lore2"));
    }
}
