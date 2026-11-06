package aaaa0ggmc.ulmodpack.mixin.impl;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntitySlime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 史莱姆/岩浆怪（继承共用）：目标选择重定向。
 *
 * <p>原判定：{@code initEntityAI()}（SRG {@code func_184651_r}）硬编码
 * {@code new EntityAIFindEntityNearestPlayer(this)}——该查找器整类只扫玩家。
 * 新判定：{@code addTask} 参数经 {@link TargetSwap} 换 {@code FindNearestEnemyPlayer}
 * （{@code HateQueries.isEnemy/findNearestEnemy}，morphext 阵营图）。
 * 兼容性注记见 {@link MixinEntityCreeper}。</p>
 */
@Mixin(value = EntitySlime.class, remap = false)
public abstract class MixinEntitySlime {

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
