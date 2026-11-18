package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.TargetSwap;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.monster.EntityEvoker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 唤魔者：目标选择与躲避玩家的重定向（{@code initEntityAI}，SRG {@code func_184651_r}）。
 *
 * <p><b>判定规则（设计）</b>：</p>
 * <ul>
 *   <li><b>攻击目标</b>（:59 链式注册
 *       {@code EntityAINearestAttackableTarget<>(this, EntityPlayer.class, true)
 *       .setUnseenMemoryTicks(300)}）：经 {@link TargetSwap} 换敌人过滤版，
 *       unseenMemoryTicks=300 一并复制（村民/铁傀儡目标为「袭击村庄」语义保留）；</li>
 *   <li><b>躲避玩家</b>（:51 {@code EntityAIAvoidEntity<>(this, EntityPlayer.class, 8.0F, 0.6, 1.0)}）：
 *       §G6 躲避语义 = 只躲「敌人玩家」——换 isEnemy 谓词版躲避任务
 *       （同阵营变形玩家靠近不再吓跑唤魔者；普通玩家照常躲避）。</li>
 * </ul>
 *
 * <p>兼容性注记见 {@link MixinEntityCreeper}（禁用 @Redirect，@ModifyArg 双运行时安全）。</p>
 */
@Mixin(value = EntityEvoker.class, remap = false)
public abstract class MixinEntityEvoker {

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
