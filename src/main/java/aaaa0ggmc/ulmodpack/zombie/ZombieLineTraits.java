package aaaa0ggmc.ulmodpack.zombie;

import aaaa0ggmc.ulmodpack.UlModpack;
import net.minecraft.entity.EntityList;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 僵尸线（zombie_tide）的实体侧特性。
 *
 * 尸壳实体免疫窒息伤害——这是线内容（原版实体微调），放在整合包内容模组里；
 * 玩家处于尸壳形态时的同款免疫走 morphext 的通用机制
 * （assets/aaaa0ggmc_ul_modpack/morphext/morph_abilities.json 的 damageImmunities），
 * 两边行为一致、机制不重复实现。
 */
@Mod.EventBusSubscriber(modid = UlModpack.MODID)
public class ZombieLineTraits {

    private ZombieLineTraits() {
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.isCanceled() || event.getSource() != DamageSource.IN_WALL) {
            return;
        }
        if ("minecraft:husk".equals(String.valueOf(EntityList.getKey(event.getEntityLiving())))) {
            event.setCanceled(true);
        }
    }
}
