package aaaa0ggmc.ulmodpack.mixin.impl;

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
 * 北极熊护崽攻击任务 {@code EntityPolarBear.AIAttackPlayer}：敌人过滤。
 *
 * <p>原判定：成年北极熊在幼崽 8x4x8 范围内把「附近玩家」当唯一主动攻击目标。
 * 新判定：叠加 {@code HateQueries.isEnemy}（morphext 阵营图）——护崽只驱赶敌人
 * 玩家，同阵营（MEMBER/RELATIVE/LORD）形态玩家不触发。幼崽判定与无崽放弃逻辑不动。</p>
 */
@Mixin(targets = "net.minecraft.entity.monster.EntityPolarBear$AIAttackPlayer", remap = false)
public abstract class MixinPolarBearAttackPlayer extends EntityAINearestAttackableTarget<EntityPlayer> {

    protected MixinPolarBearAttackPlayer(EntityCreature creature, Class<EntityPlayer> targetClass, boolean checkSight) {
        super(creature, targetClass, checkSight);
    }

    @Inject(method = "func_75250_a()Z", at = @At("RETURN"), cancellable = true, remap = false)
    private void ulmodpack$enemyOnly(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())
                && !HateQueries.isEnemy(this.taskOwner, this.targetEntity)) {
            this.targetEntity = HateQueries.findNearestEnemy(
                    this.taskOwner, this.getTargetDistance(), EntityPlayer.class, null);
            cir.setReturnValue(this.targetEntity != null);
        }
    }

    @Inject(method = "func_75253_b()Z", at = @At("RETURN"), cancellable = true, remap = false)
    private void ulmodpack$enemyOnlyContinue(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())) {
            EntityLivingBase current = this.taskOwner.getAttackTarget();
            if (!HateQueries.isEnemy(this.taskOwner, current)) {
                cir.setReturnValue(false);
            }
        }
    }
}
