package aaaa0ggmc.ulmodpack;

import aaaa0ggmc.ulmodpack.command.CommandUlPack;
import aaaa0ggmc.ulmodpack.config.AnkhConfig;
import aaaa0ggmc.ulmodpack.infection.InfectionHandler;
import aaaa0ggmc.ulmodpack.infection.UlTriggers;
import aaaa0ggmc.ulmodpack.zombie.ZombieLine;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Unlimited Life（UL）整合包的内容主模组。
 *
 * 定位：整合包额外内容的容器——道具、机制、玩法系统、第三方联动等整合包级
 * 定制都放在本模组里，按包（package）分区组织，随整合包持续扩充。
 *
 * 当前内容：
 *  - item/      安卡护符（Baubles AMULET 槽，佩戴 +2 护甲）；
 *  - infection/ 感染变身：玩家种族在安卡白名单内（默认 human/villager）且佩戴护符、
 *               被不死者阵营（ul:undead，数据驱动）杀死时免死并感染变身，护符碎裂，
 *               解锁进度「被感染」；以死亡转化管线（morphext conversion）第 10 级阶段接入；
 *  - race/      种族判定（形态映射 race_map + 显式记录 ul.race + 兜底默认），
 *               供安卡白名单等玩法按「种族」而非具体形态判定；
 *  - config/    ankh.json：种族白名单、种族映射、击杀者类别等纯数据配置
 *               （config/aaaa0ggmc_ul_modpack/ankh.json 覆盖 + 热重载）；
 *  - zombie/    僵尸线实体侧特性（尸壳实体免疫窒息；玩家形态侧同款免疫
 *               走 morphext 数据，见 assets/.../morphext/morph_abilities.json）；
 *  - ftbq/      FTBQuests 形态检测任务（type: aaaa0ggmc_ul_modpack:morph）；
 *  - achievement/ 成就（原版 advancement 授予 + 旧自定义 toast 存档一次性迁移）；
 *  - command/   /ulpack 调试指令（含 /ulpack ritual 查看转化规则、/ulpack race 查看种族）。
 *
 * 转化规则（沙葬/水祭/火祭/村祭）与形态类别（ul:undead）是纯数据：
 * assets/aaaa0ggmc_ul_modpack/morphext/*.json，由 morphext 核心的通用
 * 注册表加载——本模组不实现仪式机制，只提供内容。
 *
 * 新内容直接加新包并在代理/注册器里挂上即可；本类只保留启动骨架。
 */
@Mod(
        modid = UlModpack.MODID,
        name = UlModpack.NAME,
        version = UlModpack.VERSION,
        dependencies = "required-after:ul_strength_design;required-after:baubles;required-after:metamorph;required-after:aaaa0ggmc_morph_extension;required-after:ftbquests",
        acceptableRemoteVersions = "*"
)
public class UlModpack {
    public static final String MODID = "aaaa0ggmc_ul_modpack";
    public static final String NAME = "Unlimited Life - Modpack Content";
    public static final String VERSION = "1.0.0";
    public static final Logger LOGGER = LogManager.getLogger(NAME);

    static {
        FluidRegistry.enableUniversalBucket();
    }

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
        UlModpackContainer.init(aaaa0ggmc.uld.UlStrength.service());
        proxy.preInit(event);
        AnkhConfig.init(event.getModConfigurationDirectory());
        // 强度难度集（strengths/<difficulty>/）定位 + 当前难度读取；
        // 必须在任何 UlStrength 查询之前（数值兜底不受影响，但 /ulpack strength 要能立刻用）
        aaaa0ggmc.ulmodpack.strength.StrengthSets.init(event.getModConfigurationDirectory());
        aaaa0ggmc.ulmodpack.dna.DnaRecipeRegistry.load(event.getModConfigurationDirectory());
        aaaa0ggmc.ulmodpack.dna.DnaNetwork.init();
        InfectionHandler.register();
        LOGGER.info("[ul-modpack] Pack content online (modules: ankh/infection/race/ftbq-morph).");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
        aaaa0ggmc.ulmodpack.achievement.Achievements.registerDefaults();
        // 旧存档一次性迁移挂点（实例方法 handler，类注册会丢掉 onLogin）
        MinecraftForge.EVENT_BUS.register(
                new aaaa0ggmc.ulmodpack.achievement.AchievementMigration());
        ZombieLine.register();
        net.minecraftforge.fml.common.network.NetworkRegistry.INSTANCE.registerGuiHandler(
                this, new aaaa0ggmc.ulmodpack.dna.DnaGuiHandler());
        GameRegistry.registerWorldGenerator(
                new aaaa0ggmc.ulmodpack.world.OverworldAdditionsGenerator(), 10);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
        aaaa0ggmc.ulmodpack.world.DarklandsTerrainPatch.register();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandUlPack(UlModpackContainer.instance().strengthService()));
    }
}
