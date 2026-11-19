package aaaa0ggmc.ulmodpack.dna;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * DNA 重组机 C2S 网络通道（GUI 点击「开始重组」按钮通知服务端权威校验并执行）。
 */
public final class DnaNetwork {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("ul_dna");
    private static boolean initialized;

    private DnaNetwork() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        CHANNEL.registerMessage(StartRecombineHandler.class, PacketStartRecombine.class, 0, Side.SERVER);
    }

    public static void sendStartRecombine(BlockPos pos) {
        CHANNEL.sendToServer(new PacketStartRecombine(pos));
    }

    public static class PacketStartRecombine implements IMessage {
        private BlockPos pos;

        public PacketStartRecombine() {
        }

        public PacketStartRecombine(BlockPos pos) {
            this.pos = pos;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            this.pos = BlockPos.fromLong(buf.readLong());
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeLong(this.pos.toLong());
        }
    }

    public static class StartRecombineHandler implements IMessageHandler<PacketStartRecombine, IMessage> {
        @Override
        public IMessage onMessage(PacketStartRecombine msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.pos == null || player.getDistanceSq(msg.pos.add(0.5D, 0.5D, 0.5D)) > 64.0D) {
                    return;
                }
                TileEntity te = player.world.getTileEntity(msg.pos);
                if (te instanceof TileEntityDnaRecombinator) {
                    ((TileEntityDnaRecombinator) te).tryStartRecombine(player);
                }
            });
            return null;
        }
    }
}
