package aaaa0ggmc.ulmodpack.race;

import aaaa0ggmc.morphext.ability.PlayerAbilityData;
import aaaa0ggmc.morphext.ability.PlayerAbilityStore;
import aaaa0ggmc.morphext.form.FormCategories;
import aaaa0ggmc.morphext.network.AbilityNetwork;
import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.config.AnkhConfig;
import com.google.gson.JsonObject;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * 玩家种族判定（通用、可扩展、纯数据驱动）。
 *
 * 种族 ≠ 形态（见 docs/worldwiki/安卡护符.md 与实现契约的 ul.race）：
 * 形态是「当前长什么样」，种族是「你是哪一族」。判定顺序（先命中先用）：
 *   1. 形态映射：已变身时按 ankh.json 的 {@code race_map} 查当前形态——
 *      先形态 id 精确匹配，再按该形态所属类别（morphext FormCategories）匹配；
 *   2. 显式种族记录：玩家数据 {@code ul.race}（morphext states 里的
 *      {@code aaaa0ggmc_ul_modpack:race} 条目），由内容系统写入——
 *      例如基因转化的「无形态种族变更」（村民种族当前的唯一入口，来源未实现），
 *      调试用 {@code /ulpack race set|clear}；
 *   3. 兜底：未变身 → ankh.json 的 {@code default_race}（默认 human）；
 *      已变身但映射不到 → {@code unmapped_form_race}（默认 unknown）。
 *
 * 感染完成后按结果形态回写显式记录（感染 = 种族变更）；无形态记录不影响
 * 形态映射优先级，任何后续「更多种族」只需往 race_map 加条目或写显式记录。
 */
public final class RaceResolver {
    /** 玩家数据里的显式种族记录状态 id（设计文档的 {@code ul.race}）。 */
    public static final String STATE_ID = UlModpack.MODID + ":race";

    private RaceResolver() {
    }

    /** 种族 id 归一：去空白、小写；空串返回 null。 */
    public static String normalizeRace(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return s.isEmpty() ? null : s;
    }

    /** 当前种族（自动取当前形态）。 */
    public static String resolveRace(EntityPlayerMP player) {
        return resolveRace(player, currentFormId(player));
    }

    /**
     * 当前种族。
     *
     * @param formId 玩家当前形态 id（未变身传 null；传 null 时按未变身处理）
     */
    public static String resolveRace(EntityPlayerMP player, String formId) {
        String form = FormCategories.normalize(formId);
        if (form != null) {
            String mapped = raceForForm(form);
            if (mapped != null) {
                return mapped;
            }
        }
        String explicit = explicitRace(player);
        if (explicit != null) {
            return explicit;
        }
        return form != null ? AnkhConfig.unmappedFormRace() : AnkhConfig.defaultRace();
    }

    /** 形态 → 种族映射查询（先形态 id，再形态所属类别；查不到返回 null）。 */
    public static String raceForForm(String formId) {
        String form = FormCategories.normalize(formId);
        if (form == null) {
            return null;
        }
        Map<String, String> map = AnkhConfig.raceMap();
        String race = map.get(form);
        if (race != null) {
            return race;
        }
        for (String category : new TreeSet<>(FormCategories.categoriesOf(form))) {
            race = map.get(category);
            if (race != null) {
                return race;
            }
        }
        return null;
    }

    /** 显式种族记录（ul.race）；未记录返回 null。 */
    public static String explicitRace(EntityPlayerMP player) {
        try {
            JsonObject state = PlayerAbilityStore.getLoadedState(player, STATE_ID);
            if (state.has("race") && state.get("race").isJsonPrimitive()) {
                return normalizeRace(state.get("race").getAsString());
            }
        } catch (Throwable t) {
            UlModpack.LOGGER.warn("[ul-modpack] Cannot read ul.race for {}: {}", player.getName(), t.toString());
        }
        return null;
    }

    /** 写显式种族记录（ul.race）。种族 id 必须非空。 */
    public static void setRace(EntityPlayerMP player, String race) {
        String normalized = normalizeRace(race);
        if (normalized == null) {
            throw new IllegalArgumentException("Race id must not be empty");
        }
        JsonObject state = new JsonObject();
        state.addProperty("race", normalized);
        state.addProperty("tick", PlayerAbilityStore.now(player));
        PlayerAbilityStore.update(player, (PlayerAbilityData data) -> data.putState(STATE_ID, state));
        AbilityNetwork.syncPlayer(player, "");
    }

    /** 清除显式种族记录（回到「按形态/默认推导」）。 */
    public static void clearRace(EntityPlayerMP player) {
        PlayerAbilityStore.update(player, (PlayerAbilityData data) -> data.wipe(STATE_ID));
        AbilityNetwork.syncPlayer(player, "");
    }

    /** 玩家当前形态 id（未变身 / 取不到能力时返回 null）。 */
    public static String currentFormId(EntityPlayerMP player) {
        try {
            IMorphing morphing = Morphing.get(player);
            if (morphing != null && morphing.isMorphed() && morphing.getCurrentMorph() != null) {
                return FormCategories.normalize(morphing.getCurrentMorph().name);
            }
        } catch (Throwable ignored) {
            // 能力缺失按未变身处理
        }
        return null;
    }
}
