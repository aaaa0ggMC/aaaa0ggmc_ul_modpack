package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.HateQueries;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.dragon.phase.PhaseBase;
import net.minecraft.entity.boss.dragon.phase.PhaseList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 末影龙栖息扫描阶段（{@code PhaseSittingScanning.doLocalUpdate}，SRG {@code func_188659_c}）：
 * 「附近有玩家→进入对峙/喷火」的扫描玩家查找重定向（2026-11-06 以 @Inject 复刻补回，
 * 零 @Redirect）。
 *
 * <p>原判定：两处 {@code getNearestAttackablePlayer(龙, 20/10 与 150/150)}（以龙为中心）。
 * 新判定：{@code HateQueries.findNearestEnemy}（self 中心版）——只把敌人玩家当扫描信号。
 * 视角追踪、随机偏航与阶段切换逐行保持原版。兼容性注记见 {@link MixinEntityCreeper}。</p>
 */
@Mixin(targets = "net.minecraft.entity.boss.dragon.phase.PhaseSittingScanning", remap = false)
public abstract class MixinPhaseSittingScanning extends PhaseBase {

    @Shadow
    private int scanningTime;

    protected MixinPhaseSittingScanning(EntityDragon dragon) {
        super(dragon);
    }

    @Inject(method = "func_188659_c()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void ulmodpack$scanForEnemyPlayers(CallbackInfo ci) {
        this.scanningTime++;
        // §G3 self 中心找敌：只把敌人玩家当扫描信号
        EntityLivingBase found = HateQueries.findNearestEnemy(this.dragon, 20.0D, EntityPlayer.class, null);
        if (found != null) {
            if (this.scanningTime > 25) {
                this.dragon.getPhaseManager().setPhase(PhaseList.SITTING_ATTACKING);
            } else {
                Vec3d toward = new Vec3d(
                        found.posX - this.dragon.posX, 0.0D, found.posZ - this.dragon.posZ).normalize();
                Vec3d facing = new Vec3d(
                        MathHelper.sin(this.dragon.rotationYaw * (float) (Math.PI / 180.0)),
                        0.0D,
                        -MathHelper.cos(this.dragon.rotationYaw * (float) (Math.PI / 180.0))
                ).normalize();
                float dot = (float) facing.dotProduct(toward);
                float angle = (float) (Math.acos(dot) * 180.0F / (float) Math.PI) + 0.5F;
                if (angle < 0.0F || angle > 10.0F) {
                    double dx = found.posX - this.dragon.dragonPartHead.posX;
                    double dz = found.posZ - this.dragon.dragonPartHead.posZ;
                    float turn = MathHelper.clamp(
                            MathHelper.wrapDegrees(
                                    180.0F - (float) (MathHelper.atan2(dx, dz) * 180.0F / (float) Math.PI)
                                            - this.dragon.rotationYaw),
                            -100.0F, 100.0F);
                    this.dragon.randomYawVelocity *= 0.8F;
                    float dist = MathHelper.sqrt(dx * dx + dz * dz) + 1.0F;
                    float distCap = dist;
                    if (dist > 40.0F) {
                        dist = 40.0F;
                    }
                    this.dragon.randomYawVelocity =
                            (float) (this.dragon.randomYawVelocity + turn * (0.7F / dist / distCap));
                    this.dragon.rotationYaw = this.dragon.rotationYaw + this.dragon.randomYawVelocity;
                }
            }
        } else if (this.scanningTime >= 100) {
            EntityLivingBase far = HateQueries.findNearestEnemy(this.dragon, 150.0D, EntityPlayer.class, null);
            this.dragon.getPhaseManager().setPhase(PhaseList.TAKEOFF);
            if (far != null) {
                this.dragon.getPhaseManager().setPhase(PhaseList.CHARGING_PLAYER);
                this.dragon.getPhaseManager().getPhase(PhaseList.CHARGING_PLAYER)
                        .setTarget(new Vec3d(far.posX, far.posY, far.posZ));
            }
        }
        ci.cancel();
    }
}
