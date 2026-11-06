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
 * 潜影贝感知任务 {@code EntityShulker.AIAttackNearest}：敌人过滤。
 *
 * <p>原判定：只把 {@code EntityPlayer} 选为攻击目标（附着面方向裁剪感知范围保留）。
 * 新判定：叠加 {@code HateQueries.isEnemy}（morphext 阵营图）——只感知敌人玩家。</p>
 */
@Mixin(targets = "net.minecraft.entity.monster.EntityShulker$AIAttackNearest", remap = false)
public abstract class MixinShulkerAttackNearest extends EntityAINearestAttackableTarget<EntityPlayer> {

    protected MixinShulkerAttackNearest(EntityCreature creature, Class<EntityPlayer> targetClass, boolean checkSight) {
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
