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
 * <p><b>SRG 名约定（2026-11-07 修）</b>：{@code remap = false} 时 refmap 不参与，而 reobfJar 只重映射
 * 「owner 为已映射 MC 类」的引用——{@code @Shadow} 声明及其在本 mixin 方法体里的引用 owner 是
 * 本 mixin 类，不在 MC 映射表内。因此 {@code @Shadow} 成员名与上述内部引用<b>必须</b>直接写 SRG 名
 * （查 {@code reference/mappings-stable39/srg-mcp.srg}），写 MCP 名会原样留在字节码里，运行时
 * 直接 {@code InvalidMixinException: @Shadow ... was not located}。方法体里对原版成员的引用仍由
 * reobfJar 重映射，无需手写 SRG。</p>
 * <p>本类映射：currentPath = {@code field_188677_b}、clockwise = {@code field_188679_d}、strafePlayer = {@code func_188674_a}、navigateToNextPathNode = {@code func_188676_k}。</p>
 */
@Mixin(targets = "net.minecraft.entity.boss.dragon.phase.PhaseHoldingPattern", remap = false)
public abstract class MixinPhaseHoldingPattern extends PhaseBase {

    @Shadow
    private Path field_188677_b;

    @Shadow
    private boolean field_188679_d;

    protected MixinPhaseHoldingPattern(EntityDragon dragon) {
        super(dragon);
    }

    @Shadow
    private void func_188674_a(EntityPlayer player) {
        throw new AssertionError("mixin shadow");
    }

    @Shadow
    private void func_188676_k() {
        throw new AssertionError("mixin shadow");
    }

    @Inject(method = "func_188675_j()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void ulmodpack$flyTowardEnemyPlayers(CallbackInfo ci) {
        if (this.field_188677_b != null && this.field_188677_b.isFinished()) {
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
                this.func_188674_a(enemy);
                ci.cancel();
                return;
            }
        }

        if (this.field_188677_b == null || this.field_188677_b.isFinished()) {
            int startIdx = this.dragon.initPathPoints();
            int targetIdx = startIdx;
            if (this.dragon.getRNG().nextInt(8) == 0) {
                this.field_188679_d = !this.field_188679_d;
                targetIdx = startIdx + 6;
            }

            if (this.field_188679_d) {
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

            this.field_188677_b = this.dragon.findPath(startIdx, targetIdx, null);
            if (this.field_188677_b != null) {
                this.field_188677_b.incrementPathIndex();
            }
        }

        this.func_188676_k();
        ci.cancel();
    }
}
