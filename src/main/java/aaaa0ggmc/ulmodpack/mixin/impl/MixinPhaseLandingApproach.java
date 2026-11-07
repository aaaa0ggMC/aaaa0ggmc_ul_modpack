package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.HateQueries;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.dragon.phase.PhaseBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.gen.feature.WorldGenEndPodium;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 末影龙降落进近阶段（{@code PhaseLandingApproach.findNewTarget}，SRG {@code func_188681_j}）：
 * 降落目标玩家查找重定向（2026-11-06 以 @Inject 复刻补回，零 @Redirect）。
 *
 * <p>原判定：{@code getNearestAttackablePlayer(基座, 128, 128)} 找最近玩家定降落朝向。
 * 新判定：{@code HateQueries.findNearestEnemy}（§G3 锚点版）——只按敌人玩家定向。
 * 路径计算逐行保持原版。兼容性注记见 {@link MixinEntityCreeper}。</p>
 * <p><b>SRG 名约定（2026-11-07 修）</b>：{@code remap = false} 时 refmap 不参与，而 reobfJar 只重映射
 * 「owner 为已映射 MC 类」的引用——{@code @Shadow} 声明及其在本 mixin 方法体里的引用 owner 是
 * 本 mixin 类，不在 MC 映射表内。因此 {@code @Shadow} 成员名与上述内部引用<b>必须</b>直接写 SRG 名
 * （查 {@code reference/mappings-stable39/srg-mcp.srg}），写 MCP 名会原样留在字节码里，运行时
 * 直接 {@code InvalidMixinException: @Shadow ... was not located}。方法体里对原版成员的引用仍由
 * reobfJar 重映射，无需手写 SRG。</p>
 * <p>本类映射：currentPath = {@code field_188683_b}、navigateToNextPathNode = {@code func_188682_k}。</p>
 */
@Mixin(targets = "net.minecraft.entity.boss.dragon.phase.PhaseLandingApproach", remap = false)
public abstract class MixinPhaseLandingApproach extends PhaseBase {

    @Shadow
    private Path field_188683_b;

    protected MixinPhaseLandingApproach(EntityDragon dragon) {
        super(dragon);
    }

    @Shadow
    private void func_188682_k() {
        throw new AssertionError("mixin shadow");
    }

    @Inject(method = "func_188681_j()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void ulmodpack$landTowardEnemyPlayers(CallbackInfo ci) {
        if (this.field_188683_b == null || this.field_188683_b.isFinished()) {
            int startIdx = this.dragon.initPathPoints();
            BlockPos podium = this.dragon.world
                    .getTopSolidOrLiquidBlock(WorldGenEndPodium.END_PODIUM_LOCATION);
            // §G3 锚点找敌：以 End 基座为中心
            EntityPlayer enemy = HateQueries.findNearestEnemy(
                    this.dragon, new Vec3d(podium), 128.0D, EntityPlayer.class, null);
            int targetIdx;
            if (enemy != null) {
                Vec3d dir = new Vec3d(enemy.posX, 0.0D, enemy.posZ).normalize();
                targetIdx = this.dragon.getNearestPpIdx(-dir.x * 40.0D, 105.0D, -dir.z * 40.0D);
            } else {
                targetIdx = this.dragon.getNearestPpIdx(40.0D, podium.getY(), 0.0D);
            }

            PathPoint end = new PathPoint(podium.getX(), podium.getY(), podium.getZ());
            this.field_188683_b = this.dragon.findPath(startIdx, targetIdx, end);
            if (this.field_188683_b != null) {
                this.field_188683_b.incrementPathIndex();
            }
        }

        this.func_188682_k();
        ci.cancel();
    }
}
