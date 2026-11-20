package aaaa0ggmc.ulmodpack.breed;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.dna.DnaRecipe;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import javax.annotation.Nullable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 数据驱动的繁殖白名单与规则注册表。
 * 默认读取 {@code assets/aaaa0ggmc_ul_modpack/breeding_rules.json}，
 * 若存在 {@code config/aaaa0ggmc_ul_modpack/breeding_rules.json} 则覆盖对应目标实体规则或追加。
 */
public final class BreedingRuleRegistry {

    private static final String BUNDLED_PATH = "/assets/" + UlModpack.MODID + "/breeding_rules.json";
    private static final Map<String, List<BreedingRule>> RULES_BY_TARGET = new LinkedHashMap<>();

    private BreedingRuleRegistry() {
    }

    public static void load(@Nullable File configDir) {
        RULES_BY_TARGET.clear();
        try (InputStream in = BreedingRuleRegistry.class.getResourceAsStream(BUNDLED_PATH)) {
            if (in != null) {
                parseStream(in, false);
            } else {
                UlModpack.LOGGER.warn("[breed] Missing bundled {}", BUNDLED_PATH);
            }
        } catch (Exception e) {
            UlModpack.LOGGER.error("[breed] Failed loading bundled breeding_rules.json", e);
        }

        if (configDir != null) {
            File override = new File(new File(configDir, UlModpack.MODID), "breeding_rules.json");
            if (override.isFile()) {
                try (InputStream in = new FileInputStream(override)) {
                    parseStream(in, true);
                    UlModpack.LOGGER.info("[breed] Loaded config override {}", override.getPath());
                } catch (Exception e) {
                    UlModpack.LOGGER.error("[breed] Failed loading override {}", override.getPath(), e);
                }
            }
        }
        UlModpack.LOGGER.info("[breed] Loaded breeding rules for {} target entities.", RULES_BY_TARGET.size());
    }

    private static void parseStream(InputStream in, boolean replaceTargetLists) {
        JsonObject root = new JsonParser()
                .parse(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject();
        JsonArray arr = root.getAsJsonArray("rules");
        if (arr == null) {
            return;
        }
        Map<String, List<BreedingRule>> incoming = new LinkedHashMap<>();
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject obj = el.getAsJsonObject();
            String target = normalize(obj.get("target_entity").getAsString());
            String morph = obj.has("required_morph") ? normalize(obj.get("required_morph").getAsString()) : target;
            boolean requireAdult = !obj.has("require_adult") || obj.get("require_adult").getAsBoolean();
            List<String> playerVariants = parseStringList(obj.get("required_player_variants"));
            Map<String, Object> playerNbt = parseSimpleMap(obj.getAsJsonObject("required_player_nbt"));
            Map<String, Object> targetNbt = parseSimpleMap(obj.getAsJsonObject("required_target_nbt"));

            List<DnaRecipe.ItemRequirement> items = new ArrayList<>();
            if (obj.has("items") && obj.get("items").isJsonArray()) {
                for (JsonElement itemEl : obj.getAsJsonArray("items")) {
                    items.add(parseReq(itemEl.getAsJsonObject()));
                }
            } else if (obj.has("item") && obj.get("item").isJsonObject()) {
                items.add(parseReq(obj.getAsJsonObject("item")));
            }

            String offspring = obj.has("offspring_entity")
                    ? normalize(obj.get("offspring_entity").getAsString()) : target;
            boolean offspringBaby = !obj.has("offspring_baby") || obj.get("offspring_baby").getAsBoolean();
            List<String> inheritKeys = parseStringList(obj.get("inherit_nbt_keys"));
            int cooldownTicks = obj.has("cooldown_ticks") ? obj.get("cooldown_ticks").getAsInt() : 1200;
            int xp = obj.has("xp") ? obj.get("xp").getAsInt() : 3;

            BreedingRule rule = new BreedingRule(
                    target, morph, requireAdult, playerVariants, playerNbt, targetNbt,
                    items, offspring, offspringBaby, inheritKeys, cooldownTicks, xp);
            incoming.computeIfAbsent(target, k -> new ArrayList<>()).add(rule);
        }

        if (replaceTargetLists) {
            RULES_BY_TARGET.putAll(incoming);
        } else {
            for (Map.Entry<String, List<BreedingRule>> e : incoming.entrySet()) {
                RULES_BY_TARGET.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(e.getValue());
            }
        }
    }

    private static DnaRecipe.ItemRequirement parseReq(JsonObject o) {
        String item = o.get("item").getAsString();
        int meta = o.has("meta") ? o.get("meta").getAsInt() : 0;
        int count = o.has("count") ? o.get("count").getAsInt() : 1;
        return new DnaRecipe.ItemRequirement(item, meta, count);
    }

    private static List<String> parseStringList(@Nullable JsonElement el) {
        if (el == null || !el.isJsonArray()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        for (JsonElement item : el.getAsJsonArray()) {
            if (item.isJsonPrimitive()) {
                out.add(item.getAsString());
            }
        }
        return out;
    }

    private static Map<String, Object> parseSimpleMap(@Nullable JsonObject obj) {
        if (obj == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
            if (!e.getValue().isJsonPrimitive()) {
                continue;
            }
            JsonPrimitive p = e.getValue().getAsJsonPrimitive();
            if (p.isBoolean()) {
                map.put(e.getKey(), p.getAsBoolean());
            } else if (p.isNumber()) {
                map.put(e.getKey(), p.getAsInt());
            } else {
                map.put(e.getKey(), p.getAsString());
            }
        }
        return map;
    }

    public static List<BreedingRule> getRules(@Nullable String entityId) {
        if (entityId == null) {
            return Collections.emptyList();
        }
        List<BreedingRule> rules = RULES_BY_TARGET.get(normalize(entityId));
        return rules == null ? Collections.emptyList() : Collections.unmodifiableList(rules);
    }

    public static boolean isWhitelisted(@Nullable String entityId) {
        return !getRules(entityId).isEmpty();
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
