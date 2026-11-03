package aaaa0ggmc.ulmodpack.zombie.horde;

import aaaa0ggmc.uld.UlStrength;
import aaaa0ggmc.ulmodpack.UlModpack;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 自定义包通道 {@code ul:ability}：C2S「使用主动能力」+ S2C「队伍快照」。
 *
 * <p>触发语义（用户设计 2026-11-03 重做）：
 * <ul>
 *   <li><b>对着僵尸使用技能 = 招募</b>：客户端 ray trace（交互事件的实体命中）命中僵尸族
 *       实体时上报目标实体 id，服务端校验距离/权限后招募它（说服度匹配规则见
 *       {@link HordeManager#tryRecruitAt}）；</li>
 *   <li><b>对着空气潜行使用技能 = 解散当前队伍</b>（明确的解散动作）；</li>
 *   <li>发动屠村 / 震地走能力轮盘（{@link HordeAbilities}）与 {@code /ulpack tide} 命令。</li>
 * </ul>
 *
 * 1.12.2 空手右键空气服务端收不到交互事件，故客户端在「空手 + 潜行 + 右键」时上报
 * 空目标 id；包里不传玩家身份，一律取连接上下文里的玩家，服务端做全部权威校验。
 *
 * <p>S2C {@link TeamMessage} 只携带展示数据（说服度/上限/随从列表），由
 * {@code UlModpack.proxy}（SidedProxy）中转——专用服务器侧的 Handler 与消息类不引用
 * 任何客户端类，客户端类只在 {@code ClientProxy} 覆写里出现。
 */
public final class HordeNetwork {

    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel(UlModpack.MODID + ":ul_ability");

    /** S2C 队伍快照负载上限（随从 ≤ followCap 20 条，16KB 极宽松）。 */
    public static final int MAX_TEAM_BYTES = 16384;

    private HordeNetwork() {
    }

    /** 注册消息（在 {@link aaaa0ggmc.ulmodpack.zombie.ZombieLine#register()} 调用）。 */
    public static void init() {
        CHANNEL.registerMessage(Handler.class, UseMessage.class, 0, Side.SERVER);
        CHANNEL.registerMessage(TeamHandler.class, TeamMessage.class, 1, Side.CLIENT);
    }

    /**
     * 客户端上报一次「使用主动能力」。
     *
     * @param targetId 客户端 ray trace 命中的僵尸族实体 id；0 = 对空气（潜行时=解散）
     */
    public static void sendUse(int targetId) {
        CHANNEL.sendToServer(new UseMessage(targetId));
    }

    /** 服务端把队伍快照推给主人客户端（缓存于客户端，B 键状态页读取）。 */
    public static void sendTeam(EntityPlayerMP player, String json) {
        CHANNEL.sendTo(new TeamMessage(json), player);
    }

    private static double airReach() {
        return UlStrength.num("zombie_tide.horde.air_reach", 4.5D);
    }

    /** 「使用主动能力」：targetId>0 = 对着僵尸族实体使用（招募它）；0 = 对空气（潜行=解散）。 */
    public static final class UseMessage implements IMessage {
        public int targetId;

        public UseMessage() {
        }

        public UseMessage(int targetId) {
            this.targetId = targetId;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            targetId = buf.readInt();
            if (targetId < 0 || targetId > 1 << 24) {
                throw new IllegalArgumentException("Invalid target entity id");
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(targetId);
        }
    }

    public static final class Handler implements IMessageHandler<UseMessage, IMessage> {
        @Override
        public IMessage onMessage(UseMessage message, MessageContext context) {
            final int targetId = message.targetId;
            EntityPlayerMP player = context.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (player.connection == null || player.connection.player != player || player.isDead) {
                    return;
                }
                // 空手才算「使用主动能力」姿态（潜行与否按分支判定）
                if (!player.getHeldItemMainhand().isEmpty() || !player.getHeldItemOffhand().isEmpty()) {
                    return;
                }
                if (targetId > 0) {
                    // 对着僵尸使用技能 = 招募：只信客户端报的目标 id，服务端校验距离/权限/种类
                    Entity target = player.world.getEntityByID(targetId);
                    if (target instanceof EntityLiving) {
                        HordeManager.tryRecruitAt(player, (EntityLiving) target);
                    } else {
                        HordeManager.tryRecruitAt(player, null); // 目标已消失/无效 → 明确失败提示
                    }
                    return;
                }
                // 对空气潜行使用技能 = 解散当前队伍（明确的解散动作）
                if (player.isSneaking()) {
                    HordeManager.disband(player);
                }
            });
            return null;
        }
    }

    /** S2C 队伍快照（JSON：说服度/上限/尸潮状态/随从列表）。 */
    public static final class TeamMessage implements IMessage {
        public String json;

        public TeamMessage() {
        }

        public TeamMessage(String json) {
            this.json = json == null ? "{}" : json;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            int size = buf.readInt();
            if (size < 0 || size > MAX_TEAM_BYTES || size > buf.readableBytes()) {
                throw new IllegalArgumentException("Invalid team snapshot size");
            }
            byte[] bytes = new byte[size];
            buf.readBytes(bytes);
            json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (bytes.length > MAX_TEAM_BYTES) {
                throw new IllegalArgumentException("Team snapshot exceeds size limit");
            }
            buf.writeInt(bytes.length);
            buf.writeBytes(bytes);
        }
    }

    /**
     * 只经 {@code SidedProxy} 中转到客户端缓存；专用服务器不会执行，且本类不引用任何
     * 客户端类（静态检查红线）。
     */
    public static final class TeamHandler implements IMessageHandler<TeamMessage, IMessage> {
        @Override
        public IMessage onMessage(TeamMessage message, MessageContext context) {
            UlModpack.proxy.onHordeSnapshot(message.json);
            return null;
        }
    }
}
