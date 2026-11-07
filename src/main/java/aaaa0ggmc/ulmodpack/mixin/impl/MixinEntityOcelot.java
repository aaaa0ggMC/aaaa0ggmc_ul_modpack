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
 * <p><b>SRG 名约定（2026-11-07 修）</b>：{@code remap = false} 时 refmap 不参与，而 reobfJar 只重映射
 * 「owner 为已映射 MC 类」的引用——{@code @Shadow} 声明及其在本 mixin 方法体里的引用 owner 是
 * 本 mixin 类，不在 MC 映射表内。因此 {@code @Shadow} 成员名与上述内部引用<b>必须</b>直接写 SRG 名
 * （查 {@code reference/mappings-stable39/srg-mcp.srg}），写 MCP 名会原样留在字节码里，运行时
 * 直接 {@code InvalidMixinException: @Shadow ... was not located}。方法体里对原版成员的引用仍由
 * reobfJar 重映射，无需手写 SRG。</p>
 * <p>本类映射：avoidEntity = {@code field_175545_bm}。</p>
 */
@Mixin(value = EntityOcelot.class, remap = false)
public abstract class MixinEntityOcelot {

    @Shadow
    private EntityAIAvoidEntity<EntityPlayer> field_175545_bm;

    @Inject(method = "func_175544_ck()V", at = @At("HEAD"), cancellable = true, remap = false)
    private void ulmodpack$avoidOnlyEnemies(CallbackInfo ci) {
        EntityOcelot self = (EntityOcelot) (Object) this;
        if (this.field_175545_bm == null) {
            this.field_175545_bm = new EntityAIAvoidEntity<>(
                    self,
                    EntityPlayer.class,
                    input -> HateQueries.isEnemy(self, input),
                    16.0F, 0.8, 1.33);
        }
        self.tasks.removeTask(this.field_175545_bm);
        if (!self.isTamed()) {
            self.tasks.addTask(4, this.field_175545_bm);
        }
        ci.cancel();
    }
}
