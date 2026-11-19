package aaaa0ggmc.ulmodpack.fluid;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.UlModpackContainer;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.init.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fluids.BlockFluidBase;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 孟婆汤交互与「遗忘」效果处理器：
 * <ul>
 *   <li>玩家手持空碗右键孟婆汤源流体盛汤（每格源流体 16 碗，潜行右键批量盛取）；</li>
 *   <li>常规生物落入孟婆汤获得 10s「遗忘」效果，结束时清仇恨并进入 1s 失忆窗口（期间阻止重新索敌）；</li>
 *   <li>失忆结束后若仍在液体中，会继续触发下一轮 10s「遗忘」；</li>
 *   <li>强力生物免疫（黑名单 + 自动 Boss / 高血量豁免）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = UlModpack.MODID)
public final class LetheInteractionHandler {

    public static final int BOWLS_PER_SOURCE = 16;
    private static final int OBLIVION_TICKS = 200; // 10s
    private static final long AMNESIA_TICKS = 20L; // 1s
    private static final String NBT_AMNESIA_UNTIL = "ulOblivionAmnesiaUntil";

    /** 显式免疫黑名单（除自动 !isNonBoss() 与高血量豁免外的精英怪）。 */
    private static final Set<String> IMMUNE_BLACKLIST;

    static {
        Set<String> set = new HashSet<>();
        set.add("minecraft:elder_guardian");
        set.add("minecraft:wither");
        set.add("minecraft:ender_dragon");
        IMMUNE_BLACKLIST = Collections.unmodifiableSet(set);
    }

    /** (dim << 32) ^ pos.toLong() 对应剩余可盛碗数。使用维度分桶 Map 避免碰撞。 */
    private static final Map<Integer, Map<Long, Integer>> REMAINING_SCOOPS = new HashMap<>();

    private LetheInteractionHandler() {
    }

    public static void resetScoops(World world, BlockPos pos) {
        Map<Long, Integer> dimMap = REMAINING_SCOOPS.get(world.provider.getDimension());
        if (dimMap != null) {
            dimMap.remove(pos.toLong());
        }
    }

    public static void clearScoops(World world, BlockPos pos) {
        resetScoops(world, pos);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || stack.getItem() != Items.BOWL) {
            return;
        }
        EntityPlayer player = event.getEntityPlayer();
        World world = event.getWorld();
        RayTraceResult hit = rayTraceFluid(world, player);
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() != CommonProxy.LETHE_WATER_BLOCK
                || state.getValue(BlockFluidBase.LEVEL) != 0) {
            return;
        }

        event.setCancellationResult(EnumActionResult.SUCCESS);
        event.setCanceled(true);
        player.swingArm(event.getHand());

        if (world.isRemote) {
            return;
        }

        int dim = world.provider.getDimension();
        Map<Long, Integer> dimMap = REMAINING_SCOOPS.computeIfAbsent(dim, k -> new HashMap<>());
        int remaining = dimMap.getOrDefault(pos.toLong(), BOWLS_PER_SOURCE);

        int maxByHand = player.capabilities.isCreativeMode ? remaining : stack.getCount();
        int scoopCount = player.isSneaking() ? Math.min(remaining, maxByHand) : 1;
        if (scoopCount <= 0) {
            return;
        }

        if (!player.capabilities.isCreativeMode) {
            stack.shrink(scoopCount);
        }
        ItemStack soup = new ItemStack(CommonProxy.LETHE_SOUP, scoopCount);
        if (!player.inventory.addItemStackToInventory(soup)) {
            player.dropItem(soup, false);
        }

        remaining -= scoopCount;
        if (remaining <= 0) {
            dimMap.remove(pos.toLong());
            world.setBlockToAir(pos);
        } else {
            dimMap.put(pos.toLong(), remaining);
        }

        world.playSound(null, pos, SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.PLAYERS, 0.8F, 1.0F);
    }

    private static RayTraceResult rayTraceFluid(World world, EntityPlayer player) {
        double reach = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue();
        Vec3d start = player.getPositionEyes(1.0F);
        Vec3d look = player.getLook(1.0F);
        Vec3d end = start.add(look.x * reach, look.y * reach, look.z * reach);
        return world.rayTraceBlocks(start, end, true, false, false);
    }

    public static void onMobInLethe(EntityLiving mob) {
        if (mob == null || mob.isDead || isImmuneToOblivion(mob)) {
            return;
        }
        if (mob.isPotionActive(CommonProxy.POTION_OBLIVION)) {
            return;
        }
        long now = mob.world.getTotalWorldTime();
        long amnesiaUntil = mob.getEntityData().getLong(NBT_AMNESIA_UNTIL);
        if (now < amnesiaUntil) {
            return;
        }
        int duration = Math.max(20, UlModpackContainer.instance().strengthService()
                .integer("lethe.oblivion_ticks", OBLIVION_TICKS));
        mob.addPotionEffect(new PotionEffect(CommonProxy.POTION_OBLIVION, duration, 0, false, true));
    }

    public static boolean isImmuneToOblivion(EntityLiving mob) {
        if (!mob.isNonBoss()) {
            return true;
        }
        float maxHp = (float) UlModpackContainer.instance().strengthService()
                .num("lethe.oblivion_max_hp", 100.0D);
        if (maxHp > 0.0F && mob.getMaxHealth() >= maxHp) {
            return true;
        }
        ResourceLocation key = EntityList.getKey(mob);
        return key != null && IMMUNE_BLACKLIST.contains(key.toString());
    }

    @SubscribeEvent
    public static void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity.world.isRemote || !(entity instanceof EntityLiving)) {
            return;
        }
        EntityLiving mob = (EntityLiving) entity;
        long now = mob.world.getTotalWorldTime();

        PotionEffect oblivion = mob.getActivePotionEffect(CommonProxy.POTION_OBLIVION);
        if (oblivion != null && oblivion.getDuration() <= 1) {
            long amnesiaTicks = Math.max(1L, UlModpackContainer.instance().strengthService()
                    .integer("lethe.oblivion_amnesia_ticks", (int) AMNESIA_TICKS));
            mob.getEntityData().setLong(NBT_AMNESIA_UNTIL, now + amnesiaTicks);
            mob.setAttackTarget(null);
            mob.setRevengeTarget(null);
            mob.getNavigator().clearPath();
            if (mob.world instanceof WorldServer) {
                ((WorldServer) mob.world).spawnParticle(EnumParticleTypes.SPELL_MOB,
                        mob.posX, mob.posY + mob.height * 0.6D, mob.posZ,
                        10, 0.3D, 0.3D, 0.3D, 0.02D);
            }
            return;
        }

        long amnesiaUntil = mob.getEntityData().getLong(NBT_AMNESIA_UNTIL);
        if (now < amnesiaUntil) {
            if (mob.getAttackTarget() != null) {
                mob.setAttackTarget(null);
            }
            if (mob.getRevengeTarget() != null) {
                mob.setRevengeTarget(null);
            }
        }
    }

    @SubscribeEvent
    public static void onSetAttackTarget(LivingSetAttackTargetEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity.world.isRemote || event.getTarget() == null || !(entity instanceof EntityLiving)) {
            return;
        }
        EntityLiving mob = (EntityLiving) entity;
        long amnesiaUntil = mob.getEntityData().getLong(NBT_AMNESIA_UNTIL);
        if (mob.world.getTotalWorldTime() < amnesiaUntil) {
            // 仅清 attackTarget，不在此事件内碰 revengeTarget（避免破坏 HurtByTarget.alertOthers 不变量）
            mob.setAttackTarget(null);
        }
    }
}
