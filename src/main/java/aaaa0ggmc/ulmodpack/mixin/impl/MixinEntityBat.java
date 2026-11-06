package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.HateQueries;
import net.minecraft.entity.passive.EntityAmbientCreature;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 蝙蝠：倒挂惊飞（{@code EntityBat.updateAITasks}，SRG {@code func_70619_bc}）的
 * 玩家敏感判定改走统一仇恨接口（2026-11-06 补回文档遗留的「蝙蝠惊飞待改写」）。
 *
 * <p><b>原判定</b>（1.12.2 {@code EntityBat.java:121}）：倒挂且头顶方块是完整方块时，
 * {@code this.world.getNearestPlayerNotCreative(this, 4.0)} 非 null → 解除倒挂并放
 * 1025 起飞事件。即「任意非创造/旁观玩家进入 4 格就惊飞」，不看阵营。</p>
 *
 * <p><b>新判定</b>（§G6 躲避语义 = 只躲敌人）：同一位置、同一 4.0 格欧氏距离、
 * 同一 tick 节奏，唯一改动是玩家筛选从「非创造/旁观」叠加
 * {@link HateQueries#isEnemy}——同阵营变形玩家（如蝙蝠/动物形态）靠近不再惊飞；
 * 普通无阵营玩家按 MODERATE→敌对回退照常惊飞；创造/旁观语义保持原版。
 * 无阵营映射时 {@code isEnemy} 回退 MODERATE=敌对，行为与原版一致。</p>
 *
 * <p><b>执行侧</b>：{@code updateAITasks} 只在服务端 tick 调用，判定与
 * {@code setIsBatHanging}/世界事件都在服务端，客户端由 dataManager 同步姿态。</p>
 *
 * <p><b>实现</b>：{@code @Inject HEAD cancel} + 方法体复刻（唯一改动为玩家查询行）；
 * 复刻体对 MCP 名的引用经 reobfJar 重映射。父类
 * {@code EntityAmbientCreature}/{@code EntityLiving.updateAITasks()} 为空实现，
 * 仍保留 {@code super.updateAITasks()} 调用以维持 super 链。继承目标父类仅为
 * javac 调 super 所需（与 {@link MixinPhaseHoldingPattern} 同一写法）；
 * 构造器体与 {@code EntityBat(World)} 逐行等价，即使被 Mixin 合并也不改变初始化。
 * 兼容性注记见 {@link MixinEntityCreeper}（Cleanroom 禁用 @Redirect）。</p>
 */
@Mixin(value = EntityBat.class, remap = false)
public abstract class MixinEntityBat extends EntityAmbientCreature {

    @Shadow
    private BlockPos spawnPosition;

    protected MixinEntityBat(World world) {
        super(world);
        // 与 EntityBat(World) 的初始化保持一致（见类注释：防止构造器被合并时丢初始化）
        EntityBat self = (EntityBat) (Object) this;
        this.setSize(0.5F, 0.9F);
        self.setIsBatHanging(true);
    }

    @Inject(method = "func_70619_bc()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void ulmodpack$flyAwayFromEnemyPlayers(CallbackInfo ci) {
        EntityBat self = (EntityBat) (Object) this;
        super.updateAITasks();

        BlockPos pos = new BlockPos(self);
        BlockPos above = pos.up();
        if (self.getIsBatHanging()) {
            if (self.world.getBlockState(above).isNormalCube()) {
                if (this.rand.nextInt(200) == 0) {
                    self.rotationYawHead = this.rand.nextInt(360);
                }

                // 原版：getNearestPlayerNotCreative(self, 4.0) != null
                // 新：4 格内最近的非创造/旁观「敌人」玩家（§G6 躲避语义）
                EntityPlayer enemy = HateQueries.findNearestEnemy(
                        self, 4.0D, EntityPlayer.class,
                        player -> !player.isSpectator() && !player.isCreative());
                if (enemy != null) {
                    self.setIsBatHanging(false);
                    self.world.playEvent(null, 1025, pos, 0);
                }
            } else {
                self.setIsBatHanging(false);
                self.world.playEvent(null, 1025, pos, 0);
            }
        } else {
            if (this.spawnPosition != null
                    && (!self.world.isAirBlock(this.spawnPosition) || this.spawnPosition.getY() < 1)) {
                this.spawnPosition = null;
            }

            if (this.spawnPosition == null || this.rand.nextInt(30) == 0
                    || this.spawnPosition.distanceSq((int) self.posX, (int) self.posY, (int) self.posZ) < 4.0D) {
                this.spawnPosition = new BlockPos(
                        (int) self.posX + this.rand.nextInt(7) - this.rand.nextInt(7),
                        (int) self.posY + this.rand.nextInt(6) - 2,
                        (int) self.posZ + this.rand.nextInt(7) - this.rand.nextInt(7));
            }

            double dx = this.spawnPosition.getX() + 0.5D - self.posX;
            double dy = this.spawnPosition.getY() + 0.1D - self.posY;
            double dz = this.spawnPosition.getZ() + 0.5D - self.posZ;
            self.motionX = self.motionX + (Math.signum(dx) * 0.5D - self.motionX) * 0.1F;
            self.motionY = self.motionY + (Math.signum(dy) * 0.7F - self.motionY) * 0.1F;
            self.motionZ = self.motionZ + (Math.signum(dz) * 0.5D - self.motionZ) * 0.1F;
            float yaw = (float) (MathHelper.atan2(self.motionZ, self.motionX) * 180.0D / (float) Math.PI) - 90.0F;
            float delta = MathHelper.wrapDegrees(yaw - self.rotationYaw);
            self.moveForward = 0.5F;
            self.rotationYaw += delta;
            if (this.rand.nextInt(100) == 0 && self.world.getBlockState(above).isNormalCube()) {
                self.setIsBatHanging(true);
            }
        }

        ci.cancel();
    }
}
