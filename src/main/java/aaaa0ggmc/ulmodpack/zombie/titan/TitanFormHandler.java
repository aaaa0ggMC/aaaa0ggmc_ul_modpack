package aaaa0ggmc.ulmodpack.zombie.titan;

import aaaa0ggmc.morphext.ability.PlayerAbilityData;
import aaaa0ggmc.morphext.ability.PlayerAbilityStore;
import aaaa0ggmc.uld.IStrengthService;
import aaaa0ggmc.uld.UlStrength;
import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.faction.HateQueries;
import aaaa0ggmc.ulmodpack.zombie.ZombieForms;
import aaaa0ggmc.ulmodpack.zombie.ZombieLine;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
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
 *  - 震地：半径 4.5、伤害 = 当前攻击力 × slam_damage_multiplier（默认 1.5）、
 *    缓慢 II 3 秒、击退、冷却 15 秒、消耗饥饿，不破坏方块。
 *
 * 全部数值经 {@link UlStrength}（键 zombie_tide.titan.*）。由
 * {@link aaaa0ggmc.ulmodpack.zombie.ZombieLine#register()} 手动注册。
 */
public final class TitanFormHandler {

    private static volatile IStrengthService strength = UlStrength.service();

    public TitanFormHandler() {
    }

    public static void setStrengthService(IStrengthService service) {
        if (service != null) {
            strength = service;
        }
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
            float mult = (float) strength.num("zombie_tide.titan.dig_mult", 0.5D);
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
     * 目标过滤走核心「替玩家选作战目标」统一判据（{@code isValidTargetFor}：
     * 敌意 + 反向盟友 + 随从归属三层），不破坏方块。
     *
     * @return true = 本次已执行；false = 非泰坦形态或冷却中（H7：能力框架据此回执冷却）
     */
    public static boolean slam(EntityPlayerMP player) {
        if (!ZombieForms.isTitan(player)) {
            return false;
        }
        long now = PlayerAbilityStore.now(player);
        int cooldown = PlayerAbilityStore.get(player).getCooldown(ZombieLine.ABILITY_SLAM, now);
        if (cooldown > 0) {
            player.sendStatusMessage(new TextComponentTranslation("ul.titan.slam.cooldown"), true);
            return false;
        }

        double radius = strength.num("zombie_tide.titan.slam_radius", 4.5D);
        float damage = slamDamage(player);
        int slowTicks = strength.integer("zombie_tide.titan.slam_slow_ticks", 60);
        int slowAmplifier = strength.integer("zombie_tide.titan.slam_slow_amplifier", 1);
        float knockback = (float) strength.num("zombie_tide.titan.slam_knockback", 1.0D);
        int cooldownTicks = slamCooldownTicks();

        DamageSource source = DamageSource.causePlayerDamage(player);
        List<EntityLivingBase> targets = player.world.getEntitiesWithinAABB(EntityLivingBase.class,
                player.getEntityBoundingBox().grow(radius));
        for (EntityLivingBase target : targets) {
            // FVP-16 同型缺陷落在 ul 的一处（审计报告 §2.2 FVP-16 只列了核心
            // integration/metamorph/vanilla/ 下 5 个 AoE，漏了本处；按同一判据补入）：
            // 改前只排自己与自己的随从，队友、他人驯服宠物、阵营和平生物都会被卷进
            // AoE（注释却写「半径内敌人伤害」——文档/实现漂移）。改后走核心统一判据
            // {@code HateQueries.isValidTargetFor(player, target)}（isEnemy + 反向盟友
            // + 随从归属三层）：村民/铁傀儡按 undead 阵营判敌（尸潮屠村与守村战不受
            // 影响），自己的随从/宠物/队友与阵营和平层不再被误伤；无映射生物 MODERATE
            // 回退判敌，未适配生物不变傻（AGENTS.md 红线）。
            if (!HateQueries.isValidTargetFor(player, target)) {
                continue;
            }
            target.attackEntityFrom(source, damage);
            target.addPotionEffect(new PotionEffect(MobEffects.SLOWNESS, slowTicks, slowAmplifier, false, true));
            target.knockBack(player, knockback, player.posX - target.posX, player.posZ - target.posZ);
        }

        // 消耗饥饿（降低食物等级，不低于 0）
        int cost = (int) strength.num("zombie_tide.titan.slam_hunger_cost", 4.0D);
        int level = player.getFoodStats().getFoodLevel();
        if (level > 0) {
            player.getFoodStats().setFoodLevel(Math.max(0, level - cost));
        }

        // 形态冷却策略最外层裁决：泰坦（ul:undead）乘数 ×0.25 在此统一套用
        int scaledCooldown = aaaa0ggmc.morphext.ability.AbilityCooldownPolicy
                .scale(player, ZombieLine.ABILITY_SLAM, cooldownTicks);
        PlayerAbilityStore.update(player, data ->
                data.setCooldown(ZombieLine.ABILITY_SLAM, now, scaledCooldown));

        // 表现：落地震击粒子 + 低音爆炸声（不破坏方块）
        if (player.world instanceof WorldServer) {
            ((WorldServer) player.world).spawnParticle(EnumParticleTypes.EXPLOSION_LARGE,
                    player.posX, player.posY, player.posZ, 4, radius, 0.1D, radius, 0.0D);
        }
        player.world.playSound(null, player.posX, player.posY, player.posZ,
                SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.7F, 1.1F);
        return true;
    }

    /** 震地冷却基数（tick，档位 t2=300t/15s）；H7 执行分支回执用，框架统一套形态策略缩放。 */
    public static int slamCooldownTicks() {
        return strength.cooldownTicks("zombie_tide.titan.slam_cooldown_ticks", "t2", 300);
    }

    /**
     * 震地伤害（点）= 玩家<b>当前</b> {@code generic.attackDamage} 属性值 ×
     * {@code zombie_tide.titan.slam_damage_multiplier}（默认 1.5）。
     *
     * <p>口径（2026-11-07）：旧键 {@code slam_damage} 写死 6.0，比泰坦平A（攻击力 20.0，
     * 见 morph_abilities.json 的 giant.attackDamage）还低，震地等于自残式削弱。改为从攻击力
     * 派生后与平A/武器/力量药水/属性加成同源，倍率 1.5 = 泰坦 20 → 30 点（较平A +50%，
     * 明显更高但不至于一击清场；范围目标各自结算，仍受护甲减免与既有击退/缓慢/饥饿/冷却
     * 代价约束）。取「当前值」而非基础值，玩家侧加成全部跟随。</p>
     *
     * <p>属性实例缺失（理论不可达：玩家属性表必有该项）时按空手 1.0 兜底，不抛异常。</p>
     */
    public static float slamDamage(EntityPlayerMP player) {
        IAttributeInstance attack = player.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        double current = attack == null ? 1.0D : attack.getAttributeValue();
        double multiplier = strength.num("zombie_tide.titan.slam_damage_multiplier", 1.5D);
        return (float) (current * multiplier);
    }
}
