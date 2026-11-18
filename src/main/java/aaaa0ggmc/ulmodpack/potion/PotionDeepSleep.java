package aaaa0ggmc.ulmodpack.potion;

import aaaa0ggmc.ulmodpack.UlModpack;
import net.minecraft.potion.Potion;

/**
 * 安眠效果：持续期间只要躺上床或睡袋进入睡眠状态，立即解除变身、退回玩家原本种族。
 */
public class PotionDeepSleep extends Potion {

    public PotionDeepSleep() {
        super(false, 0x5B6EE1);
        setRegistryName(UlModpack.MODID, "deep_sleep");
        setPotionName("effect." + UlModpack.MODID + ".deep_sleep");
        setBeneficial();
    }
}
