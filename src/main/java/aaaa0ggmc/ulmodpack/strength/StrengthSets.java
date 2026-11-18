package aaaa0ggmc.ulmodpack.strength;

import aaaa0ggmc.uld.DesignRegistry;
import aaaa0ggmc.uld.UlStrength;

import java.io.File;
import java.util.List;

/**
 * 强度难度袋访问门面：完全委托给 {@link DesignRegistry} 单一真值源。
 */
public final class StrengthSets {

    public static final String DIR_NAME = DesignRegistry.SETS_DIR_NAME;
    public static final String CONFIG_NAME = DesignRegistry.ACTIVE_CONFIG_NAME;
    public static final String DEFAULT_SET = DesignRegistry.DEFAULT_SET;

    private StrengthSets() {
    }

    public static void init(File modConfigurationDirectory) {
        DesignRegistry.defaultInstance().initialize(modConfigurationDirectory);
    }

    public static void reload() {
        UlStrength.service().reload();
    }

    public static String active() {
        return UlStrength.service().activeSet();
    }

    public static List<String> available() {
        return UlStrength.service().availableSets();
    }

    public static boolean has(String name) {
        return setDir(name) != null;
    }

    public static boolean setActive(String name) {
        return UlStrength.service().setActiveSet(name);
    }

    public static File setDir(String name) {
        return DesignRegistry.defaultInstance().setDir(name);
    }

    public static File activeSetDir() {
        return setDir(active());
    }
}
