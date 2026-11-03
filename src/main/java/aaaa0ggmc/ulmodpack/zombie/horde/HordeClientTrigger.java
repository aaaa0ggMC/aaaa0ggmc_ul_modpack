package aaaa0ggmc.ulmodpack.zombie.horde;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import aaaa0ggmc.ulmodpack.UlModpack;

/**
 * 客户端输入触发器（用户设计 2026-11-03 重做）：
 * <ul>
 *   <li><b>对着僵尸使用技能 = 招募</b>：空手右键命中僵尸族实体（交互事件的实体命中就是
 *       客户端 ray trace 结果）→ 上报目标实体 id；</li>
 *   <li><b>对着空气潜行使用技能 = 解散当前队伍</b>：空手 + 潜行 + 右键空气（RightClickEmpty）
 *       → 上报空目标 id。</li>
 * </ul>
 *
 * 1.12.2 空手右键空气服务端收不到交互事件，故在这里把手势上报上去，
 * 由服务端 {@link HordeNetwork.Handler} 做全部权威判定（距离/权限/说服度）。
 * 只做姿态判定（空手 + 手势种类），不判技能/形态——那些在服务端。
 *
 * 触发走**标准输入抽象**：{@code PlayerInteractEvent.RightClickEmpty} 与
 * {@code PlayerInteractEvent.EntityInteract(Specific)}——真实鼠标与 ul-agent-control 的
 * 虚拟 use（KeyBinding 状态驱动同一条路径）都从这两个事件进来，不轮询物理鼠标
 * （Wayland/远程桌面下拿不到可靠鼠标状态）。
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = UlModpack.MODID)
public final class HordeClientTrigger {

    /** 同一次右键可能连发事件（主/副手各一次、按住每 4 tick 一次），按冷却去重。 */
    private static long lastSendMs;

    private HordeClientTrigger() {
    }

    /** 对空气（潜行 + 空手右键）→ 解散队伍：上报空目标 id。 */
    @SubscribeEvent
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || event.getWorld() == null || !event.getWorld().isRemote) {
            return;
        }
        if (!mc.player.isSneaking()
                || !mc.player.getHeldItemMainhand().isEmpty()
                || !mc.player.getHeldItemOffhand().isEmpty()) {
            return;
        }
        if (dedupe()) {
            return;
        }
        HordeNetwork.sendUse(0);
    }

    /**
     * 对着僵尸（空手右键命中僵尸族实体）→ 招募它：上报目标实体 id。
     *
     * <p>1.12.2 右键实体会先后派发 {@code EntityInteractSpecific}（INTERACT_AT）与
     * {@code EntityInteract}（INTERACT，经 {@code EntityPlayer.interactOn}），主/副手
     * 各一轮；两个事件都接、靠 {@link #dedupe()} 去重，只发一份上报。
     */
    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        reportZombieTarget(event.getWorld(), event.getTarget());
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        reportZombieTarget(event.getWorld(), event.getTarget());
    }

    private static void reportZombieTarget(net.minecraft.world.World world, Entity target) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || world == null || !world.isRemote) {
            return;
        }
        if (!mc.player.getHeldItemMainhand().isEmpty()
                || !mc.player.getHeldItemOffhand().isEmpty()) {
            return;
        }
        // 僵尸族（含僵尸猪人——EntityPigZombie 不是 EntityZombie 子类）才走招募；
        // 判定与服务端 HordeManager.isZombieFamily 同一套，其它实体保持原版交互
        if (!HordeManager.isZombieFamily(target)) {
            return;
        }
        if (dedupe()) {
            return;
        }
        HordeNetwork.sendUse(target.getEntityId());
    }

    private static boolean dedupe() {
        long now = System.currentTimeMillis();
        if (now - lastSendMs < 250) {
            return true;
        }
        lastSendMs = now;
        return false;
    }

    /** 兼容入口：无（物理鼠标轮询已移除，见类注释）。 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // 空实现——保留订阅方法位以免 FML 事件扫描行为变化；实际触发在两个交互事件里。
    }
}
