package aaaa0ggmc.ulmodpack.zombie.horde;

import aaaa0ggmc.morphext.ability.PlayerAbilityData;
import aaaa0ggmc.morphext.ability.PlayerAbilityStore;
import aaaa0ggmc.morphext.follower.Followers;
import aaaa0ggmc.morphext.network.AbilityNetwork;
import aaaa0ggmc.uld.UlStrength;
import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.proxy.CommonProxy;
import aaaa0ggmc.ulmodpack.zombie.ZombieForms;
import aaaa0ggmc.ulmodpack.zombie.ZombieLine;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityZombie;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.village.Village;
import net.minecraft.village.VillageCollection;
import net.minecraft.world.World;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 尸潮系统（尸群领导 + 屠村）核心逻辑。
 *
 * 触发由 {@link HordeEvents}（潜行右键方块/实体）与 {@link HordeNetwork}（手势客户端上报，
 * ul:ability 包）分发进来；本类只做服务端权威判定与状态推进，不含事件订阅。
 *
 * 流程（用户设计 2026-11-03 重做）：招募（对着僵尸使用技能，说服度决定条件/上限/随从
 * 倍率；不再要求夜晚与移动）→ 解散（对着空气潜行使用技能，明确动作）→ 发动（能力轮盘 /
 * 命令，32 格内活村民 ≥4 的村庄进入屠村）→ tick（每 20 tick：刷铁傀儡、判成功/中止）
 * → 收尾（成功 count+1、说服度 ±、随从散开）。玩家死亡=失败（见 onLivingDeath，
 * 即使被仪式接住）。
 *
 * 全部数值经 {@link UlStrength}（键 zombie_tide.horde.*），兜底默认与 defaults.json /
 * 模组 ul_strength/zombie_tide.json 一致。只存 UUID 与坐标（{@link HordeState}），
 * 随从关系随 {@link Followers} 的 NBT 持久化。队伍快照经 {@link HordeNetwork} S2C
 * 推送到客户端缓存，供 B 键状态页展示。
 */
public final class HordeManager {

    /** 随从说服度属性修饰符固定 UUID（生命/攻击，重复套用/移除幂等）。 */
    private static final UUID FOLLOWER_HEALTH_UUID = UUID.fromString("b16ef011-0000-4000-8000-000000000001");
    private static final UUID FOLLOWER_ATTACK_UUID = UUID.fromString("b16ef011-0000-4000-8000-000000000002");

    private HordeManager() {
    }

    // ============================================================ 招募

    /**
     * 招募入口（能力框架兜底 / 演示脚本）：优先招募视线内的僵尸族目标，
     * 没有目标时退回就近批量招募（旧语义，兼容 {@code demo-zombie-tide.sh} 的
     * {@code invoke tryRecruit}）。手势路径带目标实体 id，直接走
     * {@link #tryRecruitAt(EntityPlayerMP, EntityLiving)}。
     */
    public static void tryRecruit(EntityPlayerMP player) {
        EntityLiving target = findLookedZombie(player);
        if (target != null) {
            tryRecruitAt(player, target);
        } else {
            tryRecruitNearby(player);
        }
    }

    /**
     * 招募指定僵尸族目标（新触发：对着僵尸使用技能，客户端上报目标实体 id）。
     *
     * 规则（用户设计）：去掉旧的「必须夜晚」「必须移动」两个门槛，保留冷却与说服度匹配——
     * 说服度 &lt; thresholdMid 时目标须残血到 hurtBelow 比例以下才能招；
     * &lt; thresholdLow 时还要消耗腐肉；人数上限 followCap。
     */
    public static void tryRecruitAt(EntityPlayerMP player, EntityLiving target) {
        if (!ZombieForms.hasLeadership(player)) {
            action(player, "ul.horde.fail.skill");
            return;
        }
        if (!ZombieForms.isUndead(player)) {
            action(player, "ul.horde.fail.form");
            return;
        }
        if (onCooldown(player, ZombieLine.ABILITY_RECRUIT)) {
            return; // 8 秒冷却，静默限流
        }
        double reach = recruitReach();
        if (target == null || target.isDead || !isZombieFamily(target)) {
            action(player, "ul.horde.recruit.fail.target");
            return;
        }
        if (player.getDistanceSq(target) > reach * reach) {
            action(player, "ul.horde.recruit.fail.target"); // 服务端距离校验，不信客户端
            return;
        }
        if (Followers.isFollower(target)) {
            action(player, "ul.horde.recruit.fail.own");
            return;
        }
        int persuasion = Persuasion.get(player);
        int cap = Persuasion.followCap(persuasion);
        if (cap - countFollowers(player) <= 0) {
            action(player, "ul.horde.recruit.fail.cap");
            return;
        }
        boolean needHurt = persuasion < Persuasion.thresholdMid();
        boolean needFlesh = persuasion < Persuasion.thresholdLow();
        double hurtBelow = UlStrength.num("zombie_tide.horde.persuasion_hurt_below", 0.5D);
        int fleshCost = UlStrength.integer("zombie_tide.horde.recruit_flesh_cost", 1);
        if (needFlesh && countItem(player, Items.ROTTEN_FLESH) < fleshCost) {
            action(player, "ul.horde.recruit.fail.flesh");
            return;
        }
        // 说服度 <50：目标须先打残（生命 < hurtBelow 比例）
        if (needHurt && target.getHealth() >= target.getMaxHealth() * hurtBelow) {
            action(player, "ul.horde.recruit.fail.persuasion");
            return;
        }
        Followers.setOwner(target, player.getUniqueID(), player.world.getTotalWorldTime());
        applyFollowerMultiplier(target, Persuasion.multiplier(persuasion));
        if (needFlesh) {
            consumeItem(player, Items.ROTTEN_FLESH, fleshCost);
        }
        setCooldown(player, ZombieLine.ABILITY_RECRUIT, recruitCooldownTicks());
        action(player, "ul.horde.recruit.ok", 1);
        syncTeam(player);
    }

    /**
     * 就近批量招募（旧语义保留：能力框架对空气释放 / 演示脚本兼容）。
     * 门槛与 {@link #tryRecruitAt} 一致：无夜晚/移动要求，按说服度匹配。
     */
    private static void tryRecruitNearby(EntityPlayerMP player) {
        if (!ZombieForms.hasLeadership(player)) {
            action(player, "ul.horde.fail.skill");
            return;
        }
        if (!ZombieForms.isUndead(player)) {
            action(player, "ul.horde.fail.form");
            return;
        }
        if (onCooldown(player, ZombieLine.ABILITY_RECRUIT)) {
            return; // 8 秒冷却，静默限流
        }

        int persuasion = Persuasion.get(player);
        int cap = Persuasion.followCap(persuasion);
        int room = cap - countFollowers(player);
        if (room <= 0) {
            action(player, "ul.horde.recruit.fail.cap");
            return;
        }

        boolean needHurt = persuasion < Persuasion.thresholdMid();
        boolean needFlesh = persuasion < Persuasion.thresholdLow();
        double hurtBelow = UlStrength.num("zombie_tide.horde.persuasion_hurt_below", 0.5D);
        int fleshCost = UlStrength.integer("zombie_tide.horde.recruit_flesh_cost", 1);

        // 说服度 <20 要搭一块腐肉：先预检，不满足提示且不招募
        if (needFlesh && countItem(player, Items.ROTTEN_FLESH) < fleshCost) {
            action(player, "ul.horde.recruit.fail.flesh");
            return;
        }

        List<EntityLiving> candidates = zombiesNear(player);
        org.apache.logging.log4j.LogManager.getLogger("ulmodpack")
                .info("[horde] recruit: candidates=" + candidates.size() + " room=" + room + " needHurt=" + needHurt);
        double multiplier = Persuasion.multiplier(persuasion);
        int recruited = 0;
        for (EntityLiving zombie : candidates) {
            if (recruited >= room) {
                break;
            }
            if (Followers.isFollower(zombie)) {
                continue;
            }
            // 说服度 <50：目标须先打残（生命 < hurtBelow 比例）
            if (needHurt && zombie.getHealth() >= zombie.getMaxHealth() * hurtBelow) {
                continue;
            }
            Followers.setOwner(zombie, player.getUniqueID(), player.world.getTotalWorldTime());
            applyFollowerMultiplier(zombie, multiplier);
            recruited++;
        }

        if (recruited <= 0) {
            action(player, needHurt ? "ul.horde.recruit.fail.persuasion" : "ul.horde.recruit.fail.none");
            return;
        }
        if (needFlesh) {
            consumeItem(player, Items.ROTTEN_FLESH, fleshCost);
        }
        setCooldown(player, ZombieLine.ABILITY_RECRUIT, recruitCooldownTicks());
        action(player, "ul.horde.recruit.ok", recruited);
        syncTeam(player);
    }

    /** 招募射程（服务端权威距离校验）。 */
    private static double recruitReach() {
        return UlStrength.num("zombie_tide.horde.recruit_reach", 4.5D);
    }

    /**
     * 视线内（recruit_reach）最近的僵尸族实体——能力框架兜底入口的目标解析
     * （手势路径客户端 ray trace 后直接上报目标 id，不走这里）。
     */
    private static EntityLiving findLookedZombie(EntityPlayerMP player) {
        double reach = recruitReach();
        net.minecraft.util.math.Vec3d eye = player.getPositionEyes(1.0F);
        net.minecraft.util.math.Vec3d look = player.getLook(1.0F);
        net.minecraft.util.math.Vec3d end = eye.add(look.x * reach, look.y * reach, look.z * reach);
        // 方块遮挡：取方块命中距离为上限，不隔墙招募
        net.minecraft.util.math.RayTraceResult blockHit = player.rayTrace(reach, 1.0F);
        double bestDist = blockHit == null || blockHit.hitVec == null
                ? reach * reach : blockHit.hitVec.squareDistanceTo(eye);
        EntityLiving best = null;
        for (Entity e : player.world.getEntitiesWithinAABBExcludingEntity(player,
                player.getEntityBoundingBox().grow(reach))) {
            if (!isZombieFamily(e) || e.isDead) {
                continue;
            }
            AxisAlignedBB bounds = e.getEntityBoundingBox();
            if (bounds == null) {
                continue;
            }
            net.minecraft.util.math.RayTraceResult hit = bounds.calculateIntercept(eye, end);
            if (hit == null || hit.hitVec == null) {
                continue;
            }
            double d = hit.hitVec.squareDistanceTo(eye);
            if (d <= bestDist) {
                bestDist = d;
                best = (EntityLiving) e;
            }
        }
        return best;
    }

    // ============================================================ 发动 / 解放（能力框架入口）

    /**
     * 能力框架兜底入口（轮盘「发动尸潮」/「震地」与 {@code demo-zombie-tide.sh} 的
     * {@code invoke onAirUse} 共用）：泰坦形态 → 震地；否则走尸群领导的发动/解放分支
     * （有随从且有有效村庄=发动，有随从无村庄=解放，无随从有村庄=提示招募，都无=解散）。
     *
     * <p>手势语义已重做（用户设计）：「对着空气潜行使用技能」不再进本方法，而是明确的
     * {@link #disband}（解散队伍）；招募见 {@link #tryRecruitAt}。
     */
    public static void onAirUse(EntityPlayerMP player) {
        if (!ZombieForms.hasLeadership(player)) {
            action(player, "ul.horde.fail.skill");
            return;
        }
        if (!ZombieForms.isUndead(player)) {
            action(player, "ul.horde.fail.form");
            return;
        }
        HordeState state = HordeState.get(player.world);
        HordeState.Entry entry = state.getOrCreate(player.getUniqueID());
        if (entry.active) {
            action(player, "ul.horde.launch.fail.active");
            return;
        }
        boolean titan = ZombieForms.isTitan(player);
        int followers = countFollowers(player);
        BlockPos village = findVillageCenter(player);
        // 有随从 + 有效村庄 → 发动屠村（泰坦也能带队）
        if (followers > 0 && village != null) {
            launch(player, entry, state, village);
            return;
        }
        // 泰坦（未在带队屠村）→ 震地
        if (titan) {
            aaaa0ggmc.ulmodpack.zombie.titan.TitanFormHandler.slam(player);
            return;
        }
        // 非泰坦：有随从无村庄 → 解放；无随从有村庄 → 提示招募；都无 → 解散
        if (followers > 0) {
            release(player);
        } else if (village != null) {
            action(player, "ul.horde.launch.fail.empty");
        } else {
            release(player);
        }
    }

    /** 进入屠村：锁定村庄中心、初始化会话。 */
    private static void launch(EntityPlayerMP player, HordeState.Entry entry, HordeState state, BlockPos center) {
        entry.active = true;
        entry.villageCenter = center;
        entry.startTick = player.world.getTotalWorldTime();
        entry.lastGolemSpawnTick = 0L;
        entry.abortSinceTick = 0L;
        state.put(player.getUniqueID(), entry);
        action(player, "ul.horde.launch");
    }

    /** 解放队伍：移除随从标签、散开恢复野生。 */
    public static void release(EntityPlayerMP player) {
        releaseFollowers(player);
        action(player, "ul.horde.release");
        syncTeam(player);
    }

    /**
     * 解散当前队伍（明确的解散动作：对着空气潜行使用技能，用户设计 2026-11-03）。
     * 复用原「无随从无村庄=解散」分支的散开语义；进行中的尸潮一并收尾
     * （同「中止」不扣说服度）。
     */
    public static void disband(EntityPlayerMP player) {
        HordeState state = HordeState.get(player.world);
        HordeState.Entry entry = state.get(player.getUniqueID());
        int before = countFollowers(player);
        boolean endedTide = entry != null && entry.active;
        if (endedTide) {
            endTide(player, entry, state); // 收尾含散开随从
        } else {
            releaseFollowers(player);
        }
        if (endedTide || before > 0) {
            action(player, "ul.horde.disband");
        } else {
            action(player, "ul.horde.disband.none");
        }
        syncTeam(player);
    }

    // ============================================================ tick（每 20 tick 推进）

    /** 世界 tick：推进本维度所有进行中的尸潮。 */
    public static void tick(World world) {
        if (world.isRemote) {
            return;
        }
        long interval = (long) UlStrength.num("zombie_tide.horde.tick_interval", 20.0D);
        if (interval <= 0 || world.getTotalWorldTime() % (long) interval != 0) {
            return;
        }
        HordeState state = HordeState.get(world);
        // 快照遍历，避免遍历中修改
        List<Map.Entry<UUID, HordeState.Entry>> snapshot = new ArrayList<>();
        for (Map.Entry<UUID, HordeState.Entry> e : state.snapshotEntries().entrySet()) {
            snapshot.add(e);
        }
        for (Map.Entry<UUID, HordeState.Entry> e : snapshot) {
            HordeState.Entry entry = e.getValue();
            if (!entry.active || entry.villageCenter == null) {
                continue;
            }
            EntityPlayer player = world.getPlayerEntityByUUID(e.getKey());
            if (!(player instanceof EntityPlayerMP)) {
                continue; // 主人不在线/不在本维度：暂停（不判失败）
            }
            EntityPlayerMP owner = (EntityPlayerMP) player;

            maybeSpawnGolem(world, entry);
            commandFollowers(owner, entry);

            // 成功：活村民清零
            if (countVillagers(world, entry.villageCenter) <= 0) {
                succeed(owner, entry, state);
                continue;
            }
            // 中止：远离村庄超过阈值持续 abort_ticks
            double abortDist = UlStrength.num("zombie_tide.horde.abort_distance", 48.0D);
            long abortTicks = (long) UlStrength.num("zombie_tide.horde.abort_ticks", 600.0D);
            boolean far = owner.world != world
                    || owner.getDistanceSq(entry.villageCenter) > abortDist * abortDist;
            if (far) {
                if (entry.abortSinceTick == 0L) {
                    entry.abortSinceTick = world.getTotalWorldTime();
                } else if (world.getTotalWorldTime() - entry.abortSinceTick >= abortTicks) {
                    abort(owner, entry, state);
                    continue;
                }
            } else {
                entry.abortSinceTick = 0L;
            }
            state.put(e.getKey(), entry);
        }
    }

    /** 尸潮期间每 golem_interval tick 尝试刷 1 只铁傀儡，同时存活 ≤ golem_max_alive。 */
    /**
     * 尸潮作战指令：随从僵尸跟随 AI 只跟随不作战（实体 AI 优先级被跟随任务占着），
     * 每轮给它们指派最近的活村民为攻击目标——「带队屠村」的作战语义在这里。
     * 铁傀儡会还击随从（原版守村 AI），随从被铁傀儡打死掉热忱的腐肉。
     */
    private static void commandFollowers(EntityPlayerMP owner, HordeState.Entry entry) {
        World world = owner.world;
        EntityVillager target = null;
        double best = Double.MAX_VALUE;
        for (EntityVillager v : world.getEntitiesWithinAABB(EntityVillager.class,
                new AxisAlignedBB(entry.villageCenter).grow(16.0D))) {
            if (v.isDead) {
                continue;
            }
            double d = v.getDistanceSq(entry.villageCenter);
            if (d < best) {
                best = d;
                target = v;
            }
        }
        if (target == null) {
            return;
        }
        for (Entity entity : world.getEntitiesWithinAABB(EntityLiving.class,
                owner.getEntityBoundingBox().grow(64.0D))) {
            if (isZombieFamily(entity) && Followers.isFollower(entity)) {
                EntityLiving z = (EntityLiving) entity;
                if (z.getAttackTarget() == null || z.getAttackTarget().isDead) {
                    z.setAttackTarget(target);
                }
            }
        }
    }

    private static void maybeSpawnGolem(World world, HordeState.Entry entry) {
        long now = world.getTotalWorldTime();
        long interval = (long) UlStrength.num("zombie_tide.horde.golem_interval_ticks", 100.0D);
        if (entry.lastGolemSpawnTick != 0L && now - entry.lastGolemSpawnTick < interval) {
            return;
        }
        entry.lastGolemSpawnTick = now;
        int maxAlive = UlStrength.integer("zombie_tide.horde.golem_max_alive", 9);
        if (countGolems(world, entry.villageCenter) >= maxAlive) {
            return;
        }
        BlockPos pos = findGolemSpawn(world, entry.villageCenter);
        if (pos == null) {
            return;
        }
        EntityIronGolem golem = new EntityIronGolem(world);
        golem.setLocationAndAngles(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D,
                world.rand.nextFloat() * 360.0F, 0.0F);
        golem.setPlayerCreated(false);
        world.spawnEntity(golem);
    }

    // ============================================================ 收尾

    private static void succeed(EntityPlayerMP player, HordeState.Entry entry, HordeState state) {
        incrementTideCleared(player);
        Persuasion.add(player, UlStrength.num("zombie_tide.horde.persuasion_win", 10.0D));
        endTide(player, entry, state);
        chat(player, "ul.horde.win");
    }

    /** 玩家死亡 = 尸潮失败（LivingDeathEvent 里调用，即使死亡随后被仪式接住）。 */
    public static void onOwnerDeath(EntityPlayerMP player) {
        HordeState state = HordeState.get(player.world);
        HordeState.Entry entry = state.get(player.getUniqueID());
        if (entry != null && entry.active) {
            Persuasion.add(player, UlStrength.num("zombie_tide.horde.persuasion_fail", -20.0D));
            endTide(player, entry, state);
            chat(player, "ul.horde.fail");
        }
    }

    private static void abort(EntityPlayerMP player, HordeState.Entry entry, HordeState state) {
        endTide(player, entry, state);
        chat(player, "ul.horde.abort"); // 中止不扣说服度
    }

    /** 结束/中止收尾：清 active、散开随从。 */
    private static void endTide(EntityPlayerMP player, HordeState.Entry entry, HordeState state) {
        entry.active = false;
        entry.villageCenter = null;
        entry.abortSinceTick = 0L;
        state.put(player.getUniqueID(), entry);
        releaseFollowers(player);
        syncTeam(player);
    }

    // ============================================================ 调试 / 状态（命令用）

    /** 调试：强制在村民聚集地发动屠村（忽略队伍/村民数检查）。 */
    public static void forceStart(EntityPlayerMP player) {
        double radius = UlStrength.num("zombie_tide.horde.village_radius", 32.0D);
        // 与正式发动同一套「村民聚集即村庄」判定；调试版把人数下限压到 1
        List<EntityVillager> villagers = player.world.getEntitiesWithinAABB(
                EntityVillager.class, player.getEntityBoundingBox().grow(radius));
        BlockPos center = null;
        if (!villagers.isEmpty()) {
            double sx = 0, sy = 0, sz = 0;
            int n = 0;
            for (EntityVillager v : villagers) {
                if (v.isDead) {
                    continue;
                }
                n++;
                sx += v.posX;
                sy += v.posY;
                sz += v.posZ;
            }
            if (n > 0) {
                center = new BlockPos(sx / n, sy / n, sz / n);
            }
        }
        if (center == null) {
            action(player, "ul.horde.launch.fail.place");
            return;
        }
        HordeState state = HordeState.get(player.world);
        HordeState.Entry entry = state.getOrCreate(player.getUniqueID());
        entry.active = true;
        entry.villageCenter = center;
        entry.startTick = player.world.getTotalWorldTime();
        entry.lastGolemSpawnTick = 0L;
        entry.abortSinceTick = 0L;
        state.put(player.getUniqueID(), entry);
        action(player, "ul.horde.launch");
    }

    /** 调试：强制结束当前尸潮并解散随从。 */
    public static void forceStop(EntityPlayerMP player) {
        HordeState state = HordeState.get(player.world);
        HordeState.Entry entry = state.get(player.getUniqueID());
        if (entry != null && entry.active) {
            endTide(player, entry, state);
        } else {
            releaseFollowers(player);
        }
        action(player, "ul.horde.release");
    }

    /** 当前随从数量（命令/状态用）。 */
    public static int followerCount(EntityPlayerMP player) {
        return countFollowers(player);
    }

    /** 当前是否有进行中的尸潮（命令/状态用）。 */
    public static boolean isTideActive(EntityPlayerMP player) {
        HordeState.Entry entry = HordeState.get(player.world).get(player.getUniqueID());
        return entry != null && entry.active;
    }

    // ============================================================ 热忱的腐肉掉落

    /** 随从被铁傀儡打死（且主人尸潮 active）→ 掉 1 个热忱的腐肉。 */
    public static void onFollowerDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        EntityLivingBase dead = event.getEntityLiving();
        if (!Followers.isFollower(dead)) {
            return;
        }
        Entity trueSource = event.getSource().getTrueSource();
        if (!(trueSource instanceof EntityIronGolem)) {
            return; // 只铁傀儡击杀才掉；玩家/其它生物杀不掉
        }
        UUID owner = Followers.getOwnerUuid(dead);
        if (owner == null) {
            return;
        }
        HordeState state = HordeState.get(dead.world);
        HordeState.Entry entry = state.get(owner);
        if (entry == null || !entry.active) {
            return;
        }
        int count = UlStrength.integer("zombie_tide.horde.flesh_drop", 1);
        ItemStack flesh = new ItemStack(CommonProxy.ZEALOUS_FLESH, count);
        event.getDrops().add(new EntityItem(dead.world, dead.posX, dead.posY, dead.posZ, flesh));
        Followers.clearOwner(dead);
    }

    // ============================================================ 随从查询 / 属性

    private static int releaseFollowers(EntityPlayerMP player) {
        List<EntityLiving> followers = followersOf(player);
        for (EntityLiving f : followers) {
            removeFollowerMultiplier(f);
            Followers.clearOwner(f);
        }
        return followers.size();
    }

    private static int countFollowers(EntityPlayerMP player) {
        return followersOf(player).size();
    }

    private static List<EntityLiving> followersOf(EntityPlayerMP player) {
        UUID owner = player.getUniqueID();
        double r = UlStrength.num("zombie_tide.horde.follower_count_radius", 64.0D);
        List<EntityLiving> out = new ArrayList<>();
        for (EntityLiving e : player.world.getEntitiesWithinAABB(EntityLiving.class,
                player.getEntityBoundingBox().grow(r))) {
            if (owner.equals(Followers.getOwnerUuid(e))) {
                out.add(e);
            }
        }
        return out;
    }

    /** 按说服度给随从生命/攻击打折（MULTIPLY_TOTAL，倍率 <1 时生效）。 */
    private static void applyFollowerMultiplier(EntityLiving zombie, double multiplier) {
        if (multiplier == 1.0D) {
            return; // 满说服度不打折
        }
        double amount = multiplier - 1.0D; // 1.0 为允许的字面量
        IAttributeInstance hp = zombie.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        if (hp != null) {
            hp.removeModifier(FOLLOWER_HEALTH_UUID);
            hp.applyModifier(new AttributeModifier(FOLLOWER_HEALTH_UUID, "ul.follower_health", amount, 2));
        }
        IAttributeInstance atk = zombie.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        if (atk != null) {
            atk.removeModifier(FOLLOWER_ATTACK_UUID);
            atk.applyModifier(new AttributeModifier(FOLLOWER_ATTACK_UUID, "ul.follower_attack", amount, 2));
        }
        if (zombie.getHealth() > zombie.getMaxHealth()) {
            zombie.setHealth(zombie.getMaxHealth());
        }
    }

    private static void removeFollowerMultiplier(EntityLiving zombie) {
        IAttributeInstance hp = zombie.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH);
        if (hp != null) {
            hp.removeModifier(FOLLOWER_HEALTH_UUID);
        }
        IAttributeInstance atk = zombie.getEntityAttribute(SharedMonsterAttributes.ATTACK_DAMAGE);
        if (atk != null) {
            atk.removeModifier(FOLLOWER_ATTACK_UUID);
        }
    }

    // ============================================================ 村庄 / 铁傀儡

    /** 32 格内最近的「有效」村庄（活村民 ≥ village_min_villagers）。 */
    /**
     * 找「村庄」= 村民聚集地（设计修订：不查原版村庄结构/门认领——村民聚在一起就算村庄，
     * 对玩家反而利好刷铁机）。统计玩家 village_radius 内活村民，够 minVillagers 就以
     * 质心为村庄中心；不足返回 null。
     */
    private static BlockPos findVillageCenter(EntityPlayerMP player) {
        double radius = UlStrength.num("zombie_tide.horde.village_radius", 32.0D);
        int minVillagers = UlStrength.integer("zombie_tide.horde.village_min_villagers", 4);
        List<EntityVillager> villagers = player.world.getEntitiesWithinAABB(
                EntityVillager.class, player.getEntityBoundingBox().grow(radius));
        int n = 0;
        double sx = 0, sy = 0, sz = 0;
        for (EntityVillager v : villagers) {
            if (v.isDead) {
                continue;
            }
            n++;
            sx += v.posX;
            sy += v.posY;
            sz += v.posZ;
        }
        if (n < minVillagers) {
            return null;
        }
        return new BlockPos(sx / n, sy / n, sz / n);
    }

    private static int countVillagers(World world, BlockPos center) {
        double r = UlStrength.num("zombie_tide.horde.villager_count_radius", 48.0D);
        return world.getEntitiesWithinAABB(net.minecraft.entity.passive.EntityVillager.class,
                new AxisAlignedBB(center).grow(r)).size();
    }

    private static int countGolems(World world, BlockPos center) {
        double r = UlStrength.num("zombie_tide.horde.golem_spawn_radius", 16.0D);
        return world.getEntitiesWithinAABB(EntityIronGolem.class,
                new AxisAlignedBB(center).grow(r)).size();
    }

    /** 村庄中心 golem_spawn_radius 内随机找合法落点（实心方块上、两格空气）。 */
    private static BlockPos findGolemSpawn(World world, BlockPos center) {
        int r = (int) UlStrength.num("zombie_tide.horde.golem_spawn_radius", 16.0D);
        int tries = UlStrength.integer("zombie_tide.horde.golem_spawn_tries", 12);
        for (int i = 0; i < tries; i++) {
            // 对称区间 [-r, r]：0..r 加随机方向，避免写死倍率字面量
            int spanX = world.rand.nextInt(r + 1);
            int spanZ = world.rand.nextInt(r + 1);
            int dx = world.rand.nextBoolean() ? spanX : -spanX;
            int dz = world.rand.nextBoolean() ? spanZ : -spanZ;
            BlockPos ground = world.getHeight(center.add(dx, 0, dz));
            if (world.isAirBlock(ground) && world.isAirBlock(ground.up())) {
                return ground;
            }
        }
        return null;
    }

    // ============================================================ 招募候选

    /**
     * 僵尸族判定（README 语义）：{@link EntityZombie} 及其子类（僵尸、尸壳、僵尸村民、
     * 巨人）+ 僵尸猪人。注意 {@code EntityPigZombie} 继承自 {@code EntityMonster}，
     * <b>不是</b> {@code EntityZombie} 子类，必须显式并入，否则火祭产出的僵尸猪人
     * 既招不了也带不动（历史 bug：instanceof EntityZombie 漏掉猪人）。
     */
    public static boolean isZombieFamily(Entity e) {
        return e instanceof EntityZombie
                || e instanceof net.minecraft.entity.monster.EntityPigZombie;
    }

    /** recruit_radius 内的僵尸族（含僵尸猪人，见 {@link #isZombieFamily}），近者优先。 */
    private static List<EntityLiving> zombiesNear(EntityPlayerMP player) {
        double r = UlStrength.num("zombie_tide.horde.recruit_radius", 16.0D);
        List<EntityLiving> out = new ArrayList<>();
        for (EntityLiving e : player.world.getEntitiesWithinAABB(EntityLiving.class,
                player.getEntityBoundingBox().grow(r))) {
            if (isZombieFamily(e) && !e.isDead) {
                out.add(e);
            }
        }
        out.sort(Comparator.comparingDouble(player::getDistanceSq));
        return out;
    }

    // ============================================================ 队伍快照（S2C 同步，B 键状态页数据源）

    /** 队伍快照节流间隔（tick）：随从血量变化频繁，5 秒一拍足够状态页展示。 */
    private static final long TEAM_SYNC_INTERVAL_TICKS = 100L;

    /** 快照里随从列表封顶：followCap 最大 20，但 countFollowers 按半径统计可短时超编。 */
    private static final int TEAM_SNAPSHOT_MAX_FOLLOWERS = 32;

    /** 随从名截断长度（码点）：防超长自命名把 JSON 顶穿 MAX_TEAM_BYTES。 */
    private static final int TEAM_SNAPSHOT_NAME_LIMIT = 64;

    /** 上次推给各玩家的快照与时间戳（仅服务端主线程访问）。 */
    private static final java.util.Map<UUID, String> LAST_TEAM_JSON = new java.util.WeakHashMap<>();
    private static final java.util.Map<UUID, Long> LAST_TEAM_TICK = new java.util.WeakHashMap<>();

    /** 立即推送队伍快照（招募/解散/收尾等变化点调用）。 */
    public static void syncTeam(EntityPlayerMP player) {
        String json = teamJson(player);
        LAST_TEAM_JSON.put(player.getUniqueID(), json);
        LAST_TEAM_TICK.put(player.getUniqueID(), player.world.getTotalWorldTime());
        try {
            HordeNetwork.sendTeam(player, json);
        } catch (Throwable t) {
            UlModpack.LOGGER.warn("[ul-modpack] horde team sync failed: {}", t.toString());
        }
    }

    /** 节流同步（PlayerTickEvent 调用）：快照有变化才推，且不超过每 5 秒一拍。 */
    public static void syncTeamIfChanged(EntityPlayerMP player) {
        long now = player.world.getTotalWorldTime();
        Long last = LAST_TEAM_TICK.get(player.getUniqueID());
        if (last != null && now - last < TEAM_SYNC_INTERVAL_TICKS) {
            return;
        }
        // 每拍最多构建一次 teamJson；没变化也要推进时间戳，否则 idle 后每个 tick 都重建 JSON
        LAST_TEAM_TICK.put(player.getUniqueID(), now);
        if (teamJson(player).equals(LAST_TEAM_JSON.get(player.getUniqueID()))) {
            return;
        }
        syncTeam(player);
    }

    /** 队伍快照 JSON：说服度 / 队伍上限 / 尸潮状态 / 随从列表（名称、形态 id、血量）。 */
    private static String teamJson(EntityPlayerMP player) {
        List<EntityLiving> members = followersOf(player);
        JsonObject root = new JsonObject();
        int persuasion = Persuasion.get(player);
        root.addProperty("persuasion", persuasion);
        root.addProperty("cap", Persuasion.followCap(persuasion));
        root.addProperty("tideActive", isTideActive(player));
        // 状态页分节显隐：有尸群领导或有随从才展示（避免无关玩家被刷屏）
        root.addProperty("hasHorde", ZombieForms.hasLeadership(player) || !members.isEmpty());
        JsonArray followers = new JsonArray();
        int listed = 0;
        for (EntityLiving f : members) {
            if (listed >= TEAM_SNAPSHOT_MAX_FOLLOWERS) {
                break; // 防御性封顶：JSON 大小可控（32 条 × ≈300B ≈ 9.6KB < MAX_TEAM_BYTES）
            }
            JsonObject e = new JsonObject();
            net.minecraft.util.ResourceLocation type = net.minecraft.entity.EntityList.getKey(f);
            e.addProperty("type", type == null ? "" : type.toString());
            e.addProperty("name", truncate(f.getName(), TEAM_SNAPSHOT_NAME_LIMIT));
            e.addProperty("custom", f.hasCustomName());
            e.addProperty("health", Math.round(f.getHealth() * 10.0D) / 10.0D);
            e.addProperty("maxHealth", Math.round(f.getMaxHealth() * 10.0D) / 10.0D);
            followers.add(e);
            listed++;
        }
        root.add("followers", followers);
        return root.toString();
    }

    /** 按码点截断（不劈开代理对）；超长加省略号提示。 */
    private static String truncate(String s, int maxCodePoints) {
        if (s == null) {
            return "";
        }
        if (s.codePointCount(0, s.length()) <= maxCodePoints) {
            return s;
        }
        int end = s.offsetByCodePoints(0, maxCodePoints);
        return s.substring(0, end) + "…";
    }

    // ============================================================ 工具

    public static void incrementTideCleared(EntityPlayerMP player) {
        PlayerAbilityStore.update(player, (PlayerAbilityData data) -> {
            JsonObject prev = data.getState(ZombieLine.STATE_TIDE_CLEARED);
            int count = prev.has("count") ? prev.get("count").getAsInt() : 0;
            JsonObject next = new JsonObject();
            next.addProperty("count", count + 1);
            data.putState(ZombieLine.STATE_TIDE_CLEARED, next);
        });
        try {
            AbilityNetwork.syncPlayer(player, "");
        } catch (Throwable ignored) {
        }
    }

    public static int tidesCleared(EntityPlayerMP player) {
        JsonObject state = PlayerAbilityStore.get(player).getState(ZombieLine.STATE_TIDE_CLEARED);
        return state.has("count") ? state.get("count").getAsInt() : 0;
    }

    private static boolean onCooldown(EntityPlayerMP player, String abilityId) {
        return PlayerAbilityStore.get(player).getCooldown(abilityId, PlayerAbilityStore.now(player)) > 0;
    }

    private static void setCooldown(EntityPlayerMP player, String abilityId, int ticks) {
        if (ticks <= 0) {
            return;
        }
        PlayerAbilityStore.update(player, data ->
                data.setCooldown(abilityId, PlayerAbilityStore.now(player), ticks));
    }

    private static int recruitCooldownTicks() {
        return UlStrength.integer("zombie_tide.horde.recruit_cooldown_ticks", 160);
    }

    private static int countItem(EntityPlayerMP player, Item item) {
        int total = 0;
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void consumeItem(EntityPlayerMP player, Item item, int amount) {
        int left = amount;
        for (int i = 0; i < player.inventory.getSizeInventory() && left > 0; i++) {
            ItemStack stack = player.inventory.getStackInSlot(i);
            if (!stack.isEmpty() && stack.getItem() == item) {
                int take = Math.min(left, stack.getCount());
                stack.shrink(take);
                left -= take;
            }
        }
    }

    private static void action(EntityPlayerMP player, String key, Object... args) {
        org.apache.logging.log4j.LogManager.getLogger("ulmodpack")
                .info("[horde] action: " + key + (args.length > 0 ? " " + java.util.Arrays.toString(args) : ""));
        player.sendStatusMessage(new TextComponentTranslation(key, args), true);
    }

    private static void chat(EntityPlayerMP player, String key, Object... args) {
        player.sendMessage(new TextComponentTranslation(key, args));
    }
}
