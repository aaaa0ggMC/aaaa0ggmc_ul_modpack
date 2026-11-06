package aaaa0ggmc.ulmodpack.mixin.impl;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityVex;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 恼鬼：目标选择重定向（{@code initEntityAI}，SRG {@code func_184651_r}）——
 * 原版默认唯一主动攻击目标=玩家。{@code addTask} 参数经 {@link TargetSwap} 换
 * 敌人过滤版（{@code HateQueries.isEnemy}）。兼容性注记见 {@link MixinEntityCreeper}。
 */
@Mixin(value = EntityVex.class, remap = false)
public abstract class MixinEntityVex {

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
