package de.streuland.plot.biome;

import org.bukkit.block.Biome;

import java.util.Collections;
import java.util.Map;

/**
 * Resolves biome bonus rules from static profiles + optional dynamic overrides.
 */
public class BiomeBonusResolver {
    private final Map<String, BiomeProfile> profiles;

    public BiomeBonusResolver(Map<String, BiomeProfile> profiles) {
        this.profiles = profiles == null ? Collections.emptyMap() : Collections.unmodifiableMap(profiles);
    }

    public BiomeRuleSet resolve(Biome biome, BiomeRuleSet override) {
        BiomeRuleSet base = resolveBase(biome);
        if (override == null) {
            return base;
        }
        BiomeRuleSet merged = new BiomeRuleSet(base.getBuildRules(), base.getResourceMultipliers(), base.getEnvironmentConstraints());
        merged.setFarmlandAreaMultiplier(override.getFarmlandAreaMultiplier() != 1.0 ? override.getFarmlandAreaMultiplier() : base.getFarmlandAreaMultiplier());
        merged.setMobSpawnRateMultiplier(override.getMobSpawnRateMultiplier() != 1.0 ? override.getMobSpawnRateMultiplier() : base.getMobSpawnRateMultiplier());
        merged.setJungleRainHarvestMultiplier(override.getJungleRainHarvestMultiplier() != 1.0 ? override.getJungleRainHarvestMultiplier() : base.getJungleRainHarvestMultiplier());
        merged.setSnowyDecayRateMultiplier(override.getSnowyDecayRateMultiplier() != 1.0 ? override.getSnowyDecayRateMultiplier() : base.getSnowyDecayRateMultiplier());
        merged.setGrantsNightVision(override.isGrantsNightVision() || base.isGrantsNightVision());
        merged.setGrantsHeatResistanceOnSpawn(override.isGrantsHeatResistanceOnSpawn() || base.isGrantsHeatResistanceOnSpawn());
        return merged;
    }

    public BiomeRuleSet resolveBase(Biome biome) {
        if (biome == null) {
            return new BiomeRuleSet(null, null, null);
        }
        BiomeProfile profile = profiles.get(biome.name());
        return profile == null ? new BiomeRuleSet(null, null, null) : profile.getCombinedRules();
    }
}
