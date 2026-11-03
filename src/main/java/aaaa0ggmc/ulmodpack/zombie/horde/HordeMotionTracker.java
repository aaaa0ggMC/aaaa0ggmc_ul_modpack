package aaaa0ggmc.ulmodpack.zombie.horde;

import aaaa0ggmc.ulmodpack.UlModpack;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 「跑动中」判定的位移史跟踪。
 *
 * 服务端判玩家移动的三个坑（全实测过）：
 *  - moveForward/moveStrafing 不随包同步（恒 0）；
 *  - posX-prevPosX 每 tick 被实体更新归零，在 tick 首永远是 0；
 *  - motionX/Z 服务端不给玩家跑物理（输入轴为 0，永不更新）。
 * 所以自己跟踪：玩家 tick 里记最近一次水平位移的世界时间，
 * 「最近 {@value #MOVE_WINDOW_TICKS} tick 内动过」即算跑动中（宽容语义：
 * 玩家减速瞬间用能力也算，手感更好）。
 */
@Mod.EventBusSubscriber(modid = UlModpack.MODID)
public final class HordeMotionTracker {

    /** [TUNING] 跑动判定窗口（tick）。40=2 秒。 */
    private static final long MOVE_WINDOW_TICKS = 40L;

    private static final Map<UUID, double[]> LAST_POS = new HashMap<>();
    private static final Map<UUID, Long> LAST_MOVE_TICK = new HashMap<>();

    private HordeMotionTracker() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote) {
            return;
        }
        UUID id = event.player.getUniqueID();
        double[] last = LAST_POS.get(id);
        double x = event.player.posX;
        double z = event.player.posZ;
        if (last != null) {
            double dx = x - last[0];
            double dz = z - last[2];
            if (dx * dx + dz * dz > 1.0E-4) {
                LAST_MOVE_TICK.put(id, event.player.world.getTotalWorldTime());
            }
        }
        LAST_POS.put(id, new double[]{x, event.player.posY, z});
    }

    /** 最近窗口内移动过（含正在移动）。 */
    public static boolean movedRecently(EntityPlayerMP player) {
        Long last = LAST_MOVE_TICK.get(player.getUniqueID());
        return last != null && player.world.getTotalWorldTime() - last < MOVE_WINDOW_TICKS;
    }
}
