package aaaa0ggmc.ulmodpack.faction;

import aaaa0ggmc.morphext.faction.FactionRelation;
import aaaa0ggmc.uld.UlStrength;
import aaaa0ggmc.ulmodpack.zombie.horde.HordeManager;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 村民躲避「敌对阵营玩家」（僵尸阵营玩家靠近时恐慌/逃跑）。
 *
 * <p>根因（2026-11-07 用户实测）：原版村民只躲 {@code EntityZombie} 类实例
 * （{@code EntityVillager.initEntityAI} 的 {@code EntityAIAvoidEntity<EntityZombie>}，
 * reference/.../passive/EntityVillager.java:328），玩家变身僵尸后实体仍是
 * {@code EntityPlayer}，原版任务永远不触发——「玩家变成僵尸后村民不逃跑」。</p>
 *
 * <p>修法：补一条<b>阵营判定</b>（非硬编码 instanceof）的躲避任务——村民把
 * 「关系 = {@link FactionRelation#ENEMY} 的玩家」当躲避对象。僵尸/不死阵营玩家
 * 对村民是 ENEMY（morphext faction_relations.json {@code undead↔villager=ENEMY}），
 * 普通玩家无阵营回退 MODERATE、不会误触发。判定走 morphext 仇恨统一接口
 * （{@link HateQueries#relation}）。参数与原版僵尸躲避一致（8 格察觉、0.6/0.6 速度）。</p>
 *
 * <p>顺带承担入侵标记的惰性清理（见 {@link HordeManager#clearStaleInvadeMark}）：
 * 尸潮收尾时未加载的村民带着标记重新加载，这里补清。</p>
 *
 * <p>服务端权威：任务只在服务端世界安装（AI 行为以服务端为准，客户端由位置同步）。</p>
 */
public final class VillagerFearHandler {

    /** 躲避触发距离（格）：与原版村民躲僵尸一致（EntityVillager.initEntityAI）。 */
    private static float avoidDistance() {
        return (float) aaaa0ggmc.ulmodpack.UlModpackContainer.instance().strengthService().num("zombie_tide.villager_fear.avoid_distance", 12.0D);
    }

    /** 逃跑速度（远离档）：与原版村民躲僵尸一致。 */
    private static double fleeSpeedFar() {
        return aaaa0ggmc.ulmodpack.UlModpackContainer.instance().strengthService().num("zombie_tide.villager_fear.flee_speed_far", 0.6D);
    }

    /** 逃跑速度（贴近档）：与原版村民躲僵尸一致。 */
    private static double fleeSpeedNear() {
        return aaaa0ggmc.ulmodpack.UlModpackContainer.instance().strengthService().num("zombie_tide.villager_fear.flee_speed_near", 0.6D);
    }

    /** 幂等标记：同一实体实例不重复加任务（实体跨世界搬运等场景防重复）。 */
    private static final String TAG_FEAR_AI = "ulFearAi";

    private VillagerFearHandler() {
    }

    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event.getWorld().isRemote || !(event.getEntity() instanceof EntityVillager)) {
            return;
        }
        EntityVillager villager = (EntityVillager) event.getEntity();
        // 惰性清理：尸潮已结束（条目已删）但仍带入侵标记的村民，重载时补清标记
        HordeManager.clearStaleInvadeMark(villager);
        if (villager.getEntityData().getBoolean(TAG_FEAR_AI)) {
            return;
        }
        villager.getEntityData().setBoolean(TAG_FEAR_AI, true);
        villager.tasks.addTask(1, new EntityAIAvoidEntity<EntityPlayer>(villager, EntityPlayer.class,
                player -> {
                    FactionRelation rel = HateQueries.relation(villager, player);
                    return rel == FactionRelation.ENEMY || rel == FactionRelation.FEAR;
                },
                avoidDistance(), fleeSpeedFar(), fleeSpeedNear()));
    }
}
