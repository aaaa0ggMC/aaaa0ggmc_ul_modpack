package aaaa0ggmc.ulmodpack.client;

import aaaa0ggmc.morphext.client.render.MorphTransitionRegistry;
import aaaa0ggmc.morphext.form.FormCategories;
import aaaa0ggmc.ulmodpack.zombie.ZombieLine;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 僵尸 ↔ 泰坦（{@code minecraft:giant}）形态切换的客户端过渡动画（通过
 * {@link MorphTransitionRegistry} DI 注入，不修改 morphext 核心）。
 *
 * <p>粒子均走 {@code player.world.spawnParticle}，自动遵守原版粒子设置。</p>
 */
@SideOnly(Side.CLIENT)
public final class TitanTransitionAnimators {

    private TitanTransitionAnimators() {
    }

    public static void register() {
        // 1. 不死者 → 泰坦：26 tick 巨化觉醒（蓄力膨胀 + 过冲震颤 + 血红螺旋粒子 + 冲击波）
        MorphTransitionRegistry.register(
                ctx -> ZombieLine.FORM_TITAN.equals(ctx.toForm)
                        && !ZombieLine.FORM_TITAN.equals(ctx.fromForm)
                        && FormCategories.has(ctx.fromForm, ZombieLine.CATEGORY_UNDEAD),
                RiseToTitanAnimator::new
        );

        // 2. 泰坦 → 不死者：18 tick 萎缩退化（收缩回弹 + 消散烟雾环）
        MorphTransitionRegistry.register(
                ctx -> ZombieLine.FORM_TITAN.equals(ctx.fromForm)
                        && !ZombieLine.FORM_TITAN.equals(ctx.toForm)
                        && FormCategories.has(ctx.toForm, ZombieLine.CATEGORY_UNDEAD),
                ShrinkFromTitanAnimator::new
        );
    }

    private static final class RiseToTitanAnimator implements MorphTransitionRegistry.ITransitionAnimator {
        @Override
        public int durationTicks() {
            return 26;
        }

        @Override
        public void applyGlTransform(EntityPlayer player, float progress, float partialTicks) {
            // 先从 0.22 倍急剧膨胀到 1.18 倍过冲，再回落至 1.0 倍
            float scale;
            if (progress < 0.72F) {
                float t = progress / 0.72F;
                float easeOut = 1.0F - (float) Math.pow(1.0F - t, 3.0D);
                scale = 0.22F + (1.18F - 0.22F) * easeOut;
            } else {
                float t = (progress - 0.72F) / 0.28F;
                float smooth = t * t * (3.0F - 2.0F * t);
                scale = 1.18F - 0.18F * smooth;
            }
            float shake = (float) Math.sin(progress * 32.0F) * (1.0F - progress) * 0.08F;
            GlStateManager.translate(shake, 0.0F, -shake * 0.7F);
            GlStateManager.scale(scale, scale, scale);
        }

        @Override
        public void onClientTick(EntityPlayer player, int tick, int durationTicks, float progress) {
            double h = Math.max(1.8D, player.height);
            double r = 0.8D + progress * 1.4D;
            // 双股血红与暗紫螺旋上升粒子
            for (int i = 0; i < 6; i++) {
                double angle = tick * 0.55D + i * (Math.PI / 3.0D);
                double px = player.posX + Math.cos(angle) * r;
                double pz = player.posZ + Math.sin(angle) * r;
                double py = player.posY + (progress * h * 0.85D) + (player.world.rand.nextDouble() - 0.5D) * 0.4D;
                player.world.spawnParticle(EnumParticleTypes.REDSTONE, px, py, pz, 0.85D, 0.08D, 0.08D);
                if (i % 2 == 0) {
                    player.world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, px, py, pz, 0.0D, 0.06D, 0.0D);
                }
            }
            // 峰值爆发冲击波
            if (tick == 18) {
                player.world.spawnParticle(EnumParticleTypes.EXPLOSION_LARGE,
                        player.posX, player.posY + h * 0.45D, player.posZ, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    private static final class ShrinkFromTitanAnimator implements MorphTransitionRegistry.ITransitionAnimator {
        @Override
        public int durationTicks() {
            return 18;
        }

        @Override
        public void applyGlTransform(EntityPlayer player, float progress, float partialTicks) {
            float easeOut = 1.0F - (float) Math.pow(1.0F - progress, 2.0D);
            float scale = 1.65F - 0.65F * easeOut;
            float tremor = (float) Math.sin(progress * 24.0F) * (1.0F - progress) * 0.05F;
            GlStateManager.translate(tremor, 0.0F, 0.0F);
            GlStateManager.scale(scale, scale, scale);
        }

        @Override
        public void onClientTick(EntityPlayer player, int tick, int durationTicks, float progress) {
            double r = Math.max(0.4D, 1.8D * (1.0D - progress));
            for (int i = 0; i < 4; i++) {
                double angle = tick * 0.7D + i * (Math.PI * 0.5D);
                double px = player.posX + Math.cos(angle) * r;
                double pz = player.posZ + Math.sin(angle) * r;
                double py = player.posY + 0.5D + player.world.rand.nextDouble() * 1.4D;
                player.world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, px, py, pz,
                        -Math.cos(angle) * 0.06D, 0.03D, -Math.sin(angle) * 0.06D);
                player.world.spawnParticle(EnumParticleTypes.REDSTONE, px, py, pz, 0.65D, 0.12D, 0.12D);
            }
        }
    }
}
