package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.client.VanillaGlowingBridge;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 仅客户端：截断原版 {@code Entity.isGlowing()}，阻止实体进入原版 outline framebuffer
 * 与光影 {@code gbuffers_entities_glowing}（避免纯白块与里外翻），
 * 改为记录进屏幕空间边缘发光渲染队列。
 */
@Mixin(value = Entity.class, remap = false)
public abstract class MixinEntity {

    @Shadow
    public World field_70170_p;

    @Shadow
    protected abstract boolean func_70083_f(int flag);

    @Inject(method = "func_184202_aL", at = @At("HEAD"), cancellable = true, remap = false)
    private void ul_redirectClientGlowing(CallbackInfoReturnable<Boolean> cir) {
        if (this.field_70170_p != null && this.field_70170_p.isRemote) {
            if (this.func_70083_f(6)) {
                VanillaGlowingBridge.recordGlowing((Entity) (Object) this);
            }
            cir.setReturnValue(false);
        }
    }
}
