package de.streuland.bootstrap;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class ConfigValidationService {
    private final JavaPlugin plugin;

    public ConfigValidationService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void validateAndNormalize() {
        FileConfiguration config = plugin.getConfig();
        List<String> warnings = new ArrayList<>();

        normalizeInt(config, "plot.delete-confirm-timeout-seconds", 5, 600, 30, warnings);
        normalizeInt(config, "plot.restore-confirm-timeout-seconds", 5, 600, 30, warnings);
        normalizeInt(config, "web.port", 1, 65535, 8090, warnings);
        normalizeInt(config, "terrain.adjacent-water-threshold", 0, 25, 3, warnings);

        String storageType = config.getString("storage.type", "yaml");
        if (!"yaml".equalsIgnoreCase(storageType) && !"sqlite".equalsIgnoreCase(storageType)) {
            warnings.add("Invalid storage.type='" + storageType + "'. Falling back to 'yaml'.");
            config.set("storage.type", "yaml");
        }

        for (String warning : warnings) {
            plugin.getLogger().warning("[config] " + warning);
        }
        if (!warnings.isEmpty()) {
            plugin.saveConfig();
        }
    }

    private static void normalizeInt(FileConfiguration config, String path, int min, int max, int fallback, List<String> warnings) {
        int value = config.getInt(path, fallback);
        if (value < min || value > max) {
            warnings.add(path + "=" + value + " out of range [" + min + ", " + max + "]; using " + fallback);
            config.set(path, fallback);
        }
    }
}
