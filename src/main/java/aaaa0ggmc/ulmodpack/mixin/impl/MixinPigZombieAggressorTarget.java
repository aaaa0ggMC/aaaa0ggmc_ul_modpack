package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.morphext.faction.AggressionPolicy;
import aaaa0ggmc.ulmodpack.faction.HateQueries;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 僵尸猪人愤怒索敌（{@code EntityPigZombie.AITargetAggressor}）：敌人过滤。
 *
 * <p>原判定：愤怒后（isAngry）仍只搜 {@code EntityPlayer}——「最近的玩家」而非
 * 「最近的敌人」。新判定：选目标/续期都经 {@code HateQueries.isEnemy}
 * （morphext 阵营图）：跳过非敌人玩家改选最近敌人玩家；目标失去敌资格即停任务。</p>
 *
 * <p>模式：super 选出 targetEntity 后在 RETURN 复核并改写（findNearestEnemy
 * 跳过非敌人找下一个）。写 targetEntity 字段安全——读侧 startExecuting 只在
 * shouldExecute 返回 true 时执行，改写后为 null 时已置 false 不会读到。</p>
 */
@Mixin(targets = "net.minecraft.entity.monster.EntityPigZombie$AITargetAggressor", remap = false)
public abstract class MixinPigZombieAggressorTarget extends EntityAINearestAttackableTarget<EntityPlayer> {

    protected MixinPigZombieAggressorTarget(EntityCreature creature, Class<EntityPlayer> targetClass, boolean checkSight) {
        super(creature, targetClass, checkSight);
    }

    @Inject(method = "func_75250_a()Z", at = @At("RETURN"), cancellable = true, remap = false)
    private void ulmodpack$enemyOnly(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())
                && !HateQueries.isEnemy(this.taskOwner, this.targetEntity, AggressionPolicy.DEFENSIVE)) {
            this.targetEntity = HateQueries.findNearestEnemy(
                    this.taskOwner, this.getTargetDistance(), EntityPlayer.class, null);
            cir.setReturnValue(this.targetEntity != null);
        }
    }

    @Inject(method = "func_75253_b()Z", at = @At("RETURN"), cancellable = true, remap = false)
    private void ulmodpack$enemyOnlyContinue(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            EntityLivingBase current = this.taskOwner.getAttackTarget();
            if (!HateQueries.isEnemy(this.taskOwner, current, AggressionPolicy.DEFENSIVE)) {
                cir.setReturnValue(false);
            }
        }
    }
}
