package aaaa0ggmc.ulmodpack.zombie;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.zombie.horde.HordeAbilities;
import aaaa0ggmc.ulmodpack.zombie.horde.HordeEvents;
import aaaa0ggmc.ulmodpack.zombie.horde.HordeNetwork;
import aaaa0ggmc.ulmodpack.zombie.titan.TitanFormHandler;
import aaaa0ggmc.ulmodpack.zombie.titan.TitanSlamMigration;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventBus;

/**
 * 不死者线（zombie_tide）总入口：尸群领导 / 屠村 / 泰坦。
 *
 * 只暴露 {@link #register()} 一个静态接线点，由主模组在 preInit/init 期调用
 * （不在本类改 {@code UlModpack}，主代理统一接线）。注册内容：
 *  - 服务端事件总线：尸潮事件、泰坦形态事件；
 *  - 能力注册表：尸群指挥 / 震地 两个主动能力（morphext AbilityRegistry +
 *    ServerAbilityService，轮盘/状态页可见；2026-11-06 统一走能力轮盘释放链路，
 *    右键手势触发已移除；2026-11-07 招募/发动/解放三能力按用户设计合并为
 *    {@code horde_command}，服务端按「目标 + 潜行」分派）；
 *  - 自定义包通道 {@code ul:ability}：S2C 队伍快照（说服度/随从列表，B 键状态页）。
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

    /**
     * 两个主动能力 id（内容模组命名空间，AbilityRegistry 唯一键）。
     * {@code horde_command} = 2026-11-07 合并后的尸群指挥（招募/踢出/发动/解散
     * 按「目标 + 潜行」在服务端分派，取代旧 horde_recruit / horde_launch /
     * horde_release 三能力，0.1 未发布不做兼容）。
     */
    public static final String ABILITY_COMMAND = UlModpack.MODID + ":horde_command";
    public static final String ABILITY_SLAM = UlModpack.MODID + ":titan_slam";

    /**
     * 发动尸潮的冷却键（沿用旧 horde_launch 能力 id 作 PlayerAbilityData 冷却键，
     * 不再是独立能力）：horde_command「潜行 + 无目标」发动分支与 onAirUse 共用，
     * 固定 30 秒（cooldown_policy.json 的 abilityFixedCooldowns 覆盖不打折）。
     */
    public static final String ABILITY_LAUNCH = UlModpack.MODID + ":horde_launch";

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
        bus.register(new TitanSlamMigration());
        HordeAbilities.registerAll();
        HordeNetwork.init();
        UlModpack.LOGGER.info("[ul-modpack] Zombie line online (horde leadership / village tide / titan).");
    }
}
