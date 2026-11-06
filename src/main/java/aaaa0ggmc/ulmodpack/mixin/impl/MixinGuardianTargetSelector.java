package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.HateQueries;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 守卫者/远古守卫者目标选择器 {@code EntityGuardian.GuardianTargetSelector}：敌人过滤。
 *
 * <p>原判定：{@code apply()} 只认 {@code EntityPlayer} 或 {@code EntitySquid}
 * 为合法光束目标（+9 格距离下限）。新判定：玩家一路叠加
 * {@code HateQueries.isEnemy}（morphext 阵营图）——敌人玩家才可被锁定，
 * 鱿鱼特例保留原样。</p>
 */
@Mixin(targets = "net.minecraft.entity.monster.EntityGuardian$GuardianTargetSelector", remap = false)
public abstract class MixinGuardianTargetSelector {

    @Shadow
    private EntityGuardian parentEntity;

    @Inject(
            method = "apply(Lnet/minecraft/entity/EntityLivingBase;)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void ulmodpack$playersMustBeEnemies(EntityLivingBase candidate, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())
                && candidate instanceof EntityPlayer
                && !HateQueries.isEnemy(this.parentEntity, candidate)) {
            cir.setReturnValue(false);
        }
    }
}
