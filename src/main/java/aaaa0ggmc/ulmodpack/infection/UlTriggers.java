package aaaa0ggmc.ulmodpack.infection;

import aaaa0ggmc.ulmodpack.achievement.CryingWolfTrigger;
import net.minecraft.advancements.CriteriaTriggers;

/**
 * 自定义进度触发器注册（preInit 期一次性注册）。
 *
 * <p>本模组全部自定义 trigger 的单一注册点：{@link #INFECTED}（感染变身，
 * {@code aaaa0ggmc_ul_modpack:infected}）与 {@link #CRYING_WOLF}（狼来了，
 * {@code aaaa0ggmc_ul_modpack:crying_wolf}）。都必须早于任何玩家登录
 * （{@code PlayerAdvancements} 构造时按 criteria 反序列化 trigger），故放 preInit。</p>
 */
public final class UlTriggers {

    public static InfectedTrigger INFECTED;

    public static CryingWolfTrigger CRYING_WOLF;

    private UlTriggers() {
    }

    public static void init() {
        if (INFECTED == null) {
            INFECTED = CriteriaTriggers.register(new InfectedTrigger());
        }
        if (CRYING_WOLF == null) {
            CRYING_WOLF = CriteriaTriggers.register(new CryingWolfTrigger());
        }
    }
}
