package aaaa0ggmc.ulmodpack;

import aaaa0ggmc.ulmodpack.achievement.CryingWolfTrigger;
import aaaa0ggmc.ulmodpack.infection.InfectedTrigger;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.ICriterionTrigger;
import net.minecraft.advancements.PlayerAdvancements;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * InfectedTrigger / CryingWolfTrigger 多玩家监听器隔离测试（无 Forge 依赖）。
 */
public class TriggerIsolationTest {

    private static <T> T allocate(Class<T> cls) throws Exception {
        Class<?> unsafeCls = Class.forName("sun.misc.Unsafe");
        Field f = unsafeCls.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Object unsafe = f.get(null);
        Method alloc = unsafeCls.getMethod("allocateInstance", Class.class);
        return cls.cast(alloc.invoke(unsafe, cls));
    }

    private static Advancement newAdvancement(String name) {
        return new Advancement(
                new ResourceLocation("test", name),
                null, null, null,
                Collections.emptyMap(),
                new String[0][]);
    }
    @SuppressWarnings("unchecked")
    private static Map<PlayerAdvancements, Set<?>> listenersMap(Object trigger) throws Exception {
        Field f = trigger.getClass().getDeclaredField("listenersByPlayer");
        f.setAccessible(true);
        return (Map<PlayerAdvancements, Set<?>>) f.get(trigger);
    }

    @Test
    public void infectedTriggerRemoveAllIsolatesPlayers() throws Exception {
        InfectedTrigger trigger = new InfectedTrigger();
        PlayerAdvancements playerA = allocate(PlayerAdvancements.class);
        PlayerAdvancements playerB = allocate(PlayerAdvancements.class);
        Advancement advA = newAdvancement("a");
        Advancement advB = newAdvancement("b");

        ICriterionTrigger.Listener<InfectedTrigger.Instance> listenerA =
                new ICriterionTrigger.Listener<>(new InfectedTrigger.Instance(), advA, "a");
        ICriterionTrigger.Listener<InfectedTrigger.Instance> listenerB =
                new ICriterionTrigger.Listener<>(new InfectedTrigger.Instance(), advB, "b");

        trigger.addListener(playerA, listenerA);
        trigger.addListener(playerB, listenerB);

        Map<PlayerAdvancements, Set<?>> map = listenersMap(trigger);
        assertEquals(2, map.size());

        trigger.removeAllListeners(playerA);
        assertFalse(map.containsKey(playerA));
        assertTrue(map.containsKey(playerB));
        assertEquals(1, map.get(playerB).size());
    }

    @Test
    public void cryingWolfTriggerRemoveAllIsolatesPlayers() throws Exception {
        CryingWolfTrigger trigger = new CryingWolfTrigger();
        PlayerAdvancements playerA = allocate(PlayerAdvancements.class);
        PlayerAdvancements playerB = allocate(PlayerAdvancements.class);
        Advancement advA = newAdvancement("a");
        Advancement advB = newAdvancement("b");

        ICriterionTrigger.Listener<CryingWolfTrigger.Instance> listenerA =
                new ICriterionTrigger.Listener<>(new CryingWolfTrigger.Instance(), advA, "a");
        ICriterionTrigger.Listener<CryingWolfTrigger.Instance> listenerB =
                new ICriterionTrigger.Listener<>(new CryingWolfTrigger.Instance(), advB, "b");

        trigger.addListener(playerA, listenerA);
        trigger.addListener(playerB, listenerB);

        Map<PlayerAdvancements, Set<?>> map = listenersMap(trigger);
        assertEquals(2, map.size());

        trigger.removeAllListeners(playerA);
        assertFalse(map.containsKey(playerA));
        assertTrue(map.containsKey(playerB));
        assertEquals(1, map.get(playerB).size());
    }
}
