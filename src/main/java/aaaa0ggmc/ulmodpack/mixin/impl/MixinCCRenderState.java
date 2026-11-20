package aaaa0ggmc.ulmodpack.mixin.impl;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cleanroom + Celeritas 区块渲染线程传入的 {@code BufferBuilder.getVertexFormat()} 可能返回 {@code null}，
 * 导致 CodeChickenLib {@code CCRenderState.bind(BufferBuilder)} 后 {@code fmt == null}，
 * 渲染 ThermalDynamics 管道时在 {@code CCRenderPipeline.rebuild} 抛出 NPE 并渲染为空气。
 * 在 {@code bind} 返回前若 {@code fmt == null}，兜底回填 {@link DefaultVertexFormats#BLOCK}。
 */
@Mixin(targets = "codechicken.lib.render.CCRenderState", remap = false)
public abstract class MixinCCRenderState {

    @Shadow(remap = false)
    public VertexFormat fmt;

    @Inject(method = "bind", at = @At("RETURN"), remap = false)
    private void onBindReturn(BufferBuilder r, CallbackInfo ci) {
        if (this.fmt == null) {
            this.fmt = DefaultVertexFormats.BLOCK;
        }
    }
}
