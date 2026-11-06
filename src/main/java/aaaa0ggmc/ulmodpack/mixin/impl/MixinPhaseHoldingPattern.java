package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.HateQueries;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.dragon.phase.PhaseBase;
import net.minecraft.entity.boss.dragon.phase.PhaseList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.pathfinding.Path;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.gen.feature.WorldGenEndPodium;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 末影龙盘旋阶段（{@code PhaseHoldingPattern.findNewTarget}，SRG {@code func_188675_j}）：
 * 飞行目标玩家查找重定向（2026-11-06 以 @Inject 复刻补回，零 @Redirect）。
 *
 * <p>原判定：{@code getNearestAttackablePlayer(BlockPos, 64, 64)}（以 End 基座为中心）
 * 找最近玩家做盘旋/扫射目标。新判定：{@code HateQueries.findNearestEnemy}（§G3
 * 锚点版，以基座为中心）——只把敌人玩家当飞行目标（变形同阵营玩家被跳过）。
 * 阶段机其余行为（路径点巡回、随机进近、strafe 触发概率）逐行保持原版。</p>
 *
 * <p>实现：{@code @Inject HEAD cancel} + 方法体复刻（唯一改动为查询行）。
 * 复刻体对 MCP 名的引用经 reobfJar 重映射；{@code @Shadow} 私有方法/字段同机制。
 * 兼容性注记见 {@link MixinEntityCreeper}（Cleanroom 禁用 @Redirect）。</p>
 */
@Mixin(targets = "net.minecraft.entity.boss.dragon.phase.PhaseHoldingPattern", remap = false)
public abstract class MixinPhaseHoldingPattern extends PhaseBase {

    @Shadow
    private Path currentPath;

    @Shadow
    private boolean clockwise;

    protected MixinPhaseHoldingPattern(EntityDragon dragon) {
        super(dragon);
    }

    @Shadow
    private void strafePlayer(EntityPlayer player) {
        throw new AssertionError("mixin shadow");
    }

    @Shadow
    private void navigateToNextPathNode() {
        throw new AssertionError("mixin shadow");
    }

    @Inject(method = "func_188675_j()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void ulmodpack$flyTowardEnemyPlayers(CallbackInfo ci) {
        if (this.currentPath != null && this.currentPath.isFinished()) {
            BlockPos podium = this.dragon.world
                    .getTopSolidOrLiquidBlock(new BlockPos(WorldGenEndPodium.END_PODIUM_LOCATION));
            int crystals = this.dragon.getFightManager() == null
                    ? 0 : this.dragon.getFightManager().getNumAliveCrystals();
            if (this.dragon.getRNG().nextInt(crystals + 3) == 0) {
                this.dragon.getPhaseManager().setPhase(PhaseList.LANDING_APPROACH);
                ci.cancel();
                return;
            }

            double distFactor = 64.0D;
            // §G3 锚点找敌：以 End 基座为中心，只把敌人玩家当飞行目标
            EntityPlayer enemy = HateQueries.findNearestEnemy(
                    this.dragon, new Vec3d(podium), distFactor, EntityPlayer.class, null);
            if (enemy != null) {
                distFactor = enemy.getDistanceSqToCenter(podium) / 512.0D;
            }

            if (enemy != null && (this.dragon.getRNG().nextInt(MathHelper.abs((int) distFactor) + 2) == 0
                    || this.dragon.getRNG().nextInt(crystals + 2) == 0)) {
                this.strafePlayer(enemy);
                ci.cancel();
                return;
            }
        }

        if (this.currentPath == null || this.currentPath.isFinished()) {
            int startIdx = this.dragon.initPathPoints();
            int targetIdx = startIdx;
            if (this.dragon.getRNG().nextInt(8) == 0) {
                this.clockwise = !this.clockwise;
                targetIdx = startIdx + 6;
            }

            if (this.clockwise) {
                targetIdx++;
            } else {
                targetIdx--;
            }

            if (this.dragon.getFightManager() != null
                    && this.dragon.getFightManager().getNumAliveCrystals() >= 0) {
                targetIdx %= 12;
                if (targetIdx < 0) {
                    targetIdx += 12;
                }
            } else {
                targetIdx -= 12;
                targetIdx &= 7;
                targetIdx += 12;
            }

            this.currentPath = this.dragon.findPath(startIdx, targetIdx, null);
            if (this.currentPath != null) {
                this.currentPath.incrementPathIndex();
            }
        }

        this.navigateToNextPathNode();
        ci.cancel();
    }
}
