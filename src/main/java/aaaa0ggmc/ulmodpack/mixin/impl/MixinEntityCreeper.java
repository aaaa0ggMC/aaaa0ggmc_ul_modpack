package aaaa0ggmc.ulmodpack.mixin.impl;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityCreeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 苦力怕（BUG 修复第一优先）：目标选择重定向。
 *
 * <p>根因：{@code EntityCreeper.initEntityAI()} 硬编码
 * {@code new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true)}——
 * 只认 {@code EntityPlayer} 为敌，玩家变形僵尸（zombie 阵营与 creeper 阵营
 * MEMBER 关系）后依然被选为攻击目标，进而驱动 {@code EntityAICreeperSwell}
 * 膨胀/引爆。爆炸目标选择（把谁当引爆对象）正是这条 targetTasks 链。</p>
 *
 * <p>新判定：{@code addTask} 的目标 AI 参数经 {@link TargetSwap} 换成
 * {@code NearestEnemyPlayerTarget}（{@code HateQueries.isEnemy}，morphext 阵营图）。</p>
 *
 * <p><b>兼容性注记（2026-11-06 急修）</b>：原 {@code @Redirect(NEW)} 在 Cleanroom
 * 运行时触发 MixinExtras FactoryRedirectWrapper 与新版 Mixin {@code Annotations} 的
 * 结构不兼容（{@code ArrayList cannot be cast to AnnotationNode}），全部
 * {@code @Redirect} 一律改写为 {@code @ModifyArg}（改 {@code addTask} 参数），
 * 见 {@link TargetSwap}。绑定仍以 SRG 名 + {@code remap=false}（仓库惯例）。</p>
 */
@Mixin(value = EntityCreeper.class, remap = false)
public abstract class MixinEntityCreeper {

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
