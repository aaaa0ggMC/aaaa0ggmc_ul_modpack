package aaaa0ggmc.ulmodpack.zombie;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.zombie.horde.HordeAbilities;
import aaaa0ggmc.ulmodpack.zombie.horde.HordeEvents;
import aaaa0ggmc.ulmodpack.zombie.horde.HordeNetwork;
import aaaa0ggmc.ulmodpack.zombie.titan.TitanFormHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventBus;

/**
 * 不死者线（zombie_tide）总入口：尸群领导 / 屠村 / 泰坦。
 *
 * 只暴露 {@link #register()} 一个静态接线点，由主模组在 preInit/init 期调用
 * （不在本类改 {@code UlModpack}，主代理统一接线）。注册内容：
 *  - 服务端事件总线：尸潮事件、泰坦形态事件；
 *  - 能力注册表：招募 / 发动 / 解放 / 震地 四个主动能力（morphext AbilityRegistry +
 *    ServerAbilityService，轮盘/状态页可见，主要触发走本线的潜行右键 + ul:ability 包）；
 *  - 自定义包通道 {@code ul:ability}：客户端「对空气使用」上报，服务端判「对空气」后
 *    分派发动 / 解放 / 震地（1.12.2 空手右键空气服务端收不到，必须补包）。
 *
 * 客户端输入触发器（{@code HordeClientTrigger}）自带
 * {@code @Mod.EventBusSubscriber(Side.CLIENT)}，随模组加载自动注册，无需在此调用。
 *
 * 常量集中在此，避免散落：技能 id、形态类别、能力 id、玩家数据状态键。
 */
public final class ZombieLine {

    /** 尸群领导技能 id（morphext SkillRegistry 定义，见 assets/.../morphext/skills.json）。 */
    public static final String SKILL_HORDE_LEADERSHIP = "ul:horde_leadership";
    /** 不死者形态类别（form_categories.json 数据驱动）。 */
    public static final String CATEGORY_UNDEAD = "ul:undead";
    /** 泰坦形态 id（原版巨人）。 */
    public static final String FORM_TITAN = "minecraft:giant";

    /** 四个主动能力 id（内容模组命名空间，AbilityRegistry 唯一键）。 */
    public static final String ABILITY_RECRUIT = UlModpack.MODID + ":horde_recruit";
    public static final String ABILITY_LAUNCH = UlModpack.MODID + ":horde_launch";
    public static final String ABILITY_RELEASE = UlModpack.MODID + ":horde_release";
    public static final String ABILITY_SLAM = UlModpack.MODID + ":titan_slam";

    /**
     * 玩家数据状态键（morphext PlayerAbilityStore，扁平 states 映射）：
     *  - {@link #STATE_TIDE_CLEARED}：通关计数 {@code {"count":N}}，FTBQuests progress 任务读它；
     *  - {@link #STATE_PERSUASION}：说服度 {@code {"value":N}}。
     */
    public static final String STATE_TIDE_CLEARED = "ul:tide/cleared";
    public static final String STATE_PERSUASION = "ul.tide.persuasion";

    private static boolean registered = false;

    private ZombieLine() {
    }

    /** 主代理接线点：注册事件、能力与自定义包通道。幂等。 */
    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        EventBus bus = MinecraftForge.EVENT_BUS;
        bus.register(new HordeEvents());
        bus.register(new TitanFormHandler());
        HordeAbilities.registerAll();
        HordeNetwork.init();
        UlModpack.LOGGER.info("[ul-modpack] Zombie line online (horde leadership / village tide / titan).");
    }
}
