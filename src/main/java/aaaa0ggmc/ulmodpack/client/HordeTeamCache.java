package aaaa0ggmc.ulmodpack.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 尸群队伍快照的客户端缓存（B 键状态页数据源）。
 *
 * <b>只在客户端加载</b>：唯一写入口是 {@code ClientProxy.onHordeSnapshot}
 * （{@code SidedProxy} 覆写，主线程调度后调用 {@link #replace}），读入口是
 * {@code FollowerStatusSection}。专用服务器不加载本类——服务端代码只经
 * {@code CommonProxy.onHordeSnapshot} 空实现中转。
 *
 * 数据由服务端 {@code HordeManager.teamJson} 生成、经 {@code HordeNetwork} 的
 * S2C {@code TeamMessage} 推送：说服度 / 队伍上限 / 尸潮状态 / 随从列表。
 */
public final class HordeTeamCache {

    /** 单个随从的展示数据。 */
    public static final class Follower {
        public final String type;
        public final String name;
        public final boolean customName;
        public final double health;
        public final double maxHealth;

        Follower(String type, String name, boolean customName, double health, double maxHealth) {
            this.type = type == null ? "" : type;
            this.name = name == null ? "" : name;
            this.customName = customName;
            this.health = health;
            this.maxHealth = maxHealth;
        }
    }

    /** 一份队伍快照（不可变；读侧无锁）。 */
    public static final class Snapshot {
        public final int persuasion;
        public final int cap;
        public final boolean tideActive;
        /** 有尸群领导或有随从——状态页分节显隐标志。 */
        public final boolean hasHorde;
        public final List<Follower> followers;

        Snapshot(int persuasion, int cap, boolean tideActive, boolean hasHorde, List<Follower> followers) {
            this.persuasion = persuasion;
            this.cap = cap;
            this.tideActive = tideActive;
            this.hasHorde = hasHorde;
            this.followers = Collections.unmodifiableList(followers);
        }
    }

    private static final Snapshot EMPTY = new Snapshot(0, 0, false, false, new ArrayList<Follower>());
    private static volatile Snapshot current = EMPTY;

    private HordeTeamCache() {
    }

    /** 当前快照（从未同步过时为空快照）。 */
    public static Snapshot get() {
        return current;
    }

    /** 用服务端快照 JSON 整体替换缓存（主线程调用；坏数据丢弃不覆盖）。 */
    public static void replace(String json) {
        try {
            JsonObject root = new JsonParser().parse(json).getAsJsonObject();
            int persuasion = root.has("persuasion") ? root.get("persuasion").getAsInt() : 0;
            int cap = root.has("cap") ? root.get("cap").getAsInt() : 0;
            boolean tideActive = root.has("tideActive") && root.get("tideActive").getAsBoolean();
            boolean hasHorde = root.has("hasHorde") && root.get("hasHorde").getAsBoolean();
            List<Follower> followers = new ArrayList<>();
            if (root.has("followers")) {
                JsonArray arr = root.getAsJsonArray("followers");
                for (JsonElement el : arr) {
                    JsonObject e = el.getAsJsonObject();
                    followers.add(new Follower(
                            e.has("type") ? e.get("type").getAsString() : "",
                            e.has("name") ? e.get("name").getAsString() : "",
                            e.has("custom") && e.get("custom").getAsBoolean(),
                            e.has("health") ? e.get("health").getAsDouble() : 0.0D,
                            e.has("maxHealth") ? e.get("maxHealth").getAsDouble() : 0.0D));
                }
            }
            current = new Snapshot(persuasion, cap, tideActive, hasHorde, followers);
        } catch (RuntimeException bad) {
            // 服务端数据异常时不覆盖旧缓存，静默保留上一帧
        }
    }

    /** 连接切换/登出时清空（未显式调用也安全：换服首包即整体替换）。 */
    public static void clear() {
        current = EMPTY;
    }
}
