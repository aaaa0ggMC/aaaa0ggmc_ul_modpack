package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.TargetSwap;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityWitch;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 女巫：目标选择重定向（{@code initEntityAI}，SRG {@code func_184651_r}）——
 * 原版 {@code EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true)}
 * 只认玩家为敌（投掷药水目标）。{@code addTask} 参数经 {@link TargetSwap} 换
 * 敌人过滤版（{@code HateQueries.isEnemy}，morphext 阵营图）。
 * 兼容性注记见 {@link MixinEntityCreeper}（禁用 @Redirect）。
 */
@Mixin(value = EntityWitch.class, remap = false)
public abstract class MixinEntityWitch {

    @ModifyArg(
            method = "func_184651_r()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/EntityAITasks;addTask(ILnet/minecraft/entity/ai/EntityAIBase;)V"
            ),
            index = 1,
            remap = false
    )
    private EntityAIBase ulmodpack$swapEnemyTarget(EntityAIBase original) {
        return TargetSwap.swap(original, (EntityCreature) (Object) this);
    }
}
