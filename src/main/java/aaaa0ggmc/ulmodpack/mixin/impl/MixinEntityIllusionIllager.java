package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.TargetSwap;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityIllusionIllager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 幻术师（1.12.2 类名 {@code EntityIllusionIllager}）：目标选择重定向
 * （{@code initEntityAI}，SRG {@code func_184651_r}）——原版
 * {@code EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true)
 * .setUnseenMemoryTicks(300)} 只认玩家为敌（弓箭/致盲术目标）。
 * {@code addTask} 参数经 {@link TargetSwap} 换敌人过滤版，unseenMemoryTicks 一并复制。
 * 村民目标（袭击村庄）保留。兼容性注记见 {@link MixinEntityCreeper}。
 */
@Mixin(value = EntityIllusionIllager.class, remap = false)
public abstract class MixinEntityIllusionIllager {

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
