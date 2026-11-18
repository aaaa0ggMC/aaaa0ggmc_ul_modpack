package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.FindNearestEnemyPlayer;
import aaaa0ggmc.ulmodpack.faction.HateQueries;
import aaaa0ggmc.ulmodpack.faction.NearestEnemyPlayerTarget;
import aaaa0ggmc.ulmodpack.faction.NearestEnemyTarget;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.ai.EntityAIFindEntityNearestPlayer;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.monster.AbstractSkeleton;
import net.minecraft.entity.player.EntityPlayer;

import java.lang.reflect.Field;

/**
 * 索敌/躲避任务替身选择（{@code @ModifyArg} 注入点共用）。
 *
 * <p>兼容性背景：Cleanroom 运行时的新版 Mixin {@code Annotations} 与 MixinBooter 12.2
 * 捆绑的旧 MixinExtras（FactoryRedirectWrapperMixinTransformer）对 {@code @Redirect}
 * 注解数据结构语义不兼容（{@code ArrayList cannot be cast to AnnotationNode}），因此
 * 本仓库禁用 {@code @Redirect}/MixinExtras 系注解，目标替换一律走
 * {@code @ModifyArg}（改 {@code EntityAITasks.addTask} 的 AI 参数），由本类按原实例
 * 类型选择敌人过滤版替身。</p>
 *
 * <p>替换矩阵（其余任务原样返回——村民/铁傀儡等「袭击村庄」语义不动）：</p>
 * <ul>
 *   <li>{@code EntityAINearestAttackableTarget} 目标类=Player → {@code NearestEnemyPlayerTarget}；
 *       目标类=AbstractSkeleton（狼）→ {@code NearestEnemyTarget}；链式注册
 *       （{@code .setUnseenMemoryTicks(n)}）的 unseenMemoryTicks 值一并复制；</li>
 *   <li>{@code EntityAIFindEntityNearestPlayer} → {@code FindNearestEnemyPlayer}；</li>
 *   <li>{@code EntityAIAvoidEntity}（躲避玩家）→ 同参数重建，躲避谓词换成
 *       {@code HateQueries.isEnemy}（只躲敌人玩家，同阵营变形玩家不触发逃跑，§G6）。</li>
 * </ul>
 *
 * <p>protected 字段经反射读取一次并缓存（SRG 名已对 mappings-stable39 核实：
 * targetClass=field_75307_b、shouldCheckSight=field_75297_f、
 * unseenMemoryTicks=field_188510_h、avoidDistance=field_179508_f、
 * farSpeed=field_75378_b、nearSpeed=field_75379_c）；替身保持原参数语义。</p>
 */
final class TargetSwap {

    private static final Field TARGET_CLASS;
    private static final Field CHECK_SIGHT;
    private static final Field UNSEEN_MEMORY;
    private static final Field AVOID_CLASS;
    private static final Field AVOID_DISTANCE;
    private static final Field FAR_SPEED;
    private static final Field NEAR_SPEED;

    static {
        try {
            TARGET_CLASS = field(EntityAINearestAttackableTarget.class, "field_75307_b", "targetClass");
            CHECK_SIGHT = field(EntityAITarget.class, "field_75297_f", "shouldCheckSight");
            UNSEEN_MEMORY = field(EntityAITarget.class, "field_188510_h", "unseenMemoryTicks");
            AVOID_CLASS = field(EntityAIAvoidEntity.class, "field_181064_i", "classToAvoid");
            AVOID_DISTANCE = field(EntityAIAvoidEntity.class, "field_179508_f", "avoidDistance");
            FAR_SPEED = field(EntityAIAvoidEntity.class, "field_75378_b", "farSpeed");
            NEAR_SPEED = field(EntityAIAvoidEntity.class, "field_75379_c", "nearSpeed");
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Field field(Class<?> owner, String srgName, String mcpName) throws ReflectiveOperationException {
        try {
            Field f = owner.getDeclaredField(srgName);
            f.setAccessible(true);
            return f;
        } catch (NoSuchFieldException ignored) {
            Field f = owner.getDeclaredField(mcpName);
            f.setAccessible(true);
            return f;
        }
    }

    private TargetSwap() {
    }

    static EntityAIBase swap(EntityAIBase original, net.minecraft.entity.EntityLiving owner) {
        try {
            if (original instanceof EntityAIFindEntityNearestPlayer) {
                return new FindNearestEnemyPlayer(owner);
            }
            if (!(owner instanceof EntityCreature)) {
                return original;
            }
            EntityCreature creature = (EntityCreature) owner;
            if (original instanceof EntityAINearestAttackableTarget) {
                Class<?> targetClass = (Class<?>) TARGET_CLASS.get(original);
                boolean checkSight = (Boolean) CHECK_SIGHT.get(original);
                EntityAITarget swapped = null;
                if (targetClass == EntityPlayer.class) {
                    swapped = new NearestEnemyPlayerTarget(creature, checkSight);
                } else if (targetClass == AbstractSkeleton.class) {
                    @SuppressWarnings("unchecked")
                    Class<? extends EntityLivingBase> type =
                            (Class<? extends EntityLivingBase>) targetClass;
                    swapped = new NearestEnemyTarget<>(creature, type, checkSight);
                }
                if (swapped != null) {
                    swapped.setUnseenMemoryTicks(UNSEEN_MEMORY.getInt(original));
                    return swapped;
                }
            }
            if (original instanceof EntityAIAvoidEntity) {
                Class<?> avoidClass = (Class<?>) AVOID_CLASS.get(original);
                if (avoidClass == EntityPlayer.class) {
                    @SuppressWarnings("unchecked")
                    EntityAIAvoidEntity<EntityPlayer> avoid =
                            (EntityAIAvoidEntity<EntityPlayer>) original;
                    return new EntityAIAvoidEntity<>(
                            creature,
                            EntityPlayer.class,
                            input -> HateQueries.isEnemy(creature, input),
                            AVOID_DISTANCE.getFloat(avoid),
                            FAR_SPEED.getDouble(avoid),
                            NEAR_SPEED.getDouble(avoid));
                }
            }
        } catch (IllegalAccessException ignored) {
        }
        return original;
    }
}
