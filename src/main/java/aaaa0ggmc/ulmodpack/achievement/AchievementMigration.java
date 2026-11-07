package aaaa0ggmc.ulmodpack.achievement;

import aaaa0ggmc.ulmodpack.UlModpack;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

/**
 * 旧存档一次性迁移挂点：玩家登录时把自定义 toast 时代的成就解锁表转成原版 advancement。
 *
 * <p>时机：{@code PlayerLoggedInEvent}（{@code PlayerList.initializeConnectionToPlayer}
 * 尾部）——此时 {@code EntityPlayerMP} 已构造完（其构造函数已建好并 load 了
 * {@code PlayerAdvancements}），morphext 玩家数据也可读，且早于首个 tick 的
 * {@code flushDirty}（首个 advancement 同步包带上迁移结果，原版按 first sync 不弹 toast）。
 * 与 {@code TitanSlamMigration} 同一时机与同一写法。</p>
 *
 * <p>幂等：迁移本身只读旧键、授予后整键清除；无旧键时完全不写盘。数据损坏/非主线程
 * 不阻断登录（只告警），下次登录会重试。</p>
 */
public final class AchievementMigration {

    public AchievementMigration() {
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player == null || event.player.world == null || event.player.world.isRemote
                || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP mp = (EntityPlayerMP) event.player;
        try {
            if (Achievements.migrateLegacyState(mp) > 0) {
                UlModpack.LOGGER.info("[ul-modpack] Legacy achievements migrated for {}", mp.getName());
            }
        } catch (RuntimeException error) {
            UlModpack.LOGGER.warn("[ul-modpack] legacy achievement migration skipped: {}", error.toString());
        }
    }
}
