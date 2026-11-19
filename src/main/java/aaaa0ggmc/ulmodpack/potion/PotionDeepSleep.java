package aaaa0ggmc.ulmodpack.potion;

import aaaa0ggmc.ulmodpack.UlModpack;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.potion.Potion;

/**
 * 安眠效果：
 * <ul>
 *   <li>自带缓慢 I（移速 -15%）与虚弱 I（攻击力 -4.0）；</li>
 *   <li>自带半速生命恢复（每 100 tick 恢复 1 点生命，等同于生命恢复 I 一半时长/速率的总回复量）；</li>
 *   <li>持续期间只要躺上床或睡袋进入睡眠状态，立即解除变身、退回玩家原本种族。</li>
 * </ul>
 */
public class PotionDeepSleep extends Potion {

    private static final String SLOW_UUID = "9d6e4c1b-3a28-4f57-8b10-6c4d2e8f1a01";
    private static final String WEAK_UUID = "9d6e4c1b-3a28-4f57-8b10-6c4d2e8f1a02";
    private static final int BASE_REGEN_INTERVAL_TICKS = 100;

    public PotionDeepSleep() {
        super(false, 0x5B6EE1);
        setRegistryName(UlModpack.MODID, "deep_sleep");
        setPotionName("effect." + UlModpack.MODID + ".deep_sleep");
        setBeneficial();
        registerPotionAttributeModifier(SharedMonsterAttributes.MOVEMENT_SPEED, SLOW_UUID, -0.15D, 2);
        registerPotionAttributeModifier(SharedMonsterAttributes.ATTACK_DAMAGE, WEAK_UUID, -4.0D, 0);
    }

    @Override
    public boolean isReady(int duration, int amplifier) {
        int interval = BASE_REGEN_INTERVAL_TICKS >> amplifier;
        return interval <= 0 || duration % interval == 0;
    }

    @Override
    public void performEffect(EntityLivingBase entity, int amplifier) {
        if (entity.getHealth() < entity.getMaxHealth()) {
            entity.heal(1.0F);
        }
    }
}
