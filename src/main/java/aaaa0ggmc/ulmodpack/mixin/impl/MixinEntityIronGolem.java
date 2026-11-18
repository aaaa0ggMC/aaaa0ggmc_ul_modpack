package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.TargetSwap;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityIronGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 铁傀儡：主动索敌 {@code IMob} 时按 {@code HateQueries.isEnemy} 过滤。
 *
 * <p>使铁傀儡不再主动攻击属于村民亲缘阵营（RELATIVE）的原版僵尸村民，
 * 仍正常攻击普通僵尸、骷髅等敌对怪物。</p>
 */
@Mixin(value = EntityIronGolem.class, remap = false)
public abstract class MixinEntityIronGolem {

    @ModifyArg(
            method = "func_184651_r()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/EntityAITasks;func_75776_a(ILnet/minecraft/entity/ai/EntityAIBase;)V"
            ),
            index = 1,
            remap = false
    )
    private EntityAIBase ulmodpack$filterMobTarget(EntityAIBase original) {
        return TargetSwap.swap(original, (EntityCreature) (Object) this);
    }
}
