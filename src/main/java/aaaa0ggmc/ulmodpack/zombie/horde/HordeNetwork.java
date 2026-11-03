package aaaa0ggmc.ulmodpack.zombie.horde;

import aaaa0ggmc.uld.UlStrength;
import aaaa0ggmc.ulmodpack.UlModpack;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 自定义能力包通道 {@code ul:ability}（客户端发、服务端判）。
 *
 * 1.12.2 空手右键空气服务端收不到交互事件，故「对空气使用」（发动 / 解放 / 震地）
 * 由客户端在「空手 + 潜行 + 右键」时上报一个空消息；服务端再用 {@code rayTrace}
 * 权威分派：瞄到方块/实体 → 招募；瞄到空气 → 发动/解放/震地。包里不传玩家身份，
 * 一律取连接上下文里的玩家。
 */
public final class HordeNetwork {

    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel(UlModpack.MODID + ":ul_ability");

    private HordeNetwork() {
    }

    /** 注册消息（在 {@link aaaa0ggmc.ulmodpack.zombie.ZombieLine#register()} 调用）。 */
    public static void init() {
        CHANNEL.registerMessage(Handler.class, UseMessage.class, 0, Side.SERVER);
    }

    /** 客户端上报一次「使用主动能力」。 */
    public static void sendUse() {
        CHANNEL.sendToServer(new UseMessage());
    }

    private static double airReach() {
        return UlStrength.num("zombie_tide.horde.air_reach", 4.5D);
    }

    /** 空消息：语义全在「谁发的 + 服务端视线判定」里。 */
    public static final class UseMessage implements IMessage {
        public UseMessage() {
        }

        @Override
        public void fromBytes(ByteBuf buf) {
        }

        @Override
        public void toBytes(ByteBuf buf) {
        }
    }

    public static final class Handler implements IMessageHandler<UseMessage, IMessage> {
        @Override
        public IMessage onMessage(UseMessage message, MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.connection == null || player.connection.player != player || player.isDead) {
                    return;
                }
                // 空手 + 潜行才算「使用主动能力」姿态
                if (!player.isSneaking()
                        || !player.getHeldItemMainhand().isEmpty()
                        || !player.getHeldItemOffhand().isEmpty()) {
                    return;
                }
                double reach = airReach();
                RayTraceResult blockHit = player.rayTrace(reach, 1.0F);
                if (blockHit != null && blockHit.typeOfHit != RayTraceResult.Type.MISS) {
                    HordeManager.tryRecruit(player); // 瞄到方块 → 招募
                } else if (lookingAtEntity(player, reach)) {
                    HordeManager.tryRecruit(player); // 瞄到实体 → 招募
                } else {
                    HordeManager.onAirUse(player); // 空气 → 发动/解放/震地
                }
            });
            return null;
        }

        /** 视线（eye→reach）是否与某实体碰撞箱相交。 */
        private static boolean lookingAtEntity(EntityPlayerMP player, double reach) {
            Vec3d eye = player.getPositionEyes(1.0F);
            Vec3d look = player.getLook(1.0F);
            Vec3d end = eye.add(look.x * reach, look.y * reach, look.z * reach);
            AxisAlignedBB box = player.getEntityBoundingBox().grow(reach);
            for (Entity entity : player.world.getEntitiesWithinAABBExcludingEntity(player, box)) {
                if (!entity.canBeCollidedWith()) {
                    continue;
                }
                AxisAlignedBB bounds = entity.getEntityBoundingBox();
                if (bounds != null && bounds.calculateIntercept(eye, end) != null) {
                    return true;
                }
            }
            return false;
        }
    }
}
