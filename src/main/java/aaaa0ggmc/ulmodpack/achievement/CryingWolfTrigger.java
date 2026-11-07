package aaaa0ggmc.ulmodpack.achievement;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.minecraft.advancements.ICriterionTrigger;
import net.minecraft.advancements.PlayerAdvancements;
import net.minecraft.advancements.critereon.AbstractCriterionInstance;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 成就「狼来了 / Crying Wolf」的原版进度触发器（{@code aaaa0ggmc_ul_modpack:crying_wolf}）。
 *
 * <p>无条件实例：尸潮发动失败（附近活村民不足、走失败宣告流程）累计到阈值时由
 * {@link Achievements#unlock} 授予本 criterion，触发点见
 * {@link aaaa0ggmc.ulmodpack.zombie.horde.HordeManager#launchOrDisband}。</p>
 *
 * <p>为什么用自定义 trigger 而不是 {@code minecraft:impossible}：本模组已有同一模式
 * （{@link aaaa0ggmc.ulmodpack.infection.InfectedTrigger}，成就「被感染」在线上跑通），
 * listener 机制与直接 {@code PlayerAdvancements#grantCriterion} 等价，且 criterion 语义
 * 自带「只能由本模组代码授予」。{@code impossible} 方案同样可行（少一个类，但每次要自己
 * 去 AdvancementManager 取 Advancement 对象），此处按既有约定保持一致。</p>
 */
public class CryingWolfTrigger implements ICriterionTrigger<CryingWolfTrigger.Instance> {

    public static final ResourceLocation ID = new ResourceLocation("aaaa0ggmc_ul_modpack", "crying_wolf");

    /** criterion 名（与 advancements/crying_wolf.json 的 criteria 键一致）。 */
    public static final String CRITERION = "crying_wolf";

    private final Set<Listener<Instance>> listeners = new HashSet<>();

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void addListener(PlayerAdvancements advancements, Listener<Instance> listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(PlayerAdvancements advancements, Listener<Instance> listener) {
        listeners.remove(listener);
    }

    @Override
    public void removeAllListeners(PlayerAdvancements advancements) {
        listeners.clear();
    }

    @Override
    public Instance deserializeInstance(JsonObject json, JsonDeserializationContext context) {
        return new Instance();
    }

    /** 狼来了触发点（服务端）：授予该玩家 crying_wolf criterion。 */
    public void trigger(EntityPlayerMP player) {
        PlayerAdvancements advancements = player.getAdvancements();
        List<Listener<Instance>> pending = new ArrayList<>(listeners);
        for (Listener<Instance> listener : pending) {
            if (listener.getCriterionInstance().test(player)) {
                listener.grantCriterion(advancements);
            }
        }
    }

    public static class Instance extends AbstractCriterionInstance {

        public Instance() {
            super(ID);
        }

        public boolean test(EntityPlayerMP player) {
            return true;
        }
    }
}
