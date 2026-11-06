package aaaa0ggmc.ulmodpack.faction;

import aaaa0ggmc.morphext.faction.AggressionPolicy;
import aaaa0ggmc.morphext.faction.FactionEnmity;
import aaaa0ggmc.morphext.faction.FactionRelation;
import com.google.common.base.Predicate;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import javax.annotation.Nullable;

/**
 * 统一仇恨接口（morphext {@code FactionEnmity} 的本模组统一调用入口）。
 *
 * <p>自 G1–G10 扩展落地后本类为<b>纯委托门面</b>：全部方法一行委托
 * {@code FactionEnmity}（morph-extension {@code faction} 包），判定逻辑零复制
 * （逐生物层不得复制判定逻辑的边界要求）。保留本入口的目的：25+ 注入点的
 * import 面稳定，morphext 门面若再演进只改本文件。</p>
 *
 * <p>语义速览（详见 morph-extension {@code ARCHITECTURE.md}「仇恨统一接口」节）：</p>
 * <ul>
 *   <li>{@code isEnemy}：ENEMY/MODERATE（含无阵营回退）→ 敌对；MEMBER → 被打才反击；
 *       RELATIVE → 半血以下自卫；LORD → 永不；内建主人/同主豁免（§G1）与
 *       变身 dummy 归属归一（resolveHost）；可传 {@link AggressionPolicy#DEFENSIVE}
 *       变为「仅报复」口径（§G7，中立生物用）；</li>
 *   <li>{@code isAlly}：主人/同主/同队/阵营和平合一（§G1）；</li>
 *   <li>{@code isTolerantOf}：RELATIVE/LORD 宽容面（§G5，受击减让用）；</li>
 *   <li>{@code provokedBy}：在案挑衅统一判据（revenge + dummy 归属，§G10）；</li>
 *   <li>{@code findNearestEnemy}：支持 self 中心/Vec3d/BlockPos 锚点（§G3）与
 *       {@code Class<E>} 类型化零强转（§G4）；</li>
 *   <li>{@code resolveDamageSource}：近战→投射物→dummy 三级伤害归因（§G2）。</li>
 * </ul>
 *
 * <p>全部查询纯读；据此改写游戏状态的代码在服务端执行。</p>
 */
public final class HateQueries {

    private HateQueries() {
    }

    /** {@code relation(self, target)}：阵营图原始关系。 */
    public static FactionRelation relation(EntityLivingBase self, EntityLivingBase target) {
        return FactionEnmity.relation(self, target);
    }

    /** {@code isEnemy(self, target)}：默认 AGGRESSIVE 口径（MODERATE=敌对，保原版）。 */
    public static boolean isEnemy(EntityLivingBase self, EntityLivingBase target) {
        return FactionEnmity.isEnemy(self, target);
    }

    /** {@code isEnemy(self, target, policy)}：DEFENSIVE=仅报复（中立生物口径，§G7）。 */
    public static boolean isEnemy(EntityLivingBase self, EntityLivingBase target, AggressionPolicy policy) {
        return FactionEnmity.isEnemy(self, target, policy);
    }

    /** {@code isAlly}：主人/同主/同队/阵营和平合一（§G1）。 */
    public static boolean isAlly(EntityLivingBase self, EntityLivingBase target) {
        return FactionEnmity.isAlly(self, target);
    }

    /** {@code isTolerantOf}：宽容面（RELATIVE/LORD，§G5 受击减让）。 */
    public static boolean isTolerantOf(EntityLivingBase self, EntityLivingBase target) {
        return FactionEnmity.isTolerantOf(self, target);
    }

    /** {@code provokedBy}：在案挑衅判据（revenge + dummy 归属，§G10）。 */
    public static boolean provokedBy(EntityLivingBase self, EntityLivingBase target) {
        return FactionEnmity.provokedBy(self, target);
    }

    /** {@code findNearestEnemy(self, range, filter)}：self 中心找最近敌人。 */
    @Nullable
    public static EntityLivingBase findNearestEnemy(EntityLivingBase self, double range,
                                                   @Nullable Predicate<? super EntityLivingBase> filter) {
        return FactionEnmity.findNearestEnemy(self, range, filter);
    }

    /** 类型化零强转版（§G4）。 */
    @Nullable
    public static <E extends EntityLivingBase> E findNearestEnemy(EntityLivingBase self, double range,
                                                                 Class<E> type,
                                                                 @Nullable Predicate<? super E> filter) {
        return FactionEnmity.findNearestEnemy(self, range, type, filter);
    }

    /** 锚点版（§G3，判定主体仍是 self，扫描中心是 anchor）。 */
    @Nullable
    public static EntityLivingBase findNearestEnemy(EntityLivingBase self, BlockPos anchor, double range,
                                                   @Nullable Predicate<? super EntityLivingBase> filter) {
        return FactionEnmity.findNearestEnemy(self, anchor, range, filter);
    }

    /** 锚点 + 类型化版（§G3/§G4）。 */
    @Nullable
    public static <E extends EntityLivingBase> E findNearestEnemy(EntityLivingBase self, Vec3d anchor, double range,
                                                                 Class<E> type,
                                                                 @Nullable Predicate<? super E> filter) {
        return FactionEnmity.findNearestEnemy(self, anchor, range, type, filter);
    }

    /** {@code resolveDamageSource}：伤害三级归因（近战→投射物→dummy，§G2）。 */
    @Nullable
    public static EntityLivingBase resolveDamageSource(@Nullable DamageSource source) {
        return FactionEnmity.resolveDamageSource(source);
    }

    /** {@code resolveHost}：变身 dummy 归一到宿主玩家。 */
    @Nullable
    public static EntityPlayer resolveHost(@Nullable net.minecraft.entity.Entity entity) {
        return FactionEnmity.resolveHost(entity);
    }
}
