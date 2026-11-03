package aaaa0ggmc.ulmodpack.proxy;

import aaaa0ggmc.morphext.client.status.StatusSectionRegistry;
import aaaa0ggmc.ulmodpack.client.FollowerStatusSection;
import aaaa0ggmc.ulmodpack.client.HordeTeamCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
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
