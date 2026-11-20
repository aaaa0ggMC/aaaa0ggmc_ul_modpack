package aaaa0ggmc.ulmodpack.mixin.impl;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

/**
 * Cleanroom + Actinium 下，{@code CCBlockRendererDispatcher.func_175018_a} 在处理
 * {@code BlockRenderingRegistry.canHandle(renderType)} 方块（如 ThermalDynamics 管道）时
 * 不会调用 {@code parentDispatcher.func_175018_a}，从而绕过了 Actinium 注入在原版
 * {@code BlockRendererDispatcher} 上的 {@code beginVanillaCapture / endVanillaCapture}，
 * 导致 {@code ChunkBufferBuilder} 未进入绘制状态（{@code fmt == null}、缓冲区越界且顶点未提交到网格）。
 *
 * <p>本 Mixin 仅在检测到 Actinium {@code ChunkBufferBuilder} 且命中 CCL 自定义渲染分支时，
 * 补齐成对的 {@code actinium$beginVanillaCapture / actinium$endVanillaCapture} 调用；
 * Forge 端无 Actinium 时自动静默跳过。</p>
 */
@Mixin(targets = "codechicken.lib.render.block.CCBlockRendererDispatcher", remap = false)
public abstract class MixinCCBlockRendererDispatcher {

    private static final ThreadLocal<BufferBuilder> ACTIVE_CAPTURE = new ThreadLocal<>();

    private static boolean initialized;
    private static Class<?> chunkBufferClass;
    private static Method beginCaptureMethod;
    private static Method endCaptureMethod;
    private static Method cclCanHandleMethod;

    private static void ensureInit() {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            chunkBufferClass = Class.forName("com.dhj.actinium.render.terrain.compile.ChunkBufferBuilder");
            beginCaptureMethod = chunkBufferClass.getMethod(
                    "actinium$beginVanillaCapture", BufferBuilder.class, BlockPos.class, IBlockState.class);
            endCaptureMethod = chunkBufferClass.getMethod(
                    "actinium$endVanillaCapture", BufferBuilder.class);
            Class<?> regClass = Class.forName("codechicken.lib.render.block.BlockRenderingRegistry");
            cclCanHandleMethod = regClass.getMethod("canHandle", EnumBlockRenderType.class);
        } catch (Throwable ignored) {
            chunkBufferClass = null;
        }
    }

    @Inject(method = "func_175018_a", at = @At("HEAD"), remap = false)
    private void onRenderBlockHead(IBlockState state, BlockPos pos, IBlockAccess world, BufferBuilder buffer,
                                   CallbackInfoReturnable<Boolean> cir) {
        ensureInit();
        if (chunkBufferClass == null || !chunkBufferClass.isInstance(buffer) || ACTIVE_CAPTURE.get() != null) {
            return;
        }
        try {
            if (Boolean.TRUE.equals(cclCanHandleMethod.invoke(null, state.getRenderType()))) {
                ACTIVE_CAPTURE.set(buffer);
                beginCaptureMethod.invoke(buffer, buffer, pos, state);
            }
        } catch (Throwable ignored) {
            ACTIVE_CAPTURE.remove();
        }
    }

    @Inject(method = "func_175018_a", at = @At("RETURN"), remap = false)
    private void onRenderBlockReturn(IBlockState state, BlockPos pos, IBlockAccess world, BufferBuilder buffer,
                                     CallbackInfoReturnable<Boolean> cir) {
        if (ACTIVE_CAPTURE.get() == buffer) {
            ACTIVE_CAPTURE.remove();
            try {
                endCaptureMethod.invoke(buffer, buffer);
            } catch (Throwable ignored) {
            }
        }
    }
}
