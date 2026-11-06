package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.HateQueries;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.passive.EntityWolf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 狼：tamed/owner 与阵营图结合。
 *
 * <p><b>判定规则（设计）</b>：</p>
 * <ol>
 *   <li><b>主人永不敌对</b>：{@code shouldAttackEntity}（SRG {@code func_142018_a}）的
 *       候选目标过滤——{@code FactionEnmity.isEnemy} 已内建主人/同主豁免（§G1），
 *       {@code !isEnemy} 一行涵盖「主人 + 非敌人」全部拒绝语义；</li>
 *   <li><b>野狼（含驯服狼对非主人）敌意走 isEnemy</b>：同阵营/臣服形态不打，
 *       敌人（含普通玩家，MODERATE 回退）保持原版攻击；</li>
 *   <li><b>骷髅目标阵营化</b>：{@code initEntityAI}:95 的骷髅索敌（wolf↔skeleton
 *       ENEMY 边固化于 faction_relations.json）经 {@link TargetSwap} 换
 *       {@code NearestEnemyTarget} 泛型替身。</li>
 * </ol>
 *
 * <p>{@code attackEntityFrom} 的「非玩家/非箭攻击者伤害减半」保留原样：
 * 属对玩家的平衡优待而非仇恨检测（重定向语义换轨待拍板，见清单 §7.1 §G5）。
 * 兼容性注记见 {@link MixinEntityCreeper}（@Redirect→@ModifyArg）。</p>
 */
@Mixin(value = EntityWolf.class, remap = false)
public abstract class MixinEntityWolf {

    /**
     * {@code shouldAttackEntity(target, owner)}：原版规则（不打苦力怕/恶魂/
     * 他人驯服狼/驯服马、PvP 过滤）之上叠加敌意判定。
     */
    @Inject(
            method = "func_142018_a(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/entity/EntityLivingBase;)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void ulmodpack$onlyAttackEnemies(EntityLivingBase target, EntityLivingBase owner,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())
                && !HateQueries.isEnemy((EntityWolf) (Object) this, target)) {
            cir.setReturnValue(false);
        }
    }

    /** 骷髅目标：addTask 参数经 TargetSwap 换 NearestEnemyTarget（保持原 checkSight 语义）。 */
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
