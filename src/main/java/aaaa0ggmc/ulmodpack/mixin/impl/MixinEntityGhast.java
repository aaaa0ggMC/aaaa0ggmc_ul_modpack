package aaaa0ggmc.ulmodpack.mixin.impl;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityGhast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 恶魂：目标选择重定向（{@code initEntityAI}，SRG {@code func_184651_r}）——
 * 原 {@code EntityAIFindEntityNearestPlayer}（只扫玩家）经 {@link TargetSwap} 换
 * {@code FindNearestEnemyPlayer}（{@code HateQueries}）。火球反弹秒杀特判属伤害规则
 * 非仇恨检测，清单标注不动。兼容性注记见 {@link MixinEntityCreeper}。
 */
@Mixin(value = EntityGhast.class, remap = false)
public abstract class MixinEntityGhast {

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
        return TargetSwap.swap(original, (EntityLiving) (Object) this);
    }
}
