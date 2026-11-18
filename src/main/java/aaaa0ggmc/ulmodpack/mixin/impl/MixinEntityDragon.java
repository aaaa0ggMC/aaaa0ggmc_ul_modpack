package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.HateQueries;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.MultiPartEntityPart;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.dragon.phase.PhaseManager;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 末影龙：「对玩家特判」重定向到统一仇恨接口（Boss 阶段机回退语义保留）。
 *
 * <p><b>判定规则（设计）</b>：阶段飞行/扫描的玩家查找点见清单
 * （§3.3「Phase×3 待改写」降级注记）；本体两处玩家特判：</p>
 * <ol>
 *   <li><b>伤害受理门</b>（{@code attackDragonFrom}，SRG {@code func_82195_e}：
 *       原版「{@code getTrueSource() instanceof EntityPlayer || isExplosion()} 才受理」
 *       → 新规则「{@code isEnemy(dragon, attacker) || isExplosion()}」（§G2 归因）。
 *       行为变化：敌对生物可伤龙（原版仅玩家/爆炸）；变形同阵营（ender 族 RELATIVE）
 *       玩家不可伤。判定放 {@code attackDragonFrom} 入口 HEAD（其为唯一受理落点，
 *       attackEntityFromPart 与水晶爆炸回敬都经它）。</li>
 *   <li><b>水晶仇恨归因</b>（{@code onCrystalDestroyed}，SRG {@code func_184672_a}）：
 *       敌人攻击者归因，否则以水晶位置为锚点（§G3）找最近敌人玩家；找不到归因 null。</li>
 * </ol>
 *
 * <p><b>兼容性注记（2026-11-06 急修）</b>：原伤害门为 {@code @Redirect}
 * （改 attackDragonFrom 调用）；Cleanroom 上 @Redirect 必崩（见
 * {@link MixinEntityCreeper} 注记），改写为 {@code @Inject} 到
 * {@code attackDragonFrom} 本体 HEAD cancel。</p>
 * <p><b>SRG 名约定（2026-11-07 修）</b>：{@code remap = false} 时 refmap 不参与，而 reobfJar 只重映射
 * 「owner 为已映射 MC 类」的引用——{@code @Shadow} 声明及其在本 mixin 方法体里的引用 owner 是
 * 本 mixin 类，不在 MC 映射表内。因此 {@code @Shadow} 成员名与上述内部引用<b>必须</b>直接写 SRG 名
 * （查 {@code reference/mappings-stable39/srg-mcp.srg}），写 MCP 名会原样留在字节码里，运行时
 * 直接 {@code InvalidMixinException: @Shadow ... was not located}。方法体里对原版成员的引用仍由
 * reobfJar 重映射，无需手写 SRG。</p>
 * <p>本类映射：healingEnderCrystal = {@code field_70992_bH}、dragonPartHead = {@code field_70986_h}、phaseManager = {@code field_184677_bJ}。</p>
 */
@Mixin(value = EntityDragon.class, remap = false)
public abstract class MixinEntityDragon {

    @Shadow
    EntityEnderCrystal field_70992_bH;

    @Shadow
    MultiPartEntityPart field_70986_h;

    @Shadow
    PhaseManager field_184677_bJ;

    /**
     * 伤害受理门：非敌人定向伤害不受理（原版 {@code instanceof EntityPlayer || isExplosion}）。
     * 爆炸照常结算（波及口径）。
     */
    @Inject(
            method = "func_82195_e(Lnet/minecraft/util/DamageSource;F)Z",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ulmodpack$onlyEnemiesDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        EntityLivingBase attacker = HateQueries.resolveDamageSource(source);
        boolean enemyStrike = attacker != null && HateQueries.isEnemy((EntityDragon) (Object) this, attacker);
        if (!enemyStrike && !source.isExplosion()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 水晶仇恨归因：HEAD 复刻原版 20 行逻辑，仅把两处玩家特判换成
     * isEnemy / findNearestEnemy（其余语义不变：healingEnderCrystal 爆炸回敬、
     * 阶段回调携带归因玩家）。
     */
    @Inject(
            method = "func_184672_a(Lnet/minecraft/entity/item/EntityEnderCrystal;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/DamageSource;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private void ulmodpack$enemyAttribution(EntityEnderCrystal crystal, BlockPos pos, DamageSource source,
                                            CallbackInfo ci) {
        EntityDragon self = (EntityDragon) (Object) this;
        EntityPlayer blamed = null;
        // §G2 归因 + §G3 锚点找敌：先按伤害三级归因，敌人攻击者直接归因；
        // 否则以水晶位置为锚点找最近敌人玩家（原版是硬找任意玩家）。
        EntityLivingBase attacker = HateQueries.resolveDamageSource(source);
        if (attacker instanceof EntityPlayer && HateQueries.isEnemy(self, attacker)) {
            blamed = (EntityPlayer) attacker;
        } else {
            blamed = HateQueries.findNearestEnemy(
                    self, new net.minecraft.util.math.Vec3d(pos), 64.0D, EntityPlayer.class, null);
        }

        if (crystal == this.field_70992_bH) {
            self.attackEntityFromPart(
                    this.field_70986_h, DamageSource.causeExplosionDamage(blamed), 10.0F);
        }
        this.field_184677_bJ.getCurrentPhase().onCrystalDestroyed(crystal, pos, source, blamed);
        ci.cancel();
    }
}
