package aaaa0ggmc.ulmodpack.zombie.titan;

import aaaa0ggmc.morphext.conversion.MorphFactory;
import aaaa0ggmc.uld.UlStrength;
import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.zombie.ZombieForms;
import aaaa0ggmc.ulmodpack.zombie.ZombieLine;
import aaaa0ggmc.ulmodpack.zombie.horde.HordeManager;
import mchorse.metamorph.api.MorphAPI;
import mchorse.metamorph.api.morphs.AbstractMorph;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraft.util.SoundCategory;

/**
 * 泰坦「 Feast 」：吃下热忱的腐肉 → 非死亡变泰坦（{@code minecraft:giant}）。
 *
 * 本线唯一规则豁免：不走死亡结算。前置：已解锁尸群领导 + 至少通关一次尸潮
 * （{@code tides_cleared ≥ 1}）+ 头顶 12 格净空 + 当前形态属 {@code ul:undead}。
 * 变身是强制 morph + 回满血；体型（碰撞箱 6 倍）留待 morphext-patch-giant，当前只用属性 + 特效。
 *
 * 由 {@code item.ItemZealousFlesh} 调用：{@link #checkFailure} 在右键预检（不满足提示且不消耗），
 * {@link #transform} 在进食完成时执行（服务端）。
 */
public final class TitanFeast {

    private TitanFeast() {
    }

    /** 预检：返回失败提示 lang key；{@code null} = 满足全部条件。 */
    public static String checkFailure(EntityPlayerMP player) {
        if (!ZombieForms.hasLeadership(player)) {
            return "ul.titan.feast.fail.skill";
        }
        if (HordeManager.tidesCleared(player) < 1) {
            return "ul.titan.feast.fail.tide";
        }
        if (!hasHeadroom(player)) {
            return "ul.titan.feast.fail.room";
        }
        if (!ZombieForms.isUndead(player)) {
            return "ul.titan.feast.fail.form";
        }
        return null;
    }

    /** 满足条件：强制变泰坦、回满血、发提示与特效。服务端调用。 */
    public static void transform(EntityPlayerMP player) {
        AbstractMorph morph = MorphFactory.buildFromFormId(ZombieLine.FORM_TITAN, player.world);
        if (morph != null) {
            MorphAPI.acquire(player, morph);
            MorphAPI.morph(player, morph, true);
        } else {
            UlModpack.LOGGER.warn("[ul-modpack] No giant morph available — titan transform skipped");
        }
        // 形态会改写最大生命，变身之后回满
        player.setHealth(player.getMaxHealth());
        player.sendStatusMessage(new TextComponentTranslation("ul.titan.feast.ok"), true);

        if (player.world instanceof WorldServer) {
            WorldServer world = (WorldServer) player.world;
            world.spawnParticle(EnumParticleTypes.TOTEM, player.posX, player.posY + 1.0D, player.posZ,
                    60, 0.8D, 1.2D, 0.8D, 0.1D);
            player.world.playSound(null, player.posX, player.posY, player.posZ,
                    SoundEvents.ENTITY_ZOMBIE_INFECT, SoundCategory.PLAYERS, 1.0F, 0.6F);
        }
    }

    /** 头顶 headroom 格是否净空（泰坦约 11.7 格高，需 12 格垂直空间）。 */
    private static boolean hasHeadroom(EntityPlayerMP player) {
        int headroom = UlStrength.integer("zombie_tide.titan.headroom", 12);
        BlockPos base = new BlockPos(player.posX, player.posY, player.posZ);
        for (int i = 1; i <= headroom; i++) {
            if (!player.world.isAirBlock(base.up(i))) {
                return false;
            }
        }
        return true;
    }
}
