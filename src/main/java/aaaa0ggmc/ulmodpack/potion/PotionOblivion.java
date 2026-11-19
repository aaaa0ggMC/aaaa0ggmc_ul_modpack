package aaaa0ggmc.ulmodpack.potion;

import aaaa0ggmc.ulmodpack.UlModpack;
import net.minecraft.potion.Potion;

/**
 * 遗忘效果：常规生物落入孟婆汤时获得（10s），倒计时结束时清除仇恨并进入 1s 失忆窗口（无法重新索敌）。
 */
public class PotionOblivion extends Potion {

    public PotionOblivion() {
        super(true, 0xC6B8E8);
        setRegistryName(UlModpack.MODID, "oblivion");
        setPotionName("effect." + UlModpack.MODID + ".oblivion");
    }
}
