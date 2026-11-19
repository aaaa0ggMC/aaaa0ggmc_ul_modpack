package aaaa0ggmc.ulmodpack.fluid;

import aaaa0ggmc.ulmodpack.UlModpack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;

/**
 * 孟婆汤（Water of Lethe）流体定义。
 * 通过 Forge {@code FluidRegistry} + {@code UniversalBucket} 注册，天然兼容科技模组储罐、管道与流体容器。
 */
public class FluidLethe extends Fluid {

    public static final String FLUID_NAME = "lethe_water";
    /** 复用原版 32 帧平滑水体贴图 + 淡紫雾白着色，避免低帧自定义贴图在光影下闪烁。 */
    public static final ResourceLocation STILL = new ResourceLocation("minecraft", "blocks/water_still");
    public static final ResourceLocation FLOWING = new ResourceLocation("minecraft", "blocks/water_flow");

    public FluidLethe() {
        super(FLUID_NAME, STILL, FLOWING);
        setUnlocalizedName(UlModpack.MODID + "." + FLUID_NAME);
        setColor(0xFFE8D8FF);
        setDensity(1050);
        setViscosity(1200);
        setLuminosity(3);
        setTemperature(305);
    }
}
