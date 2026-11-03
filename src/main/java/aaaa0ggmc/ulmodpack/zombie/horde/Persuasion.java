package aaaa0ggmc.ulmodpack.zombie.horde;

import aaaa0ggmc.uld.UlStrength;
import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.zombie.ZombieLine;
import aaaa0ggmc.morphext.ability.PlayerAbilityData;
import aaaa0ggmc.morphext.ability.PlayerAbilityStore;
import aaaa0ggmc.morphext.network.AbilityNetwork;
import com.google.gson.JsonObject;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentTranslation;

/**
 * 说服度（尸群领导的「死人信不信你」）。
 *
 * 存 morphext 玩家数据 states[{@value aaaa0ggmc.ulmodpack.zombie.ZombieLine#STATE_PERSUASION}]
 * 的 {@code {"value":N}}，范围 0..{@link #start()}（初始即上限，设计 0-100）。
 * 屠村成功 +{@code persuasion_win}、失败 -{@code persuasion_fail}；低于阈值影响招募条件、
 * 随从属性倍率与队伍上限。变化时在动作栏提示并同步客户端。
 *
 * 全部数值经 {@link UlStrength}（键 zombie_tide.horde.*），兜底默认与 defaults.json 一致。
 */
public final class Persuasion {

    private Persuasion() {
    }

    /** 初始 / 上限说服度（设计 100）。 */
    public static int start() {
        return (int) Math.round(UlStrength.num("zombie_tide.horde.persuasion_start", 100.0D));
    }

    /** 当前说服度（缺省回落初始值，旧存档无字段时补默认）。 */
    public static int get(EntityPlayerMP player) {
        JsonObject state = PlayerAbilityStore.get(player).getState(ZombieLine.STATE_PERSUASION);
        if (state.has("value")) {
            return clamp((int) Math.round(state.get("value").getAsDouble()));
        }
        return start();
    }

    /** 增减说服度；有变化才落盘 + 动作栏提示 + 同步。 */
    public static void add(EntityPlayerMP player, double delta) {
        int before = get(player);
        int after = clamp((int) Math.round(before + delta));
        if (after == before) {
            return;
        }
        set(player, after);
        try {
            player.sendStatusMessage(new TextComponentTranslation("ul.horde.persuasion", after), true);
            AbilityNetwork.syncPlayer(player, "");
        } catch (Throwable t) {
            UlModpack.LOGGER.warn("[ul-modpack] persuasion feedback failed: {}", t.toString());
        }
    }

    /** 直接写入（钳制后）。 */
    public static void set(EntityPlayerMP player, int value) {
        int v = clamp(value);
        JsonObject state = new JsonObject();
        state.addProperty("value", v);
        PlayerAbilityStore.update(player, (PlayerAbilityData data) ->
                data.putState(ZombieLine.STATE_PERSUASION, state));
    }

    /** 随从生命 / 攻击属性倍率（按说服度档位）。 */
    public static double multiplier(int persuasion) {
        if (persuasion >= thresholdHigh()) {
            return UlStrength.num("zombie_tide.horde.mult_high", 1.0D);
        }
        if (persuasion >= thresholdMid()) {
            return UlStrength.num("zombie_tide.horde.mult_mid", 0.85D);
        }
        if (persuasion >= thresholdLow()) {
            return UlStrength.num("zombie_tide.horde.mult_low", 0.7D);
        }
        return UlStrength.num("zombie_tide.horde.mult_bottom", 0.55D);
    }

    /** 队伍上限（按说服度档位）。 */
    public static int followCap(int persuasion) {
        if (persuasion >= thresholdHigh()) {
            return UlStrength.integer("zombie_tide.horde.follow_cap_full", 20);
        }
        if (persuasion >= thresholdMid()) {
            return UlStrength.integer("zombie_tide.horde.follow_cap_high", 16);
        }
        if (persuasion >= thresholdLow()) {
            return UlStrength.integer("zombie_tide.horde.follow_cap_mid", 12);
        }
        return UlStrength.integer("zombie_tide.horde.follow_cap_low", 8);
    }

    /** 低于此阈值招募要「打残」（生命 < persuasion_hurt_below 比例）。设计 50。 */
    public static double thresholdMid() {
        return UlStrength.num("zombie_tide.horde.persuasion_threshold_mid", 50.0D);
    }

    /** 低于此阈值招募还要搭一块腐肉。设计 20。 */
    public static double thresholdLow() {
        return UlStrength.num("zombie_tide.horde.persuasion_threshold_low", 20.0D);
    }

    private static double thresholdHigh() {
        return UlStrength.num("zombie_tide.horde.persuasion_threshold_high", 80.0D);
    }

    private static int clamp(int value) {
        // 范围 0..start()（start 即设计上限 100）；0 为允许的字面量
        return Math.max(0, Math.min(start(), value));
    }
}
