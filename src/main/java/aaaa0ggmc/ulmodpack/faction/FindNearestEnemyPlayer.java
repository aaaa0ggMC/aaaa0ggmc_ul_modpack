package aaaa0ggmc.ulmodpack.faction;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIFindEntityNearestPlayer;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 「最近的敌对玩家」查找器：原版 {@code EntityAIFindEntityNearestPlayer}
 * （整类只扫 {@code world.playerEntities}，只认玩家为敌）的阵营化替身。
 *
 * <p>继承原类以满足 {@code @Redirect(NEW)} 的返回类型要求（替换其构造调用），
 * 目标选择与续期全部改走统一仇恨接口 {@link HateQueries}（morphext 阵营图判定）：
 * 只把「敌人玩家」选为攻击目标（跳过非敌人找下一个敌人）；目标失去敌资格
 * （变形换阵营/臣服）即停任务，走原版 {@code resetTask} 停用语义清空攻击目标。</p>
 */
public class FindNearestEnemyPlayer extends EntityAIFindEntityNearestPlayer {

    private final EntityCreature hateOwner;
    private EntityPlayer hateTarget;

    public FindNearestEnemyPlayer(EntityCreature creature) {
        super(creature);
        this.hateOwner = creature;
    }

    @Override
    public boolean shouldExecute() {
        this.hateTarget = HateQueries.findNearestEnemy(
                this.hateOwner, this.maxTargetRange(), EntityPlayer.class, null);
        return this.hateTarget != null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        return this.hateTarget != null
                && this.hateTarget.isEntityAlive()
                && HateQueries.isEnemy(this.hateOwner, this.hateTarget);
    }

    @Override
    public void startExecuting() {
        this.hateOwner.setAttackTarget(this.hateTarget);
    }

    @Override
    public void resetTask() {
        this.hateOwner.setAttackTarget(null);
        this.hateTarget = null;
    }

    @Override
    public void updateTask() {
        this.hateOwner.setAttackTarget(this.hateTarget);
    }
}
