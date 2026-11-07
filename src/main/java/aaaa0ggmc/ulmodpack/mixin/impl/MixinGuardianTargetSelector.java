package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.HateQueries;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 守卫者/远古守卫者目标选择器 {@code EntityGuardian.GuardianTargetSelector}：敌人过滤。
 *
 * <p>原判定：{@code apply()} 只认 {@code EntityPlayer} 或 {@code EntitySquid}
 * 为合法光束目标（+9 格距离下限）。新判定：玩家一路叠加
 * {@code HateQueries.isEnemy}（morphext 阵营图）——敌人玩家才可被锁定，
 * 鱿鱼特例保留原样。</p>
 * <p><b>SRG 名约定（2026-11-07 修）</b>：{@code remap = false} 时 refmap 不参与，而 reobfJar 只重映射
 * 「owner 为已映射 MC 类」的引用——{@code @Shadow} 声明及其在本 mixin 方法体里的引用 owner 是
 * 本 mixin 类，不在 MC 映射表内。因此 {@code @Shadow} 成员名与上述内部引用<b>必须</b>直接写 SRG 名
 * （查 {@code reference/mappings-stable39/srg-mcp.srg}），写 MCP 名会原样留在字节码里，运行时
 * 直接 {@code InvalidMixinException: @Shadow ... was not located}。方法体里对原版成员的引用仍由
 * reobfJar 重映射，无需手写 SRG。</p>
 * <p>本类映射：parentEntity = {@code field_179916_a}。</p>
 */
@Mixin(targets = "net.minecraft.entity.monster.EntityGuardian$GuardianTargetSelector", remap = false)
public abstract class MixinGuardianTargetSelector {

    @Shadow
    private EntityGuardian field_179916_a;

    @Inject(
            method = "apply(Lnet/minecraft/entity/EntityLivingBase;)Z",
            at = @At("RETURN"),
            cancellable = true,
            remap = false
    )
    private void ulmodpack$playersMustBeEnemies(EntityLivingBase candidate, CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue())
                && candidate instanceof EntityPlayer
                && !HateQueries.isEnemy(this.field_179916_a, candidate)) {
            cir.setReturnValue(false);
        }
    }
}
