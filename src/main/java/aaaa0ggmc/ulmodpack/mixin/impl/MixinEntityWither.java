package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.HateQueries;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.util.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 凋灵：「对玩家特判」重定向到统一仇恨接口（Boss 敌意回退语义保留）。
 *
 * <p><b>判定规则（设计）</b>：Boss 头颅目标机对周围生物的敌意保持原版
 * （NOT_UNDEAD 泛型回退，无映射回退 MODERATE→敌对即原版行为），只重定向两处
 * 玩家特判：</p>
 * <ol>
 *   <li><b>同属性免疫的「玩家例外」</b>（{@code attackEntityFrom}，SRG
 *       {@code func_70097_a}）：原版「同 CreatureAttribute 的非玩家攻击者免疫、
 *       玩家例外可伤」→ 新规则「同属性的<em>非敌人</em>攻击者免疫」。行为变化：
 *       变形同阵营亡灵玩家不可再伤凋灵；敌对同属性生物可伤；普通玩家仍可伤。</li>
 *   <li><b>头颅发射</b>（{@code launchWitherSkullToEntity}，SRG {@code func_82216_a}）：
 *       发射前过 {@code isEnemy}——非敌人目标不发射。选目标/watched-target 维持
 *       原版（含创造玩家 disableDamage 豁免与 NOT_UNDEAD 候选）。</li>
 * </ol>
 *
 * <p><b>兼容性注记（2026-11-06 急修）</b>：原头颅过滤为 {@code @Redirect}
 * （改 updateAITasks 里的调用）；Cleanroom 上 @Redirect 必崩
 * （见 {@link MixinEntityCreeper} 注记），改写为 {@code @Inject} 到
 * {@code launchWitherSkullToEntity} 本体 HEAD cancel。</p>
 */
@Mixin(value = EntityWither.class, remap = false)
public abstract class MixinEntityWither {

    /**
     * 同属性非敌人免疫：原版四与条件中的 {@code !(entity1 instanceof EntityPlayer)}
     * 重定向为 {@code !isEnemy(wither, entity1)}（§G2 三级归因）。
     * 在 HEAD 短路，其余分支（护甲挡箭、无敌、溺水等）照常走原版。
     */
    @Inject(
            method = "func_70097_a(Lnet/minecraft/util/DamageSource;F)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ulmodpack$sameAttributeNonEnemyImmune(DamageSource source, float amount,
                                                       CallbackInfoReturnable<Boolean> cir) {
        EntityLivingBase attacker = HateQueries.resolveDamageSource(source);
        if (attacker != null && attacker != (Object) this) {
            EntityWither self = (EntityWither) (Object) this;
            if (attacker.getCreatureAttribute() == self.getCreatureAttribute()
                    && !HateQueries.isEnemy(self, attacker)) {
                cir.setReturnValue(false);
            }
        }
    }

    /** 头颅发射过滤：非敌人目标不发射（选目标逻辑保持原版）。 */
    @Inject(
            method = "func_82216_a(ILnet/minecraft/entity/EntityLivingBase;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ulmodpack$skullOnlyAtEnemies(int skullId, EntityLivingBase target, CallbackInfo ci) {
        if (!HateQueries.isEnemy((EntityWither) (Object) this, target)) {
            ci.cancel();
        }
    }
}
