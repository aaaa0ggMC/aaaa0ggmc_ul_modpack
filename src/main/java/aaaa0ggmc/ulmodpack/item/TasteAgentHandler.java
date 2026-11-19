package aaaa0ggmc.ulmodpack.item;

import aaaa0ggmc.ulmodpack.UlModpack;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.MobEffects;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * 甜蜜素 / 苦味剂后效处理：正面 Buff 结束后施加中毒 I。
 * 连续食用会累加待触发的中毒时长（上限 2400 tick = 2 分钟），不再因重置计时器而无限跳过中毒。
 */
@Mod.EventBusSubscriber(modid = UlModpack.MODID)
public final class TasteAgentHandler {

    private static final int MAX_STACKED_POISON_TICKS = 2400;

    private static final String NBT_SWEET_AT = "ulSweetenerPoisonAt";
    private static final String NBT_SWEET_DUR = "ulSweetenerPoisonDur";
    private static final String NBT_BITTER_AT = "ulBitterantPoisonAt";
    private static final String NBT_BITTER_DUR = "ulBitterantPoisonDur";

    private TasteAgentHandler() {
    }

    public static void scheduleAfterEffectPoison(EntityPlayer player, boolean sweetener, int delayTicks, int poisonTicks) {
        NBTTagCompound data = player.getEntityData();
        long now = player.world.getTotalWorldTime();
        long candidateAt = now + Math.max(1L, delayTicks);
        String keyAt = sweetener ? NBT_SWEET_AT : NBT_BITTER_AT;
        String keyDur = sweetener ? NBT_SWEET_DUR : NBT_BITTER_DUR;

        long existingAt = data.getLong(keyAt);
        int existingDur = data.getInteger(keyDur);
        int stackedDur = Math.min(MAX_STACKED_POISON_TICKS,
                (existingAt > now ? existingDur : 0) + Math.max(20, poisonTicks));
        long triggerAt = existingAt > now ? Math.max(existingAt, candidateAt) : candidateAt;

        data.setLong(keyAt, triggerAt);
        data.setInteger(keyDur, stackedDur);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) {
            return;
        }
        EntityPlayer player = event.player;
        if (player.ticksExisted % 5 != 0) {
            return;
        }
        NBTTagCompound data = player.getEntityData();
        long now = player.world.getTotalWorldTime();

        flushIfDue(player, data, now, NBT_SWEET_AT, NBT_SWEET_DUR);
        flushIfDue(player, data, now, NBT_BITTER_AT, NBT_BITTER_DUR);
    }

    private static void flushIfDue(EntityPlayer player, NBTTagCompound data, long now, String keyAt, String keyDur) {
        long dueAt = data.getLong(keyAt);
        if (dueAt > 0L && now >= dueAt) {
            int addDur = Math.max(20, data.getInteger(keyDur));
            data.removeTag(keyAt);
            data.removeTag(keyDur);
            PotionEffect existing = player.getActivePotionEffect(MobEffects.POISON);
            int totalDur = Math.min(MAX_STACKED_POISON_TICKS,
                    (existing != null ? existing.getDuration() : 0) + addDur);
            int amp = existing != null ? Math.max(existing.getAmplifier(), 0) : 0;
            player.addPotionEffect(new PotionEffect(MobEffects.POISON, totalDur, amp, false, true));
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntityLiving().world.isRemote || !(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }
        NBTTagCompound data = event.getEntityLiving().getEntityData();
        data.removeTag(NBT_SWEET_AT);
        data.removeTag(NBT_SWEET_DUR);
        data.removeTag(NBT_BITTER_AT);
        data.removeTag(NBT_BITTER_DUR);
    }
}
