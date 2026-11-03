package aaaa0ggmc.ulmodpack.zombie.horde;

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
 * 招募/解散的输入触发不走本类——手势由客户端经 {@link HordeNetwork} 的
 * {@code ul:ability} 包上报（对着僵尸=招募、对空气潜行=解散），
 * 服务端权威校验（避免与交互事件双重触发）。
 * 本类只接：
 *  - 玩家死亡 → 尸潮失败（即使死亡随后被感染/仪式接住，也判失败）；
 *  - 随从被铁傀儡打死（LivingDropsEvent）→ 掉热忱的腐肉；
 *  - 世界 tick → 推进尸潮（刷铁傀儡 / 判成功 / 判中止）；
 *  - 玩家 tick → 队伍快照节流同步（B 键状态页数据源，变化才推、5 秒一拍）；
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
            HordeManager.syncTeamIfChanged((EntityPlayerMP) event.player);
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
        // 随从不伤主人：1.12.2 的 LivingSetAttackTargetEvent 只读、不可取消，故在攻击入口拦伤害。
        // 原版僵尸 AI 仍可能把主人设为目标并起手，但伤害被取消，等效「不攻击主人」。
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
            event.setCanceled(true);
        }
    }
}
