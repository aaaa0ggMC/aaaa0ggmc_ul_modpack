package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.TargetSwap;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityVindicator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 卫道士：目标选择重定向（{@code initEntityAI}，SRG {@code func_184651_r}）。
 * {@code addTask} 参数经 {@link TargetSwap} 换敌人过滤版（{@code HateQueries.isEnemy}）。
 *
 * <p>Johnny 模式（{@code AIJohnnyAttack}）目标类本就是 {@code EntityLivingBase}
 * 全体（打一切生物，非玩家硬编码），保留原样；村民/铁傀儡目标（袭击村庄）保留。
 * 兼容性注记见 {@link MixinEntityCreeper}。</p>
 */
@Mixin(value = EntityVindicator.class, remap = false)
public abstract class MixinEntityVindicator {

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
