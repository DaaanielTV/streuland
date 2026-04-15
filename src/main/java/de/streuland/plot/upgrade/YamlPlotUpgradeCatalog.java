package de.streuland.plot.upgrade;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class YamlPlotUpgradeCatalog {
    private YamlPlotUpgradeCatalog() {
    }

    public static PlotUpgradeTree load(File file) {
        FileConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return load(yaml);
    }

    public static PlotUpgradeTree load(FileConfiguration yaml) {
        PlotUpgradeTree tree = new PlotUpgradeTree();
        loadProgressionTrack(yaml, tree);
        loadExperienceRules(yaml, tree);
        loadLevelRewards(yaml, tree);

        ConfigurationSection upgrades = yaml.getConfigurationSection("upgrades");
        if (upgrades == null) {
            return DefaultPlotUpgradeCatalog.create();
        }

        for (String id : upgrades.getKeys(false)) {
            ConfigurationSection section = upgrades.getConfigurationSection(id);
            if (section == null) {
                continue;
            }

            PlotUpgradeType type = PlotUpgradeType.valueOf(section.getString("type", "UTILITY").toUpperCase(Locale.ROOT));
            String displayName = section.getString("name", id);
            int level = section.getInt("level", 1);
            int requiredPlotLevel = section.getInt("required-plot-level", 1);
            int requiredPrestigeLevel = section.getInt("required-prestige-level", 0);
            int xpReward = section.getInt("reward.xp", 0);
            double price = section.getDouble("cost.vault", 0D);
            long cooldown = section.getLong("cost.cooldown-seconds", 0L);

            List<PlotUpgradeRequirement> requirements = new ArrayList<>();
            for (Map<?, ?> req : section.getMapList("requirements")) {
                String reqId = String.valueOf(req.getOrDefault("id", ""));
                int minLevel = ((Number) req.getOrDefault("level", 1)).intValue();
                if (!reqId.isEmpty()) {
                    requirements.add(new PlotUpgradeRequirement(reqId, minLevel));
                }
            }

            Map<String, String> settings = new LinkedHashMap<>();
            ConfigurationSection settingsSection = section.getConfigurationSection("settings");
            if (settingsSection != null) {
                for (String key : settingsSection.getKeys(true)) {
                    Object value = settingsSection.get(key);
                    if (value != null && !(value instanceof ConfigurationSection)) {
                        settings.put(key, String.valueOf(value));
                    }
                }
            }

            tree.addUpgrade(new PlotUpgradeDefinition(
                    id,
                    type,
                    displayName,
                    level,
                    requiredPlotLevel,
                    requiredPrestigeLevel,
                    xpReward,
                    new PlotUpgradeCost(price, Collections.emptyMap(), Duration.ofSeconds(Math.max(0L, cooldown))),
                    requirements,
                    settings
            ));
        }

        return tree;
    }

    private static void loadProgressionTrack(FileConfiguration yaml, PlotUpgradeTree tree) {
        ConfigurationSection progression = yaml.getConfigurationSection("progression");
        if (progression == null) {
            return;
        }

        String modeRaw = progression.getString("mode", "currency");
        PlotProgressionTrack.Mode mode = "xp".equalsIgnoreCase(modeRaw)
                ? PlotProgressionTrack.Mode.XP
                : PlotProgressionTrack.Mode.CURRENCY;

        Map<Integer, Integer> thresholds = new LinkedHashMap<>();
        ConfigurationSection levels = progression.getConfigurationSection("levels");
        if (levels != null) {
            for (String key : levels.getKeys(false)) {
                try {
                    int level = Integer.parseInt(key);
                    thresholds.put(level, levels.getInt(key, 0));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        tree.setProgressionTrack(new PlotProgressionTrack(mode, thresholds));
    }

    private static void loadExperienceRules(FileConfiguration yaml, PlotUpgradeTree tree) {
        ConfigurationSection experience = yaml.getConfigurationSection("progression.experience");
        if (experience == null) {
            return;
        }
        int defaultXp = experience.getInt("default-xp", 1);
        long cooldownMs = experience.getLong("cooldown-ms", 1500L);
        Map<String, Integer> sourceXp = new LinkedHashMap<>();
        ConfigurationSection rules = experience.getConfigurationSection("rules");
        if (rules != null) {
            for (String source : rules.getKeys(false)) {
                sourceXp.put(source.toLowerCase(Locale.ROOT), Math.max(0, rules.getInt(source, defaultXp)));
            }
        }
        tree.setExperienceRuleSet(new PlotExperienceRuleSet(defaultXp, cooldownMs, sourceXp));
    }

    private static void loadLevelRewards(FileConfiguration yaml, PlotUpgradeTree tree) {
        ConfigurationSection rewards = yaml.getConfigurationSection("progression.level-rewards");
        if (rewards == null) {
            return;
        }

        for (String levelKey : rewards.getKeys(false)) {
            int level;
            try {
                level = Integer.parseInt(levelKey);
            } catch (NumberFormatException ignored) {
                continue;
            }
            ConfigurationSection section = rewards.getConfigurationSection(levelKey);
            if (section == null) {
                continue;
            }
            int storageSlots = section.getInt("storage-slots", 0);
            List<String> abilities = section.getStringList("abilities");
            List<String> cosmetics = section.getStringList("cosmetics");
            Map<String, Double> statBonuses = new LinkedHashMap<>();
            ConfigurationSection statsSection = section.getConfigurationSection("stats");
            if (statsSection != null) {
                for (String stat : statsSection.getKeys(false)) {
                    statBonuses.put(stat, statsSection.getDouble(stat, 0D));
                }
            }
            tree.putLevelReward(new PlotLevelReward(
                    level,
                    storageSlots,
                    new java.util.LinkedHashSet<>(abilities),
                    new java.util.LinkedHashSet<>(cosmetics),
                    statBonuses
            ));
        }
    }
}
