package aaaa0ggmc.ulmodpack.zombie.horde;

import aaaa0ggmc.morphext.ability.PlayerAbilityData;
import aaaa0ggmc.morphext.ability.PlayerAbilityStore;
import aaaa0ggmc.morphext.follower.Followers;
import aaaa0ggmc.morphext.follower.TimedSummons;
import aaaa0ggmc.morphext.network.AbilityNetwork;
import aaaa0ggmc.morphext.render.Outlines;
import aaaa0ggmc.uld.UlStrength;
import aaaa0ggmc.ulmodpack.UlModpack;
import aaaa0ggmc.ulmodpack.faction.HateQueries;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.Village;
import net.minecraft.village.VillageCollection;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.util.text.TextComponentTranslation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 尸潮系统（尸群领导 + 屠村）核心逻辑。
 *
 * 触发统一走能力轮盘（{@link HordeAbilities} → ServerAbilityService，2026-11-06
 * 移除右键手势）；本类只做服务端权威判定与状态推进，不含事件订阅。
 *
 * 流程（2026-11-07 起三能力合并为 {@code horde_command}，按「目标 + 潜行」在
 * {@link HordeAbilities} 分派到本类）：招募（服务端视线解析目标；说服度决定
 * 条件/上限/随从倍率；不要求夜晚与移动）→ 踢出单个队员（release 单体版）→
 * 发动/解散（32 格内活村民 ≥4 的村庄进入屠村，条件不足则解散）→ tick（每 20 tick：
 * 刷铁傀儡、判成功/中止）→ 收尾（成功 count+1、说服度 ±、随从散开）。玩家死亡=
 * 失败（见 onLivingDeath，即使被仪式接住）。队伍随从常驻<b>白色描边</b>
 * （核心 {@link Outlines}：team 前缀定色 + 短时长 GLOWING 触发原版 outline 路径）：
 * 每 20 tick 给队员刷新，入队即时挂（{@link #syncTeam} 顺带刷新），
 * 离队/踢出/解散由 {@link Outlines#clear} 反向解除（team 归属必须显式清，
 * 否则名字板会被 team 的 NEVER 规则永久藏掉）。
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

    /** M6：尸潮召唤铁傀儡的归属标记（值=主人 UUID 字符串；收尾按标记清理）。 */
    private static final String TAG_TIDE_GOLEM = "ulTideGolem";

    /**
     * M11：入侵村民标记（值=主人 UUID 字符串）。标记范围/持续时间与尸潮生命周期
     * 一致：tick 内续标，收尾清理（含未加载村民的重载惰性清理）。
     */
    private static final String TAG_TIDE_INVADED = "ulTideInvaded";

    /**
     * 常规村庄铁傀儡存活上限（村落线流言/恐吓基线，设计文档「尸潮.md」：常规 15 秒
     * 1 只、上限 3；尸潮 5 秒 1 只、上限 9，正好 3 倍）。{@code golem_multiplier}
     * 以它为 1.0 基线，见 {@link #golemAliveCap()}。
     */
    private static final int REGULAR_GOLEM_CAP = 3;

    private HordeManager() {
    }

    // ============================================================ 招募

    /**
     * 就近视线招募入口（保留给 {@code demo-zombie-tide.sh} 的 {@code invoke tryRecruit}；
     * 2026-11-07 合并后 {@code horde_command} 的招募分支直走 {@link #tryRecruitAt}，
     * 无目标时给失败提示、不再批量兜底）：
     * 服务端视线解析目标（{@link #findLookedZombie}，射线+最近实体，不接收客户端实体），
     * 命中僵尸族目标走 {@link #tryRecruitAt}；没有目标时退回就近批量招募（旧语义）。
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
     * 招募指定僵尸族目标（服务端解析出的目标；距离/种类/说服度规则全在服务端校验）。
     *
     * 规则（用户设计）：去掉旧的「必须夜晚」「必须移动」两个门槛，保留冷却与说服度匹配——
     * 说服度 &lt; thresholdMid 时目标须残血到 hurtBelow 比例以下才能招；
     * &lt; thresholdLow 时还要消耗腐肉；人数上限 followCap。
     *
     * @return 招募冷却中返回剩余 tick（H7：能力框架据此回执 COOLDOWN，HUD 显示冷却）；
     *         0 = 本次已尝试（成功或条件不满足，条件提示已由 Actionbar 给出）
     */
    public static int tryRecruitAt(EntityPlayerMP player, EntityLiving target) {
        if (!ZombieForms.hasLeadership(player)) {
            action(player, "ul.horde.fail.skill");
            return 0;
        }
        if (!ZombieForms.isUndead(player)) {
            action(player, "ul.horde.fail.form");
            return 0;
        }
        if (onCooldown(player, ZombieLine.ABILITY_COMMAND)) {
            // 8 秒冷却：把剩余量回给能力框架，冷却被拒不误报 SUCCESS(0)
            return PlayerAbilityStore.get(player)
                    .getCooldown(ZombieLine.ABILITY_COMMAND, PlayerAbilityStore.now(player));
        }
        double reach = recruitReach();
        if (target == null || target.isDead || !isZombieFamily(target)) {
            action(player, "ul.horde.recruit.fail.target");
            return 0;
        }
        if (player.getDistanceSq(target) > reach * reach) {
            action(player, "ul.horde.recruit.fail.target"); // 服务端距离校验，不信客户端
            return 0;
        }
        if (Followers.isFollower(target)) {
            action(player, "ul.horde.recruit.fail.own");
            return 0;
        }
        int persuasion = Persuasion.get(player);
        int cap = Persuasion.followCap(persuasion);
        if (cap - countFollowers(player) <= 0) {
            action(player, "ul.horde.recruit.fail.cap");
            return 0;
        }
        boolean needHurt = persuasion < Persuasion.thresholdMid();
        boolean needFlesh = persuasion < Persuasion.thresholdLow();
        double hurtBelow = UlStrength.num("zombie_tide.horde.persuasion_hurt_below", 0.5D);
        int fleshCost = UlStrength.integer("zombie_tide.horde.recruit_flesh_cost", 1);
        if (needFlesh && countItem(player, Items.ROTTEN_FLESH) < fleshCost) {
            action(player, "ul.horde.recruit.fail.flesh");
            return 0;
        }
        // 说服度 <50：目标须先打残（生命 < hurtBelow 比例）
        if (needHurt && target.getHealth() >= target.getMaxHealth() * hurtBelow) {
            action(player, "ul.horde.recruit.fail.persuasion");
            return 0;
        }
        Followers.setOwner(target, player.getUniqueID(), player.world.getTotalWorldTime());
        applyFollowerMultiplier(target, Persuasion.multiplier(persuasion));
        if (needFlesh) {
            consumeItem(player, Items.ROTTEN_FLESH, fleshCost);
        }
        setCooldown(player, ZombieLine.ABILITY_COMMAND, recruitCooldownTicks());
        action(player, "ul.horde.recruit.ok", 1);
        syncTeam(player);
        return 0;
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
        if (onCooldown(player, ZombieLine.ABILITY_COMMAND)) {
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
        setCooldown(player, ZombieLine.ABILITY_COMMAND, recruitCooldownTicks());
        action(player, "ul.horde.recruit.ok", recruited);
        syncTeam(player);
    }

    /** 招募射程（服务端权威距离校验）。 */
    private static double recruitReach() {
        return UlStrength.num("zombie_tide.horde.recruit_reach", 4.5D);
    }

    /**
     * 视线内（recruit_reach）最近的僵尸族实体——招募的服务端目标解析
     * （射线 + 最近实体选取，不接收客户端传实体）。
     */
    private static EntityLiving findLookedZombie(net.minecraft.entity.player.EntityPlayer player) {
        net.minecraft.entity.EntityLivingBase best = findLooked(player, true);
        return best instanceof EntityLiving ? (EntityLiving) best : null;
    }

    /**
     * 客户端 SPI 用（{@code horde_command} 的 {@code clientPickTarget}）：视线射线选
     * 「任意活体」目标（2026-11-07 放宽，不再只认僵尸族——招募/踢人判定全在服务端
     * 分派），命中返回实体 id，否则 0。纯查询无副作用，客户端/服务端都可调；
     * 只做目标选择，执行仍走服务端权威链路。
     */
    public static int findLookedLivingId(net.minecraft.entity.player.EntityPlayer player) {
        net.minecraft.entity.EntityLivingBase best = findLooked(player, false);
        return best == null ? 0 : best.getEntityId();
    }

    /**
     * 视线射线选目标（眼睛+视线+AABB 拦截+方块遮挡，方块命中距离为上限，不隔墙选取）：
     * 最近的存活活体；{@code zombieOnly} 只认僵尸族。
     */
    private static net.minecraft.entity.EntityLivingBase findLooked(
            net.minecraft.entity.player.EntityPlayer player, boolean zombieOnly) {
        double reach = recruitReach();
        net.minecraft.util.math.Vec3d eye = player.getPositionEyes(1.0F);
        net.minecraft.util.math.Vec3d look = player.getLook(1.0F);
        net.minecraft.util.math.Vec3d end = eye.add(look.x * reach, look.y * reach, look.z * reach);
        net.minecraft.util.math.RayTraceResult blockHit = player.rayTrace(reach, 1.0F);
        double bestDist = blockHit == null || blockHit.hitVec == null
                ? reach * reach : blockHit.hitVec.squareDistanceTo(eye);
        net.minecraft.entity.EntityLivingBase best = null;
        for (Entity e : player.world.getEntitiesWithinAABBExcludingEntity(player,
                player.getEntityBoundingBox().grow(reach))) {
            if (e.isDead || !(e instanceof net.minecraft.entity.EntityLivingBase)) {
                continue;
            }
            if (zombieOnly && !isZombieFamily(e)) {
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
                best = (net.minecraft.entity.EntityLivingBase) e;
            }
        }
        return best;
    }

    // ============================================================ 发动 / 踢出 / 解散（能力框架入口）

    /**
     * 潜行 + 无目标（{@code horde_command}，2026-11-07 合并语义）：尝试发动尸潮；
     * 不满足发动条件 → 解散当前队伍（原 launch 失败则 release 的合并语义）。
     * 发动条件 = 尸群领导 + 不死者形态 + 有随从 + <b>附近活村民 ≥ village_min_villagers</b>
     * （半径 village_radius 内已加载活村民计数，<b>不要求村庄结构</b>，荒郊野外可发动）+
     * 无进行中的尸潮。村民不足走「失败宣告」（提示 + 解散 + 指数衰减扣声望 + 解锁
     * 「狼来了」成就），不是静默拦截。
     * 不满足（含已在进行中、发动不能）一律 {@link #disband}——「解散当前队伍，
     * 进行中的尸潮一并收尾」。
     *
     * <p>「已在进行中 → 解散」是 <b>toggle 语义的有意设计</b>（§6.4 复核结论）：
     * 三能力合并后玩家没有独立的「停止」能力，二次「潜行对空」就是手动强制收尾的
     * 唯一入口；与演示入口 {@link #onAirUse} 的「进行中只提示、不收尾」是<b>有意分歧</b>，
     * 后者防演示脚本重复 {@code invoke} 误收尾中断录像（见其注释）。</p>
     */
    public static int launchOrDisband(EntityPlayerMP player) {
        HordeState state = HordeState.get(player.world);
        HordeState.Entry entry = state.getOrCreate(player.getUniqueID());
        boolean canLead = ZombieForms.hasLeadership(player) && ZombieForms.isUndead(player);
        int followers = countFollowers(player);
        BlockPos village = canLead ? findVillageCenter(player) : null;
        if (!entry.active && canLead && followers > 0 && village != null) {
            // 发动冷却（固定 30 秒）：是节流不是「发动条件」，冷却中不触发解散语义
            if (onCooldown(player, ZombieLine.ABILITY_LAUNCH)) {
                // 双提示去重（2026-11-07）：冷却拒绝由能力框架回执 COOLDOWN，客户端只显示
                // 「冷却中：%ss」一条；这里再自发「潮水还在涨」就是第二条（招募冷却分支同口径，
                // 见 tryRecruitAt 只回剩余 tick、不发文案）。演示脚本入口 onAirUse 没有框架
                // 回执，仍自发文案。
                logAction(player, "ul.horde.launch.fail.cooldown");
                return PlayerAbilityStore.get(player)
                        .getCooldown(ZombieLine.ABILITY_LAUNCH, PlayerAbilityStore.now(player));
            }
            launch(player, entry, state, village);
            return 0;
        }
        // 发动条件不满足 → 仍按合并语义解散，但给出具体原因（2026-11-07 用户实测：
        // 旧文案只报泛化的「队伍解散了」，玩家完全看不出缺什么条件）
        if (!entry.active) {
            if (!canLead) {
                disband(player, ZombieForms.hasLeadership(player)
                        ? "ul.horde.fail.form" : "ul.horde.fail.skill");
            } else if (followers <= 0) {
                disband(player, "ul.horde.launch.fail.empty");
            } else {
                // 失败宣告（2026-11-07 口径最终版）：附近活村民 < village_min_villagers
                // 不是静默拦截——明确宣言 + 解散队伍 + 指数衰减扣声望（累计上限 40/20）+ 解锁「狼来了」
                Persuasion.addFailureDecay(player);
                aaaa0ggmc.ulmodpack.achievement.Achievements.unlock(
                        player, aaaa0ggmc.ulmodpack.achievement.Achievements.CRYING_WOLF);
                disband(player, "ul.horde.launch.declare");
            }
            return 0;
        }
        disband(player);
        return 0;
    }

    /** 该实体是否是该玩家的在队成员（服务端判定；踢出分支的轻校验）。 */
    public static boolean isTeamMember(EntityPlayerMP player, EntityLivingBase target) {
        return target != null && player.getUniqueID().equals(Followers.getOwnerUuid(target));
    }

    /**
     * 踢出单个队员（潜行 + 队员目标，原 release 的单体版）：解除随从关系与说服度
     * 属性打折，并解除白色描边（{@link Outlines#clear}）。
     */
    public static void kickFollower(EntityPlayerMP player, EntityLivingBase member) {
        if (!isTeamMember(player, member)) {
            action(player, "ul.horde.command.fail.member");
            return;
        }
        dismissFollower(member);
        action(player, "ul.horde.kick", member.getName());
        syncTeam(player);
    }

    /**
     * {@code demo-zombie-tide.sh} 的 {@code invoke onAirUse} 共用入口（2026-11-07 三能力
     * 合并后仅剩演示脚本用）：泰坦形态 → 震地；否则走尸群领导的发动/解放分支
     * （有随从且有有效村庄=发动，有随从无村庄=解放，无随从有村庄=提示招募，都无=解散）。
     * 玩家侧语义见 {@link #launchOrDisband}。
     *
     * <p>「已在进行中 → 只提示 {@code fail.active}、保留现场」是<b>有意分歧</b>（§6.4
     * 复核结论）：本入口只被演示脚本反射调用，重复 {@code invoke} 不应把录像中的尸潮
     * 解散掉；玩家侧的 toggle「二次潜行对空=强制收尾」只在 {@link #launchOrDisband}。</p>
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
            if (onCooldown(player, ZombieLine.ABILITY_LAUNCH)) {
                action(player, "ul.horde.launch.fail.cooldown");
                return;
            }
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

    /** 进入屠村：锁定村庄中心、初始化会话；发动冷却固定 30 秒（cooldown_policy 固定覆盖）。 */
    private static void launch(EntityPlayerMP player, HordeState.Entry entry, HordeState state, BlockPos center) {
        entry.active = true;
        entry.villageCenter = center;
        entry.startTick = player.world.getTotalWorldTime();
        entry.lastGolemSpawnTick = 0L;
        entry.abortSinceTick = 0L;
        state.put(player.getUniqueID(), entry);
        setCooldown(player, ZombieLine.ABILITY_LAUNCH, launchCooldownTicks());
        Persuasion.resetFailStreak(player); // 发动成功即清「狼来了」连击
        action(player, "ul.horde.launch");
    }

    /** 解放队伍：移除随从标签、散开恢复野生。 */
    public static void release(EntityPlayerMP player) {
        releaseFollowers(player);
        action(player, "ul.horde.release");
        syncTeam(player);
    }

    /**
     * 解散当前队伍（含进行中的尸潮收尾）。玩家入口 = {@code horde_command} 潜行+无目标
     * （{@link #launchOrDisband} 的发动失败分支，2026-11-07 合并）；另保留 public 供
     * {@code /ulpack} 命令或演示脚本接入。复用原「无随从无村庄=解散」分支的散开语义；
     * 进行中的尸潮一并收尾（同「中止」不扣说服度）。
     */
    public static void disband(EntityPlayerMP player) {
        disband(player, null);
    }

    /**
     * 解散（可带明确失败原因）：{@code reasonKey} 非空时用原因文案替代泛化的
     * 「队伍解散了」——发动失败时可操作的信息（缺什么条件）优先于解散本身
     * （「失败即解散」是 §6.4 的合并语义，玩家侧见任务书说明）。
     */
    private static void disband(EntityPlayerMP player, String reasonKey, Object... reasonArgs) {
        HordeState state = HordeState.get(player.world);
        HordeState.Entry entry = state.get(player.getUniqueID());
        int before = countFollowers(player);
        boolean endedTide = entry != null && entry.active;
        if (endedTide) {
            endTide(player, entry, state); // 收尾含散开随从
        } else {
            releaseFollowers(player);
            // M10：launchOrDisband 会 getOrCreate 出空会话；解散后不留 inactive 条目
            if (entry != null) {
                state.remove(player.getUniqueID());
            }
        }
        if (reasonKey != null) {
            action(player, reasonKey, reasonArgs);
        } else if (endedTide || before > 0) {
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
        sweepStaleOutlines(world);
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

            maybeSpawnGolem(world, owner, entry);
            commandFollowers(owner, entry);
            markInvadedVillagers(world, owner, entry);

            // 中止口径先算（成功判定要用）：玩家远离村庄 = 走中止语义，不判成功
            double abortDist = UlStrength.num("zombie_tide.horde.abort_distance", 48.0D);
            long abortTicks = (long) UlStrength.num("zombie_tide.horde.abort_ticks", 600.0D);
            boolean far = owner.world != world
                    || owner.getDistanceSq(entry.villageCenter) > abortDist * abortDist;

            // 成功：活村民清零。防假胜利（2026-11-07 用户实测「0 村民出现胜利文案」）：
            // 村民未加载 ≠ 被肃清，必须村庄中心区块已加载；且玩家在场（非远离状态）
            // 才判成功——否则旧会话/走远后未加载的村庄会被直接判成胜利。
            if (!far && world.isBlockLoaded(entry.villageCenter)
                    && countVillagers(world, entry.villageCenter) <= 0) {
                succeed(owner, entry, state);
                continue;
            }
            // 中止：远离村庄超过阈值持续 abort_ticks
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

    /**
     * 尸潮作战指令：随从僵尸跟随 AI 只跟随不作战（实体 AI 优先级被跟随任务占着），
     * 每轮给它们指派最近的活村民为攻击目标——「带队屠村」的作战语义在这里。
     * 铁傀儡会还击随从（原版守村 AI），随从被铁傀儡打死掉热忱的腐肉。
     *
     * <p><b>FVP-33（R2 类名选敌 + R5 自行 setAttackTarget）</b>：改前按
     * {@code EntityVillager.class} 硬编码「最近村民」并直接 {@code setAttackTarget}，
     * 与核心 {@code faction_relations.json} 已声明的 {@code undead→villager ENEMY}
     * 构成双真相——代码自带第二份敌我裁决，图若改口（如未来声明和解）代码不跟随。
     * 改后选目标走 {@link HateQueries#findNearestEnemy}（内部过 {@code isEnemy}
     * 阵营图裁决）：候选范围仍是尸潮指定作战对象（村民 = 入侵目标，这是玩法范围
     * 不是敌我裁决），「能不能打、打哪只」由图决定。今日图判 ENEMY，选中的村民与
     * 改前的「村庄中心 16 格内最近活村民」逐字节一致，行为不退化；图判非敌时返回
     * {@code null}，不指派（关系单一真值 = 图）。
     * 锚点用村庄中心而非随从自身：保留「全队集火同一目标、远处随从也向村庄集结」
     * 的屠村语义（{@code findNearestEnemy} 的 self 只参与敌意判定，扫描中心是锚点）。
     */
    private static void commandFollowers(EntityPlayerMP owner, HordeState.Entry entry) {
        World world = owner.world;
        for (Entity entity : world.getEntitiesWithinAABB(EntityLiving.class,
                owner.getEntityBoundingBox().grow(64.0D))) {
            // 只指派自己的随从（L7 owner 过滤）：他人随从不得被拉去打村民
            if (!isZombieFamily(entity) || !Followers.isFollowerOf(entity, owner.getUniqueID())) {
                continue;
            }
            EntityLiving z = (EntityLiving) entity;
            if (z.getAttackTarget() != null && !z.getAttackTarget().isDead) {
                continue; // 已有活目标不重指（与改前同口径）
            }
            // FVP-33：敌人选择改问阵营图（村民只是候选，许不许可由图裁决）
            EntityVillager target = HateQueries.findNearestEnemy(z, new Vec3d(entry.villageCenter),
                    16.0D, EntityVillager.class, null);
            if (target != null) {
                z.setAttackTarget(target);
            }
        }
    }

    /**
     * 尸潮期间每 golem_interval tick 尝试刷 1 只铁傀儡，同时存活 ≤ {@link #golemAliveCap()}
     * （golem_max_alive 与 golem_multiplier 合成上限）。
     */
    private static void maybeSpawnGolem(World world, EntityPlayerMP owner, HordeState.Entry entry) {
        long now = world.getTotalWorldTime();
        long interval = (long) UlStrength.num("zombie_tide.horde.golem_interval_ticks", 100.0D);
        if (entry.lastGolemSpawnTick != 0L && now - entry.lastGolemSpawnTick < interval) {
            return;
        }
        entry.lastGolemSpawnTick = now;
        if (countGolems(world, entry.villageCenter) >= golemAliveCap()) {
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
        // M6：标记尸潮召唤的傀儡并挂持久 TTL（默认 60s），尸潮收尾时按标记清理；
        // 即使收尾时区块未加载，重载后也会因 TTL 到期消失，不再累积野生铁傀儡。
        golem.getEntityData().setString(TAG_TIDE_GOLEM, owner.getUniqueID().toString());
        TimedSummons.mark(golem, golemLifespanTicks());
        world.spawnEntity(golem);
    }

    /** 尸潮召唤铁傀儡的存活上限（tick）；到期自动清理，兜底区块未加载时的收尾遗漏。 */
    private static int golemLifespanTicks() {
        return UlStrength.integer("zombie_tide.horde.golem_lifespan_ticks", 1200);
    }

    /**
     * 尸潮傀儡存活上限 = 常规上限 {@link #REGULAR_GOLEM_CAP}（3 只）×
     * {@code zombie_tide.horde.golem_multiplier}（设计常量 tideIronGolemMultiplier；
     * 配置值 3.0 = 「对常规 3 只正好 3 倍」→ 9，见设计文档「尸潮.md」铁傀儡刷新一节），
     * 再与 {@code golem_max_alive} 取小封顶（默认 ≤9）。倍率读不到回退 1.0
     * （= 常规 1 倍、仅 3 只）；结果下限 1。
     */
    private static int golemAliveCap() {
        int maxAlive = UlStrength.integer("zombie_tide.horde.golem_max_alive", 9);
        double multiplier = UlStrength.num("zombie_tide.horde.golem_multiplier", 1.0D);
        int cap = (int) Math.round(REGULAR_GOLEM_CAP * multiplier);
        if (cap < 1) {
            cap = 1;
        }
        return Math.min(cap, maxAlive);
    }

    /** M6：清理本场尸潮召唤的铁傀儡（跨维度扫描已加载区块；未加载的由 TTL 兜底）。 */
    private static int clearTideGolems(EntityPlayerMP player) {
        if (player == null || player.server == null) {
            return 0;
        }
        String ownerId = player.getUniqueID().toString();
        int removed = 0;
        for (WorldServer world : player.server.worlds) {
            for (Entity entity : new ArrayList<>(world.loadedEntityList)) {
                if (entity instanceof EntityIronGolem
                        && ownerId.equals(entity.getEntityData().getString(TAG_TIDE_GOLEM))) {
                    entity.setDead();
                    removed++;
                }
            }
        }
        return removed;
    }

    // ============================================================ 入侵村民标记（M11，白色描边）

    /**
     * 尸潮 active 期间给入侵范围内的村民续挂「入侵标记」（NBT {@link #TAG_TIDE_INVADED}，
     * 值=主人 UUID）。范围 = 成功判定口径（村庄中心 villager_count_radius 内的活村民），
     * 持续时间与尸潮生命周期一致：tick 每拍续标，{@link #endTide} / 重载惰性清理收尾。
     *
     * <p>描边走核心统一 API {@link Outlines#mark}（默认<b>白色</b>：team 前缀定色 +
     * 短时长 GLOWING 触发原版 outline 渲染路径）。幂等，每拍重复调用不重复建 team。</p>
     */
    private static void markInvadedVillagers(World world, EntityPlayerMP owner, HordeState.Entry entry) {
        String ownerId = owner.getUniqueID().toString();
        double r = UlStrength.num("zombie_tide.horde.villager_count_radius", 48.0D);
        for (EntityVillager v : world.getEntitiesWithinAABB(EntityVillager.class,
                new AxisAlignedBB(entry.villageCenter).grow(r))) {
            if (v.isDead) {
                continue;
            }
            if (!ownerId.equals(v.getEntityData().getString(TAG_TIDE_INVADED))) {
                v.getEntityData().setString(TAG_TIDE_INVADED, ownerId);
            }
            Outlines.mark(v);
        }
    }

    /**
     * 收尾清理：移除本场入侵标记 + 白色描边（跨维度扫描已加载村民；未加载的由
     * {@link #clearStaleInvadeMark} 在村民重载时惰性清理）。
     */
    private static void clearInvadedVillagers(EntityPlayerMP player) {
        if (player == null || player.server == null) {
            return;
        }
        String ownerId = player.getUniqueID().toString();
        for (WorldServer world : player.server.worlds) {
            for (Entity entity : new ArrayList<>(world.loadedEntityList)) {
                if (entity instanceof EntityVillager
                        && ownerId.equals(entity.getEntityData().getString(TAG_TIDE_INVADED))) {
                    entity.getEntityData().removeTag(TAG_TIDE_INVADED);
                    Outlines.clear(entity);
                }
            }
        }
    }

    /**
     * 村民重载时的惰性清理：入侵标记只在尸潮生命周期内有效。主人的会话已结束
     * （条目不存在/非 active）则补清标记与描边，防止存档残留标记。
     */
    public static void clearStaleInvadeMark(EntityVillager villager) {
        String ownerId = villager.getEntityData().getString(TAG_TIDE_INVADED);
        if (ownerId.isEmpty()) {
            return;
        }
        UUID owner;
        try {
            owner = UUID.fromString(ownerId);
        } catch (IllegalArgumentException corrupt) {
            villager.getEntityData().removeTag(TAG_TIDE_INVADED);
            return;
        }
        HordeState.Entry entry = HordeState.get(villager.world).get(owner);
        if (entry != null && entry.active) {
            return; // 尸潮进行中：保留标记（tick 会续挂描边）
        }
        villager.getEntityData().removeTag(TAG_TIDE_INVADED);
        Outlines.clear(villager);
    }

    /**
     * 兜底清扫本维度「还挂在描边 team 上、但已不该描边」的已加载实体。
     *
     * <p>覆盖 {@link #dismissFollower} / {@link #clearInvadedVillagers} 够不到的情况：
     * 解散时随从/村民所在区块未加载 → {@link Followers#revokeAll} 与登记表撤销都要等
     * 实体重新入世，而 team 归属是存在 scoreboard 里的，不解除就会把名字板永久藏掉
     * （team 的 {@code NEVER} 规则）。这里在世界 tick 节拍上补清。</p>
     *
     * <p>安全口径（本模组里描边只由随从与入侵村民产生）：只清「既不是任何玩家的
     * 随从、也不是进行中尸潮的入侵村民」的已描边实体；任何在队随从与活跃入侵一律
     * 保留。无描边 team 时零成本返回（{@link Outlines#hasMarks}）。</p>
     */
    private static void sweepStaleOutlines(World world) {
        if (!Outlines.hasMarks(world)) {
            return;
        }
        HordeState state = HordeState.get(world);
        for (Entity entity : new ArrayList<>(world.loadedEntityList)) {
            if (!Outlines.isMarked(entity)) {
                continue;
            }
            if (Followers.getOwnerUuid(entity) != null) {
                continue; // 仍是（某位玩家的）在队随从
            }
            if (entity instanceof EntityVillager && hasActiveInvasion((EntityVillager) entity, state)) {
                continue; // 仍是进行中尸潮的入侵村民
            }
            Outlines.clear(entity);
        }
    }

    /** 该村民的入侵标记是否指向一场仍在进行的尸潮。 */
    private static boolean hasActiveInvasion(EntityVillager villager, HordeState state) {
        String ownerId = villager.getEntityData().getString(TAG_TIDE_INVADED);
        if (ownerId.isEmpty()) {
            return false;
        }
        try {
            HordeState.Entry entry = state.get(UUID.fromString(ownerId));
            return entry != null && entry.active;
        } catch (IllegalArgumentException corrupt) {
            return false;
        }
    }

    // ============================================================ 收尾

    private static void succeed(EntityPlayerMP player, HordeState.Entry entry, HordeState state) {
        // tide_success 时间戳：热忱的腐肉获得/食用门槛的明确字段（本场确认成功即记录）
        state.recordTideSuccess(player.getUniqueID(), player.world.getTotalWorldTime());
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

    /** 结束/中止收尾：清理本场召唤物（随从全维解散 + 铁傀儡）、删会话条目。 */
    private static void endTide(EntityPlayerMP player, HordeState.Entry entry, HordeState state) {
        entry.active = false;
        entry.villageCenter = null;
        entry.abortSinceTick = 0L;
        // M10：收尾后删除条目（remove 此前无调用者，entries 随玩家数只增不删）；
        // 进行中的会话仍按 active 恢复，删除只发生在结束点。
        state.remove(player.getUniqueID());
        releaseFollowers(player);
        clearTideGolems(player);
        clearInvadedVillagers(player);
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
            // 明确提示缺什么（原 fail.place 文案不含条件，用户实测看不懂）
            action(player, "ul.horde.launch.fail.village", Math.round(radius), 1);
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

    /** 随从被铁傀儡打死（且主人尸潮 active + 已确认过尸潮成功）→ 掉 1 个热忱的腐肉。 */
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
        // 门槛（2026-11-07 用户反馈「未确认成功就能提前获得」）：获得条件校验
        // tide_success 时间戳——没确认过尸潮成功的「坏领袖」拿不到热忱的腐肉。
        if (!hasTideSuccess(dead.world, owner)) {
            EntityPlayer ownerPlayer = dead.world.getPlayerEntityByUUID(owner);
            if (ownerPlayer instanceof EntityPlayerMP) {
                warnNoLeader((EntityPlayerMP) ownerPlayer);
            }
            return;
        }
        int count = UlStrength.integer("zombie_tide.horde.flesh_drop", 1);
        ItemStack flesh = new ItemStack(CommonProxy.ZEALOUS_FLESH, count);
        event.getDrops().add(new EntityItem(dead.world, dead.posX, dead.posY, dead.posZ, flesh));
        Followers.clearOwner(dead);
    }

    /** 「坏领袖」提示节流（tick）：一场战斗多个随从阵亡时不刷屏。 */
    private static long noLeaderWarnIntervalTicks() {
        return Math.max(1L, (long) UlStrength.num(
                "zombie_tide.horde.no_leader_warn_interval_ticks", 100.0D));
    }
    private static final java.util.Map<UUID, Long> LAST_NO_LEADER_WARN = new java.util.WeakHashMap<>();

    /** 未满足热忱的腐肉门槛时的提示（获得/食用共用一条文案）。 */
    private static void warnNoLeader(EntityPlayerMP player) {
        long now = player.world.getTotalWorldTime();
        Long last = LAST_NO_LEADER_WARN.get(player.getUniqueID());
        if (last != null && now - last < noLeaderWarnIntervalTicks()) {
            return;
        }
        LAST_NO_LEADER_WARN.put(player.getUniqueID(), now);
        action(player, "ul.zealous_flesh.fail.leader");
    }

    // ============================================================ 随从查询 / 属性

    /**
     * H9：解散/收尾时解除该玩家<b>全部</b>随从关系（不再只扫 64 格）：
     * 先撤已加载实体的属性打折，再由核心 {@link Followers#revokeAll} 跨维度清理；
     * 未加载区块的随从按登记表 UUID 标记撤销，实体重新加载时由 FollowerHandler 清除。
     */
    private static int releaseFollowers(EntityPlayerMP player) {
        MinecraftServer server = player.server;
        if (server == null) {
            return 0;
        }
        for (WorldServer world : server.worlds) {
            for (Entity entity : world.loadedEntityList) {
                if (entity instanceof EntityLivingBase
                        && player.getUniqueID().equals(Followers.getOwnerUuid(entity))) {
                    removeFollowerMultiplier((EntityLivingBase) entity);
                    Outlines.clear(entity);
                }
            }
        }
        return Followers.revokeAll(player.getUniqueID(), server);
    }

    /** 单个随从离队（release/kick 共用）：撤说服度属性打折 + 清随从关系 + 解除白色描边。 */
    private static void dismissFollower(EntityLivingBase follower) {
        removeFollowerMultiplier(follower);
        Followers.clearOwner(follower);
        // team 归属必须显式解除（否则名字板被 team 的 NEVER 永久藏掉）
        Outlines.clear(follower);
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
    private static void applyFollowerMultiplier(EntityLivingBase zombie, double multiplier) {
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

    private static void removeFollowerMultiplier(EntityLivingBase zombie) {
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

    /** 活村民计数（成功判定口径）：滤 {@code isDead}，死村民不再拖一拍误判「未清零」。 */
    private static int countVillagers(World world, BlockPos center) {
        double r = UlStrength.num("zombie_tide.horde.villager_count_radius", 48.0D);
        int n = 0;
        for (EntityVillager v : world.getEntitiesWithinAABB(EntityVillager.class,
                new AxisAlignedBB(center).grow(r))) {
            if (v.isDead) {
                continue;
            }
            n++;
        }
        return n;
    }

    private static int countGolems(World world, BlockPos center) {
        double r = UlStrength.num("zombie_tide.horde.golem_spawn_radius", 16.0D);
        return world.getEntitiesWithinAABB(EntityIronGolem.class,
                new AxisAlignedBB(center).grow(r)).size();
    }

    /**
     * 找傀儡落点：每列先做「地面优先」扫描（中心 y±6 内<b>最低</b>的「实心顶面 +
     * 2 宽 4 高 2 深净空」落点，语义对齐原版 {@code Village.findRandomSpawnPos}），
     * 避免刷在屋顶/树冠上玩家看不见、还可能超出 countGolems 的统计盒；
     * 地面扫描失败的列走高度图兜底（原行为：最上层不透明方块之上、两格空气）。
     */
    private static BlockPos findGolemSpawn(World world, BlockPos center) {
        int r = (int) UlStrength.num("zombie_tide.horde.golem_spawn_radius", 16.0D);
        int tries = UlStrength.integer("zombie_tide.horde.golem_spawn_tries", 12);
        BlockPos fallback = null;
        for (int i = 0; i < tries; i++) {
            // 对称区间 [-r, r]：0..r 加随机方向，避免写死倍率字面量
            int spanX = world.rand.nextInt(r + 1);
            int spanZ = world.rand.nextInt(r + 1);
            int dx = world.rand.nextBoolean() ? spanX : -spanX;
            int dz = world.rand.nextBoolean() ? spanZ : -spanZ;
            BlockPos column = center.add(dx, 0, dz);
            BlockPos ground = findGroundLevelSpawn(world, column, center.getY());
            if (ground != null) {
                return ground;
            }
            // 高度图兜底只记录、不立即返回：后续列的地面落点优先
            if (fallback == null) {
                BlockPos surface = world.getHeight(column);
                if (world.isAirBlock(surface) && world.isAirBlock(surface.up())) {
                    fallback = surface;
                }
            }
        }
        return fallback;
    }

    /**
     * 柱内地面落点扫描：从 {@code baseY - 6} 向上找最低的「实心顶面 + 2×4×2 净空」
     * 位置（原版 {@code Village.isAreaClearAround} 语义；傀儡 2.7 格高，净空取 4）。
     * 取最低优先是为了避开屋顶/树冠；找不到返回 null。
     */
    private static BlockPos findGroundLevelSpawn(World world, BlockPos column, int baseY) {
        for (int y = baseY - 6; y <= baseY + 6; y++) {
            BlockPos pos = new BlockPos(column.getX(), y, column.getZ());
            if (!world.getBlockState(pos.down()).isTopSolid()) {
                continue;
            }
            if (isAreaClear(world, pos, 2, 4, 2)) {
                return pos;
            }
        }
        return null;
    }

    /** 以 pos 为脚下的 w×h×d 净空检查（w/d 为偶数时向 -x/-z 偏半格，对齐原版）。 */
    private static boolean isAreaClear(World world, BlockPos pos, int w, int h, int d) {
        int x0 = pos.getX() - w / 2;
        int z0 = pos.getZ() - d / 2;
        for (int x = x0; x < x0 + w; x++) {
            for (int y = pos.getY(); y < pos.getY() + h; y++) {
                for (int z = z0; z < z0 + d; z++) {
                    if (world.getBlockState(new BlockPos(x, y, z)).isNormalCube()) {
                        return false;
                    }
                }
            }
        }
        return true;
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

    // ============================================================ 队员常驻白色描边（服务端权威）

    /** 描边刷新周期（tick）：每 20 tick 校验一次队员集合并续挂（≤ {@link Outlines#REFRESH_TICKS}）。 */
    private static final int GLOW_REFRESH_TICKS = 20;

    /**
     * 给当前队员（{@link #followersOf}）续挂<b>白色</b>描边（核心 {@link Outlines#mark}：
     * 按颜色命名的 scoreboard team 决定描边颜色，短时长 {@code MobEffects.GLOWING}
     * 触发原版 outline 渲染路径）。每 {@link #GLOW_REFRESH_TICKS} tick 由玩家 tick 调用，
     * 入队/队伍变化点由 {@link #syncTeam} 即时挂上。
     *
     * <p>离队/踢出/解散/收尾一律显式 {@link Outlines#clear}：team 归属不会自然过期，
     * 不解除会把名字板永久藏掉（team 的 {@code NEVER} 规则）。</p>
     */
    public static void refreshFollowerGlow(EntityPlayerMP player) {
        if (player.world.isRemote
                || player.world.getTotalWorldTime() % GLOW_REFRESH_TICKS != 0) {
            return;
        }
        applyGlow(player);
    }

    /** 立刻给当前队员挂白色描边（队伍变化点调用，入队即时描边）。 */
    private static void applyGlow(EntityPlayerMP player) {
        for (EntityLiving f : followersOf(player)) {
            Outlines.mark(f);
        }
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

    /** 立即推送队伍快照（招募/解散/收尾等变化点调用）；同时给新队员即时挂白色描边。 */
    public static void syncTeam(EntityPlayerMP player) {
        applyGlow(player);
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
        // 状态页分节显隐判据（2026-11-07 改）：只看 horde_command 能力是否被当前形态授予
        // （ZombieForms.canCommandHorde = 尸群领导技能 + 不死者形态，与服务端释放链的
        // isAvailable 同源），不再用「有领导或有随从」兜底——技能是持久资产、随从是世界
        // 存档状态，取消变形后旧判据仍为真，面板会一直挂着。
        // 权威在服务端：客户端 HordeTeamCache 只做 S2C 快照镜像，不自行判定。
        root.addProperty("granted", ZombieForms.canCommandHorde(player));
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

    /**
     * 是否已「确认过至少一次尸潮成功」（热忱的腐肉的获得/食用门槛）。
     * 权威字段 = {@link HordeState} 的 tide_success 时间戳（{@link #succeed} 写入）；
     * 旧存档兼容：无时间戳但通关计数 ≥ 1 视为已成功（计数同样只在 succeed 写入）。
     */
    public static boolean hasTideSuccess(EntityPlayerMP player) {
        if (HordeState.get(player.world).getTideSuccessTick(player.getUniqueID()) > 0L) {
            return true;
        }
        return tidesCleared(player) >= 1;
    }

    /**
     * UUID 版（随从掉落用：主人可能不在线）。在线时兼容旧存档计数；离线只认时间戳。
     */
    private static boolean hasTideSuccess(World world, UUID owner) {
        if (HordeState.get(world).getTideSuccessTick(owner) > 0L) {
            return true;
        }
        EntityPlayer player = world.getPlayerEntityByUUID(owner);
        return player instanceof EntityPlayerMP && tidesCleared((EntityPlayerMP) player) >= 1;
    }

    private static boolean onCooldown(EntityPlayerMP player, String abilityId) {
        return PlayerAbilityStore.get(player).getCooldown(abilityId, PlayerAbilityStore.now(player)) > 0;
    }

    private static void setCooldown(EntityPlayerMP player, String abilityId, int ticks) {
        if (ticks <= 0) {
            return;
        }
        // 形态冷却策略最外层裁决：僵尸形态乘数（ul:undead ×0.25）/固定覆盖在此统一套用
        int scaled = aaaa0ggmc.morphext.ability.AbilityCooldownPolicy.scale(player, abilityId, ticks);
        if (scaled <= 0) {
            return;
        }
        PlayerAbilityStore.update(player, data ->
                data.setCooldown(abilityId, PlayerAbilityStore.now(player), scaled));
    }

    /** 招募冷却基数（tick，档位 t1=160t/8s）；执行分支据此回执，框架统一套形态策略缩放（H7）。 */
    public static int recruitCooldownTicks() {
        return UlStrength.cooldownTicks("zombie_tide.horde.recruit_cooldown_ticks", "t1", 160);
    }

    /**
     * 发动尸潮冷却基数（tick，档位 t3=600t/30s）；执行分支据此回执（H7）。
     * 注意：cooldown_policy.json 的 {@code horde_launch} 固定钉子（30s 不打折）不受档位影响，
     * 改档位 t3 时需同步改该钉子（显式例外，见其 _doc）。
     */
    public static int launchCooldownTicks() {
        return UlStrength.cooldownTicks("zombie_tide.horde.launch_cooldown_ticks", "t3", 600);
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

    public static void action(EntityPlayerMP player, String key, Object... args) {
        org.apache.logging.log4j.LogManager.getLogger("ulmodpack")
                .info("[horde] action: " + key + (args.length > 0 ? " " + java.util.Arrays.toString(args) : ""));
        player.sendStatusMessage(new TextComponentTranslation(key, args), true);
    }

    /**
     * 只留服务端日志、不发玩家消息（2026-11-07 双提示去重）：
     * 能力释放链的拒绝分支改走 {@code ActivationResult.locked(reason)}，由 morphext 客户端
     * 用 reason 的 i18n 键在 Actionbar 渲染<b>唯一一条</b>提示；服务端若再自发一条文案，
     * 玩家会同时看到两条（如「要对着一只僵尸。」+「当前形态无法使用该能力」）。
     * 本方法保留原来的日志留痕，不产生任何玩家可见消息。
     */
    public static void logAction(EntityPlayerMP player, String key, Object... args) {
        org.apache.logging.log4j.LogManager.getLogger("ulmodpack")
                .info("[horde] action: {} [{}]{}", key, player.getName(),
                        args.length > 0 ? " " + java.util.Arrays.toString(args) : "");
    }

    private static void chat(EntityPlayerMP player, String key, Object... args) {
        player.sendMessage(new TextComponentTranslation(key, args));
    }
}
