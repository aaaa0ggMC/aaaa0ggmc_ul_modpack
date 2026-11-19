package aaaa0ggmc.ulmodpack.dna;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.IGuiHandler;

import javax.annotation.Nullable;

/**
 * DNA 重组机 GUI 处理器（服务端返回 Container，客户端返回 GuiContainer）。
 */
public class DnaGuiHandler implements IGuiHandler {

    public static final int GUI_DNA_RECOMBINATOR = 1;

    @Nullable
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == GUI_DNA_RECOMBINATOR) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof TileEntityDnaRecombinator) {
                return new ContainerDnaRecombinator(player.inventory, (TileEntityDnaRecombinator) te);
            }
        }
        return null;
    }

    @Nullable
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id == GUI_DNA_RECOMBINATOR) {
            TileEntity te = world.getTileEntity(new BlockPos(x, y, z));
            if (te instanceof TileEntityDnaRecombinator) {
                return new GuiDnaRecombinator(player.inventory, (TileEntityDnaRecombinator) te);
            }
        }
        return null;
    }
}
