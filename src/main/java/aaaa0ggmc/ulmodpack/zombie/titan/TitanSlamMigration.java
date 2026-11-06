package aaaa0ggmc.ulmodpack.zombie.titan;

import aaaa0ggmc.morphext.ability.PlayerAbilityData;
import aaaa0ggmc.morphext.ability.PlayerAbilityStore;
import aaaa0ggmc.morphext.network.AbilityNetwork;
import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.zombie.ZombieLine;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * 旧存档一次性迁移：{@code titan_slam} 已从「horde_leadership 技能全局解锁」改为
 * 「{@code minecraft:giant} 形态作用域」（morph_abilities.json 的 giant.actions）。
 *
 * <p>老存档的全局 unlocked 里可能仍留有该 id，会让它在其它僵尸形态也显示。
 * 玩家登录时：unlocked 含该 id → 用核心通用 {@link PlayerAbilityData#clearUnlocked(String)}
 * 移除（不写 removed，不碰 states/cooldowns）并立即 {@link AbilityNetwork#syncPlayer}
 * 推送新快照。不含时只读一次、不写盘，幂等。</p>
 *
 * <p>id 属于内容层（{@link ZombieLine#ABILITY_SLAM}），核心不写死任何具体能力。</p>
 */
public final class TitanSlamMigration {

    public TitanSlamMigration() {
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        EntityPlayer player = event.player;
        if (player == null || player.world == null || player.world.isRemote
                || !(player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP mp = (EntityPlayerMP) player;
        try {
            PlayerAbilityData data = PlayerAbilityStore.get(mp);
            if (!data.isUnlocked(ZombieLine.ABILITY_SLAM)) {
                return;
            }
            PlayerAbilityStore.update(mp, next -> next.clearUnlocked(ZombieLine.ABILITY_SLAM));
            AbilityNetwork.syncPlayer(mp, "");
            UlModpack.LOGGER.info("[ul-modpack] Migrated legacy global unlock of {} to giant morph scope",
                    ZombieLine.ABILITY_SLAM);
        } catch (RuntimeException error) {
            // 数据损坏或非主线程时不阻断登录；核心自身的登录同步仍会尝试加载。
            UlModpack.LOGGER.warn("[ul-modpack] titan_slam legacy migration skipped: {}", error.toString());
        }
    }
}
