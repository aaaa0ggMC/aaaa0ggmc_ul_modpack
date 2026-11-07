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
 * <p><b>SRG 名约定（2026-11-07 修）</b>：{@code remap = false} 时 refmap 不参与，而 reobfJar 只重映射
 * 「owner 为已映射 MC 类」的引用——{@code @Shadow} 声明及其在本 mixin 方法体里的引用 owner 是
 * 本 mixin 类，不在 MC 映射表内。因此 {@code @Shadow} 成员名与上述内部引用<b>必须</b>直接写 SRG 名
 * （查 {@code reference/mappings-stable39/srg-mcp.srg}），写 MCP 名会原样留在字节码里，运行时
 * 直接 {@code InvalidMixinException: @Shadow ... was not located}。方法体里对原版成员的引用仍由
 * reobfJar 重映射，无需手写 SRG。</p>
 * <p>本类映射：villageAgressorTarget = {@code field_75304_b}、irongolem = {@code field_75305_a}。</p>
 */
@Mixin(value = EntityAIDefendVillage.class, remap = false)
public abstract class MixinEntityAIDefendVillage {

    @Shadow
    EntityLivingBase field_75304_b;

    @Shadow
    net.minecraft.entity.monster.EntityIronGolem field_75305_a;

    @Inject(method = "func_75250_a()Z", at = @At("RETURN"), cancellable = true, remap = false)
    private void ulmodpack$onlyAttackEnemies(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())
                && !HateQueries.isEnemy(this.field_75305_a, this.field_75304_b)) {
            cir.setReturnValue(false);
        }
    }
}
