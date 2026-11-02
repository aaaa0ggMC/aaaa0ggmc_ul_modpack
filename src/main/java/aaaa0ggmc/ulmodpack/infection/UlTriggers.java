package aaaa0ggmc.ulmodpack.infection;

import net.minecraft.advancements.CriteriaTriggers;

/** 自定义进度触发器注册（preInit 期一次性注册）。 */
public final class UlTriggers {

    public static InfectedTrigger INFECTED;

    private UlTriggers() {
    }

    public static void init() {
        if (INFECTED == null) {
            INFECTED = CriteriaTriggers.register(new InfectedTrigger());
        }
    }
}
