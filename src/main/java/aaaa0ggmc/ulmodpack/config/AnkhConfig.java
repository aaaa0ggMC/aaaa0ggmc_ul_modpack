package aaaa0ggmc.ulmodpack.config;

import aaaa0ggmc.morphext.form.FormCategories;
import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.race.RaceResolver;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 安卡护符 + 种族规则配置（ankh.json）。
 *
 * 分层装载（对齐 ul-strength-design / FormCategories 的约定）：
 *   1. 内置默认：assets/aaaa0ggmc_ul_modpack/ul_config/ankh.json（带 _doc 注释键）；
 *   2. 运行时覆盖：config/aaaa0ggmc_ul_modpack/ankh.json（首次运行自动写出内置副本，
 *      直接改这份即可；出现的顶层字段整体替换内置默认，没写的字段沿用内置默认）；
 *   3. 热重载：config 文件 mtime 变化后 5 秒内自动 reload，也可 /ulpack reload。
 *
 * 字段含义与默认值见内置文件里的 _doc / _&lt;字段&gt; 注释键（解析时跳过 _ 前缀键）。
 * 以 _ 开头的键只是文档，不参与逻辑。
 */
public final class AnkhConfig {
    /** 内置默认资源路径（classpath）。 */
    public static final String RESOURCE_PATH = "assets/" + UlModpack.MODID + "/ul_config/ankh.json";

    private static File configFile;

    private static volatile Set<String> raceWhitelist = Collections.emptySet();
    private static volatile Set<String> killerCategories = Collections.emptySet();
    private static volatile Map<String, String> raceMap = Collections.emptyMap();
    private static volatile Map<String, String> raceTargetForms = Collections.emptyMap();
    private static volatile String defaultRace = "human";
    private static volatile String unmappedFormRace = "unknown";

    private AnkhConfig() {
    }

    /** 首次运行把内置默认复制到 config 目录作为用户可改的覆盖文件。 */
    public static void init(File modConfigurationDirectory) {
        File dir = new File(modConfigurationDirectory, UlModpack.MODID);
        if (!dir.exists() && !dir.mkdirs()) {
            UlModpack.LOGGER.warn("[ul-modpack] Cannot create config dir {}", dir);
        }
        configFile = new File(dir, "ankh.json");
        if (!configFile.exists()) {
            writeDefaultConfig();
        }
        reload();
    }

    /** 重新合并并解析（内置默认 ← config 覆盖）。 */
    public static void reload() {
        JsonObject merged = readObject("bundled:ankh.json",
                AnkhConfig.class.getClassLoader().getResourceAsStream(RESOURCE_PATH));
        if (merged == null) {
            merged = new JsonObject();
        }
        if (configFile != null && configFile.exists()) {
            JsonObject override = readObject("config:" + configFile.getName(), openFile(configFile));
            if (override != null) {
                for (Map.Entry<String, JsonElement> entry : override.entrySet()) {
                    if (entry.getKey().startsWith("_")) {
                        continue; // _doc 等注释键
                    }
                    merged.add(entry.getKey(), entry.getValue());
                }
            }
        }
        parse(merged);
        UlModpack.LOGGER.info("[ul-modpack] Ankh config loaded: raceWhitelist={}, killerCategories={}, "
                        + "raceMap={} entries, raceTargetForms={} entries",
                raceWhitelist, killerCategories, raceMap.size(), raceTargetForms.size());
    }

    /** config 文件 mtime 签名（热重载轮询用）。 */
    public static long configSignature() {
        return configFile == null ? 0L : configFile.lastModified();
    }

    // ---------------------------------------------------------------- 访问

    /** 种族白名单（安卡只对该列表内的种族生效）。 */
    public static Set<String> raceWhitelist() {
        return raceWhitelist;
    }

    public static boolean isRaceWhitelisted(String race) {
        return raceWhitelist.contains(RaceResolver.normalizeRace(race));
    }

    /** 击杀者必须属于的形态类别（FormCategories 类别 id）。 */
    public static Set<String> killerCategories() {
        return killerCategories;
    }

    /** 种族映射（形态 id / 形态类别 id → 种族 id），键已归一。 */
    public static Map<String, String> raceMap() {
        return raceMap;
    }

    /** 种族 → 感染固定目标形态（键已归一；没有条目 = 变击杀者形态）。 */
    public static Map<String, String> raceTargetForms() {
        return raceTargetForms;
    }

    /** 该种族感染后的固定目标形态；没有配置返回 null（变击杀者形态）。 */
    public static String raceTargetForm(String race) {
        return raceTargetForms.get(RaceResolver.normalizeRace(race));
    }

    /** 未变身且无显式种族记录时的种族。 */
    public static String defaultRace() {
        return defaultRace;
    }

    /** 已变身但种族映射查不到时的种族。 */
    public static String unmappedFormRace() {
        return unmappedFormRace;
    }

    // ---------------------------------------------------------------- 内部

    private static void parse(JsonObject root) {
        // race_whitelist：种族 id 列表
        Set<String> whitelist = stringSet(root, "race_whitelist", RaceResolver::normalizeRace);
        raceWhitelist = whitelist != null ? whitelist : setOf("human", "villager");
        // killer_categories：形态类别 id 列表（FormCategories）
        Set<String> killers = stringSet(root, "killer_categories", FormCategories::normalize);
        killerCategories = killers != null ? killers : setOf("ul:undead");
        // race_map：形态 id / 类别 id → 种族 id
        Map<String, String> map = stringMap(root, "race_map", FormCategories::normalize, RaceResolver::normalizeRace);
        if (map == null) {
            map = new LinkedHashMap<>();
            map.put(FormCategories.normalize("ul:undead"), "undead");
            map.put(FormCategories.normalize("minecraft:villager"), "villager");
        }
        raceMap = Collections.unmodifiableMap(map);
        // race_target_forms：种族 id → 目标形态 id
        Map<String, String> targets = stringMap(root, "race_target_forms",
                RaceResolver::normalizeRace, FormCategories::normalize);
        if (targets == null) {
            targets = new LinkedHashMap<>();
            targets.put("villager", FormCategories.normalize("minecraft:zombie_villager"));
        }
        raceTargetForms = Collections.unmodifiableMap(targets);
        defaultRace = stringScalar(root, "default_race", "human");
        unmappedFormRace = stringScalar(root, "unmapped_form_race", "unknown");
    }

    private static String stringScalar(JsonObject root, String key, String fallback) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        String value = RaceResolver.normalizeRace(element.getAsString());
        return value != null ? value : fallback;
    }

    /** 字符串数组 → 集合（逐项归一）。缺失或非法返回 null。 */
    private static Set<String> stringSet(JsonObject root, String key,
                                         java.util.function.Function<String, String> normalizer) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonArray()) {
            return null;
        }
        Set<String> out = new LinkedHashSet<>();
        for (JsonElement item : (JsonArray) element) {
            if (item == null || !item.isJsonPrimitive()) {
                continue;
            }
            String value = normalizer.apply(item.getAsString());
            if (value != null) {
                out.add(value);
            }
        }
        return Collections.unmodifiableSet(out);
    }

    /** 字符串对象 → 映射（键、值各自归一）。缺失或非法返回 null。 */
    private static Map<String, String> stringMap(JsonObject root, String key,
                                                 java.util.function.Function<String, String> keyNormalizer,
                                                 java.util.function.Function<String, String> valueNormalizer) {
        JsonElement element = root.get(key);
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : ((JsonObject) element).entrySet()) {
            if (entry.getValue() == null || !entry.getValue().isJsonPrimitive()) {
                continue;
            }
            String mapKey = keyNormalizer.apply(entry.getKey());
            String value = valueNormalizer.apply(entry.getValue().getAsString());
            if (mapKey != null && value != null) {
                out.put(mapKey, value);
            }
        }
        return Collections.unmodifiableMap(out);
    }

    private static Set<String> setOf(String... values) {
        Set<String> out = new LinkedHashSet<>();
        Collections.addAll(out, values);
        return Collections.unmodifiableSet(out);
    }

    private static void writeDefaultConfig() {
        try (InputStream is = AnkhConfig.class.getClassLoader().getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                UlModpack.LOGGER.error("[ul-modpack] Bundled {} missing — cannot write default config", RESOURCE_PATH);
                return;
            }
            Files.copy(is, configFile.toPath());
            UlModpack.LOGGER.info("[ul-modpack] Wrote default {}", configFile.getName());
        } catch (Throwable t) {
            UlModpack.LOGGER.warn("[ul-modpack] Failed to write default ankh.json: {}", t.toString());
        }
    }

    private static InputStream openFile(File file) {
        try {
            return Files.newInputStream(file.toPath());
        } catch (Throwable t) {
            UlModpack.LOGGER.warn("[ul-modpack] Cannot open {}: {}", file, t.toString());
            return null;
        }
    }

    private static JsonObject readObject(String source, InputStream is) {
        if (is == null) {
            return null;
        }
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            JsonElement json = new JsonParser().parse(reader);
            return json != null && json.isJsonObject() ? json.getAsJsonObject() : null;
        } catch (Throwable t) {
            UlModpack.LOGGER.error("[ul-modpack] Failed to parse ankh config from {}", source, t);
            return null;
        }
    }

    /** config/aaaa0ggmc_ul_modpack/ankh.json 改动自动热重载（100 tick = 5 秒轮询 mtime）。 */
    @Mod.EventBusSubscriber(modid = UlModpack.MODID)
    public static final class ReloadWatcher {
        private static long lastSignature = Long.MIN_VALUE;
        private static int tickCounter;

        private ReloadWatcher() {
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.START) {
                return;
            }
            if (++tickCounter % 100 != 0) {
                return;
            }
            long signature = configSignature();
            if (signature != lastSignature) {
                if (lastSignature != Long.MIN_VALUE) {
                    UlModpack.LOGGER.info("[ul-modpack] ankh.json changed — reloading");
                    reload();
                }
                lastSignature = signature;
            }
        }
    }
}
