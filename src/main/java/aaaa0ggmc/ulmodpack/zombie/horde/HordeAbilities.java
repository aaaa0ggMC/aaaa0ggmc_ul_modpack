package aaaa0ggmc.ulmodpack.zombie.horde;

import aaaa0ggmc.morphext.ability.AbilityRegistry;
import aaaa0ggmc.morphext.ability.AbstractActiveAbility;
import aaaa0ggmc.morphext.ability.ActivationResult;
import aaaa0ggmc.morphext.ability.IAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import aaaa0ggmc.morphext.ability.ServerAbilityService;
import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.zombie.ZombieForms;
import aaaa0ggmc.ulmodpack.zombie.ZombieLine;
import aaaa0ggmc.ulmodpack.zombie.titan.TitanFormHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * 尸群领导 / 泰坦的四个主动能力注册（morphext {@link AbilityRegistry} + {@link ServerAbilityService}）。
 *
 * 能力在轮盘/状态页可见、可被框架释放；但「对空气使用」在 1.12.2 无法由框架表达，
 * 主要触发走本线的 {@code ul:ability} 包（{@link HordeNetwork}）直接调
 * {@link HordeManager} / {@link TitanFormHandler}，本类的 {@code activate} 只作框架兜底，
 * 与主触发跑同一套逻辑、共用同一份冷却（PlayerAbilityData，键=能力 id）。
 */
public final class HordeAbilities {

    private static boolean registered = false;

    private HordeAbilities() {
    }

    public static void registerAll() {
        if (registered) {
            return;
        }
        registered = true;
        AbilityRegistry.register(new RecruitAbility());
        AbilityRegistry.register(new LaunchAbility());
        AbilityRegistry.register(new ReleaseAbility());
        AbilityRegistry.register(new SlamAbility());
        try {
            // 前缀 = 内容模组命名空间；isAvailable 只看是否解锁尸群领导，形态/条件在各能力内判
            ServerAbilityService.register(UlModpack.MODID + ":", new HordeAbilityHandler());
        } catch (RuntimeException ex) {
            UlModpack.LOGGER.warn("[ul-modpack] ServerAbilityService prefix unavailable; "
                    + "abilities still registered: {}", ex.toString());
        }
    }

    /** 招募：对着僵尸族实体使用技能（手势路径上报目标实体 id；本入口做视线内目标解析）。 */
    private static final class RecruitAbility extends AbstractActiveAbility {
        private RecruitAbility() {
            super(ZombieLine.ABILITY_RECRUIT);
        }

        @Override
        public ActivationResult activate(EntityPlayer player, MorphContext context) {
            if (player instanceof EntityPlayerMP) {
                HordeManager.tryRecruit((EntityPlayerMP) player);
            }
            return ActivationResult.success(0);
        }
    }

    /** 发动尸潮：把队伍指向 32 格内活村民 ≥4 的村庄（发动/解放/震地的框架兜底分派）。 */
    private static final class LaunchAbility extends AbstractActiveAbility {
        private LaunchAbility() {
            super(ZombieLine.ABILITY_LAUNCH);
        }

        @Override
        public ActivationResult activate(EntityPlayer player, MorphContext context) {
            if (player instanceof EntityPlayerMP) {
                HordeManager.onAirUse((EntityPlayerMP) player);
            }
            return ActivationResult.success(0);
        }
    }

    /** 解放队伍：让随从散开恢复野生（明确的解散动作走手势：对空气潜行使用技能）。 */
    private static final class ReleaseAbility extends AbstractActiveAbility {
        private ReleaseAbility() {
            super(ZombieLine.ABILITY_RELEASE);
        }

        @Override
        public ActivationResult activate(EntityPlayer player, MorphContext context) {
            if (player instanceof EntityPlayerMP) {
                HordeManager.release((EntityPlayerMP) player);
            }
            return ActivationResult.success(0);
        }
    }

    /** 震地：泰坦形态潜行 + 右键空手。 */
    private static final class SlamAbility extends AbstractActiveAbility {
        private SlamAbility() {
            super(ZombieLine.ABILITY_SLAM);
        }

        @Override
        public ActivationResult activate(EntityPlayer player, MorphContext context) {
            if (player instanceof EntityPlayerMP) {
                TitanFormHandler.slam((EntityPlayerMP) player);
            }
            return ActivationResult.success(0);
        }
    }

    /** 框架释放入口：按能力 id 转派到对应 {@link AbilityRegistry} 原型。 */
    private static final class HordeAbilityHandler implements ServerAbilityService.Handler {
        @Override
        public boolean isAvailable(EntityPlayerMP player, String id) {
            return ZombieForms.hasLeadership(player);
        }

        @Override
        public ActivationResult activate(EntityPlayerMP player, String id) {
            IAbility ability = AbilityRegistry.get(id);
            if (ability == null) {
                return ActivationResult.unavailable();
            }
            try {
                // IActiveAbility 覆盖了 activate(EntityPlayer)（内部转 activate(player, new MorphContext(player, null))）
                return ability.activate(player);
            } catch (RuntimeException error) {
                UlModpack.LOGGER.warn("[ul-modpack] ability {} failed: {}", id, error.toString());
                return ActivationResult.unavailable();
            }
        }
    }
}
