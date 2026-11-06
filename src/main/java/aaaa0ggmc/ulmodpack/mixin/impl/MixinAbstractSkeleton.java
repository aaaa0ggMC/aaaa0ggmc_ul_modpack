package aaaa0ggmc.ulmodpack.mixin.impl;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.AbstractSkeleton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 骷髅基类（骷髅/流浪者/凋灵骷髅共用）：目标选择重定向。
 * {@code addTask} 参数经 {@link TargetSwap} 换 {@code NearestEnemyPlayerTarget}
 * （{@code HateQueries.isEnemy}，morphext 阵营图）。铁傀儡目标（袭击村庄）保留。
 * 兼容性注记见 {@link MixinEntityCreeper}。
 */
@Mixin(value = AbstractSkeleton.class, remap = false)
public abstract class MixinAbstractSkeleton {

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
