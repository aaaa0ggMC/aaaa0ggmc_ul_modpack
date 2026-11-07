package aaaa0ggmc.ulmodpack.zombie;

import aaaa0ggmc.morphext.form.FormCategories;
import aaaa0ggmc.morphext.skill.SkillRegistry;
import mchorse.metamorph.capabilities.morphing.IMorphing;
import mchorse.metamorph.capabilities.morphing.Morphing;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * 不死者线共用的形态 / 技能判定helper。
 *
 * 形态侧统一读 Metamorph 的当前 morph 名，类别判定走 morphext 通用
 * {@link FormCategories}（数据见 form_categories.json），不在这里维护实体名单；
 * 技能侧走 {@link SkillRegistry}。客户端不调用这些方法。
 */
public final class ZombieForms {

    private ZombieForms() {
    }

    /** 当前形态 id；未变身返回空串。 */
    public static String currentFormId(EntityPlayerMP player) {
        IMorphing morphing = Morphing.get(player);
        if (morphing != null && morphing.isMorphed() && morphing.getCurrentMorph() != null) {
            return String.valueOf(morphing.getCurrentMorph().name);
        }
        return "";
    }

    /** 当前形态是否属于给定类别（ul:undead 等）。 */
    public static boolean hasCategory(EntityPlayerMP player, String category) {
        String form = currentFormId(player);
        return !form.isEmpty() && FormCategories.has(form, category);
    }

    /** 是否处于不死者形态。 */
    public static boolean isUndead(EntityPlayerMP player) {
        return hasCategory(player, ZombieLine.CATEGORY_UNDEAD);
    }

    /** 是否处于泰坦（巨人）形态。 */
    public static boolean isTitan(EntityPlayerMP player) {
        return ZombieLine.FORM_TITAN.equals(currentFormId(player));
    }

    /** 是否已解锁尸群领导技能。 */
    public static boolean hasLeadership(EntityPlayerMP player) {
        return SkillRegistry.has(player, ZombieLine.SKILL_HORDE_LEADERSHIP);
    }

    /**
     * 尸群指挥能力（{@link ZombieLine#ABILITY_COMMAND}）此刻是否被当前形态授予
     * （2026-11-07 状态页面板显隐判据）：尸群领导技能 + 不死者形态，二者同时满足。
     *
     * <p>与服务端释放链的可用性判定同源——{@code HordeAbilities.HordeAbilityHandler}
     * 对 {@code horde_command} 的 {@code isAvailable} 就是本方法，保证「面板可见
     * ⟺ 服务端接受释放」。面板显隐只用本判据，不再用「有领导或有随从」兜底：
     * 后者在取消变形后仍为真（技能持久、随从是世界存档状态），面板会一直挂着。</p>
     */
    public static boolean canCommandHorde(EntityPlayerMP player) {
        return hasLeadership(player) && isUndead(player);
    }
}
