package aaaa0ggmc.ulmodpack.zombie.horde;

import aaaa0ggmc.ulmodpack.UlModpack;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 自定义包通道 {@code ul:ability}：S2C「队伍快照」。
 *
 * <p>触发语义（2026-11-06 统一轮盘触发；2026-11-07 三能力合并）：尸群的全部主动能力
 * （{@code horde_command / titan_slam}）经 morphext
 * 统一释放链路（ClientAbilityExecutor → AbilityNetwork → ServerAbilityService →
 * {@link HordeAbilities}）触发；招募/踢出的目标获取在服务端按「目标 + 潜行」分派
 * （{@link HordeManager}，不接收客户端实体，客户端射线只交目标 id）。
 * 原 C2S 手势包（{@code UseMessage}，右键手势直连）已按用户拍板移除。</p>
 *
 * <p>S2C {@link TeamMessage} 只携带展示数据（说服度/上限/随从列表），由
 * {@code UlModpack.proxy}（SidedProxy）中转——专用服务器侧的 Handler 与消息类不引用
 * 任何客户端类，客户端类只在 {@code ClientProxy} 覆写里出现。
 */
public final class HordeNetwork {

    /**
     * 通道名 = {@code ul:ability}。**不得超过 20 字符**：1.12.2 原版
     * {@code CPacketCustomPayload/SPacketCustomPayload} 以 {@code readString(20)} 解码
     * 通道名，超长直接 DecoderException 断线（已用 forgeBin 实测验证）。故不带
     * {@code <modid>:} 前缀，与各类 javadoc 里的 {@code ul:ability} 一致。
     */
    public static final SimpleNetworkWrapper CHANNEL =
            NetworkRegistry.INSTANCE.newSimpleChannel("ul:ability");

    /**
     * S2C 队伍快照负载上限。原版单包 payload 上限 32767 字节（超限走 FML|MP 分片），
     * 16KB 一步到位不触发分片；生产侧 {@code HordeManager.teamJson} 对随从名截断 +
     * 列表封顶，保证实际 JSON 远小于此值（32 条 × ≈300B ≈ 9.6KB）。
     */
    public static final int MAX_TEAM_BYTES = 16384;

    private HordeNetwork() {
    }

    /** 注册消息（在 {@link aaaa0ggmc.ulmodpack.zombie.ZombieLine#register()} 调用）。 */
    public static void init() {
        CHANNEL.registerMessage(TeamHandler.class, TeamMessage.class, 0, Side.CLIENT);
    }

    /** 服务端把队伍快照推给主人客户端（缓存于客户端，B 键状态页读取）。 */
    public static void sendTeam(EntityPlayerMP player, String json) {
        CHANNEL.sendTo(new TeamMessage(json), player);
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
                // 负长度 / 超上限 / 超剩余：丢弃本包内容，不在解码线程抛异常；
                // 空串会被 HordeTeamCache.replace 当坏数据丢弃、保留旧缓存
                buf.skipBytes(buf.readableBytes());
                json = "";
                return;
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
