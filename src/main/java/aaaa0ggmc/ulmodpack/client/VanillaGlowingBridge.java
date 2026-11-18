package aaaa0ggmc.ulmodpack.client;

import aaaa0ggmc.morphext.client.render.ClientOutlineState;
import aaaa0ggmc.morphext.render.Outlines;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Team;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 整合包侧原版 GLOWING 桥接器：
 * 将原版光谱箭 / 发光药水 / scoreboard team 发光实体接管到核心屏幕空间边缘发光渲染器。
 */
@SideOnly(Side.CLIENT)
public final class VanillaGlowingBridge {

    private VanillaGlowingBridge() {
    }

    public static void recordGlowing(Entity entity) {
        if (entity == null) {
            return;
        }
        int rgb = 0xFFFFFF;
        Team team = entity.getTeam();
        if (team instanceof ScorePlayerTeam) {
            rgb = Outlines.colorToRgb(((ScorePlayerTeam) team).getColor());
        }
        ClientOutlineState.addFrameTarget(entity.getEntityId(), rgb);
    }
}
