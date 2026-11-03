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
import com.google.gson.JsonObject;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityZombie;
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
 * 触发由 {@link HordeEvents}（潜行右键方块/实体）与 {@link HordeNetwork}（对空气使用，
 * 客户端 ul:ability 包）分发进来；本类只做服务端权威判定与状态推进，不含事件订阅。
 *
 * 流程：招募（夜晚+移动+潜行右键，说服度决定条件/上限/随从倍率）→ 发动（对空气，32 格内
 * 活村民 ≥4 的村庄进入屠村）→ tick（每 20 tick：刷铁傀儡、判成功/中止）→ 收尾（成功
 * count+1、说服度 ±、随从散开）。玩家死亡=失败（见 onLivingDeath，即使被仪式接住）。
 *
 * 全部数值经 {@link UlStrength}（键 zombie_tide.horde.*），兜底默认与 defaults.json /
 * 模组 ul_strength/zombie_tide.json 一致。只存 UUID 与坐标（{@link HordeState}），
 * 随从关系随 {@link Followers} 的 NBT 持久化。
 */
public final class HordeManager {

    /** 随从说服度属性修饰符固定 UUID（生命/攻击，重复套用/移除幂等）。 */
    private static final UUID FOLLOWER_HEALTH_UUID = UUID.fromString("b16ef011-0000-4000-8000-000000000001");
    private static final UUID FOLLOWER_ATTACK_UUID = UUID.fromString("b16ef011-0000-4000-8000-000000000002");

    private HordeManager() {
    }

    // ============================================================ 招募

    /** 潜行右键方块/实体：招募 16 格内僵尸族。夜晚 + 移动中才响应。 */
    public static void tryRecruit(EntityPlayerMP player) {
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
        if (isDay(player.world)) {
            action(player, "ul.horde.recruit.fail.day");
            return;
        }
        if (!isMoving(player)) {
            action(player, "ul.horde.recruit.fail.move");
            return;
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
    }

    // ============================================================ 发动 / 解放（对空气使用）

    /**
     * 对空气使用（客户端 ul:ability 包上报，服务端判「对空气」后分派）。
     * 泰坦形态 → 震地；否则走尸群领导的发动/解放分支（有随从且有有效村庄=发动，
     * 有随从无村庄=解放，无随从有村庄=提示招募，都无=解散）。
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
        Village village = nearestValidVillage(player);
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
    private static void launch(EntityPlayerMP player, HordeState.Entry entry, HordeState state, Village village) {
        entry.active = true;
        entry.villageCenter = village.getCenter();
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
    }

    // ============================================================ 调试 / 状态（命令用）

    /** 调试：强制在最近村庄发动屠村（忽略队伍/村民数检查）。 */
    public static void forceStart(EntityPlayerMP player) {
        VillageCollection collection = player.world.getVillageCollection();
        double radius = UlStrength.num("zombie_tide.horde.village_radius", 32.0D);
        Village village = collection == null ? null
                : collection.getNearestVillage(new BlockPos(player), (int) radius);
        if (village == null) {
            action(player, "ul.horde.launch.fail.place");
            return;
        }
        HordeState state = HordeState.get(player.world);
        HordeState.Entry entry = state.getOrCreate(player.getUniqueID());
        entry.active = true;
        entry.villageCenter = village.getCenter();
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
    private static Village nearestValidVillage(EntityPlayerMP player) {
        VillageCollection collection = player.world.getVillageCollection();
        if (collection == null) {
            return null;
        }
        double radius = UlStrength.num("zombie_tide.horde.village_radius", 32.0D);
        double radiusSq = radius * radius;
        int minVillagers = UlStrength.integer("zombie_tide.horde.village_min_villagers", 4);
        Village best = null;
        double bestDist = Double.MAX_VALUE;
        for (Village village : collection.getVillageList()) {
            BlockPos center = village.getCenter();
            double distSq = center.distanceSq(player.posX, player.posY, player.posZ);
            if (distSq > radiusSq) {
                continue;
            }
            if (countVillagers(player.world, center) < minVillagers) {
                continue;
            }
            if (distSq < bestDist) {
                bestDist = distSq;
                best = village;
            }
        }
        return best;
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

    /** recruit_radius 内的僵尸族（instanceof EntityZombie 覆盖僵尸/尸壳/僵尸村民/僵尸猪人），近者优先。 */
    private static List<EntityLiving> zombiesNear(EntityPlayerMP player) {
        double r = UlStrength.num("zombie_tide.horde.recruit_radius", 16.0D);
        List<EntityLiving> out = new ArrayList<>();
        for (EntityLiving e : player.world.getEntitiesWithinAABB(EntityLiving.class,
                player.getEntityBoundingBox().grow(r))) {
            if (e instanceof EntityZombie && !e.isDead) {
                out.add(e);
            }
        }
        out.sort(Comparator.comparingDouble(player::getDistanceSq));
        return out;
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

    public static boolean isDay(World world) {
        return world.isDaytime();
    }

    private static boolean isMoving(EntityPlayerMP player) {
        return player.moveForward != 0.0F || player.moveStrafing != 0.0F;
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
        player.sendStatusMessage(new TextComponentTranslation(key, args), true);
    }

    private static void chat(EntityPlayerMP player, String key, Object... args) {
        player.sendMessage(new TextComponentTranslation(key, args));
    }
}
