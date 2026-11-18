package aaaa0ggmc.ulmodpack.faction;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIFindEntityNearestPlayer;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 「最近的敌对玩家」查找器：原版 {@code EntityAIFindEntityNearestPlayer}
 * （整类只扫 {@code world.playerEntities}，只认玩家为敌）的阵营化替身。
 */
public class FindNearestEnemyPlayer extends EntityAIFindEntityNearestPlayer {

    private final EntityLiving hateOwner;
    private EntityPlayer hateTarget;

    public FindNearestEnemyPlayer(EntityLiving owner) {
        super(owner);
        this.hateOwner = owner;
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
