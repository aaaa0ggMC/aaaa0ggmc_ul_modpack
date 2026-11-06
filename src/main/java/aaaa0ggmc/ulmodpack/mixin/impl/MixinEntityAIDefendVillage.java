package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.HateQueries;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIDefendVillage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 铁傀儡村庄防御 AI（{@code EntityAIDefendVillage}）：仇恨目标过敌意判定。
 *
 * <p><b>判定规则（设计）</b>：铁傀儡是村庄守护者，仇恨来源两路——
 * {@code Village.findNearestVillageAggressor}（攻击村民/村庄的通用攻击者）与
 * 每 20 次一试的 {@code Village.getNearestTargetPlayer}（声望扫描出的低声望玩家）。
 * 两路选出的 {@code villageAgressorTarget} 都在本判定上叠加
 * {@code HateQueries.isEnemy(golem, target)}（morphext 阵营图 + 玩家 FactionPassive
 * 身份压图）：</p>
 * <ul>
 *   <li>玩家变形 golem/villager 族形态（身份 MEMBER/RELATIVE/LORD）→ 非敌人，
 *       村庄铁傀儡不把它当仇恨目标（身份压图生效）；</li>
 *   <li>普通玩家（MODERATE 回退）→ 敌人，照原版攻击（低声望玩家仍会被声望扫描命中）；</li>
 *   <li>攻击村民的任意敌人生物 → 敌人，照原版攻击。</li>
 * </ul>
 *
 * <p>每次 {@code shouldExecute} 都会重找目标（非缓存续期），RETURN 拒绝不会阻塞
 * 下一轮重试；{@code canAttackClass} 的「玩家建造的不打玩家类」属保护主人语义
 * （类级 API 无实例敌意信息），保留；{@code onDeath} 声望扣减依赖
 * {@code attackingPlayer} 记账，属声望系统（见缺口 §G2），保留。</p>
 */
@Mixin(value = EntityAIDefendVillage.class, remap = false)
public abstract class MixinEntityAIDefendVillage {

    @Shadow
    EntityLivingBase villageAgressorTarget;

    @Shadow
    net.minecraft.entity.monster.EntityIronGolem irongolem;

    @Inject(method = "func_75250_a()Z", at = @At("RETURN"), cancellable = true, remap = false)
    private void ulmodpack$onlyAttackEnemies(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())
                && !HateQueries.isEnemy(this.irongolem, this.villageAgressorTarget)) {
            cir.setReturnValue(false);
        }
    }
}
