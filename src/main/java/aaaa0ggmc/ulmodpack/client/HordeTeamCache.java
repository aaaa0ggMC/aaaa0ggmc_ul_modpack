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
 * <p><b>降级为纯网络镜像（2026-11-07）</b>：队员列表的权威状态是服务端世界存档
 * （{@code Followers/FollowerRegistry} 的 owner NBT + {@code HordeState}），客户端拿不到
 * 也不该拿；分节显隐的权威是「{@code horde_command} 能力是否被当前形态授予」
 * （服务端 {@code ZombieForms.canCommandHorde}，与释放链 {@code isAvailable} 同源）。
 * 因此本缓存<b>只做 S2C 快照缓存、不做任何状态判定</b>：不重算显隐、不推断队伍关系、
 * 不维护第二份权威；{@link Snapshot#granted} 就是服务端判定的逐字镜像，显隐判据随
 * 服务端单点修改（此前缓存放的 {@code hasHorde = 有领导或有随从} 已删除）。</p>
 *
 * 数据由服务端 {@code HordeManager.teamJson} 生成、经 {@code HordeNetwork} 的
 * S2C {@code TeamMessage} 推送：能力授予标志 / 说服度 / 队伍上限 / 尸潮状态 / 随从列表。
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
        /**
         * {@code horde_command} 能力是否被当前形态授予（服务端判定，逐字镜像）：
         * true = 状态页「尸群队伍」分节显示（哪怕没有随从），false = 整个分节不显示。
         * 判据见服务端 {@code ZombieForms.canCommandHorde}；本缓存不重新判定。
         */
        public final boolean granted;
        public final List<Follower> followers;

        Snapshot(int persuasion, int cap, boolean tideActive, boolean granted, List<Follower> followers) {
            this.persuasion = persuasion;
            this.cap = cap;
            this.tideActive = tideActive;
            this.granted = granted;
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
            boolean granted = root.has("granted") && root.get("granted").getAsBoolean();
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
            current = new Snapshot(persuasion, cap, tideActive, granted, followers);
        } catch (RuntimeException bad) {
            // 服务端数据异常时不覆盖旧缓存，静默保留上一帧
        }
    }

    /** 连接切换/登出时清空（{@code ClientProxy.onClientDisconnect} 断线事件调用；换服首包也会整体替换）。 */
    public static void clear() {
        current = EMPTY;
    }
}
