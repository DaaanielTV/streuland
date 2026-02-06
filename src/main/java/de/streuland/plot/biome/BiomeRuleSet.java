package de.streuland.plot.biome;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents biome-dependent rules and multipliers for a plot.
 */
public class BiomeRuleSet {
    private final Set<String> buildRules;
    private final Map<String, Double> resourceMultipliers;
    private final Map<String, Integer> environmentConstraints;

    public BiomeRuleSet(Set<String> buildRules, Map<String, Double> resourceMultipliers, Map<String, Integer> environmentConstraints) {
        this.buildRules = buildRules == null ? new HashSet<>() : new HashSet<>(buildRules);
        this.resourceMultipliers = resourceMultipliers == null ? new HashMap<>() : new HashMap<>(resourceMultipliers);
        this.environmentConstraints = environmentConstraints == null ? new HashMap<>() : new HashMap<>(environmentConstraints);
    }

    public Set<String> getBuildRules() {
        return Collections.unmodifiableSet(buildRules);
    }

    public Map<String, Double> getResourceMultipliers() {
        return Collections.unmodifiableMap(resourceMultipliers);
    }

    public Map<String, Integer> getEnvironmentConstraints() {
        return Collections.unmodifiableMap(environmentConstraints);
    }

    /**
     * Merge another rule set into this one.
     *
     * Build rules are unioned, resource multipliers are multiplied, and constraints
     * are combined based on key prefix (max-* chooses the smaller value, min-* chooses the larger value).
     */
    public void merge(BiomeRuleSet other) {
        if (other == null) {
            return;
        }
        buildRules.addAll(other.buildRules);
        for (Map.Entry<String, Double> entry : other.resourceMultipliers.entrySet()) {
            resourceMultipliers.merge(entry.getKey(), entry.getValue(), (oldValue, newValue) -> oldValue * newValue);
        }
        for (Map.Entry<String, Integer> entry : other.environmentConstraints.entrySet()) {
            String key = entry.getKey();
            int value = entry.getValue();
            if (key.startsWith("max-")) {
                environmentConstraints.merge(key, value, Math::min);
            } else if (key.startsWith("min-")) {
                environmentConstraints.merge(key, value, Math::max);
            } else {
                environmentConstraints.put(key, value);
            }
        }
    }
}
