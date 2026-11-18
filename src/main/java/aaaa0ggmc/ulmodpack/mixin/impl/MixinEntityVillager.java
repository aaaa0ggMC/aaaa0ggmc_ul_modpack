package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.TargetSwap;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.passive.EntityVillager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 村民：躲避僵尸任务改为按 {@code HateQueries.isEnemy} 过滤。
 *
 * <p>原版 {@code initEntityAI()}（SRG {@code func_184651_r}）对 {@code EntityZombie.class}
 * 无差别躲避，导致属于村民亲缘阵营的原版僵尸村民也会把村民吓跑。替换后普通僵尸/尸壳等
 * 仍会触发躲避，而原版僵尸村民（RELATIVE）不再吓跑村民。</p>
 */
@Mixin(value = EntityVillager.class, remap = false)
public abstract class MixinEntityVillager {

    @ModifyArg(
            method = "func_184651_r()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/EntityAITasks;func_75776_a(ILnet/minecraft/entity/ai/EntityAIBase;)V"
            ),
            index = 1,
            remap = false
    )
    private EntityAIBase ulmodpack$filterZombieAvoid(EntityAIBase original) {
        return TargetSwap.swap(original, (EntityCreature) (Object) this);
    }
}
