package de.streuland.plot.biome;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BiomeBonusService {
    private final PlotManager plotManager;
    private final Map<String, BiomeProfile> profiles;

    public BiomeBonusService(PlotManager plotManager, FileConfiguration config) {
        this.plotManager = plotManager;
        this.profiles = Collections.unmodifiableMap(new HashMap<>(new BiomeConfigLoader(config).loadProfiles()));
    }

    public BiomeRuleSet getRuleSetForPlot(Plot plot) {
        if (plot == null) {
            return new BiomeRuleSet(null, null, null);
        }
        Biome biome = plotManager.getWorld().getBlockAt(plot.getCenterX(), plot.getSpawnY(), plot.getCenterZ()).getBiome();
        return getRuleSetForBiome(biome);
    }

    public BiomeRuleSet getRuleSetForBiome(Biome biome) {
        if (biome == null) {
            return new BiomeRuleSet(null, null, null);
        }
        BiomeProfile profile = profiles.get(biome.name());
        return profile == null ? new BiomeRuleSet(null, null, null) : profile.getCombinedRules();
    }

    public String describeBonuses(Biome biome) {
        BiomeRuleSet rules = getRuleSetForBiome(biome);
        StringBuilder builder = new StringBuilder();
        if (rules.getFarmlandAreaMultiplier() > 1.0) {
            builder.append("+Farmland ").append((int) Math.round((rules.getFarmlandAreaMultiplier() - 1.0) * 100)).append("% ");
        }
        if (rules.isGrantsNightVision()) {
            builder.append("Night Vision ");
        }
        if (rules.getMobSpawnRateMultiplier() > 1.0) {
            builder.append("Mob Spawn +").append((int) Math.round((rules.getMobSpawnRateMultiplier() - 1.0) * 100)).append("% ");
        }
        if (rules.isGrantsHeatResistanceOnSpawn()) {
            builder.append("Heat Resistance on Spawn ");
        }
        if (rules.getJungleRainHarvestMultiplier() > 1.0) {
            builder.append("Rain Harvest +").append((int) Math.round((rules.getJungleRainHarvestMultiplier() - 1.0) * 100)).append("% ");
        }
        if (rules.getSnowyDecayRateMultiplier() < 1.0) {
            builder.append("Decay -").append((int) Math.round((1.0 - rules.getSnowyDecayRateMultiplier()) * 100)).append("% ");
        }
        return builder.length() == 0 ? "Keine aktiven Boni" : builder.toString().trim();
    }
}
