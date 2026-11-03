package aaaa0ggmc.ulmodpack.zombie.horde;

import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import aaaa0ggmc.ulmodpack.UlModpack;

/**
 * 客户端输入触发器：空手 + 潜行 + 右键（边沿）时上报一次 {@code ul:ability} 包。
 *
 * 1.12.2 空手右键空气服务端收不到交互事件，故在这里把「使用主动能力」的手势上报上去，
 * 由服务端 {@link HordeNetwork.Handler} 用 rayTrace 权威分派招募 / 发动 / 解放 / 震地。
 * 只做姿态判定（空手 + 潜行），不判技能/形态——那些在服务端。
 *
 * 触发走**标准输入抽象**：{@code PlayerInteractEvent.RightClickEmpty}——
 * 真实鼠标右键空气（Minecraft.rightClickMouse → ForgeHooks.onEmptyClick）与
 * ul-agent-control 的虚拟 use（KeyBinding 状态驱动同一条路径）都从这个事件进来，
 * 不轮询物理鼠标（Wayland/远程桌面下拿不到可靠鼠标状态）。
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = UlModpack.MODID)
public final class HordeClientTrigger {

    /** 同一次右键可能连发事件（按住每 4 tick 一次），按冷却去重。 */
    private static long lastSendMs;

    private HordeClientTrigger() {
    }

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
        long now = System.currentTimeMillis();
        if (now - lastSendMs < 250) {
            return;
        }
        lastSendMs = now;
        HordeNetwork.sendUse();
    }

    /** 兼容入口：无（物理鼠标轮询已移除，见类注释）。 */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        // 空实现——保留订阅方法位以免 FML 事件扫描行为变化；实际触发在 onRightClickEmpty。
    }
}
