package aaaa0ggmc.ulmodpack.zombie.titan;

import aaaa0ggmc.morphext.ability.PlayerAbilityData;
import aaaa0ggmc.morphext.ability.PlayerAbilityStore;
import aaaa0ggmc.morphext.follower.Followers;
import aaaa0ggmc.uld.UlStrength;
import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.zombie.ZombieForms;
import aaaa0ggmc.ulmodpack.zombie.ZombieLine;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.MobEffects;
import net.minecraft.init.SoundEvents;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.WorldServer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.SleepingLocationCheckEvent;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.List;

/**
 * 泰坦（{@code minecraft:giant}）形态效果（事件侧）+ 震地主动能力。
 *
 * 形态属性（近战 +6、击退抗性 0.8、移速 ×0.75）走 morph_abilities.json 数据，不在这里；
 * 本类只做无法用属性表达的代价与主动能力：
 *  - 摔落免疫：{@link LivingFallEvent} 取消并清零 fallDistance；
 *  - 挖掘 ×0.5：{@link PlayerEvent.BreakSpeed}；
 *  - 不能睡床：{@link SleepingLocationCheckEvent} DENY（1.12.2 的 PlayerSleepInBedEvent
 *    不可取消，故用可判定结果的睡眠位置检查事件）；
 *  - 震地：半径 4.5、6 伤害、缓慢 II 3 秒、击退、冷却 15 秒、消耗饥饿，不破坏方块。
 *
 * 全部数值经 {@link UlStrength}（键 zombie_tide.titan.*）。由
 * {@link aaaa0ggmc.ulmodpack.zombie.ZombieLine#register()} 手动注册。
 */
public final class TitanFormHandler {

    public TitanFormHandler() {
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        EntityLivingBase entity = event.getEntityLiving();
        if (entity instanceof EntityPlayerMP && ZombieForms.isTitan((EntityPlayerMP) entity)) {
            event.setCanceled(true);
            entity.fallDistance = 0.0F;
        }
    }

    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntityPlayer() instanceof EntityPlayerMP
                && ZombieForms.isTitan((EntityPlayerMP) event.getEntityPlayer())) {
            float mult = (float) UlStrength.num("zombie_tide.titan.dig_mult", 0.5D);
            event.setNewSpeed(event.getOriginalSpeed() * mult);
        }
    }

    @SubscribeEvent
    public static void onSleepCheck(SleepingLocationCheckEvent event) {
        if (event.getEntityPlayer() instanceof EntityPlayerMP
                && ZombieForms.isTitan((EntityPlayerMP) event.getEntityPlayer())) {
            event.setResult(Event.Result.DENY);
            ((EntityPlayerMP) event.getEntityPlayer())
                    .sendStatusMessage(new TextComponentTranslation("ul.titan.bed"), true);
        }
    }

    // ============================================================ 震地

    /**
     * 震地（泰坦主动能力）：半径内敌人伤害 + 缓慢 + 击退，消耗饥饿，15 秒冷却。
     * 排除自己与自己的随从；不破坏方块。
     */
    public static void slam(EntityPlayerMP player) {
        if (!ZombieForms.isTitan(player)) {
            return;
        }
        long now = PlayerAbilityStore.now(player);
        int cooldown = PlayerAbilityStore.get(player).getCooldown(ZombieLine.ABILITY_SLAM, now);
        if (cooldown > 0) {
            player.sendStatusMessage(new TextComponentTranslation("ul.titan.slam.cooldown"), true);
            return;
        }

        double radius = UlStrength.num("zombie_tide.titan.slam_radius", 4.5D);
        float damage = (float) UlStrength.num("zombie_tide.titan.slam_damage", 6.0D);
        int slowTicks = UlStrength.integer("zombie_tide.titan.slam_slow_ticks", 60);
        int slowAmplifier = UlStrength.integer("zombie_tide.titan.slam_slow_amplifier", 1);
        float knockback = (float) UlStrength.num("zombie_tide.titan.slam_knockback", 1.0D);
        int cooldownTicks = UlStrength.integer("zombie_tide.titan.slam_cooldown_ticks", 300);

        DamageSource source = DamageSource.causePlayerDamage(player);
        List<EntityLivingBase> targets = player.world.getEntitiesWithinAABB(EntityLivingBase.class,
                player.getEntityBoundingBox().grow(radius));
        for (EntityLivingBase target : targets) {
            if (target == player || Followers.isFollower(target)) {
                continue; // 不打自己与自己的随从
            }
            target.attackEntityFrom(source, damage);
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, slowTicks, slowAmplifier, false, true));
            target.knockBack(player, knockback, target.posX - player.posX, target.posZ - player.posZ);
        }

        // 消耗饥饿（降低食物等级，不低于 0）
        int cost = (int) UlStrength.num("zombie_tide.titan.slam_hunger_cost", 4.0D);
        int level = player.getFoodStats().getFoodLevel();
        if (level > 0) {
            player.getFoodStats().setFoodLevel(Math.max(0, level - cost));
        }

        PlayerAbilityStore.update(player, data ->
                data.setCooldown(ZombieLine.ABILITY_SLAM, now, cooldownTicks));

        // 表现：落地震击粒子 + 低音爆炸声（不破坏方块）
        if (player.world instanceof WorldServer) {
            ((WorldServer) player.world).spawnParticle(EnumParticleTypes.EXPLOSION_LARGE,
                    player.posX, player.posY, player.posZ, 4, radius, 0.1D, radius, 0.0D);
        }
        player.world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.7F, 1.1F);
    }
}
