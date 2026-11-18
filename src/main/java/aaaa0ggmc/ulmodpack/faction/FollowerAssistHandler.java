package aaaa0ggmc.ulmodpack.faction;

import aaaa0ggmc.morphext.follower.Followers;
import aaaa0ggmc.uld.UlStrength;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「随从协助」通用语义（主人指令 / 防御 &gt; 阵营判定），内容层实现、不改 morphext 核心。
 *
 * <p>用户设计（2026-11-07）：</p>
 * <ol>
 *   <li><b>协助攻击</b>：主人攻击某实体后（含远程/技能归因伤害，经
 *       {@link HateQueries#resolveDamageSource} 归属归一），随从把该实体设为攻击目标；</li>
 *   <li><b>防御</b>：主人被攻击时随从反击攻击者（防御记录优先于协助记录）；</li>
 *   <li><b>优先级</b>：主人指令 &gt; 阵营判定——即使目标与随从同阵营（MEMBER/RELATIVE）
 *       也要攻击。直接 {@code setAttackTarget} 不经「目标选择 AI」的阵营过滤；生物目标
 *       也不受 {@code FactionManager} 的玩家目标抑制（该抑制只处理玩家/变身 dummy 目标，
 *       见 FactionManager.onLivingSetAttackTarget / onLivingUpdate 的 early-return）——
 *       「玩家打骷髅随从不出手」的骷髅（生物目标）场景由此直接生效；</li>
 *   <li><b>绝不攻击</b>：主人本人、主人的其他随从、主人的同队友玩家（及队友的随从）；</li>
 *   <li><b>防副作用</b>：协助目标失效（死亡/跑远/过期）后清空随从的攻击目标、恢复跟随；
 *       只在目标变化时设置（防每 tick 重复设置的 AI 抖动），校准每 20 tick 最多一次；
 *       不碰 revengeTarget、不与 HurtByTarget / suppressMobTarget 清仇恨机制交互。</li>
 * </ol>
 *
 * <p><b>已知边界</b>：目标是<b>玩家</b>且与随从同阵营（MEMBER/RELATIVE）时，morphext 核心
 * {@code FactionManager.onLivingSetAttackTarget / onLivingUpdate} 会按阵营关系抑制该目标
 * （防同阵营误击，suppressMobTarget）。内容层不应与之对抗（会抖动）；「主人指令目标」
 * 的核心旁路方案见回报（建议在 isAttackPermitted 前加「owner-commanded target」豁免）。</p>
 *
 * <p>协助判定条件（全部服务端，数值均走 ul-strength 配置、默认兜底）：</p>
 * <ul>
 *   <li>时效：主人最后一次对该目标动手后 {@code zombie_tide.assist.ttl_ticks}（100t=5s）内有效，命中即续期；</li>
 *   <li>距离：目标距主人 ≤ {@code zombie_tide.assist.range}（32 格）；随从距主人 ≤
 *       {@code zombie_tide.assist.follower_range}（64 格）才受令；</li>
 *   <li>过滤：目标存活，且不是主人/同主随从/同队友玩家/同队友的随从（{@link #isForbidden}）。</li>
 * </ul>
 */
public final class FollowerAssistHandler {

    /** 协助有效期（tick）：主人最后一次对该目标出手后的存活窗口。 */
    private static long assistTtlTicks() {
        return Math.max(1L, (long) aaaa0ggmc.ulmodpack.UlModpackContainer.instance().strengthService().num("zombie_tide.assist.ttl_ticks", 100.0D));
    }

    /** 目标距主人的最大协助距离（格）。 */
    private static double assistRange() {
        return aaaa0ggmc.ulmodpack.UlModpackContainer.instance().strengthService().num("zombie_tide.assist.range", 32.0D);
    }

    /** 受令随从距主人的最大距离（格，对齐 horde follower_count_radius 口径）。 */
    private static double followerRange() {
        return aaaa0ggmc.ulmodpack.UlModpackContainer.instance().strengthService().num("zombie_tide.assist.follower_range", 64.0D);
    }

    /** 校准节流（tick）：每 N tick 最多重新校准/补设一次（防每 tick 重复设置导致 AI 抖动）。 */
    private static long reconcileInterval() {
        return Math.max(1L, (long) aaaa0ggmc.ulmodpack.UlModpackContainer.instance().strengthService().num("zombie_tide.assist.reconcile_interval", 20.0D));
    }

    /** 每个主人一条协助记录（仅服务端主线程访问）。 */
    private static final Map<UUID, Assist> ASSISTS = new HashMap<>();

    private FollowerAssistHandler() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(net.minecraftforge.fml.common.gameevent.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) {
            ASSISTS.remove(event.player.getUniqueID());
        }
    }

    /** 协助记录：目标（实体 id + UUID 双保险）、有效期、是否防御语义。 */
    private static final class Assist {
        final int targetId;
        final UUID targetUuid;
        long expireTick;
        final boolean defense;

        Assist(EntityLivingBase target, long expireTick, boolean defense) {
            this.targetId = target.getEntityId();
            this.targetUuid = target.getUniqueID();
            this.expireTick = expireTick;
            this.defense = defense;
        }
    }

    /**
     * 统一记录主人意图：防御（主人被打）与协助（主人出手）共用一条记录，
     * 防御优先（协助记录不覆盖未过期的防御记录）。
     */
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity().world.isRemote) {
            return;
        }
        EntityLivingBase victim = event.getEntityLiving();
        EntityLivingBase attacker = HateQueries.resolveDamageSource(event.getSource());
        if (attacker == null || attacker == victim) {
            return;
        }
        // 防御：主人被攻击 → 随从反击攻击者
        if (victim instanceof EntityPlayerMP) {
            record((EntityPlayerMP) victim, attacker, true);
            return;
        }
        // 协助：主人（含变身 dummy 归属）出手 → 随从集火同一目标
        if (attacker instanceof EntityPlayerMP) {
            record((EntityPlayerMP) attacker, victim, false);
        }
    }

    private static void record(EntityPlayerMP owner, EntityLivingBase target, boolean defense) {
        if (target.isDead || isForbidden(owner, target)) {
            return;
        }
        long now = owner.world.getTotalWorldTime();
        Assist prev = ASSISTS.get(owner.getUniqueID());
        // 防御优先：未过期的防御记录不被普通协助降级
        if (prev != null && prev.defense && !defense && now <= prev.expireTick) {
            return;
        }
        if (prev != null && prev.targetId == target.getEntityId()
                && prev.targetUuid.equals(target.getUniqueID())) {
            prev.expireTick = now + assistTtlTicks(); // 命中续期
            return;
        }
        Assist assist = new Assist(target, now + assistTtlTicks(), defense);
        ASSISTS.put(owner.getUniqueID(), assist);
        assignFollowers(owner, assist); // 新目标立刻指派，不等下个校准拍
    }

    /** 校准拍：目标仍有效 → 补设（仅目标变化时）；失效 → 清目标恢复跟随。 */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.world.isRemote
                || !(event.player instanceof EntityPlayerMP) || ASSISTS.isEmpty()) {
            return;
        }
        long now = event.player.world.getTotalWorldTime();
        if (now % reconcileInterval() != 0) {
            return;
        }
        EntityPlayerMP owner = (EntityPlayerMP) event.player;
        Assist assist = ASSISTS.get(owner.getUniqueID());
        if (assist == null) {
            return;
        }
        EntityLivingBase target = resolve(owner.world, assist);
        boolean valid = target != null && !target.isDead
                && now <= assist.expireTick
                && owner.getDistanceSq(target) <= assistRange() * assistRange()
                && !isForbidden(owner, target);
        if (!valid) {
            clearAssist(owner, assist);
            return;
        }
        for (EntityLiving follower : followersNear(owner)) {
            // 只在目标变化时设置（setAttackTarget 每次都派发事件，重复设是抖动源）
            if (follower.getAttackTarget() != target) {
                follower.setAttackTarget(target);
            }
        }
    }

    /** 指派：给主人附近所有受令随从设置协助目标。 */
    private static void assignFollowers(EntityPlayerMP owner, Assist assist) {
        EntityLivingBase target = resolve(owner.world, assist);
        if (target == null) {
            return;
        }
        for (EntityLiving follower : followersNear(owner)) {
            follower.setAttackTarget(target);
        }
    }

    /** 清除：只把「目标 == 本次协助目标」的随从目标清空（不动它自己的复仇/其它目标），恢复跟随。 */
    private static void clearAssist(EntityPlayerMP owner, Assist assist) {
        EntityLivingBase target = resolve(owner.world, assist);
        for (EntityLiving follower : followersNear(owner)) {
            if (target != null && follower.getAttackTarget() == target) {
                follower.setAttackTarget(null);
            }
        }
        ASSISTS.remove(owner.getUniqueID());
    }

    /** 实体 id + UUID 双保险解析（防 id 复用指向别的实体）。 */
    private static EntityLivingBase resolve(World world, Assist assist) {
        Entity entity = world.getEntityByID(assist.targetId);
        if (!(entity instanceof EntityLivingBase) || !assist.targetUuid.equals(entity.getUniqueID())) {
            return null;
        }
        return (EntityLivingBase) entity;
    }

    /**
     * 禁止目标过滤（主人指令的安全边界）：主人本人、主人自己的随从、
     * 同队友玩家，以及队友的随从。
     */
    private static boolean isForbidden(EntityPlayerMP owner, EntityLivingBase target) {
        if (target == owner) {
            return true;
        }
        if (Followers.isFollowerOf(target, owner.getUniqueID())) {
            return true;
        }
        if (target instanceof EntityPlayer && HateQueries.isAlly(owner, target)) {
            return true;
        }
        UUID targetOwner = Followers.getOwnerUuid(target);
        if (targetOwner != null) {
            EntityPlayer teammate = owner.world.getPlayerEntityByUUID(targetOwner);
            return teammate != null && HateQueries.isAlly(owner, teammate);
        }
        return false;
    }

    /** 主人附近（{@code zombie_tide.assist.follower_range}）的自有随从。 */
    private static List<EntityLiving> followersNear(EntityPlayerMP owner) {
        UUID ownerUuid = owner.getUniqueID();
        List<EntityLiving> out = new ArrayList<>();
        for (EntityLiving e : owner.world.getEntitiesWithinAABB(EntityLiving.class,
                owner.getEntityBoundingBox().grow(followerRange()))) {
            if (!e.isDead && ownerUuid.equals(Followers.getOwnerUuid(e))) {
                out.add(e);
            }
        }
        return out;
    }
}
