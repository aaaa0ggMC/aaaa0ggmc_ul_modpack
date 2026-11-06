package aaaa0ggmc.ulmodpack.zombie.horde;

import aaaa0ggmc.morphext.ability.AbilityRegistry;
import aaaa0ggmc.morphext.ability.AbstractActiveAbility;
import aaaa0ggmc.morphext.ability.ActivationResult;
import aaaa0ggmc.morphext.ability.IAbility;
import aaaa0ggmc.morphext.ability.IActiveAbility;
import aaaa0ggmc.morphext.ability.MorphContext;
import aaaa0ggmc.morphext.ability.ServerAbilityService;
import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.zombie.ZombieForms;
import aaaa0ggmc.ulmodpack.zombie.ZombieLine;
import aaaa0ggmc.ulmodpack.zombie.titan.TitanFormHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * 尸群指挥 / 泰坦震地的主动能力注册（morphext {@link AbilityRegistry} + {@link ServerAbilityService}）。
 *
 * 能力在轮盘/状态页可见、可被框架释放。触发统一走 morphext 能力释放链路
 * （ClientAbilityExecutor → AbilityNetwork → ServerAbilityService → 本类
 * {@code activate}，2026-11-06 用户拍板移除右键手势直连）。2026-11-07 按用户设计
 * 把 horde_recruit / horde_launch / horde_release 三能力合并为 {@code horde_command}
 * 一个能力：服务端按「目标 + 潜行」分派（潜行状态服务端直接读
 * {@code player.isSneaking()}，不改网络协议）；客户端 pickTarget 放宽为任意活体
 * （{@link HordeManager#findLookedLivingId}），招募/踢人判定全在服务端。
 * 冷却经 PlayerAbilityData（键=能力 id）统一管理。
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
        AbilityRegistry.register(new CommandAbility());
        AbilityRegistry.register(new SlamAbility());
        try {
            // 前缀 = 内容模组命名空间；isAvailable 只看是否解锁尸群领导，形态/条件在各能力内判
            ServerAbilityService.register(UlModpack.MODID + ":", new HordeAbilityHandler());
        } catch (RuntimeException ex) {
            UlModpack.LOGGER.warn("[ul-modpack] ServerAbilityService prefix unavailable; "
                    + "abilities still registered: {}", ex.toString());
        }
    }

    /**
     * 尸群指挥（2026-11-07 合并 horde_recruit / horde_launch / horde_release，0.1 未发布
     * 不做兼容）：客户端射线选目标（{@link #clientPickTarget}，任意活体），服务端按
     * 「目标 + 潜行」分派——
     * <ul>
     *   <li>不潜行 + 目标僵尸族 → 招募（{@link HordeManager#tryRecruitAt}，说服度/腐肉
     *       规则不变，走已有招募逻辑）；</li>
     *   <li>潜行 + 目标是已招募队员 → 踢出队伍（{@link HordeManager#kickFollower}，
     *       原 release 的单体版）；</li>
     *   <li>潜行 + 无目标 → 尝试发动尸潮，不满足发动条件则解散当前队伍
     *       （{@link HordeManager#launchOrDisband}，原 launch 失败则 release 的合并语义）；</li>
     *   <li>其它组合 → 明确的失败提示（Actionbar 文案）。</li>
     * </ul>
     */
    private static final class CommandAbility extends AbstractActiveAbility {
        /** 目标距离宽松上限（防空指针/跨维异常的粗校验，不做视线/反作弊级校验）。 */
        private static final double WIDE_REACH_SQ = 8.0D * 8.0D;

        private CommandAbility() {
            super(ZombieLine.ABILITY_COMMAND);
        }

        /**
         * 客户端射线选目标（统一释放链路发请求前调用）：放宽为任意活体——
         * 招募/踢人判定全在服务端，客户端只交目标 id。命中返回实体 id，否则 0（= 无目标）。
         */
        @Override
        public int clientPickTarget(EntityPlayer player) {
            return HordeManager.findLookedLivingId(player);
        }

        @Override
        public ActivationResult activate(EntityPlayer player, MorphContext context) {
            if (!(player instanceof EntityPlayerMP)) {
                return ActivationResult.unavailable();
            }
            EntityPlayerMP mp = (EntityPlayerMP) player;
            net.minecraft.entity.EntityLivingBase target =
                    context == null ? null : context.getTarget();
            boolean far = target != null && player.getDistanceSq(target) > WIDE_REACH_SQ;

            if (!player.isSneaking()) {
                // 不潜行 → 招募：服务端轻校验目标（存在/僵尸族/距离宽松上限），
                // 说服度/腐肉/招募距离规则不变（tryRecruitAt 内）
                if (target == null || target.isDead || far
                        || !(target instanceof net.minecraft.entity.EntityLiving)
                        || !HordeManager.isZombieFamily(target)) {
                    HordeManager.action(mp, "ul.horde.recruit.fail.target");
                    return ActivationResult.unavailable();
                }
                HordeManager.tryRecruitAt(mp, (net.minecraft.entity.EntityLiving) target);
                return ActivationResult.success(0);
            }

            if (target != null) {
                // 潜行 + 有目标 → 踢出：只认自己的在队成员（服务端判定）
                if (target.isDead || far || !HordeManager.isTeamMember(mp, target)) {
                    HordeManager.action(mp, "ul.horde.command.fail.member");
                    return ActivationResult.unavailable();
                }
                HordeManager.kickFollower(mp, target);
                return ActivationResult.success(0);
            }

            // 潜行 + 无目标 → 尝试发动尸潮；不满足发动条件 → 解散当前队伍
            HordeManager.launchOrDisband(mp);
            return ActivationResult.success(0);
        }
    }

    /** 震地：泰坦形态的范围击退（{@link TitanFormHandler#slam}）。 */
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
            // titan_slam 是形态作用域能力（morph_abilities.json 的 giant.actions）：
            // 服务端只认「当前是巨人形态」，不需要也不接受 horde_leadership 授予；
            // horde_command 仍由技能全局提供（任何僵尸形态可用）。
            if (ZombieLine.ABILITY_SLAM.equals(id)) {
                return ZombieForms.isTitan(player);
            }
            return ZombieForms.hasLeadership(player);
        }

        @Override
        public ActivationResult activate(EntityPlayerMP player, String id) {
            return activate(player, id, null, null);
        }

        /**
         * targetId 通道路由：核心按客户端射线解析出的服务端目标经本重载传入，
         * 必须放进 {@link MorphContext} 再交给能力（否则默认三参委托会静默丢掉目标，
         * {@code horde_command} 永远拿不到客户端选中的目标）。
         */
        @Override
        public ActivationResult activate(EntityPlayerMP player, String id,
                                         com.google.gson.JsonObject params,
                                         net.minecraft.entity.EntityLivingBase target) {
            IAbility ability = AbilityRegistry.get(id);
            if (ability == null) {
                return ActivationResult.unavailable();
            }
            try {
                if (ability instanceof IActiveAbility) {
                    return ((IActiveAbility) ability)
                            .activate(player, new MorphContext(player, null, target));
                }
                return ability.activate(player);
            } catch (RuntimeException error) {
                UlModpack.LOGGER.warn("[ul-modpack] ability {} failed: {}", id, error.toString());
                return ActivationResult.unavailable();
            }
        }
    }
}
