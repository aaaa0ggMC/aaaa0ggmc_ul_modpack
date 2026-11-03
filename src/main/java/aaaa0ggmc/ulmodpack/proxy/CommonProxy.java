package aaaa0ggmc.ulmodpack.proxy;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.infection.UlTriggers;
import aaaa0ggmc.ulmodpack.item.ItemAnkhAmulet;
import aaaa0ggmc.ulmodpack.item.ItemZealousFlesh;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class CommonProxy {

    /** 整合包定制物品的创造标签页（后续物品都挂这里）。 */
    public static final CreativeTabs TAB = new CreativeTabs(UlModpack.MODID) {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(ANKH_AMULET);
        }
    };

    public static final ItemAnkhAmulet ANKH_AMULET = new ItemAnkhAmulet();

    /** 热忱的腐肉：吃下变泰坦（不死者线产出，随从被铁傀儡打死掉落）。 */
    public static final ItemZealousFlesh ZEALOUS_FLESH = new ItemZealousFlesh();

    public void preInit(FMLPreInitializationEvent event) {
        UlTriggers.init();
    }

    public void init(FMLInitializationEvent event) {
    }

    public void postInit(FMLPostInitializationEvent event) {
    }
}
