package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.HateQueries;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 末影人：对视仇恨的敌人判定（对视激怒）。
 *
 * <p>原判定：{@code shouldAttackPlayer(EntityPlayer)}（SRG {@code func_70821_d}）
 * 只做「南瓜头豁免 + 对视视线」判定，随后 {@code AIFindPlayer} 用
 * {@code getNearestAttackablePlayer(..., shouldAttackPlayer 谓词)} 把「正在对视的
 * 玩家」选为唯一主动目标——玩家变形同阵营形态后仍被对视激怒/追击瞬移。</p>
 *
 * <p>新判定：在对视判定之上叠加 {@code HateQueries.isEnemy(enderman, player)}
 * （morphext 阵营图）——非敌人玩家即使对视也不激怒。单点注入即同时覆盖
 * {@code AIFindPlayer} 的选目标（谓词过滤，自动跳过找下一个）与续期复查
 * （{@code shouldContinueExecuting} 内再次调用本方法），以及被击后的普通
 * HurtByTarget 复仇不受影响（provoked 窗口由统一接口放行）。</p>
 */
@Mixin(value = EntityEnderman.class, remap = false)
public abstract class MixinEntityEnderman {

    @Inject(
            method = "func_70821_d(Lnet/minecraft/entity/player/EntityPlayer;)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void ulmodpack$onlyStareAtEnemies(EntityPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())
                && !HateQueries.isEnemy((EntityEnderman) (Object) this, player)) {
            cir.setReturnValue(false);
        }
    }
}
