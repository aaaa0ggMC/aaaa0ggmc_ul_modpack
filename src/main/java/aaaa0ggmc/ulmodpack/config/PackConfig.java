package aaaa0ggmc.ulmodpack.config;

import net.minecraftforge.common.config.Configuration;

import java.io.File;

/**
 * 整合包主模组的全局配置（{@code config/aaaa0ggmc_ul_modpack.cfg}）。
 *
 * <p>只放「跨系统横切机制」的开关；内容向数值走 ul-strength-design，不进这里。</p>
 *
 * <p>本类在专用服务器上也会加载，<b>不得引用任何客户端类</b>（LWJGL/Minecraft 客户端）。</p>
 */
public final class PackConfig {

    public static final String CATEGORY_INPUT = "input";

    /**
     * 按键状态自愈（通用）：每 tick 用物理按键状态校正 {@code KeyBinding} 的按下残留。
     * 覆盖所有已注册键位（原版 + 各模组），不针对特定按键。
     */
    public static boolean keyStateHeal = true;

    /**
     * 自愈是否覆盖鼠标键位。默认关：
     * TAS 操控工具箱（ul-agent-control）会合成「按住鼠标键」，自愈会提前剪断它；
     * 且 Wayland / 远程桌面下 Mouse.isButtonDown 不可靠（见 HordeClientTrigger 注释）。
     */
    public static boolean keyStateHealMouse = false;

    /**
     * 窗口焦点变化（失焦 / 回焦）时强制 {@code KeyBinding.unPressAllKeys()}。
     * 丢失的松键事件在 LWJGL 层无法从事件状态缓冲里看穿，只有在边界处全量清零才能保证不残留。
     */
    public static boolean unpressOnFocusChange = true;

    /**
     * GUI 关闭时强制 {@code KeyBinding.unPressAllKeys()}。
     * 原版在 GUI 打开时清零、关闭时却按（可能陈旧的）物理状态回填；这里在关闭边界兜底清零。
     * 代价：开着 GUI 按住的键，关 GUI 后需要再按一次——与原版「开 GUI 清零」同方向的安全取舍。
     */
    public static boolean unpressOnGuiClose = true;

    private static Configuration config;

    private PackConfig() {
    }

    public static synchronized void init(File file) {
        config = new Configuration(file);
        load();
    }

    public static synchronized void load() {
        config.addCustomCategoryComment(CATEGORY_INPUT,
                "Generic input-state self healing (covers every registered key binding, not just movement keys)");
        keyStateHeal = config.getBoolean("key_state_heal", CATEGORY_INPUT, true,
                "Each client tick, release KeyBinding states that are stuck down while the physical key is up");
        keyStateHealMouse = config.getBoolean("key_state_heal_mouse", CATEGORY_INPUT, false,
                "Also apply key_state_heal to mouse buttons. Keep false when using ul-agent-control "
                        + "synthetic use/attack holds, and on Wayland/remote desktops (Mouse state unreliable)");
        unpressOnFocusChange = config.getBoolean("unpress_on_focus_change", CATEGORY_INPUT, true,
                "Force unPressAllKeys() when the window loses or regains focus (recovers keys whose "
                        + "release event was lost across the focus change)");
        unpressOnGuiClose = config.getBoolean("unpress_on_gui_close", CATEGORY_INPUT, true,
                "Force unPressAllKeys() when a GUI closes (held-through-a-GUI keys need one re-press, "
                        + "same safety direction as vanilla's un-press on GUI open)");

        if (config.hasChanged()) {
            config.save();
        }
    }
}
