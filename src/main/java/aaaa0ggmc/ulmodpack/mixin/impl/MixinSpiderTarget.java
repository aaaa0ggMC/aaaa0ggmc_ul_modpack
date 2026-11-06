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
 * 蜘蛛（含洞穴蜘蛛）目标任务 {@code EntitySpider.AISpiderTarget}：敌人过滤。
 *
 * <p>原判定：目标注册写死 {@code EntityPlayer.class}（铁傀儡一路不动），
 * {@code shouldExecute} 带光照闸门（{@code getBrightness() >= 0.5} 放弃）。
 * 新判定：保留光照闸门，玩家一路叠加 {@code HateQueries.isEnemy}——
 * 只把敌人玩家当目标（跳过非敌人找下一个敌人玩家）。</p>
 */
@Mixin(targets = "net.minecraft.entity.monster.EntitySpider$AISpiderTarget", remap = false)
public abstract class MixinSpiderTarget extends EntityAINearestAttackableTarget<EntityPlayer> {

    protected MixinSpiderTarget(EntityCreature creature, Class<EntityPlayer> targetClass, boolean checkSight) {
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
