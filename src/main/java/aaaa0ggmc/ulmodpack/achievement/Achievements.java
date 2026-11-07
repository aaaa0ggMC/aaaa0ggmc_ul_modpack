package aaaa0ggmc.ulmodpack.achievement;

import aaaa0ggmc.morphext.ability.PlayerAbilityData;
import aaaa0ggmc.morphext.ability.PlayerAbilityStore;
import aaaa0ggmc.ulmodpack.UlModpack;
import com.google.gson.JsonObject;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 最小可用成就框架（ul-modpack 内容层，可扩展）。
 *
 * <p>能力面：{@link #register} 注册 id/标题/描述（{@link Achievement}），
 * {@link #unlock} 幂等解锁（toast 提示 + 音效），{@link #isUnlocked} /
 * {@link #unlockedCount} / {@link #get} / {@link #all} 为查询接口（后续 FTBQ
 * 任务可直接读）。</p>
 *
 * <p>持久化：morphext 玩家数据 states["ul:achievements"]
 * {@code {"unlocked":{"<id>":<worldTick>}}}，按玩家 UUID 记录、重启保留、跨维度可见。</p>
 *
 * <p>首个成就 {@link #CRYING_WOLF}（狼来了 / Crying Wolf，典出《狼来了》）：
 * 玩家执行一次失败的尸潮发动（失败宣告流程）即解锁。</p>
 */
public final class Achievements {

    /** 「狼来了」：典出 The Boy Who Cried Wolf（谎报军情）。 */
    public static final String CRYING_WOLF = "crying_wolf";

    /** 玩家数据状态键（成就解锁表）。 */
    private static final String STATE_KEY = "ul:achievements";

    private static final Map<String, Achievement> REGISTRY = new LinkedHashMap<>();

    private Achievements() {
    }

    /** 注册内置成就（幂等；init 时调用一次）。 */
    public static void registerDefaults() {
        register(new Achievement(CRYING_WOLF,
                "aaaa0ggmc_ul_modpack.achievement.crying_wolf.name",
                "aaaa0ggmc_ul_modpack.achievement.crying_wolf.desc"));
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
     * 解锁（幂等）：首次解锁写入持久状态并 toast 提示 + 音效；
     * 已解锁直接返回 false。
     */
    public static boolean unlock(EntityPlayerMP player, String id) {
        if (player == null || !REGISTRY.containsKey(id)) {
            return false;
        }
        JsonObject state = readState(player);
        JsonObject unlocked = state.has("unlocked") && state.get("unlocked").isJsonObject()
                ? state.getAsJsonObject("unlocked") : new JsonObject();
        if (unlocked.has(id)) {
            return false;
        }
        unlocked.addProperty(id, player.world.getTotalWorldTime());
        state.add("unlocked", unlocked);
        writeState(player, state);
        toast(player, REGISTRY.get(id));
        return true;
    }

    /** 查询：该玩家是否已解锁（后续 FTBQ 任务可读）。 */
    public static boolean isUnlocked(EntityPlayerMP player, String id) {
        if (player == null) {
            return false;
        }
        JsonObject state = readState(player);
        return state.has("unlocked") && state.get("unlocked").isJsonObject()
                && state.getAsJsonObject("unlocked").has(id);
    }

    /** 查询：已解锁数量（后续 FTBQ 任务可读）。 */
    public static int unlockedCount(EntityPlayerMP player) {
        if (player == null) {
            return 0;
        }
        JsonObject state = readState(player);
        return state.has("unlocked") && state.get("unlocked").isJsonObject()
                ? state.getAsJsonObject("unlocked").size() : 0;
    }

    /** 解锁提示（chat 常驻 + toast 音效）。 */
    private static void toast(EntityPlayerMP player, Achievement achievement) {
        try {
            player.sendMessage(new TextComponentTranslation("aaaa0ggmc_ul_modpack.achievement.unlock",
                    new TextComponentTranslation(achievement.nameKey)));
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0F, 1.0F);
        } catch (Throwable t) {
            UlModpack.LOGGER.warn("[ul-modpack] achievement toast failed: {}", t.toString());
        }
    }

    private static JsonObject readState(EntityPlayerMP player) {
        JsonObject state = PlayerAbilityStore.get(player).getState(STATE_KEY);
        return state == null ? new JsonObject() : state;
    }

    private static void writeState(EntityPlayerMP player, JsonObject state) {
        PlayerAbilityStore.update(player, (PlayerAbilityData data) -> data.putState(STATE_KEY, state));
    }
}
