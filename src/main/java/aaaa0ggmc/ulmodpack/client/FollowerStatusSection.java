package aaaa0ggmc.ulmodpack.client;

import aaaa0ggmc.morphext.client.status.IStatusSection;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * B 键状态页（morphext {@code GuiAbilityStatus}）的「尸群队伍」分节（客户端专用）。
 *
 * 数据来源：服务端 {@code HordeManager.teamJson} 的 S2C 队伍快照，缓存在
 * {@link HordeTeamCache}（随从变化即时推、tick 节流 5 秒一拍、变形即时推）。
 * 界面只读缓存，每帧调用代价可忽略。
 *
 * <p><b>显隐判据（2026-11-07 改）</b>：{@code horde_command} 能力是否被当前形态授予
 * （服务端 {@code ZombieForms.canCommandHorde}：尸群领导技能 + 不死者形态）——
 * 镜像为快照的 {@link HordeTeamCache.Snapshot#granted}。不再用「有尸群领导或有随从」：
 * 技能是持久资产、随从是世界存档状态，取消变形后旧判据仍为真、面板会一直挂着。
 * 未授予时返回空列表（整个分节不显示、不占位）。</p>
 *
 * <p><b>核心 {@code isVisible} SPI（尚未落地）</b>：核心正给 {@link IStatusSection} 加
 * {@code isVisible(EntityPlayer)} 谓词（向后兼容默认实现）。本类已按该签名写好覆写，
 * 依赖 jar 里还没有这个方法，所以当前<b>不会被调用</b>——显隐仍由「空列表 = 不显示」
 * 的既有契约保证，行为不受影响；等核心 jar 落地后本方法自动生效（同一判据，只是让
 * 核心可以跳过 {@link #getLines()}），无需再改 ul 侧。</p>
 *
 * 注册时机：{@code ClientProxy.preInit} → {@code StatusSectionRegistry.register}，
 * 专用服务器不加载本类。
 */
public final class FollowerStatusSection implements IStatusSection {

    @Override
    public String getTitle() {
        return I18n.format("aaaa0ggmc_ul_modpack.status.horde.title");
    }

    /**
     * 分节是否显示（核心 {@code IStatusSection.isVisible} 覆写，签名与该 SPI 一致）。
     * 判据与服务端权威同一份数据：快照镜像的 {@code granted}（能力授予）。缓存里没有
     * 自行判定逻辑（见 {@link HordeTeamCache} 降级说明）。
     */
    public boolean isVisible(EntityPlayer player) {
        return HordeTeamCache.get().granted;
    }

    @Override
    public List<String> getLines() {
        HordeTeamCache.Snapshot snap = HordeTeamCache.get();
        if (!snap.granted) {
            return new ArrayList<>(); // 空列表 = 分节不显示、不占位
        }
        List<String> lines = new ArrayList<>();
        lines.add(I18n.format("aaaa0ggmc_ul_modpack.status.horde.followers",
                String.valueOf(snap.followers.size()), String.valueOf(snap.cap)));
        lines.add(I18n.format("aaaa0ggmc_ul_modpack.status.horde.persuasion",
                String.valueOf(snap.persuasion)));
        lines.add(I18n.format(snap.tideActive
                ? "aaaa0ggmc_ul_modpack.status.horde.tide.active"
                : "aaaa0ggmc_ul_modpack.status.horde.tide.idle"));
        if (snap.followers.isEmpty()) {
            lines.add(I18n.format("aaaa0ggmc_ul_modpack.status.horde.empty"));
        } else {
            for (HordeTeamCache.Follower f : snap.followers) {
                lines.add(I18n.format("aaaa0ggmc_ul_modpack.status.horde.follower",
                        displayName(f), fmt(f.health), fmt(f.maxHealth)));
            }
        }
        return lines;
    }

    /** 自命名随从显示原名；否则用形态 id 推实体翻译键，未知回退服务端名称。 */
    private static String displayName(HordeTeamCache.Follower f) {
        if (f.customName && !f.name.isEmpty()) {
            return f.name;
        }
        int colon = f.type.indexOf(':');
        String path = colon >= 0 ? f.type.substring(colon + 1) : f.type;
        if (!path.isEmpty()) {
            String localized = I18n.format("entity." + path + ".name");
            if (!localized.startsWith("entity.")) {
                return localized;
            }
        }
        return f.name.isEmpty() ? f.type : f.name;
    }

    private static String fmt(double value) {
        long scaled = Math.round(value * 10.0D);
        return scaled % 10 == 0 ? String.valueOf(scaled / 10) : String.valueOf(scaled / 10.0D);
    }
}
