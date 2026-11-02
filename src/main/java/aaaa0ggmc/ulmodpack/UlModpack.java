package aaaa0ggmc.ulmodpack;

import aaaa0ggmc.ulmodpack.command.CommandUlPack;
import aaaa0ggmc.ulmodpack.infection.InfectionHandler;
import aaaa0ggmc.ulmodpack.infection.UlTriggers;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * shaping_the_world 整合包深度定制主模组。
 *
 * 当前内容：
 *  - 安卡护符（Baubles AMULET 槽，佩戴 +2 护甲）；
 *  - 佩戴时被僵尸一族杀死：免去死亡（无死亡界面），化身为击杀者的形态，护符碎裂，
 *    解锁进度「被感染」；
 *  - FTBQuests 形态检测任务注入（type: aaaa0ggmc_ul_modpack:morph）。
 *
 * 后续整合包级联动（与 morphext 及各内容模组的适配）都挂在本模组下。
 */
@Mod(
        modid = UlModpack.MODID,
        name = UlModpack.NAME,
        version = UlModpack.VERSION,
        dependencies = "required-after:baubles;required-after:metamorph;required-after:aaaa0ggmc_morph_extension;required-after:ftbquests",
        acceptableRemoteVersions = "*"
)
public class UlModpack {
    public static final String MODID = "aaaa0ggmc_ul_modpack";
    public static final String NAME = "UL Modpack Core";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    @Mod.Instance(MODID)
    public static UlModpack INSTANCE;

    @SidedProxy(
            clientSide = "aaaa0ggmc.ulmodpack.proxy.ClientProxy",
            serverSide = "aaaa0ggmc.ulmodpack.proxy.CommonProxy"
    )
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("[ul-modpack] Initializing {} v{}...", NAME, VERSION);
        proxy.preInit(event);
        InfectionHandler.register();
        LOGGER.info("[ul-modpack] Ankh amulet online. The infected shall rise again.");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandUlPack());
    }
}
