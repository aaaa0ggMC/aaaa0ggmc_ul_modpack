package aaaa0ggmc.ulmodpack.mixin.impl;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * 僵尸猪人：仇恨记忆恢复泛型化（仇恨联动）。
 *
 * <p>原判定：{@code updateAITasks()}（SRG {@code func_70619_bc}）与
 * {@code readEntityFromNBT()}（SRG {@code func_70037_a}）在恢复 angerLevel 仇恨记忆时
 * 用 {@code world.getPlayerEntityByUUID(uuid)}——解析只认玩家，被非玩家攻击者
 * （或玩家变身 dummy 归属）打出的仇恨在 reload/丢失 revenge 后永远找不回。</p>
 *
 * <p>新判定：{@code @Inject HEAD} 在两方法入口补非玩家恢复（按 uuid 遍历
 * {@code loadedEntityList} 匹配后 {@code setRevengeTarget}），玩家路径保留原版
 * {@code getPlayerEntityByUUID} 不动。1.12.2 无 {@code World.getEntityByUuid}
 * （1.13+ API），故自行遍历。</p>
 *
 * <p><b>兼容性注记（2026-11-06 急修）</b>：原实现为 {@code @Redirect}（改
 * {@code getPlayerEntityByUUID} 调用）；Cleanroom 上 MixinExtras FactoryRedirectWrapper
 * 与新版 Mixin {@code Annotations} 结构不兼容必崩，故改写为 {@code @Inject}
 * （见 {@link MixinEntityCreeper} 注记）。愤怒索敌侧见
 * {@link MixinPigZombieAggressorTarget}。</p>
 * <p><b>SRG 名约定（2026-11-07 修）</b>：{@code remap = false} 时 refmap 不参与，而 reobfJar 只重映射
 * 「owner 为已映射 MC 类」的引用——{@code @Shadow} 声明及其在本 mixin 方法体里的引用 owner 是
 * 本 mixin 类，不在 MC 映射表内。因此 {@code @Shadow} 成员名与上述内部引用<b>必须</b>直接写 SRG 名
 * （查 {@code reference/mappings-stable39/srg-mcp.srg}），写 MCP 名会原样留在字节码里，运行时
 * 直接 {@code InvalidMixinException: @Shadow ... was not located}。方法体里对原版成员的引用仍由
 * reobfJar 重映射，无需手写 SRG。</p>
 * <p>本类映射：angerTargetUUID = {@code field_175459_bn}。</p>
 */
@Mixin(value = EntityPigZombie.class, remap = false)
public abstract class MixinEntityPigZombie {

    @Shadow
    private UUID field_175459_bn;

    @Inject(method = "func_70619_bc()V", at = @At("HEAD"), remap = false)
    private void ulmodpack$restoreNonPlayerRevengeTick(CallbackInfo ci) {
        ulmodpack$restoreNonPlayerRevenge();
    }

    @Inject(method = "func_70037_a(Lnet/minecraft/nbt/NBTTagCompound;)V", at = @At("HEAD"), remap = false)
    private void ulmodpack$restoreNonPlayerRevengeLoad(NBTTagCompound tag, CallbackInfo ci) {
        ulmodpack$restoreNonPlayerRevenge();
    }

    /**
     * 补非玩家仇恨恢复：angerLevel&gt;0、revenge 为空且 angerTargetUUID 指向一个
     * 已加载的非玩家生物时，直接恢复 revenge（setRevengeTarget 覆写会同步
     * angerTargetUUID）。玩家路径由原版 getPlayerEntityByUUID 处理，不在此重复。
     */
    private void ulmodpack$restoreNonPlayerRevenge() {
        EntityPigZombie self = (EntityPigZombie) (Object) this;
        if (this.field_175459_bn == null || !self.isAngry() || self.getRevengeTarget() != null
                || self.world == null) {
            return;
        }
        for (Object loaded : self.world.loadedEntityList) {
            Entity candidate = (Entity) loaded;
            if (candidate != null && this.field_175459_bn.equals(candidate.getUniqueID())) {
                if (candidate instanceof EntityLivingBase && !(candidate instanceof EntityPlayer)) {
                    self.setRevengeTarget((EntityLivingBase) candidate);
                }
                return;
            }
        }
    }
}
