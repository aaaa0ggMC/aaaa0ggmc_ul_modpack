package aaaa0ggmc.ulmodpack.mixin.impl;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntitySilverfish;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 蠹虫（银鱼）：目标选择重定向（{@code applyEntityAI}，SRG {@code func_175456_n}）。
 * {@code addTask} 参数经 {@link TargetSwap} 换敌人过滤版。生成规避属生成规则非仇恨，不动。
 * 兼容性注记见 {@link MixinEntityCreeper}（本类曾是 Cleanroom 崩溃的首个命中点）。
 */
@Mixin(value = EntitySilverfish.class, remap = false)
public abstract class MixinEntitySilverfish {

    @ModifyArg(
            method = "func_175456_n()V",
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
