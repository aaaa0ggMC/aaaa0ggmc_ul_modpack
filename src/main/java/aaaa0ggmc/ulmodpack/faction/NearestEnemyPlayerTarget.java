package aaaa0ggmc.ulmodpack.faction;

import com.google.common.base.Predicate;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.player.EntityPlayer;

import javax.annotation.Nullable;

/**
 * 「最近的敌对玩家」索敌目标：原版
 * {@code EntityAINearestAttackableTarget<>(owner, EntityPlayer.class, checkSight)}
 * 的阵营化替身。
 *
 * <p>原版把 {@code EntityPlayer.class} 当作唯一敌对目标（{@code shouldExecute} 里
 * {@code targetClass == EntityPlayer.class} 还会走 {@code getNearestAttackablePlayer}
 * 玩家专属通道），玩家一旦变形为同阵营形态（如僵尸形态玩家 vs 苦力怕 = MEMBER）
 * 仍被选为攻击目标，进而驱动膨胀/引爆等「对玩家敏感」行为。</p>
 *
 * <p>本类只重定向「原版硬编码玩家检测点」到统一仇恨接口
 * {@link HateQueries#isEnemy}：选目标时跳过非敌人玩家（找最近的<em>敌人</em>玩家，
 * 不是最近玩家），目标续期期间失去敌资格（变形切阵营/臣服）即按原版停用语义收场
 * （{@code resetTask} → {@code setAttackTarget(null)}），不碰任务内部字段。</p>
 *
 * <p>保持原版参数：targetChance=10、sortByDistance=false，与三参构造委托链一致。</p>
 */
public class NearestEnemyPlayerTarget extends EntityAINearestAttackableTarget<EntityPlayer> {

    public NearestEnemyPlayerTarget(final EntityCreature owner, boolean checkSight) {
        super(owner, EntityPlayer.class, 10, checkSight, false, new Predicate<EntityPlayer>() {
            @Override
            public boolean apply(@Nullable EntityPlayer player) {
                return HateQueries.isEnemy(owner, player);
            }
        });
    }

    @Override
    public boolean shouldExecute() {
        // super 已按 isEnemy 谓词跳过非敌人玩家选出 targetEntity；这里再复核一次，
        // 防止谓词与目标实体在极端时序下不一致。
        return super.shouldExecute() && HateQueries.isEnemy(this.taskOwner, this.targetEntity);
    }

    @Override
    public boolean shouldContinueExecuting() {
        // 原版续期只看存活/距离/队伍；补阵营复核：目标中途变节（变形换阵营、
        // 触发臣服）时停止任务，走原版 resetTask 清空攻击目标。
        return super.shouldContinueExecuting()
                && HateQueries.isEnemy(this.taskOwner, this.taskOwner.getAttackTarget());
    }
}
