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

    /** 感染发生时调用（服务端）。 */
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
