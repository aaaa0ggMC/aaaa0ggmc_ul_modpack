package aaaa0ggmc.ulmodpack.faction;

import com.google.common.base.Predicate;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;

import javax.annotation.Nullable;

/**
 * 「最近的敌对目标」索敌（泛型版）：{@code EntityAINearestAttackableTarget<>(owner, type, checkSight)}
 * 的阵营化替身，供目标类型非玩家（如狼→骷髅）的硬编码检测点使用。
 *
 * <p>与 {@link NearestEnemyPlayerTarget} 同模式：选目标时经
 * {@link HateQueries#isEnemy}（morphext 阵营图）跳过非敌人找下一个敌人；
 * 续期复核目标失去敌资格即按原版停用语义收场。保持原版参数
 * targetChance=10、sortByDistance=false。</p>
 */
public class NearestEnemyTarget<T extends EntityLivingBase> extends EntityAINearestAttackableTarget<T> {

    public NearestEnemyTarget(final EntityCreature owner, Class<T> type, boolean checkSight) {
        super(owner, type, 10, checkSight, false, new Predicate<T>() {
            @Override
            public boolean apply(@Nullable T input) {
                return HateQueries.isEnemy(owner, input);
            }
        });
    }

    @Override
    public boolean shouldExecute() {
        return super.shouldExecute() && HateQueries.isEnemy(this.taskOwner, this.targetEntity);
    }

    @Override
    public boolean shouldContinueExecuting() {
        return super.shouldContinueExecuting()
                && HateQueries.isEnemy(this.taskOwner, this.taskOwner.getAttackTarget());
    }
}
