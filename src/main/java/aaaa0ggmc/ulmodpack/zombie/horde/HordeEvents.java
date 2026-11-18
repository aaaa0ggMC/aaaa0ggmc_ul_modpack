package aaaa0ggmc.ulmodpack.zombie.horde;

import aaaa0ggmc.morphext.faction.FactionManager;
import aaaa0ggmc.morphext.follower.Followers;
import mchorse.metamorph.api.events.MorphEvent;
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
 *  - 玩家重生 → 队伍快照即推（B 键状态页重生刷新，L6）；
 *  - 随从被铁傀儡打死（LivingDropsEvent）→ 掉热忱的腐肉；
 *  - 世界 tick → 推进尸潮（刷铁傀儡 / 判成功 / 判中止）；
 *  - 玩家 tick → 队伍快照节流同步（B 键状态页数据源，变化才推、5 秒一拍）
 *    + 队员常驻白色描边续挂（每 20 tick 刷核心 Outlines，见 HordeManager.refreshFollowerGlow）；
 *  - 变形成功/解除（Metamorph {@code MorphEvent.Post}）→ 队伍快照即推：分节显隐判据是
 *    「horde_command 能力是否被当前形态授予」，形态一变判据就变，不能等下一拍节流；
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

    /**
     * 重生即推一次队伍快照（L6 重生刷新）：死亡期间随从/尸潮变化不必等下一拍
     * 节流同步（syncTeamIfChanged 最多 5 秒一拍），B 键状态页零窗口刷新。
     */
    @SubscribeEvent
    public static void onPlayerRespawn(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            HordeManager.syncTeam((EntityPlayerMP) event.player);
        }
    }

    /**
     * 变形成功 / 解除变形（Metamorph {@code MorphEvent.Post}）即推一次队伍快照：
     * 状态页分节显隐判据 = 「{@code horde_command} 能力是否被当前形态授予」
     * （{@code ZombieForms.canCommandHorde}），形态一变判据立刻翻转，等
     * {@link #onPlayerTick} 的 5 秒节流会让取消变形后的面板多挂最多 5 秒。
     * 只取服务端玩家（事件两侧都会派发；客户端侧由 S2C 快照覆盖，不发包）。
     */
    @SubscribeEvent
    public static void onMorphChange(MorphEvent.Post event) {
        if (event.player instanceof EntityPlayerMP) {
            try {
                HordeManager.syncTeam((EntityPlayerMP) event.player);
            } catch (RuntimeException error) {
                // 变身事件里抛异常会被 Forge 吞掉并打断 Metamorph 的变身流程，这里自吞并留痕
                org.apache.logging.log4j.LogManager.getLogger("ulmodpack")
                        .warn("[horde] team sync on morph failed: {}", error.toString());
            }
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
    // 随从对主人的定向友伤拦截与仇恨清除已由 morph-extension 核心 FactionManager.onLivingAttack 统一接管。
}
