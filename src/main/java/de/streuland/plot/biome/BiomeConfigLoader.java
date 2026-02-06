package de.streuland.plot.biome;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads biome profiles and stackable effects from configuration.
 */
public class BiomeConfigLoader {
    private final FileConfiguration config;

    public BiomeConfigLoader(FileConfiguration config) {
        this.config = config;
    }

    public Map<String, BiomeProfile> loadProfiles() {
        Map<String, BiomeProfile> profiles = new HashMap<>();
        ConfigurationSection biomesSection = config.getConfigurationSection("biomes.effects");
        if (biomesSection == null) {
            return profiles;
        }

        Map<String, BiomeEffect> stackableEffects = loadStackableEffects();
        for (String biomeKey : biomesSection.getKeys(false)) {
            ConfigurationSection biomeSection = biomesSection.getConfigurationSection(biomeKey);
            if (biomeSection == null) {
                continue;
            }
            List<BiomeEffect> effects = new ArrayList<>();
            effects.add(buildEffect("base", biomeSection));

            List<String> stacked = biomeSection.getStringList("stacked-effects");
            for (String effectId : stacked) {
                BiomeEffect effect = stackableEffects.get(effectId);
                if (effect != null) {
                    effects.add(effect);
                }
            }
            profiles.put(biomeKey, new BiomeProfile(biomeKey, effects));
        }

        return profiles;
    }

    private Map<String, BiomeEffect> loadStackableEffects() {
        Map<String, BiomeEffect> effects = new HashMap<>();
        ConfigurationSection stackedSection = config.getConfigurationSection("biomes.stacked-effects");
        if (stackedSection == null) {
            return effects;
        }
        for (String key : stackedSection.getKeys(false)) {
            ConfigurationSection effectSection = stackedSection.getConfigurationSection(key);
            if (effectSection != null) {
                effects.put(key, buildEffect(key, effectSection));
            }
        }
        return effects;
    }

    private BiomeEffect buildEffect(String id, ConfigurationSection section) {
        boolean enabled = section.getBoolean("enabled", true);
        int priority = section.getInt("priority", 0);
        List<String> rules = section.getStringList("build-rules");
        Map<String, Double> multipliers = toDoubleMap(section.getConfigurationSection("resource-multipliers"));
        Map<String, Integer> constraints = toIntMap(section.getConfigurationSection("environment-constraints"));
        BiomeRuleSet ruleSet = new BiomeRuleSet(
            rules == null ? null : new java.util.HashSet<>(rules),
            multipliers,
            constraints
        );
        return new BiomeEffect(id, enabled, priority, ruleSet);
    }

    private Map<String, Double> toDoubleMap(ConfigurationSection section) {
        Map<String, Double> values = new HashMap<>();
        if (section == null) {
            return values;
        }
        for (String key : section.getKeys(false)) {
            values.put(key, section.getDouble(key));
        }
        return values;
    }

    private Map<String, Integer> toIntMap(ConfigurationSection section) {
        Map<String, Integer> values = new HashMap<>();
        if (section == null) {
            return values;
        }
        for (String key : section.getKeys(false)) {
            values.put(key, section.getInt(key));
        }
        return values;
    }
}
