package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.HateQueries;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAICreeperSwell;
import net.minecraft.entity.monster.EntityCreeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 苦力怕（BUG 修复第一优先）：膨胀/引信守门。
 *
 * <p>原版 {@code EntityAICreeperSwell} 只看「有没有攻击目标、目标是否 3 格内」
 * 就起手膨胀（{@code shouldExecute}），并以 {@code updateTask} 维持引信——攻击目标
 * 来自 {@code EntityCreeper.initEntityAI} 的硬编码玩家索敌（见
 * {@code MixinEntityCreeper}）。变形僵尸玩家（与 creeper 阵营 MEMBER）因此被当作
 * 引爆对象。</p>
 *
 * <p>注入后：起手判定（膨胀）与引信维持（引爆）都过统一仇恨接口
 * {@code HateQueries.isEnemy}——非敌人目标不起手；起手后目标变节（变形换阵营）
 * 则退引。已起手的引信按 morphext 既定口径走完（波及照常结算），不吞爆炸。</p>
 *
 * <p>字段写入安全性（AGENTS.md 通用机制教训）：{@code creeperAttackTarget} 置 null
 * 已核对读侧——{@code updateTask} 首行即 {@code if (this.creeperAttackTarget == null)}
 * 复查并退引（setCreeperState(-1)），null-safe；不触碰其他任务内部字段。</p>
 *
 * <p>绑定同仓库惯例：SRG 名 + {@code remap = false}
 * （shouldExecute = {@code func_75250_a}、updateTask = {@code func_75246_d}，
 * 已对 {@code mappings-stable39} 核实）。{@code @Shadow} 成员名**必须**直接写 SRG 名
 * （swellingCreeper = {@code field_75269_a}、creeperAttackTarget = {@code field_75268_b}）：
 * {@code remap = false} 时 refmap 不参与，而 reobfJar 只重映射「owner 为已映射 MC 类」的引用——
 * {@code @Shadow} 声明及其在 mixin 自身方法体里的引用 owner 是本 mixin 类，不在 MC 映射表内，
 * 写 MCP 名会原样留在字节码里，与运行时的 SRG 名对不上，直接
 * {@code InvalidMixinException: @Shadow field ... was not located}（2026-11-07 修）。
 * 方法体里对原版成员的引用仍由 reobfJar 重映射，无需手写 SRG。</p>
 */
@Mixin(value = EntityAICreeperSwell.class, remap = false)
public abstract class MixinEntityAICreeperSwell extends EntityAIBase {

    @Shadow
    EntityCreeper field_75269_a;

    @Shadow
    EntityLivingBase field_75268_b;

    /**
     * {@code shouldExecute()}（SRG {@code func_75250_a}）：原判定
     * {@code creeperState > 0 || (attackTarget != null && distSq < 9)}。
     * 新判定：attackTarget 分支追加 {@code HateQueries.isEnemy(creeper, target)}
     * ——变形同阵营玩家站在旁边不起手；已起手（creeperState &gt; 0）的分支保持原样
     * （引信走完）。
     */
    @Inject(method = "func_75250_a()Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void ulmodpack$onlySwellAtEnemy(CallbackInfoReturnable<Boolean> cir) {
        EntityLivingBase target = this.field_75269_a.getAttackTarget();
        boolean fuseArmed = this.field_75269_a.getCreeperState() > 0;
        boolean enemyClose = target != null
                && this.field_75269_a.getDistanceSq(target) < 9.0D
                && HateQueries.isEnemy(this.field_75269_a, target);
        cir.setReturnValue(fuseArmed || enemyClose);
    }

    /**
     * {@code updateTask()}（SRG {@code func_75246_d}）引信维持：缓存目标失去敌资格
     * （变形切阵营/臣服）即置 null，原版 null 分支随即退引
     * （{@code setCreeperState(-1)}）。
     */
    @Inject(method = "func_75246_d()V", at = @At("HEAD"), remap = false)
    private void ulmodpack$disarmOnLostEnemy(CallbackInfo ci) {
        if (this.field_75268_b != null
                && !HateQueries.isEnemy(this.field_75269_a, this.field_75268_b)) {
            this.field_75268_b = null;
        }
    }
}
