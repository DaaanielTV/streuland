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
    private double farmlandAreaMultiplier;
    private double mobSpawnRateMultiplier;
    private double jungleRainHarvestMultiplier;
    private double snowyDecayRateMultiplier;
    private boolean grantsNightVision;
    private boolean grantsHeatResistanceOnSpawn;

    public BiomeRuleSet(Set<String> buildRules, Map<String, Double> resourceMultipliers, Map<String, Integer> environmentConstraints) {
        this.buildRules = buildRules == null ? new HashSet<>() : new HashSet<>(buildRules);
        this.resourceMultipliers = resourceMultipliers == null ? new HashMap<>() : new HashMap<>(resourceMultipliers);
        this.environmentConstraints = environmentConstraints == null ? new HashMap<>() : new HashMap<>(environmentConstraints);
        this.farmlandAreaMultiplier = 1.0;
        this.mobSpawnRateMultiplier = 1.0;
        this.jungleRainHarvestMultiplier = 1.0;
        this.snowyDecayRateMultiplier = 1.0;
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

    public double getFarmlandAreaMultiplier() {
        return farmlandAreaMultiplier;
    }

    public void setFarmlandAreaMultiplier(double farmlandAreaMultiplier) {
        this.farmlandAreaMultiplier = farmlandAreaMultiplier;
    }

    public double getMobSpawnRateMultiplier() {
        return mobSpawnRateMultiplier;
    }

    public void setMobSpawnRateMultiplier(double mobSpawnRateMultiplier) {
        this.mobSpawnRateMultiplier = mobSpawnRateMultiplier;
    }

    public double getJungleRainHarvestMultiplier() {
        return jungleRainHarvestMultiplier;
    }

    public void setJungleRainHarvestMultiplier(double jungleRainHarvestMultiplier) {
        this.jungleRainHarvestMultiplier = jungleRainHarvestMultiplier;
    }

    public double getSnowyDecayRateMultiplier() {
        return snowyDecayRateMultiplier;
    }

    public void setSnowyDecayRateMultiplier(double snowyDecayRateMultiplier) {
        this.snowyDecayRateMultiplier = snowyDecayRateMultiplier;
    }

    public boolean isGrantsNightVision() {
        return grantsNightVision;
    }

    public void setGrantsNightVision(boolean grantsNightVision) {
        this.grantsNightVision = grantsNightVision;
    }

    public boolean isGrantsHeatResistanceOnSpawn() {
        return grantsHeatResistanceOnSpawn;
    }

    public void setGrantsHeatResistanceOnSpawn(boolean grantsHeatResistanceOnSpawn) {
        this.grantsHeatResistanceOnSpawn = grantsHeatResistanceOnSpawn;
    }

    /**
     * Merge another rule set into this one.
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
        farmlandAreaMultiplier *= other.farmlandAreaMultiplier;
        mobSpawnRateMultiplier *= other.mobSpawnRateMultiplier;
        jungleRainHarvestMultiplier *= other.jungleRainHarvestMultiplier;
        snowyDecayRateMultiplier *= other.snowyDecayRateMultiplier;
        grantsNightVision = grantsNightVision || other.grantsNightVision;
        grantsHeatResistanceOnSpawn = grantsHeatResistanceOnSpawn || other.grantsHeatResistanceOnSpawn;
    }
}
