package aaaa0ggmc.ulmodpack.dna;

import aaaa0ggmc.ulmodpack.UlModpack;
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
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * DNA 重组白名单与配方注册表。
 * 默认读取 {@code assets/aaaa0ggmc_ul_modpack/dna_recipes.json}，若存在
 * {@code config/aaaa0ggmc_ul_modpack/dna_recipes.json} 则覆盖同名条目或追加新条目。
 */
public final class DnaRecipeRegistry {

    private static final String BUNDLED_PATH = "/assets/" + UlModpack.MODID + "/dna_recipes.json";
    private static final Map<String, DnaRecipe> RECIPES = new LinkedHashMap<>();

    private DnaRecipeRegistry() {
    }

    public static void load(@Nullable File configDir) {
        RECIPES.clear();
        try (InputStream in = DnaRecipeRegistry.class.getResourceAsStream(BUNDLED_PATH)) {
            if (in != null) {
                parseStream(in);
            } else {
                UlModpack.LOGGER.warn("[dna] Missing bundled {}", BUNDLED_PATH);
            }
        } catch (Exception e) {
            UlModpack.LOGGER.error("[dna] Failed loading bundled dna_recipes.json", e);
        }

        if (configDir != null) {
            File override = new File(new File(configDir, UlModpack.MODID), "dna_recipes.json");
            if (override.isFile()) {
                try (InputStream in = new FileInputStream(override)) {
                    parseStream(in);
                    UlModpack.LOGGER.info("[dna] Loaded config override {}", override.getPath());
                } catch (Exception e) {
                    UlModpack.LOGGER.error("[dna] Failed loading override {}", override.getPath(), e);
                }
            }
        }
        UlModpack.LOGGER.info("[dna] Loaded {} DNA recombination recipes.", RECIPES.size());
    }

    private static void parseStream(InputStream in) {
        JsonObject root = new JsonParser()
                .parse(new InputStreamReader(in, StandardCharsets.UTF_8))
                .getAsJsonObject();
        JsonArray arr = root.getAsJsonArray("recipes");
        if (arr == null) {
            return;
        }
        for (JsonElement el : arr) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonObject obj = el.getAsJsonObject();
            String entityId = normalize(obj.get("entity").getAsString());
            int energyFe = obj.has("energy_fe") ? obj.get("energy_fe").getAsInt() : 20000;
            int weaknessTicks = obj.has("weakness_ticks") ? obj.get("weakness_ticks").getAsInt() : 200;

            List<DnaRecipe.ItemRequirement> required = new ArrayList<>();
            if (obj.has("required") && obj.get("required").isJsonArray()) {
                for (JsonElement reqEl : obj.getAsJsonArray("required")) {
                    required.add(parseReq(reqEl.getAsJsonObject()));
                }
            }

            List<DnaRecipe.OptionalModifier> optional = new ArrayList<>();
            if (obj.has("optional") && obj.get("optional").isJsonArray()) {
                for (JsonElement optEl : obj.getAsJsonArray("optional")) {
                    JsonObject o = optEl.getAsJsonObject();
                    DnaRecipe.ItemRequirement req = parseReq(o);
                    Map<String, Object> whenMissing = parseSimpleMap(o.getAsJsonObject("when_missing"));
                    Map<String, Object> whenPresent = parseSimpleMap(o.getAsJsonObject("when_present"));
                    String copyMeta = o.has("copy_meta_to_byte") ? o.get("copy_meta_to_byte").getAsString() : null;
                    String missingHint = o.has("missing_hint_key") ? o.get("missing_hint_key").getAsString() : null;
                    String presentHint = o.has("present_hint_key") ? o.get("present_hint_key").getAsString() : null;
                    optional.add(new DnaRecipe.OptionalModifier(
                            req, whenMissing, whenPresent, copyMeta, missingHint, presentHint));
                }
            }
            RECIPES.put(entityId, new DnaRecipe(entityId, energyFe, weaknessTicks, required, optional));
        }
    }

    private static DnaRecipe.ItemRequirement parseReq(JsonObject o) {
        String item = o.get("item").getAsString();
        int meta = o.has("meta") ? o.get("meta").getAsInt() : 0;
        int count = o.has("count") ? o.get("count").getAsInt() : 1;
        return new DnaRecipe.ItemRequirement(item, meta, count);
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
                map.put(e.getKey(), p.getAsNumber());
            } else if (p.isString()) {
                map.put(e.getKey(), p.getAsString());
            }
        }
        return map;
    }

    public static boolean isWhitelisted(@Nullable String entityId) {
        return entityId != null && RECIPES.containsKey(normalize(entityId));
    }

    @Nullable
    public static DnaRecipe get(@Nullable String entityId) {
        if (entityId == null || entityId.isEmpty()) {
            return null;
        }
        return RECIPES.get(normalize(entityId));
    }

    public static Collection<DnaRecipe> all() {
        return Collections.unmodifiableCollection(RECIPES.values());
    }

    private static String normalize(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return s.contains(":") ? s : "minecraft:" + s;
    }
}
