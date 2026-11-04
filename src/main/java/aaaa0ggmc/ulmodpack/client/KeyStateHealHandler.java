package aaaa0ggmc.ulmodpack.client;

import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.config.PackConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;

/**
 * 通用按键状态自愈（2026-11-04，WASD 粘滞排查的通用修复）。
 *
 * <h3>要治的病</h3>
 * {@code KeyBinding.pressed}（isKeyDown 用的「按住」位）只被松键事件改回 false。
 * 松键事件一旦丢失（窗口焦点切换时 OS 不再把 KeyRelease 送给本窗口、客户端线程
 * 卡顿导致 LWJGL 事件队列溢出、其他模组消费掉事件流等），该位就永久卡在按下——
 * 表现就是「松开按键人物还朝那个方向走，再按一次才恢复」。
 * 原版只在两处自愈：GUI 打开时 {@code unPressAllKeys()}（Minecraft.displayGuiScreen），
 * 以及 GUI 关闭/点击回焦时 {@code KeyBinding.updateKeyBindState()}（Minecraft.setIngameFocus）
 * ——后者还可能用同样陈旧的物理状态把卡住位「回填」回来。本类补上剩下的缺口。
 *
 * <h3>机制（全部可配，见 {@link PackConfig}）</h3>
 * <ol>
 *   <li><b>逐 tick 偏差校正</b>（{@code key_state_heal}）：对
 *       {@code mc.gameSettings.keyBindings} 里的<b>全部</b>键位（原版 + 任意模组注册的，
 *       因此覆盖 WASD/跳跃/潜行/疾跑与一切模组技能键，不写死任何按键）逐个检查：
 *       只要「游戏认为按住（isKeyDown）」而「物理已松开」，就置松开。</li>
 *   <li><b>焦点边界清零</b>（{@code unpress_on_focus_change}）：窗口失焦/回焦沿
 *       {@code unPressAllKeys()}。丢失的松键事件在 LWJGL 事件状态缓冲里同样看不出来
 *       （isKeyDown 与事件同源），逐 tick 校正救不了这种；在边界全量清零可以保证
 *       卡住状态活不过一次焦点切换。</li>
 *   <li><b>GUI 关闭边界清零</b>（{@code unpress_on_gui_close}）：同理，GUI 打开时原版
 *       清零、关闭时却按可能陈旧的物理状态回填；关闭沿再清一次，卡住状态活不过一次
 *       开关 GUI（玩家随手按两下 E 就是一次手动恢复）。</li>
 * </ol>
 *
 * <h3>为什么不会造成新的粘滞 / 鼠标问题（静态自检）</h3>
 * <ul>
 *   <li><b>只松不按</b>：校正方向永远是「按下 → 松开」，绝不把松开的键合成按下。
 *       原版在 GUI 打开时会故意丢弃按住状态（unPressAllKeys），若反向校正会把
 *       「关 GUI 后自动往前走」这种幽灵输入造出来；我们不做反向。</li>
 *   <li><b>偏差才动</b>：健康状态（isKeyDown 与物理一致）零改动，等价于空转。</li>
 *   <li><b>不动 pressTime</b>：一次性的 {@code isPressed()} 语义（开物品栏、快捷栏、
 *       各模组 KeyInputEvent 单击判定）完全不受影响，只修「按住」位。</li>
 *   <li><b>setKeyBindState 的共享语义与事件一致</b>：同键码多绑定一起翻转，
 *       正是原版事件路径（Minecraft.runTickKeyboard）的行为。</li>
 *   <li><b>时机在 ClientTickEvent.START</b>：原版 tick 顺序是 onPreClientTick →
 *       runTickMouse/runTickKeyboard(+processKeyBinds) → 世界 tick → onPostClientTick；
 *       在 START 校正，本 tick 的所有输入消费方（processKeyBinds、MovementInputFromOptions）
 *       读到的都是校正后的状态。</li>
 *   <li><b>鼠标默认不校正</b>（{@code key_state_heal_mouse=false}）：ul-agent-control 的
 *       虚拟 use/attack 是合成的鼠标按住，校正会提前剪断；且 Wayland/远程桌面下
 *       Mouse.isButtonDown 本身不可靠。键盘侧不存在合成按住（KeybindAbility 注入自带
 *       一 tick 复位，见 morphext），所以键盘默认开、鼠标默认关。</li>
 *   <li><b>绝不抛进 tick</b>：每个键位 try/catch，键码 0 / ≥256（无法轮询的伪键码）直接跳过。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(value = Side.CLIENT, modid = UlModpack.MODID)
public final class KeyStateHealHandler {

    /** 上一 tick 的窗口焦点状态（焦点沿检测）。 */
    private static boolean displayWasActive = true;
    /** 上一 tick 的 GUI 状态（GUI 关闭沿检测）。 */
    private static boolean screenWasOpen = false;

    private KeyStateHealHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings == null) {
            return;
        }

        // ---- 边界清零：焦点沿 / GUI 关闭沿 ----
        boolean displayActive = isDisplayActive();
        boolean screenOpen = mc.currentScreen != null;
        boolean boundaryHit = false;
        if (PackConfig.unpressOnFocusChange && displayWasActive != displayActive) {
            boundaryHit = true;
        }
        if (PackConfig.unpressOnGuiClose && screenWasOpen && !screenOpen) {
            boundaryHit = true;
        }
        displayWasActive = displayActive;
        screenWasOpen = screenOpen;
        if (boundaryHit) {
            KeyBinding.unPressAllKeys();
        }

        // ---- 逐 tick 偏差校正（只松不按）----
        if (!PackConfig.keyStateHeal) {
            return;
        }
        for (KeyBinding binding : mc.gameSettings.keyBindings) {
            if (binding == null) {
                continue;
            }
            try {
                int code = binding.getKeyCode();
                Boolean up = physicalKeyUp(code);
                if (up == null) {
                    continue; // 键码无法物理轮询（0 / ≥256）：不动
                }
                // isKeyDown() = pressed && 冲突上下文 && 修饰键——正是玩家实际感受到的「按住」。
                // 只在这个可观测状态卡住时才清；清 pressed 位对同键码所有绑定生效，与事件路径同语义。
                if (binding.isKeyDown() && up) {
                    KeyBinding.setKeyBindState(code, false);
                }
            } catch (Throwable t) {
                // LWJGL 未初始化 / 越界键码等：跳过该键位，绝不打断 tick
            }
        }
    }

    /**
     * 物理按键是否已松开。返回 null 表示无法轮询（未绑定 / 伪键码 / 鼠标校正关闭）。
     */
    private static Boolean physicalKeyUp(int code) {
        if (code == 0) {
            return null; // KEY_NONE
        }
        if (code < 0) {
            // 鼠标键码 = 按钮号 - 100
            if (!PackConfig.keyStateHealMouse) {
                return null;
            }
            int button = code + 100;
            if (button < 0 || button >= Mouse.getButtonCount()) {
                return null;
            }
            return !Mouse.isButtonDown(button);
        }
        if (code >= Keyboard.KEYBOARD_SIZE) {
            return null; // 键盘事件里的伪键码（字符 + 256），无法轮询
        }
        return !Keyboard.isKeyDown(code);
    }

    private static boolean isDisplayActive() {
        try {
            return Display.isActive();
        } catch (Throwable t) {
            return true; // 无 Display 环境：视为有焦点，不触发边界清零
        }
    }
}
