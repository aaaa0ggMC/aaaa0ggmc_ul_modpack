package aaaa0ggmc.ulmodpack.dna;

import aaaa0ggmc.morphext.conversion.MorphFactory;
import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.race.RaceManager;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import javax.annotation.Nullable;

/**
 * DNA 重组后效处理器：
 * 玩家启动 DNA 重组机后获得一定时长的虚弱 I；虚弱倒计时结束时，
 * 经由 {@link RaceManager} 变身为对应的白名单生物（含可选原料决定的 NBT 变体，如没毛羊）。
 */
@Mod.EventBusSubscriber(modid = UlModpack.MODID)
public final class DnaRecombineHandler {

    private static final String NBT_PENDING_FORM = "ulDnaPendingForm";
    private static final String NBT_PENDING_DATA = "ulDnaPendingData";
    private static final String NBT_PENDING_AT = "ulDnaPendingAt";

    private DnaRecombineHandler() {
    }

    public static void scheduleRecombine(EntityPlayerMP player, String entityId,
                                         @Nullable NBTTagCompound extraEntityData, int weaknessTicks) {
        int ticks = Math.max(20, weaknessTicks);
        NBTTagCompound data = player.getEntityData();
        data.setString(NBT_PENDING_FORM, entityId);
        if (extraEntityData != null && !extraEntityData.isEmpty()) {
            data.setTag(NBT_PENDING_DATA, extraEntityData.copy());
        } else {
            data.removeTag(NBT_PENDING_DATA);
        }
        data.setLong(NBT_PENDING_AT, player.world.getTotalWorldTime() + ticks);

        player.addPotionEffect(new PotionEffect(MobEffects.WEAKNESS, ticks, 0, false, true));
        player.sendStatusMessage(new TextComponentTranslation("ul.dna.recombine_started", ticks / 20), true);
    }

    public static void cancelPending(EntityPlayer player) {
        if (player == null) {
            return;
        }
        NBTTagCompound data = player.getEntityData();
        data.removeTag(NBT_PENDING_FORM);
        data.removeTag(NBT_PENDING_DATA);
        data.removeTag(NBT_PENDING_AT);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        NBTTagCompound data = player.getEntityData();
        long pendingAt = data.getLong(NBT_PENDING_AT);
        if (pendingAt <= 0L || player.world.getTotalWorldTime() < pendingAt) {
            return;
        }
        String formId = data.getString(NBT_PENDING_FORM);
        NBTTagCompound extraData = data.hasKey(NBT_PENDING_DATA, 10)
                ? data.getCompoundTag(NBT_PENDING_DATA).copy()
                : null;
        cancelPending(player);

        if (formId.isEmpty()) {
            return;
        }
        AbstractMorph morph = MorphFactory.buildFromFormId(formId, player.world, extraData);
        if (morph == null) {
            UlModpack.LOGGER.warn("[dna] Failed to build morph '{}' for {}", formId, player.getName());
            player.sendStatusMessage(new TextComponentTranslation("ul.dna.recombine_failed"), true);
            return;
        }
        RaceManager.transition(player, morph,
                RaceManager.TransitionReason.EVOLVE, RaceManager.HealthPolicy.PRESERVE_RATIO);
        player.sendStatusMessage(new TextComponentTranslation("ul.dna.recombine_complete"), true);

        if (player.world instanceof WorldServer) {
            ((WorldServer) player.world).spawnParticle(
                    EnumParticleTypes.PORTAL, player.posX, player.posY + 1.0D, player.posZ,
                    36, 0.5D, 0.8D, 0.5D, 0.08D);
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.85F, 1.15F);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (!event.getEntityLiving().world.isRemote && event.getEntityLiving() instanceof EntityPlayer) {
            cancelPending((EntityPlayer) event.getEntityLiving());
        }
    }
}
