package aaaa0ggmc.ulmodpack.convert;

import aaaa0ggmc.ulmodpack.UlModpack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityHusk;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.DamageSource;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 僵尸转化仪式的事件入口：
 *  - {@link LivingDeathEvent}：仪式判定——死亡符合条件（物品 + 群系 + 死法）时
 *    取消死亡并换形态（见 {@link ZombieConversion}）；
 *  - {@link LivingAttackEvent}：尸壳特性——尸壳实体与尸壳形态的玩家免疫窒息伤害。
 */
@Mod.EventBusSubscriber(modid = UlModpack.MODID)
public class ZombieConversionHandler {

    private ZombieConversionHandler() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntityLiving().world.isRemote) {
            return;
        }
        if (!(event.getEntityLiving() instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityLiving();
        Class<? extends EntityLivingBase> target = ZombieConversion.ritualTarget(player, event.getSource());
        if (target == null) {
            if (ZombieConversion.isZombieForm(player)) {
                // 僵尸形态的死亡但仪式没达成：把条件取值打出来方便排障
                UlModpack.LOGGER.info("[ul-modpack] ritual miss: {}", ZombieConversion.diagnose(player, event.getSource()));
            }
            return;
        }

        event.setCanceled(true);
        player.getCombatTracker().reset();
        player.extinguish();
        player.fallDistance = 0.0F;

        Entity temp = EntityList.newEntity(target, player.world);
        String name = temp instanceof EntityLivingBase ? ((EntityLivingBase) temp).getName() : "";

        ZombieConversion.consumeRitualItem(player, target);
        if (target == EntityHusk.class) {
            ZombieConversion.burstSand(player);
        }
        if (ZombieConversion.convertPlayerTo(player, target)) {
            player.sendStatusMessage(new TextComponentTranslation("aaaa0ggmc_ul_modpack.msg.infected", name), true);
            UlModpack.LOGGER.info("[ul-modpack] Ritual converted {} into {} ({})",
                    player.getName(), name, target.getSimpleName());
        }
    }

    /** 尸壳特性：免疫窒息伤害（尸壳实体、尸壳形态的玩家）。 */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getSource() != DamageSource.IN_WALL) {
            return;
        }
        EntityLivingBase entity = event.getEntityLiving();
        if (entity.getClass() == EntityHusk.class
                || (entity instanceof EntityPlayerMP
                    && ZombieConversion.isForm((EntityPlayerMP) entity, "minecraft:husk"))) {
            event.setCanceled(true);
        }
    }
}
