package aaaa0ggmc.ulmodpack.item;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.potion.Potion;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** 物品注册（1.12.2 走 RegistryEvent.Register，构造期自动订阅，时机早于注册事件）。 */
@Mod.EventBusSubscriber(modid = UlModpack.MODID)
public class ItemRegistryHandler {

    private ItemRegistryHandler() {
    }

    @SubscribeEvent
    public static void onRegisterBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(CommonProxy.TITAN_STATUE);
    }

    @SubscribeEvent
    public static void onRegisterPotions(RegistryEvent.Register<Potion> event) {
        event.getRegistry().register(CommonProxy.POTION_DEEP_SLEEP);
    }

    @SubscribeEvent
    public static void onRegisterItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().register(CommonProxy.ANKH_AMULET);
        event.getRegistry().register(CommonProxy.ZEALOUS_FLESH);
        event.getRegistry().register(CommonProxy.TITAN_CROWN);
        event.getRegistry().register(CommonProxy.TITAN_STATUE_ITEM);
        event.getRegistry().register(CommonProxy.SLEEPING_PILL);
    }
}
