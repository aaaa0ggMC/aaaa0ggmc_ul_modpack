package aaaa0ggmc.ulmodpack.breed;

import aaaa0ggmc.ulmodpack.dna.DnaRecipe;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 单条数据驱动的「结婚戒指繁殖」规则（来自 {@code breeding_rules.json}）。
 */
public final class BreedingRule {

    public final String targetEntity;
    public final String requiredMorph;
    public final boolean requireAdult;
    public final List<String> requiredPlayerVariants;
    public final Map<String, Object> requiredPlayerNbt;
    public final Map<String, Object> requiredTargetNbt;
    public final List<DnaRecipe.ItemRequirement> acceptedItems;
    public final String offspringEntity;
    public final boolean offspringBaby;
    public final List<String> inheritNbtKeys;
    public final int cooldownTicks;
    public final int xp;

    public BreedingRule(
            String targetEntity,
            String requiredMorph,
            boolean requireAdult,
            List<String> requiredPlayerVariants,
            Map<String, Object> requiredPlayerNbt,
            Map<String, Object> requiredTargetNbt,
            List<DnaRecipe.ItemRequirement> acceptedItems,
            String offspringEntity,
            boolean offspringBaby,
            List<String> inheritNbtKeys,
            int cooldownTicks,
            int xp) {
        this.targetEntity = targetEntity;
        this.requiredMorph = requiredMorph;
        this.requireAdult = requireAdult;
        this.requiredPlayerVariants = requiredPlayerVariants == null
                ? Collections.emptyList() : Collections.unmodifiableList(requiredPlayerVariants);
        this.requiredPlayerNbt = requiredPlayerNbt == null
                ? Collections.emptyMap() : Collections.unmodifiableMap(requiredPlayerNbt);
        this.requiredTargetNbt = requiredTargetNbt == null
                ? Collections.emptyMap() : Collections.unmodifiableMap(requiredTargetNbt);
        this.acceptedItems = acceptedItems == null
                ? Collections.emptyList() : Collections.unmodifiableList(acceptedItems);
        this.offspringEntity = offspringEntity != null && !offspringEntity.isEmpty() ? offspringEntity : targetEntity;
        this.offspringBaby = offspringBaby;
        this.inheritNbtKeys = inheritNbtKeys == null
                ? Collections.emptyList() : Collections.unmodifiableList(inheritNbtKeys);
        this.cooldownTicks = Math.max(20, cooldownTicks);
        this.xp = Math.max(0, xp);
    }
}
