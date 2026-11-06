package aaaa0ggmc.ulmodpack.zombie.horde;

import aaaa0ggmc.morphext.faction.FactionManager;
import aaaa0ggmc.morphext.follower.Followers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.UUID;

/**
 * 尸潮的 Forge 事件接线（由 {@link aaaa0ggmc.ulmodpack.zombie.ZombieLine#register()} 手动注册，
 * 不用 {@code @Mod.EventBusSubscriber} 以免重复注册）。
 *
 * 招募/踢出/发动等主动能力不走本类——统一经能力轮盘释放链路（{@link HordeAbilities}
 * → ServerAbilityService）触发（2026-11-06 右键手势已移除；2026-11-07 三能力合并为
 * {@code horde_command}）。本类只接：
 *  - 玩家死亡 → 尸潮失败（即使死亡随后被感染/仪式接住，也判失败）；
 *  - 随从被铁傀儡打死（LivingDropsEvent）→ 掉热忱的腐肉；
 *  - 世界 tick → 推进尸潮（刷铁傀儡 / 判成功 / 判中止）；
 *  - 玩家 tick → 队伍快照节流同步（B 键状态页数据源，变化才推、5 秒一拍）
 *    + 队员常驻描边续挂（每 20 tick 刷原版发光，见 HordeManager.refreshFollowerGlow）；
 *  - 随从把主人设为目标 → 清目标（随从不攻击主人）。
 */
public final class HordeEvents {

    public HordeEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) {
            return;
        }
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            HordeManager.refreshFollowerGlow(player);
            HordeManager.syncTeamIfChanged(player);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntityLiving() instanceof EntityPlayerMP) {
            HordeManager.onOwnerDeath((EntityPlayerMP) event.getEntityLiving());
        }
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        HordeManager.onFollowerDrops(event);
    }

    @SubscribeEvent
    public static void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            HordeManager.tick(event.world);
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        // 随从不伤主人（仅定向打击）：1.12.2 的 LivingSetAttackTargetEvent 只读、不可取消，
        // 故在攻击入口拦伤害。原版僵尸 AI 仍可能把主人设为目标并起手，但定向伤害被取消，
        // 等效「不攻击主人」。
        // 波及不豁免（2026-11-04 拍板）：随从自爆/爆炸、范围效果溅射到主人照常结算——
        // 爆炸就是爆炸，波及谁谁掉血；判据与阵营门同一真值（FactionManager.isProtectionApplicable）。
        if (!(event.getEntityLiving() instanceof EntityPlayer)) {
            return;
        }
        EntityPlayer victim = (EntityPlayer) event.getEntityLiving();
        Entity attacker = event.getSource().getTrueSource();
        if (attacker == null) {
            return;
        }
        UUID owner = Followers.getOwnerUuid(attacker);
        if (owner != null && owner.equals(victim.getUniqueID())) {
            if (FactionManager.isProtectionApplicable(attacker, victim, event.getSource())) {
                aaaa0ggmc.morphext.debug.DamageDebugLogger.atk("HordeEvents", victim, event.getSource(),
                        event.getAmount(), "cancel@HordeEvents:owner-gate", "owner=follower");
                event.setCanceled(true);
            } else {
                aaaa0ggmc.morphext.debug.DamageDebugLogger.atk("HordeEvents", victim, event.getSource(),
                        event.getAmount(), "pass:owner-splash", "owner=follower");
            }
        }
    }
}
