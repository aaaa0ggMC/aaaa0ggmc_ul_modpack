package aaaa0ggmc.ulmodpack.race;

import aaaa0ggmc.morphext.conversion.MorphFactory;
import aaaa0ggmc.morphext.integration.metamorph.MorphTransitionKind;
import aaaa0ggmc.morphext.integration.metamorph.MorphTransitionService;
import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import aaaa0ggmc.ulmodpack.zombie.ZombieForms;
import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import javax.annotation.Nullable;

/**
 * 种族变化统一管理器（Race Transition Manager）。
 *
 * <p>集中收口整合包侧的感染、进化、临时形态覆盖（如泰坦王冠）与种族回撤（如安眠药）：</p>
 * <ul>
 *   <li>统一强制变身通道（{@code force = true}，绕过生存模式自主变形限制）；</li>
 *   <li>统一临时形态栈（{@code TEMPORARY_PUSH / TEMPORARY_POP}）与打断清理语义
 *       （{@code interruptTemporaryState}），避免各系统互相硬编码清理对方状态；</li>
 *   <li>统一血量策略（按比例保留 / 1 点残血 / 回满）。</li>
 * </ul>
 */
public final class RaceManager {

    private static final String NBT_TEMP_PREV_FORM = "ul_race_temp_prev_form";
    private static final String LEGACY_TITAN_PREV_FORM = "ul_titan_prev_form";

    public enum TransitionReason {
        /** 感染转化（如安卡护符死亡转化）。 */
        INFECTION,
        /** 永久进化 / 仪式蜕变。 */
        EVOLVE,
        /** 临时形态压栈（如佩戴泰坦王冠）。 */
        TEMPORARY_PUSH,
        /** 临时形态退栈（如摘下泰坦王冠）。 */
        TEMPORARY_POP,
        /** 完全回撤为玩家原本种族（如安眠药）。 */
        REVERT_HUMAN,
        /** 管理员命令 / 调试强制变身：不压栈、不打断临时状态（决策表与 VARIANT_APPLY / AGE_SET 相同）。 */
        ADMIN,
        /** 仅应用形态变体（颜色 / 花纹等），不改种族进程。 */
        VARIANT_APPLY,
        /** 仅设置年龄段（幼年 / 成年），不改种族进程。 */
        AGE_SET
    }

    public enum HealthPolicy {
        /** 按原最大生命比例缩放（至少保留 1.0F）。 */
        PRESERVE_RATIO,
        /** 强制设为 1.0F（感染/仪式防赖血）。 */
        SET_ONE,
        /** 变身后回满生命。 */
        FULL_HEAL
    }

    private RaceManager() {
    }

    /** 按形态 ID 切换（无额外 EntityData；{@code extraEntityData = null} 的薄转发）。 */
    public static boolean transitionToForm(EntityPlayerMP player, String formId,
                                           TransitionReason reason, HealthPolicy healthPolicy) {
        return transitionToForm(player, formId, null, reason, healthPolicy);
    }

    /**
     * 按形态 ID 切换，并可选合并额外 {@code EntityData}（如 {@code {Color:4b}} / {@code {Sheared:1b}}）。
     *
     * <p>这是 ul 包内「带变体数据变身」的唯一入口；{@code extraEntityData} 由
     * {@link MorphFactory#buildFromFormId(String, net.minecraft.world.World, NBTTagCompound)}
     * 合并进实体默认 NBT（同名键后写覆盖）。</p>
     */
    public static boolean transitionToForm(EntityPlayerMP player, String formId,
                                           @Nullable NBTTagCompound extraEntityData,
                                           TransitionReason reason, HealthPolicy healthPolicy) {
        if (player == null || formId == null || formId.isEmpty()) {
            return false;
        }
        AbstractMorph morph = MorphFactory.buildFromFormId(formId, player.world, extraEntityData);
        if (morph == null) {
            UlModpack.LOGGER.warn("[RaceManager] Failed to build morph '{}' for {} (reason={})",
                    formId, player.getName(), reason);
            return false;
        }
        return transition(player, morph, reason, healthPolicy);
    }

    /** 统一形态切换入口（{@code targetMorph == null} 表示退回玩家原本种族）。 */
    public static boolean transition(EntityPlayerMP player, @Nullable AbstractMorph targetMorph,
                                     TransitionReason reason, HealthPolicy healthPolicy) {
        if (player == null) {
            return false;
        }
        String currentForm = ZombieForms.currentFormId(player);
        String targetForm = targetMorph != null && targetMorph.name != null ? targetMorph.name : "";

        if (pushesStack(reason)) {
            if (!currentForm.isEmpty() && !currentForm.equals(targetForm)) {
                setTempPrevForm(player, currentForm);
            }
        } else if (interruptsTemporary(reason)) {
            interruptTemporaryState(player);
        }

        float healthPct = player.getMaxHealth() > 0.0F
                ? Math.max(0.0F, Math.min(1.0F, player.getHealth() / player.getMaxHealth()))
                : 1.0F;

        if (targetMorph != null) {
            MorphAPI.acquire(player, targetMorph);
            MorphAPI.morph(player, targetMorph, true);
        } else {
            MorphAPI.morph(player, null, true);
        }

        applyHealthPolicy(player, healthPct, healthPolicy);
        UlModpack.LOGGER.debug("[RaceManager] {} transitioned '{}' -> '{}' (reason={}, health={})",
                player.getName(), currentForm, targetForm, reason, healthPolicy);
        return true;
    }

    /** 退出临时形态并恢复先前压栈的形态；无记录时使用 {@code fallbackFormId}。 */
    public static boolean popTemporary(EntityPlayerMP player, String fallbackFormId, HealthPolicy healthPolicy) {
        if (player == null) {
            return false;
        }
        String prev = getTempPrevForm(player);
        String current = ZombieForms.currentFormId(player);
        if (prev.isEmpty() || prev.equals(current)) {
            prev = fallbackFormId;
        }
        clearTempPrevForm(player);
        return transitionToForm(player, prev, TransitionReason.TEMPORARY_POP, healthPolicy);
    }

    /** 一键打断并清理当前所有临时形态栈与回撤引导状态（安眠药等）。 */
    public static void interruptTemporaryState(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        clearTempPrevForm(player);
        if (player.isPotionActive(CommonProxy.POTION_DEEP_SLEEP)) {
            player.removePotionEffect(CommonProxy.POTION_DEEP_SLEEP);
        }
        aaaa0ggmc.ulmodpack.dna.DnaRecombineHandler.cancelPending(player);
    }

    public static boolean hasTemporaryForm(EntityPlayerMP player) {
        return player != null && !getTempPrevForm(player).isEmpty();
    }

    /**
     * 返回核心形态写入 SPI 的观察者实现（由 {@link UlModpackMorphextPlugin} 在
     * morphext PreInit 经 {@code MorphextPluginContext} 注册，docs/dna-recombinator-design.md §2.3 / §7 A4）。
     *
     * <p>依赖方向仍是「内容层 → 核心」：核心只提供 SPI 与回调，不反向依赖 ul 包。
     * 注册后，核心侧（{@code RuleExecutor} / {@code MetamorphServerAbilities}）发生的任何
     * 形态写入都会回调该观察者，由本类决定整合包语义。</p>
     */
    public static MorphTransitionService.Listener coreListener() {
        return LISTENER;
    }

    private static final MorphTransitionService.Listener LISTENER =
            new MorphTransitionService.Listener() {
                @Override
                public void onExternalTransition(EntityPlayerMP player,
                                                 @Nullable AbstractMorph morph,
                                                 MorphTransitionKind kind) {
                    // 决策表：核心 CONVERSION / ABILITY_GRANT 写入都对应"形态已被别处改掉"，
                    // 与 INFECTION / EVOLVE / REVERT_HUMAN 同列 —— 打断临时状态
                    // （清临时形态栈 / 清安眠药效 / 清 DNA pending）。
                    // 具体行为见 docs/dna-recombinator-design.md §2.3 行为差异清单。
                    String target = morph != null && morph.name != null ? morph.name : "<none>";
                    UlModpack.LOGGER.info("[RaceManager] external transition observed: player={} form={} kind={}",
                            player.getName(), target, kind);
                    interruptTemporaryState(player);
                }
            };

    /**
     * 决策表：该 reason 是否把当前形态压进 {@code ul_race_temp_prev_form}。
     * 纯函数——新增枚举值不会静默落进别的分支（docs/dna-recombinator-design.md §3.2）。
     */
    public static boolean pushesStack(TransitionReason reason) {
        return reason == TransitionReason.TEMPORARY_PUSH;
    }

    /**
     * 决策表：该 reason 是否调 {@link #interruptTemporaryState}（连带清安眠药效与 DNA pending）。
     * 纯函数，与 {@link #pushesStack} 互斥（压栈语义不打断自身）。
     */
    public static boolean interruptsTemporary(TransitionReason reason) {
        return reason == TransitionReason.INFECTION || reason == TransitionReason.EVOLVE
                || reason == TransitionReason.REVERT_HUMAN;
    }

    private static void applyHealthPolicy(EntityPlayerMP player, float prevRatio, HealthPolicy policy) {
        if (policy == HealthPolicy.SET_ONE) {
            player.setHealth(1.0F);
        } else if (policy == HealthPolicy.FULL_HEAL) {
            player.setHealth(player.getMaxHealth());
        } else {
            player.setHealth(Math.max(1.0F, prevRatio * player.getMaxHealth()));
        }
    }

    private static NBTTagCompound getPersisted(EntityPlayerMP player) {
        NBTTagCompound data = player.getEntityData();
        if (!data.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) {
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private static void setTempPrevForm(EntityPlayerMP player, String formId) {
        getPersisted(player).setString(NBT_TEMP_PREV_FORM, formId);
    }

    private static String getTempPrevForm(EntityPlayerMP player) {
        NBTTagCompound persisted = getPersisted(player);
        String form = persisted.getString(NBT_TEMP_PREV_FORM);
        if (form.isEmpty()) {
            form = persisted.getString(LEGACY_TITAN_PREV_FORM);
        }
        return form;
    }

    private static void clearTempPrevForm(EntityPlayerMP player) {
        NBTTagCompound persisted = getPersisted(player);
        persisted.removeTag(NBT_TEMP_PREV_FORM);
        persisted.removeTag(LEGACY_TITAN_PREV_FORM);
    }
}
