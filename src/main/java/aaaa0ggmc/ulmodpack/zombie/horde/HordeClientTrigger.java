package aaaa0ggmc.ulmodpack.zombie.horde;

import aaaa0ggmc.ulmodpack.UlModpack;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Mouse;

/**
 * 客户端输入触发器：空手 + 潜行 + 右键（边沿）时上报一次 {@code ul:ability} 包。
 *
 * 1.12.2 空手右键空气服务端收不到交互事件，故在这里把「使用主动能力」的手势上报上去，
 * 由服务端 {@link HordeNetwork.Handler} 用 rayTrace 权威分派招募 / 发动 / 解放 / 震地。
 * 只做姿态判定（空手 + 潜行），不判技能/形态——那些在服务端。
 *
 * 每 client tick 轮询物理按键（键鼠通用、兼容两次 tick 间的快速点击），边沿触发一次。
 * 客户端侧自动注册（{@code @Mod.EventBusSubscriber(Side.CLIENT)}），无需 register() 调用。
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = UlModpack.MODID)
public final class HordeClientTrigger {

    private static boolean rightDown = false;

    private HordeClientTrigger() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        boolean down = false;
        if (mc.player != null && mc.inGameHasFocus && mc.currentScreen == null) {
            down = Mouse.isButtonDown(1); // 1 = 右键
        }
        if (down && !rightDown && mc.player != null
                && mc.player.isSneaking()
                && mc.player.getHeldItemMainhand().isEmpty()
                && mc.player.getHeldItemOffhand().isEmpty()) {
            HordeNetwork.sendUse();
        }
        rightDown = down;
    }
}
