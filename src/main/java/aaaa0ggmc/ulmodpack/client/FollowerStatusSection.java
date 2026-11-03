package aaaa0ggmc.ulmodpack.client;

import aaaa0ggmc.morphext.client.status.IStatusSection;
import net.minecraft.client.resources.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * B 键状态页（morphext {@code GuiAbilityStatus}）的「尸群队伍」分节（客户端专用）。
 *
 * 数据来源：服务端 {@code HordeManager.teamJson} 的 S2C 队伍快照，缓存在
 * {@link HordeTeamCache}（随从变化即时推、tick 节流 5 秒一拍）。界面只读缓存，
 * 每帧调用代价可忽略。没有尸群领导且无随从时返回空列表（整个分节不显示）。
 *
 * 注册时机：{@code ClientProxy.preInit} → {@code StatusSectionRegistry.register}，
 * 专用服务器不加载本类。
 */
public final class FollowerStatusSection implements IStatusSection {

    @Override
    public String getTitle() {
        return I18n.format("aaaa0ggmc_ul_modpack.status.horde.title");
    }

    @Override
    public List<String> getLines() {
        HordeTeamCache.Snapshot snap = HordeTeamCache.get();
        if (!snap.hasHorde) {
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
