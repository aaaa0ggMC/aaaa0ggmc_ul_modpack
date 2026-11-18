package aaaa0ggmc.ulmodpack.zombie.horde;

import aaaa0ggmc.uld.IStrengthService;
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
 * <p>存 morphext 玩家数据 states[{@value aaaa0ggmc.ulmodpack.zombie.ZombieLine#STATE_PERSUASION}]
 * 的 {@code {"value":N,"failCount":F,"wolfCount":W,"failStreak":S}}，范围
 * {@code decay_floor}..{@link #start()}（初始即上限，设计 0-100）。屠村成功
 * +{@code persuasion_win}、死亡失败 -{@code persuasion_fail}；低于阈值影响招募条件、
 * 随从属性倍率与队伍上限。变化时在动作栏提示并同步客户端。</p>
 *
 * <p><b>失败衰减（2026-11-07 更正口径：累计衰减上限，非单次扣分）</b>——两条独立
 * 衰减轨，各自指数趋近封顶（{@link #addFailureDecay}）：</p>
 * <pre>
 *   decay(n) = cap × (1 − e^(−λ·n))          n = 该轨事件累计次数
 *   单次增量 Δ = decay(n) − decay(n−1) = 剩余额度 × (1 − e^(−λ))   // 每次只衰减剩余额度的一部分
 *   最终声望 = base − decay_fail(失败发动次数) − decay_wolf(狼来了次数)，clamp 至 [decay_floor, start]
 * </pre>
 * <ul>
 *   <li>失败轨（失败的尸潮发动）：cap = {@code zombie_tide.persuasion.fail_decay_cap}（40），
 *       无论失败多少次累计衰减 ≤ 40；</li>
 *   <li>狼来了轨（连续/重复失败发动=谎报）：cap = {@code zombie_tide.persuasion.wolf_decay_cap}（20），
 *       无论欺骗多少次累计衰减 ≤ 20；</li>
 *   <li>λ = {@code zombie_tide.persuasion.decay_lambda}（0.35）；下限（最低可招募值）
 *       = {@code zombie_tide.persuasion.decay_floor}（20 = threshold_low 线：招募只需
 *       「打残」、不需搭腐肉，杜绝「怎么招都招不到」死局；调低本键可恢复 &lt;20 需腐肉档位）。</li>
 * </ul>
 *
 * <p>全部数值经 {@link UlStrength}（键 zombie_tide.*），兜底默认与 zombie_tide.json 一致，
 * 可被 config/ul_strength_design/overrides.json 与 strengths 分难度套表覆盖。</p>
 */
public final class Persuasion {

    private static volatile IStrengthService strength = UlStrength.service();

    private Persuasion() {
    }

    public static void setStrengthService(IStrengthService service) {
        if (service != null) {
            strength = service;
        }
    }

    /** 初始 / 上限说服度（设计 100）。 */
    public static int start() {
        return (int) Math.round(strength.num("zombie_tide.horde.persuasion_start", 100.0D));
    }

    /** 声望下限（最低可招募值）：衰减/死亡后不低于此值。 */
    public static int floor() {
        return (int) Math.round(strength.num("zombie_tide.persuasion.decay_floor", 20.0D));
    }

    /** 当前说服度（缺省回落初始值，旧存档无字段时补默认）。 */
    public static int get(EntityPlayerMP player) {
        JsonObject state = readState(player);
        if (state.has("value")) {
            return clamp((int) Math.round(state.get("value").getAsDouble()));
        }
        return start();
    }

    /** 增减说服度；有变化才落盘 + 动作栏提示 + 同步（计数字段原样保留）。 */
    public static void add(EntityPlayerMP player, double delta) {
        int before = get(player);
        int after = clamp((int) Math.round(before + delta));
        if (after == before) {
            return;
        }
        JsonObject state = readState(player);
        state.addProperty("value", after);
        writeState(player, state);
        try {
            player.sendStatusMessage(new TextComponentTranslation("ul.horde.persuasion", after), true);
            AbilityNetwork.syncPlayer(player, "");
        } catch (Throwable t) {
            UlModpack.LOGGER.warn("[ul-modpack] persuasion feedback failed: {}", t.toString());
        }
    }

    /** 直接写入（钳制后，计数字段原样保留）。 */
    public static void set(EntityPlayerMP player, int value) {
        JsonObject state = readState(player);
        state.addProperty("value", clamp(value));
        writeState(player, state);
    }

    // ============================================================ 失败衰减（累计上限 + 指数趋近）

    /**
     * 失败宣告的声望衰减：失败轨 + 「狼来了」轨（连续/重复失败发动）。
     * 每轨独立计数、独立封顶（40 / 20），每次只衰减「剩余额度」的一部分，
     * 无数次之后趋近封顶、不会超过（公式见类注释）。声望不低于 {@link #floor()}。
     */
    public static void addFailureDecay(EntityPlayerMP player) {
        JsonObject state = readState(player);
        int failCount = intValue(state, "failCount") + 1;
        int failStreak = intValue(state, "failStreak") + 1;
        double delta = decayDelta("zombie_tide.persuasion.fail_decay_cap", 40.0D, failCount);
        state.addProperty("failCount", failCount);
        state.addProperty("failStreak", failStreak);
        if (failStreak >= 2) {
            // 「狼来了」：连续/重复失败发动（上一次发动尝试也是失败）＝谎报
            int wolfCount = intValue(state, "wolfCount") + 1;
            delta += decayDelta("zombie_tide.persuasion.wolf_decay_cap", 20.0D, wolfCount);
            state.addProperty("wolfCount", wolfCount);
        }
        writeState(player, state);
        add(player, -delta);
    }

    /** 发动成功即重置失败连击（「狼来了」只计连续/重复失败发动）。 */
    public static void resetFailStreak(EntityPlayerMP player) {
        JsonObject state = readState(player);
        if (intValue(state, "failStreak") != 0) {
            state.addProperty("failStreak", 0);
            writeState(player, state);
        }
    }

    /**
     * 单次衰减增量 = decay(n) − decay(n−1)，其中 decay(n)=cap×(1−e^(−λ·n))。
     * 即「剩余额度 × (1 − e^(−λ))」：每次只衰减剩余额度的一部分，累计 ≤ cap。
     */
    private static double decayDelta(String capKey, double capDefault, int count) {
        double cap = strength.num(capKey, capDefault);
        double lambda = strength.num("zombie_tide.persuasion.decay_lambda", 0.35D);
        double decayedNow = cap * (1.0D - Math.exp(-lambda * count));
        double decayedPrev = cap * (1.0D - Math.exp(-lambda * (count - 1)));
        return decayedNow - decayedPrev;
    }

    // ============================================================ 状态读写（合并，防覆盖计数字段）

    private static JsonObject readState(EntityPlayerMP player) {
        JsonObject state = PlayerAbilityStore.get(player).getState(ZombieLine.STATE_PERSUASION);
        return state == null ? new JsonObject() : state;
    }

    private static void writeState(EntityPlayerMP player, JsonObject state) {
        PlayerAbilityStore.update(player, (PlayerAbilityData data) ->
                data.putState(ZombieLine.STATE_PERSUASION, state));
    }

    private static int intValue(JsonObject state, String key) {
        return state.has(key) ? state.get(key).getAsInt() : 0;
    }

    /** 随从生命 / 攻击属性倍率（按说服度档位）。 */
    public static double multiplier(int persuasion) {
        if (persuasion >= thresholdHigh()) {
            return strength.num("zombie_tide.horde.mult_high", 1.0D);
        }
        if (persuasion >= thresholdMid()) {
            return strength.num("zombie_tide.horde.mult_mid", 0.85D);
        }
        if (persuasion >= thresholdLow()) {
            return strength.num("zombie_tide.horde.mult_low", 0.7D);
        }
        return strength.num("zombie_tide.horde.mult_bottom", 0.55D);
    }

    /** 队伍上限（按说服度档位）。 */
    public static int followCap(int persuasion) {
        if (persuasion >= thresholdHigh()) {
            return strength.integer("zombie_tide.horde.follow_cap_full", 20);
        }
        if (persuasion >= thresholdMid()) {
            return strength.integer("zombie_tide.horde.follow_cap_high", 16);
        }
        if (persuasion >= thresholdLow()) {
            return strength.integer("zombie_tide.horde.follow_cap_mid", 12);
        }
        return strength.integer("zombie_tide.horde.follow_cap_low", 8);
    }

    /** 低于此阈值招募要「打残」（生命 < persuasion_hurt_below 比例）。设计 50。 */
    public static double thresholdMid() {
        return strength.num("zombie_tide.horde.persuasion_threshold_mid", 50.0D);
    }

    /** 低于此阈值招募还要搭一块腐肉。设计 20。 */
    public static double thresholdLow() {
        return strength.num("zombie_tide.horde.persuasion_threshold_low", 20.0D);
    }

    private static double thresholdHigh() {
        return strength.num("zombie_tide.horde.persuasion_threshold_high", 80.0D);
    }

    private static int clamp(int value) {
        // 范围 decay_floor..start()（start 即设计上限 100）：下限=最低可招募值，防死局
        return Math.max(floor(), Math.min(start(), value));
    }
}
