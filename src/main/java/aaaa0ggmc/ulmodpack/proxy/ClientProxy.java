package aaaa0ggmc.ulmodpack.proxy;

import aaaa0ggmc.morphext.client.status.StatusSectionRegistry;
import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.client.FollowerStatusSection;
import aaaa0ggmc.ulmodpack.client.HordeTeamCache;
import aaaa0ggmc.ulmodpack.client.TitanTransitionAnimators;
import aaaa0ggmc.ulmodpack.fluid.FluidLethe;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

/**
 * 客户端代理：模型注册 + 队伍快照缓存 + B 键状态页分节注册。
 * 专用服务器不加载本类（{@code SidedProxy} 服务端指向 {@code CommonProxy}）。
 */
public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        MinecraftForge.EVENT_BUS.register(this);
        // B 键状态页（morphext GuiAbilityStatus）的尸群队伍分节
        registerStatusSections();
        // 僵尸 ↔ 泰坦形态切换过渡动画（DI 注册到 morphext MorphTransitionRegistry）
        TitanTransitionAnimators.register();
    }

    private static void registerStatusSections() {
        // B 键状态页分节 SPI（morphext client/status，2026-11-03 落地）：
        // 只在客户端注册；专用服务器不加载 FollowerStatusSection / HordeTeamCache。
        StatusSectionRegistry.register(new FollowerStatusSection());
    }

    @SubscribeEvent
    public void onModelRegistry(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(
                ANKH_AMULET, 0,
                new ModelResourceLocation(ANKH_AMULET.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                ZEALOUS_FLESH, 0,
                new ModelResourceLocation(ZEALOUS_FLESH.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                TITAN_CROWN, 0,
                new ModelResourceLocation(TITAN_CROWN.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                TITAN_STATUE_ITEM, 0,
                new ModelResourceLocation(TITAN_STATUE_ITEM.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                SLEEPING_PILL, 0,
                new ModelResourceLocation(SLEEPING_PILL.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                LETHE_SOUP, 0,
                new ModelResourceLocation(LETHE_SOUP.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                PIECES, 0,
                new ModelResourceLocation(PIECES.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                SHELF_OF_PIECES_ITEM, 0,
                new ModelResourceLocation(SHELF_OF_PIECES_ITEM.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                BITTERSWEET_ORE_ITEM, 0,
                new ModelResourceLocation(BITTERSWEET_ORE_ITEM.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                SWEETENER, 0,
                new ModelResourceLocation(SWEETENER.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                BITTERANT, 0,
                new ModelResourceLocation(BITTERANT.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                SWEET_DREAM, 0,
                new ModelResourceLocation(SWEET_DREAM.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                NIGHTMARE, 0,
                new ModelResourceLocation(NIGHTMARE.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                DNA_SWAB, 0,
                new ModelResourceLocation(DNA_SWAB.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                REVERSAL_GEM, 0,
                new ModelResourceLocation(REVERSAL_GEM.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                WEDDING_RING, 0,
                new ModelResourceLocation(WEDDING_RING.getRegistryName(), "inventory"));
        ModelLoader.setCustomModelResourceLocation(
                DNA_RECOMBINATOR_ITEM, 0,
                new ModelResourceLocation(DNA_RECOMBINATOR_ITEM.getRegistryName(), "inventory"));

        final ModelResourceLocation letheFluidLoc = new ModelResourceLocation(
                UlModpack.MODID + ":" + FluidLethe.FLUID_NAME, "fluid");
        ModelLoader.setCustomStateMapper(LETHE_WATER_BLOCK, new StateMapperBase() {
            @Override
            protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                return letheFluidLoc;
            }
        });
    }

    @Override
    public void onHordeSnapshot(String json) {
        // 网络线程 → 主线程；客户端类只在本覆写里出现（专用服务器静态检查红线）
        Minecraft.getMinecraft().addScheduledTask(() -> HordeTeamCache.replace(json));
    }

    /**
     * 断线/换服/退出世界时清空队伍缓存（否则 B 键状态页会显示上一个服务器的旧队伍）。
     * 事件在 netty 线程派发，和快照一样调度回主线程执行，保证与 replace() 的先后顺序。
     */
    @SubscribeEvent
    public void onClientDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(HordeTeamCache::clear);
    }
}
