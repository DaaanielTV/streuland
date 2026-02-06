package de.streuland.plot.biome;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Combines multiple biome effects into a single rule set.
 */
public class BiomeEffectStack {
    private final List<BiomeEffect> effects;

    public BiomeEffectStack(List<BiomeEffect> effects) {
        this.effects = effects == null ? new ArrayList<>() : new ArrayList<>(effects);
    }

    public List<BiomeEffect> getEffects() {
        return new ArrayList<>(effects);
    }

    public BiomeRuleSet buildCombinedRuleSet() {
        BiomeRuleSet combined = new BiomeRuleSet(null, null, null);
        effects.stream()
            .filter(BiomeEffect::isEnabled)
            .sorted(Comparator.comparingInt(BiomeEffect::getPriority))
            .forEach(effect -> combined.merge(effect.getRuleSet()));
        return combined;
    }
}
