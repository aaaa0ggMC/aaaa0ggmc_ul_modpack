package aaaa0ggmc.ulmodpack.mixin.impl;

import aaaa0ggmc.ulmodpack.faction.HateQueries;
import net.minecraft.entity.ai.EntityAIAvoidEntity;
import net.minecraft.entity.passive.EntityOcelot;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 豹猫：躲避玩家重定向（{@code setupTamedAI}，SRG {@code func_175544_ck}）。
 *
 * <p><b>判定规则（设计，§G6 躲避语义）</b>：野生豹猫的
 * {@code EntityAIAvoidEntity<>(this, EntityPlayer.class, 16.0F, 0.8, 1.33)}
 * 是「对玩家敏感」的逃跑——改为只躲「敌人玩家」（{@code HateQueries.isEnemy}）：
 * 同阵营变形玩家靠近不再吓跑豹猫，普通玩家照常。</p>
 *
 * <p>实现：{@code @Inject HEAD cancel} 复刻 setupTamedAI（10 行，remove/add 配对
 * 必须保持同一任务实例，不能用 @ModifyArg 换 addTask 参数——会被 removeTask
 * 移除不掉，故整段复刻）。兼容性注记见 {@link MixinEntityCreeper}。</p>
 */
@Mixin(value = EntityOcelot.class, remap = false)
public abstract class MixinEntityOcelot {

    @Shadow
    private EntityAIAvoidEntity<EntityPlayer> avoidEntity;

    @Inject(method = "func_175544_ck()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void ulmodpack$avoidOnlyEnemies(CallbackInfo ci) {
        EntityOcelot self = (EntityOcelot) (Object) this;
        if (this.avoidEntity == null) {
            this.avoidEntity = new EntityAIAvoidEntity<>(
                    self,
                    EntityPlayer.class,
                    input -> HateQueries.isEnemy(self, input),
                    16.0F, 0.8, 1.33);
        }
        self.tasks.removeTask(this.avoidEntity);
        if (!self.isTamed()) {
            self.tasks.addTask(4, this.avoidEntity);
        }
        ci.cancel();
    }
}
