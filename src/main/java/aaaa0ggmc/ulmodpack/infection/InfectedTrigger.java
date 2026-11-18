package aaaa0ggmc.ulmodpack.infection;

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
 * 进度「被感染」的自定义触发器（aaaa0ggmc_ul_modpack:infected）。
 * 无条件实例：感染变身发生时直接授予对应进度的 criterion。
 */
public class InfectedTrigger implements ICriterionTrigger<InfectedTrigger.Instance> {

    public static final ResourceLocation ID = new ResourceLocation("aaaa0ggmc_ul_modpack", "infected");

    private final java.util.Map<PlayerAdvancements, Set<Listener<Instance>>> listenersByPlayer = new java.util.HashMap<>();

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    public void addListener(PlayerAdvancements advancements, Listener<Instance> listener) {
        listenersByPlayer.computeIfAbsent(advancements, k -> new HashSet<>()).add(listener);
    }

    @Override
    public void removeListener(PlayerAdvancements advancements, Listener<Instance> listener) {
        Set<Listener<Instance>> set = listenersByPlayer.get(advancements);
        if (set != null) {
            set.remove(listener);
            if (set.isEmpty()) {
                listenersByPlayer.remove(advancements);
            }
        }
    }

    @Override
    public void removeAllListeners(PlayerAdvancements advancements) {
        listenersByPlayer.remove(advancements);
    }

    @Override
    public Instance deserializeInstance(JsonObject json, JsonDeserializationContext context) {
        return new Instance();
    }

    /** 感染发生时调用（服务端）。 */
    public void trigger(EntityPlayerMP player) {
        PlayerAdvancements advancements = player.getAdvancements();
        Set<Listener<Instance>> set = listenersByPlayer.get(advancements);
        if (set == null || set.isEmpty()) {
            return;
        }
        List<Listener<Instance>> pending = new ArrayList<>(set);
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
