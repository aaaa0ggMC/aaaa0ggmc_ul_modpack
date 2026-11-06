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
 */
@Mixin(targets = "net.minecraft.entity.boss.dragon.phase.PhaseLandingApproach", remap = false)
public abstract class MixinPhaseLandingApproach extends PhaseBase {

    @Shadow
    private Path currentPath;

    protected MixinPhaseLandingApproach(EntityDragon dragon) {
        super(dragon);
    }

    @Shadow
    private void navigateToNextPathNode() {
        throw new AssertionError("mixin shadow");
    }

    @Inject(method = "func_188681_j()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void ulmodpack$landTowardEnemyPlayers(CallbackInfo ci) {
        if (this.currentPath == null || this.currentPath.isFinished()) {
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
            this.currentPath = this.dragon.findPath(startIdx, targetIdx, end);
            if (this.currentPath != null) {
                this.currentPath.incrementPathIndex();
            }
        }

        this.navigateToNextPathNode();
        ci.cancel();
    }
}
