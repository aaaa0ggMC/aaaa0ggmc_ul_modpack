package aaaa0ggmc.ulmodpack.item;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import aaaa0ggmc.ulmodpack.race.RaceManager;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * 种族回撤监听：玩家在拥有「安眠」效果期间，只要进入睡眠状态（{@code isPlayerSleeping()}，
 * 兼容原版床、Traveler's Backpack 睡袋等一切设置睡眠状态的模组），立即通过 {@link RaceManager}
 * 解除变身退回玩家种族。
 */
@Mod.EventBusSubscriber(modid = UlModpack.MODID)
public final class SleepRevertHandler {

    private SleepRevertHandler() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (!player.isPlayerSleeping() || !player.isPotionActive(CommonProxy.POTION_DEEP_SLEEP)) {
            return;
        }
        IMorphing morphing = Morphing.get(player);
        if (morphing == null || !morphing.isMorphed()) {
            player.removePotionEffect(CommonProxy.POTION_DEEP_SLEEP);
            return;
        }
        revertToHuman(player);
    }

    private static void revertToHuman(EntityPlayerMP player) {
        player.wakeUpPlayer(true, false, false);
        RaceManager.transition(player, null,
                RaceManager.TransitionReason.REVERT_HUMAN, RaceManager.HealthPolicy.PRESERVE_RATIO);
        player.sendStatusMessage(new TextComponentTranslation("ul.sleeping_pill.reverted"), true);

        if (player.world instanceof WorldServer) {
            ((WorldServer) player.world).spawnParticle(
                    EnumParticleTypes.END_ROD, player.posX, player.posY + 1.0D, player.posZ,
                    24, 0.5D, 0.8D, 0.5D, 0.04D);
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.PLAYERS, 0.8F, 1.25F);
        }
    }
}
