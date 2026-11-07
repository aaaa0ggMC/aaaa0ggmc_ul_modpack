package aaaa0ggmc.ulmodpack.strength;

import aaaa0ggmc.uld.UlStrength;
import aaaa0ggmc.ulmodpack.UlModpack;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 强度难度集（{@code strengths/<difficulty>/}）的 ul 侧解析与切换调用点。
 *
 * <p><b>数据外置·强度（2026-11-07）</b>：整套强度数据按难度分文件夹放在仓库
 * {@code dev/<repo>/strengths/<difficulty>/} 下，<b>每套是完整数据集（档位 + 各模组
 * values），不是增量补丁</b>；安装/软链由 {@code scripts/sync-strengths.sh} 落到
 * {@code <gameDir>/strengths/<difficulty>/}。jar 内 {@code assets/<modid>/ul_strength/}
 * 的副本由 build.gradle 从 {@code strengths/normal/} 生成，只作离线兜底。</p>
 *
 * <p><b>与旧三层合并的关系</b>：{@link #activeSetDir()} 返回非 null 时，加载器
 * （ul-strength-design 的 {@code DesignRegistry}）应<b>整袋替换</b>旧的「bundled 默认 →
 * 各模组 assets → config 覆盖」三层合并；返回 null（没装外置袋 / 当前难度袋缺失）时
 * 走旧路径，行为与今天逐字节一致。{@code config/ul_strength_design/*.json} 覆盖层
 * 在新旧两种模式下都保持原语义（实例侧微调），详见其自身 {@code _doc}。</p>
 *
 * <p>本类只做「当前难度是哪一套、袋子在哪」，不含任何数值；数值查询一律走
 * {@link UlStrength}。专用服务器也会加载（无客户端类引用）。</p>
 */
public final class StrengthSets {

    /** 游戏目录下的难度袋根目录名。 */
    public static final String DIR_NAME = "strengths";
    /** 记录当前难度的配置文件名（{@code config/<modid>/strengths.json}）。 */
    public static final String CONFIG_NAME = "strengths.json";
    /** 默认难度（与 jar 兜底副本同一份数据）。 */
    public static final String DEFAULT_SET = "normal";
    /** 配置里的键名。 */
    private static final String KEY_ACTIVE = "active";

    private static File setsRoot;
    private static File configFile;
    private static volatile String active = DEFAULT_SET;

    private StrengthSets() {
    }

    /**
     * 初始化（{@code UlModpack.preInit} 调用）：定位 {@code <gameDir>/strengths/} 与
     * {@code config/<modid>/strengths.json}，读取当前难度；配置文件缺失时写出默认值
     * （只写不预填数值，和 ul-strength-design 的覆盖模板同一约定）。
     */
    public static void init(File modConfigurationDirectory) {
        // config 目录的上一级就是游戏目录（集成服务器 / 专用服务器都是 <gameDir>/config）
        File gameDir = modConfigurationDirectory.getParentFile();
        setsRoot = new File(gameDir, DIR_NAME);
        configFile = new File(modConfigurationDirectory, CONFIG_NAME);
        if (!setsRoot.exists() && !setsRoot.mkdirs()) {
            UlModpack.LOGGER.warn("[ul-modpack] Cannot create {} dir {}", DIR_NAME, setsRoot);
        }
        if (!configFile.exists()) {
            writeDefaultConfig();
        }
        reload();
    }

    /** 重新读取当前难度（命令切换后 / 手工改配置后调用）。 */
    public static void reload() {
        String name = DEFAULT_SET;
        if (configFile != null && configFile.exists()) {
            try {
                JsonObject root = new JsonParser()
                        .parse(new String(Files.readAllBytes(configFile.toPath()), StandardCharsets.UTF_8))
                        .getAsJsonObject();
                if (root.has(KEY_ACTIVE) && !root.get(KEY_ACTIVE).getAsString().trim().isEmpty()) {
                    name = root.get(KEY_ACTIVE).getAsString().trim();
                }
            } catch (RuntimeException | java.io.IOException bad) {
                UlModpack.LOGGER.warn("[ul-modpack] Failed to read {}: {} — using {}",
                        CONFIG_NAME, bad.toString(), DEFAULT_SET);
            }
        }
        active = normalize(name);
        UlModpack.LOGGER.info("[ul-modpack] Strength set active: {} (dir: {})",
                active, activeSetDir());
    }

    /** 当前难度名（未安装外置袋时也是配置里的值，只是 {@link #activeSetDir()} 为 null）。 */
    public static String active() {
        return active;
    }

    /** 已安装的难度袋名（{@code <gameDir>/strengths/} 下的目录，排序稳定）。 */
    public static List<String> available() {
        List<String> out = new ArrayList<>();
        File[] dirs = setsRoot == null ? null : setsRoot.listFiles(File::isDirectory);
        if (dirs != null) {
            for (File d : dirs) {
                out.add(d.getName());
            }
        }
        Collections.sort(out);
        return out;
    }

    /** 该难度袋是否已安装。 */
    public static boolean has(String name) {
        return setDir(name) != null;
    }

    /**
     * 切换当前难度：写配置 + 立即重读。返回 false = 没有这套（调用方负责提示可用列表）。
     * 写盘失败返回 false 并留日志。
     */
    public static boolean setActive(String name) {
        String normalized = normalize(name);
        if (!has(normalized)) {
            return false;
        }
        try {
            JsonObject root = new JsonObject();
            root.addProperty(KEY_ACTIVE, normalized);
            root.addProperty("_doc", "当前强度难度（strengths/<difficulty>/）。"
                    + "可用 /ulpack strength <name> 切换（会立即热重载），或直接改本文件后重启。"
                    + "安装难度袋：bash ~/Games/HMCL/scripts/sync-strengths.sh。");
            Files.write(configFile.toPath(),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                            .toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (Throwable t) {
            UlModpack.LOGGER.warn("[ul-modpack] Failed to write {}: {}", CONFIG_NAME, t.toString());
            return false;
        }
        reload();
        return true;
    }

    /** 指定难度袋的目录；不存在返回 null。 */
    public static File setDir(String name) {
        if (name == null || name.isEmpty() || setsRoot == null) {
            return null;
        }
        String normalized = normalize(name);
        File dir = new File(setsRoot, normalized);
        return dir.isDirectory() ? dir : null;
    }

    /**
     * 当前难度袋目录；{@code null} = 没有可用的外置袋（没跑安装脚本 / 该难度没装），
     * 加载器应回退到旧三层合并（= jar 内 {@code assets/<modid>/ul_strength/} 兜底副本）。
     */
    public static File activeSetDir() {
        return setDir(active);
    }

    /** 难度名归一：去空白、小写；只允许字母数字与 -_.（防目录穿越）。 */
    private static String normalize(String name) {
        if (name == null) {
            return DEFAULT_SET;
        }
        String out = name.trim().toLowerCase(java.util.Locale.ROOT);
        if (out.isEmpty()) {
            return DEFAULT_SET;
        }
        if (!out.matches("[a-z0-9][a-z0-9._-]*")) {
            return DEFAULT_SET;
        }
        return out;
    }

    private static void writeDefaultConfig() {
        try {
            JsonObject root = new JsonObject();
            root.addProperty(KEY_ACTIVE, DEFAULT_SET);
            root.addProperty("_doc", "当前强度难度（strengths/<difficulty>/）。"
                    + "可用 /ulpack strength <name> 切换（会立即热重载），或直接改本文件后重启。"
                    + "安装难度袋：bash ~/Games/HMCL/scripts/sync-strengths.sh。");
            Files.write(configFile.toPath(),
                    new com.google.gson.GsonBuilder().setPrettyPrinting().create()
                            .toJson(root).getBytes(StandardCharsets.UTF_8));
            UlModpack.LOGGER.info("[ul-modpack] Wrote default {}", CONFIG_NAME);
        } catch (Throwable t) {
            UlModpack.LOGGER.warn("[ul-modpack] Failed to write {}: {}", CONFIG_NAME, t.toString());
        }
    }
}
