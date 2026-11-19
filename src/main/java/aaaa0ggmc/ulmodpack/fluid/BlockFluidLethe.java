package aaaa0ggmc.ulmodpack.fluid;

import aaaa0ggmc.ulmodpack.UlModpack;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.BlockFluidClassic;
import net.minecraftforge.fluids.Fluid;

/**
 * 孟婆汤（Water of Lethe）流体方块。
 * <ul>
 *   <li>常规生物落入时触发「遗忘」效果（10s 倒计时结束清仇恨 + 1s 失忆）；</li>
 *   <li>玩家手持空碗右键源流体可盛取碗装孟婆汤（每格源流体可盛 16 碗，潜行右键可批量盛取）。</li>
 * </ul>
 */
public class BlockFluidLethe extends BlockFluidClassic {

    public BlockFluidLethe(Fluid fluid) {
        super(fluid, Material.WATER);
        setRegistryName(UlModpack.MODID, FluidLethe.FLUID_NAME);
        setTranslationKey(UlModpack.MODID + "." + FluidLethe.FLUID_NAME);
    }

    @Override
    public void onEntityCollision(World world, BlockPos pos, IBlockState state, Entity entity) {
        if (!world.isRemote && entity instanceof EntityLiving) {
            LetheInteractionHandler.onMobInLethe((EntityLiving) entity);
        }
        super.onEntityCollision(world, pos, state, entity);
    }

    @Override
    public void onBlockAdded(World world, BlockPos pos, IBlockState state) {
        super.onBlockAdded(world, pos, state);
        if (!world.isRemote) {
            LetheInteractionHandler.resetScoops(world, pos);
        }
    }

    @Override
    public void breakBlock(World world, BlockPos pos, IBlockState state) {
        if (!world.isRemote) {
            LetheInteractionHandler.clearScoops(world, pos);
        }
        super.breakBlock(world, pos, state);
    }
}
