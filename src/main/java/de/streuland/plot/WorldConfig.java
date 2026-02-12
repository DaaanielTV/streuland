package de.streuland.plot;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class WorldConfig {
    public static class WorldSettings {
        private final String worldName;
        private final int plotSize;
        private final int minDistance;
        private final int maxSearchRadius;
        private final int maxPlotsPerPlayer;
        private final List<String> biomeRules;
        private final List<String> upgradeTree;
        private final int plotCountCap;

        public WorldSettings(String worldName, int plotSize, int minDistance, int maxSearchRadius, int maxPlotsPerPlayer,
                             List<String> biomeRules, List<String> upgradeTree, int plotCountCap) {
            this.worldName = worldName;
            this.plotSize = plotSize;
            this.minDistance = minDistance;
            this.maxSearchRadius = maxSearchRadius;
            this.maxPlotsPerPlayer = maxPlotsPerPlayer;
            this.biomeRules = biomeRules;
            this.upgradeTree = upgradeTree;
            this.plotCountCap = plotCountCap;
        }

        public String getWorldName() { return worldName; }
        public int getPlotSize() { return plotSize; }
        public int getMinDistance() { return minDistance; }
        public int getMaxSearchRadius() { return maxSearchRadius; }
        public int getMaxPlotsPerPlayer() { return maxPlotsPerPlayer; }
        public List<String> getBiomeRules() { return biomeRules; }
        public List<String> getUpgradeTree() { return upgradeTree; }
        public int getPlotCountCap() { return plotCountCap; }
    }

    private final JavaPlugin plugin;
    private final Map<String, WorldSettings> worlds = new LinkedHashMap<>();

    public WorldConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        worlds.clear();
        loadWorld("world_main");
        loadWorld("world_nether");
        loadWorld("world_end");
    }

    private void loadWorld(String worldName) {
        String fileName = worldName + ".yml";
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        WorldSettings settings = new WorldSettings(
                worldName,
                cfg.getInt("plot.size", plugin.getConfig().getInt("plot.size", 64)),
                cfg.getInt("plot.min-distance", plugin.getConfig().getInt("plot.min-distance", 100)),
                cfg.getInt("plot.max-search-radius", plugin.getConfig().getInt("plot.max-search-radius", 5000)),
                cfg.getInt("plot.max-plots-per-player", plugin.getConfig().getInt("plot.max-plots-per-player", 4)),
                cfg.getStringList("biome-rules.allowed"),
                cfg.getStringList("upgrades.tree"),
                cfg.getInt("metadata.plot-count-cap", Integer.MAX_VALUE)
        );
        worlds.put(worldName, settings);
    }

    public Collection<WorldSettings> getAllWorlds() {
        return Collections.unmodifiableCollection(worlds.values());
    }

    public WorldSettings getSettings(String worldName) {
        return worlds.get(worldName);
    }

    public void saveMetadata(String worldName, Map<String, Object> metadata) {
        File file = new File(plugin.getDataFolder(), worldName + ".yml");
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            cfg.set("metadata." + entry.getKey(), entry.getValue());
        }
        try {
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save world metadata for " + worldName + ": " + e.getMessage());
        }
    }
}
