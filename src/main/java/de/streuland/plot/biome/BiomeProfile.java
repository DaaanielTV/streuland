package de.streuland.plot.biome;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates all effects that apply to a single biome.
 */
public class BiomeProfile {
    private final String biomeKey;
    private final List<BiomeEffect> effects;

    public BiomeProfile(String biomeKey, List<BiomeEffect> effects) {
        this.biomeKey = biomeKey;
        this.effects = effects == null ? new ArrayList<>() : new ArrayList<>(effects);
    }

    public String getBiomeKey() {
        return biomeKey;
    }

    public List<BiomeEffect> getEffects() {
        return new ArrayList<>(effects);
    }

    public BiomeRuleSet getCombinedRules() {
        return new BiomeEffectStack(effects).buildCombinedRuleSet();
    }
}
