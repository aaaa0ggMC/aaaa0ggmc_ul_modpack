package aaaa0ggmc.ulmodpack.achievement;

import aaaa0ggmc.morphext.ability.PlayerAbilityData;
import aaaa0ggmc.morphext.ability.PlayerAbilityStore;
import aaaa0ggmc.ulmodpack.UlModpack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 成就授予/查询 API（ul-modpack 内容层，可扩展）。
 *
 * <p><b>实现 = 1.12.2 原版 advancement</b>（2026-11-07 替换旧自定义 toast workaround）：
 * 定义在资源 JSON {@code assets/aaaa0ggmc_ul_modpack/advancements/<name>.json}
 * （Forge {@code AdvancementManager} 只扫这个路径，见 ForgeHooks.loadAdvancements），
 * 授予走 {@code PlayerAdvancements#grantCriterion}，持久化/同步/界面/toast 全部由原版负责。
 * 弹窗与聊天公告由 JSON 的 {@code display.show_toast / announce_to_chat} 决定，
 * 不再有本模组自己的 toast 渲染与音效代码。</p>
 *
 * <p>触发方式：自定义 {@code ICriterionTrigger}（{@link CryingWolfTrigger} /
 * {@code aaaa0ggmc_ul_modpack:crying_wolf}，{@code minecraft:impossible} + 直接
 * grantCriterion 是等价备选）在 preInit 注册到 {@code CriteriaTriggers}，条件达成时
 * {@link #unlock} 授予。</p>
 *
 * <p>能力面：{@link #register} 注册（{@link Achievement}）、{@link #unlock} 幂等授予、
 * {@link #isUnlocked} / {@link #unlockedCount} / {@link #get} / {@link #all} 查询
 * （后续 FTBQ 任务可直接读）。</p>
 *
 * <p>旧存档兼容：{@link #migrateLegacyState} 把自定义 toast 时代写在 morphext 玩家数据
 * {@code states["ul:achievements"]} 的解锁表一次性转成 advancement 授予并清键，
 * 由 {@link AchievementMigration} 在玩家登录时调用（幂等，见该类注释）。</p>
 *
 * <p>首个成就 {@link #CRYING_WOLF}（狼来了 / Crying Wolf，典出《狼来了》）：
 * 玩家执行一次失败的尸潮发动（失败宣告流程）即解锁，触发点见
 * {@link aaaa0ggmc.ulmodpack.zombie.horde.HordeManager#launchOrDisband}。</p>
 */
public final class Achievements {

    /** 「狼来了」：典出 The Boy Who Cried Wolf（谎报军情）。 */
    public static final String CRYING_WOLF = "crying_wolf";

    /** 旧自定义 toast 成就的玩家数据状态键（迁移后整键清除，勿复用）。 */
    public static final String LEGACY_STATE_KEY = "ul:achievements";

    private static final Map<String, Achievement> REGISTRY = new LinkedHashMap<>();

    private Achievements() {
    }

    /** 注册内置成就（幂等；init 时调用一次）。 */
    public static void registerDefaults() {
        register(new Achievement(CRYING_WOLF,
                new ResourceLocation(UlModpack.MODID, "crying_wolf"),
                CryingWolfTrigger.CRITERION));
    }

    /** 注册成就（id 唯一；重复注册同 id 覆盖）。 */
    public static void register(Achievement achievement) {
        REGISTRY.put(achievement.id, achievement);
    }

    /** 按 id 查询成就定义。 */
    public static Achievement get(String id) {
        return REGISTRY.get(id);
    }

    /** 全部成就定义（只读）。 */
    public static Collection<Achievement> all() {
        return Collections.unmodifiableCollection(REGISTRY.values());
    }

    /**
     * 授予（幂等）：首次授予走原版 {@code grantCriterion}（toast/聊天由原版按 JSON
     * display 决定）；已授予或资源缺失返回 false。
     *
     * @return true = 本次新授予
     */
    public static boolean unlock(EntityPlayerMP player, String id) {
        if (player == null) {
            return false;
        }
        Achievement achievement = REGISTRY.get(id);
        if (achievement == null) {
            return false;
        }
        Advancement advancement = lookup(player, achievement.advancementId);
        if (advancement == null) {
            UlModpack.LOGGER.warn("[ul-modpack] advancement {} not loaded; achievement {} not granted",
                    achievement.advancementId, id);
            return false;
        }
        return player.getAdvancements().grantCriterion(advancement, achievement.criterion);
    }

    /** 查询：该玩家是否已解锁（读原版 advancement 进度，重启/跨维度保留）。 */
    public static boolean isUnlocked(EntityPlayerMP player, String id) {
        if (player == null) {
            return false;
        }
        Achievement achievement = REGISTRY.get(id);
        if (achievement == null) {
            return false;
        }
        Advancement advancement = lookup(player, achievement.advancementId);
        if (advancement == null) {
            return false;
        }
        AdvancementProgress progress = player.getAdvancements().getProgress(advancement);
        return progress != null && progress.isDone();
    }

    /** 查询：已解锁数量（后续 FTBQ 任务可读）。 */
    public static int unlockedCount(EntityPlayerMP player) {
        int count = 0;
        for (String id : REGISTRY.keySet()) {
            if (isUnlocked(player, id)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 旧存档一次性迁移：morphext 玩家数据 {@code states["ul:achievements"]}
     * （自定义 toast 时代的 {@code {"unlocked":{"<id>":worldTick}}}）→ 原版 advancement
     * 授予，然后 {@link PlayerAbilityData#wipe} 整键清除。
     *
     * <p>幂等：无旧标记（键缺失 / 无 {@code unlocked} 对象）时只读不写、直接返回 0；
     * 有标记时逐 id 授予（{@link #unlock} 本身幂等）后清键，重登不会二次迁移。
     * 授予发生在登录事件里、早于首个 advancement 同步包，原版按 first sync 处理，
     * 老玩家不会补弹 toast。</p>
     *
     * @return 本次新授予的成就数（0 = 无旧数据或全部早已达成）
     */
    public static int migrateLegacyState(EntityPlayerMP player) {
        if (player == null) {
            return 0;
        }
        JsonObject state = PlayerAbilityStore.get(player).getState(LEGACY_STATE_KEY);
        if (state == null || !state.has("unlocked") || !state.get("unlocked").isJsonObject()) {
            return 0;
        }
        JsonObject unlocked = state.getAsJsonObject("unlocked");
        int migrated = 0;
        for (Map.Entry<String, JsonElement> entry : unlocked.entrySet()) {
            if (unlock(player, entry.getKey())) {
                migrated++;
            }
        }
        PlayerAbilityStore.update(player, data -> data.wipe(LEGACY_STATE_KEY));
        UlModpack.LOGGER.info("[ul-modpack] Migrated {} legacy achievement(s) for {} to vanilla advancements",
                migrated, player.getName());
        return migrated;
    }

    /** 取原版 Advancement 对象（服务端 AdvancementManager；未加载返回 null）。 */
    private static Advancement lookup(EntityPlayerMP player, ResourceLocation id) {
        if (player.getServer() == null) {
            return null;
        }
        return player.getServer().getAdvancementManager().getAdvancement(id);
    }
}
