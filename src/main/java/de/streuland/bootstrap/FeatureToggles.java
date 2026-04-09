package de.streuland.bootstrap;

import org.bukkit.configuration.file.FileConfiguration;

public record FeatureToggles(
        boolean marketEnabled,
        boolean approvalsEnabled,
        boolean backupsEnabled,
        boolean biomesEnabled,
        boolean upgradesEnabled,
        boolean dashboardApiEnabled
) {
    public static FeatureToggles fromConfig(FileConfiguration config) {
        return new FeatureToggles(
                config.getBoolean("features.market.enabled", true),
                config.getBoolean("features.approvals.enabled", true),
                config.getBoolean("features.backups.enabled", true),
                config.getBoolean("features.biomes.enabled", true),
                config.getBoolean("features.upgrades.enabled", true) && config.getBoolean("plot.upgrades.enabled", true),
                config.getBoolean("features.dashboard-api.enabled", true)
        );
    }
}
