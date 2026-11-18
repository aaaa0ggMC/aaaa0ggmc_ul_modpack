package aaaa0ggmc.ulmodpack.zombie.titan;

import aaaa0ggmc.morphext.ability.HumanStatureAbility;
import aaaa0ggmc.morphext.variant.VariantEventListener;
import aaaa0ggmc.ulmodpack.race.RaceManager;
import aaaa0ggmc.ulmodpack.zombie.ZombieForms;
import aaaa0ggmc.ulmodpack.zombie.ZombieLine;
import aaaa0ggmc.ulmodpack.zombie.horde.HordeManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;

/**
 * 泰坦冠冕变身（{@code minecraft:giant}）：戴上泰坦王冠 → 变身泰坦；摘下 → 退回原僵尸形态。
 *
 * <p>实际形态压栈/退栈与血量保留由 {@link RaceManager} 统一管理，本类只负责
 * 泰坦专属前置条件校验与视觉/音效演出。</p>
 */
public final class TitanFeast {

    private static final String FALLBACK_ZOMBIE_FORM = "minecraft:zombie";

    private TitanFeast() {
    }

    /** 预检：返回失败提示 lang key；{@code null} = 满足全部条件。 */
    public static String checkFailure(EntityPlayerMP player) {
        if (!ZombieForms.hasLeadership(player)) {
            return "ul.titan.feast.fail.skill";
        }
        if (!HordeManager.hasTideSuccess(player)) {
            return "ul.zealous_flesh.fail.leader";
        }
        if (!ZombieForms.isUndead(player)) {
            return "ul.titan.feast.fail.form";
        }
        if (!hasHeadroomIfNeeded(player)) {
            return "ul.titan.feast.fail.room";
        }
        return null;
    }

    /** 戴上泰坦王冠：委托 RaceManager 压栈原形态并变身泰坦，附带演出特效。 */
    public static void transform(EntityPlayerMP player) {
        RaceManager.transitionToForm(player, ZombieLine.FORM_TITAN,
                RaceManager.TransitionReason.TEMPORARY_PUSH, RaceManager.HealthPolicy.PRESERVE_RATIO);

        // 巨化一瞬间的视界震荡（短暂失明 + 轻微反胃，配合客户端过渡动画）
        player.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 26, 0, false, false));
        player.addPotionEffect(new PotionEffect(MobEffects.NAUSEA, 40, 0, false, false));
        player.sendStatusMessage(new TextComponentTranslation("ul.titan.feast.ok"), true);

        if (player.world instanceof WorldServer) {
            WorldServer world = (WorldServer) player.world;
            world.spawnParticle(EnumParticleTypes.TOTEM, player.posX, player.posY + 1.0D, player.posZ,
                    60, 0.8D, 1.2D, 0.8D, 0.1D);
            world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, player.posX, player.posY + 1.0D, player.posZ,
                    28, 0.9D, 1.2D, 0.9D, 0.08D);
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_ZOMBIE_INFECT, SoundCategory.PLAYERS, 1.0F, 0.55F);
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 0.6F, 0.65F);
        }
    }

    /** 摘下泰坦王冠：委托 RaceManager 退栈恢复原僵尸形态。 */
    public static void revert(EntityPlayerMP player) {
        if (!ZombieForms.isTitan(player)) {
            RaceManager.interruptTemporaryState(player);
            return;
        }
        RaceManager.popTemporary(player, FALLBACK_ZOMBIE_FORM, RaceManager.HealthPolicy.PRESERVE_RATIO);
        player.addPotionEffect(new PotionEffect(MobEffects.BLINDNESS, 18, 0, false, false));
        player.sendStatusMessage(new TextComponentTranslation("ul.titan.crown.revert"), true);

        if (player.world instanceof WorldServer) {
            WorldServer world = (WorldServer) player.world;
            world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, player.posX, player.posY + 1.0D, player.posZ,
                    24, 0.5D, 0.8D, 0.5D, 0.04D);
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED, SoundCategory.PLAYERS, 0.7F, 1.2F);
        }
    }

    public static boolean hasSavedPrevForm(EntityPlayerMP player) {
        return RaceManager.hasTemporaryForm(player);
    }

    /**
     * 仅在身形规整模式为 {@link HumanStatureAbility.Mode#OFF} 时检查头顶 12 格净空；
     * {@code SMART_SCALE} / {@code HUMAN_MODEL} 下玩家碰撞箱约为 1.8 格高，直接豁免。
     */
    private static boolean hasHeadroomIfNeeded(EntityPlayerMP player) {
        HumanStatureAbility.Mode mode = VariantEventListener.getStatureMode(player);
        if (mode == HumanStatureAbility.Mode.SMART_SCALE || mode == HumanStatureAbility.Mode.HUMAN_MODEL) {
            return true;
        }
        return hasHeadroom(player);
    }

    /** 头顶 headroom 格是否净空（原生泰坦约 11.7 格高，需 12 格垂直空间）。 */
    private static boolean hasHeadroom(EntityPlayerMP player) {
        int headroom = aaaa0ggmc.ulmodpack.UlModpackContainer.instance().strengthService().integer("zombie_tide.titan.headroom", 12);
        BlockPos base = new BlockPos(player.posX, player.posY, player.posZ);
        for (int i = 1; i <= headroom; i++) {
            if (!player.world.isAirBlock(base.up(i))) {
                return false;
            }
        }
        return true;
    }
}
