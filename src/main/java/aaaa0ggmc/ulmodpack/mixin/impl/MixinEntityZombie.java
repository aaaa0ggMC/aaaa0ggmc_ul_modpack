package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.TargetSwap;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityZombie;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 僵尸（含尸壳/僵尸村民/巨人等子类继承）：目标选择重定向。
 *
 * <p>原判定：{@code applyEntityAI()}（SRG {@code func_175456_n}）硬编码
 * {@code new EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true)}
 * ——只认玩家为敌。新判定：{@code addTask} 参数经 {@link TargetSwap} 换
 * {@code NearestEnemyPlayerTarget}（{@code HateQueries.isEnemy}，morphext 阵营图）。</p>
 *
 * <p>村民/铁傀儡两路目标（「袭击村庄」语义）经 TargetSwap 类型判定原样保留。
 * 兼容性注记见 {@link MixinEntityCreeper}（@Redirect→@ModifyArg 急修改写）。</p>
 */
@Mixin(value = EntityZombie.class, remap = false)
public abstract class MixinEntityZombie {

    @ModifyArg(
            method = "func_175456_n()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/EntityAITasks;func_75776_a(ILnet/minecraft/entity/ai/EntityAIBase;)V"
            ),
            index = 1,
            remap = false
    )
    private EntityAIBase ulmodpack$swapEnemyTarget(EntityAIBase original) {
        return TargetSwap.swap(original, (EntityCreature) (Object) this);
    }
}
