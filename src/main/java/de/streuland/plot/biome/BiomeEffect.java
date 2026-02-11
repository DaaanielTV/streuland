package de.streuland.plot.biome;

/**
 * A single stackable biome effect entry.
 */
public class BiomeEffect {
    private final String id;
    private final boolean enabled;
    private final int priority;
    private final BiomeRuleSet ruleSet;

    public BiomeEffect(String id, boolean enabled, int priority, BiomeRuleSet ruleSet) {
        this.id = id;
        this.enabled = enabled;
        this.priority = priority;
        this.ruleSet = ruleSet == null ? new BiomeRuleSet(null, null, null) : ruleSet;
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPriority() {
        return priority;
    }

    public BiomeRuleSet getRuleSet() {
        return ruleSet;
    }
}
