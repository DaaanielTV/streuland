package de.streuland.district;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * YAML-based storage for districts.
 */
public class DistrictStorage {
    private final JavaPlugin plugin;
    private final File dataFolder;
    private final File indexFile;
    private final Map<String, District> cachedDistricts = new HashMap<>();

    public DistrictStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "districts");
        this.indexFile = new File(dataFolder, "index.yml");

        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        loadAll();
    }

    public Collection<District> getAllDistricts() {
        return new ArrayList<>(cachedDistricts.values());
    }

    public District getDistrict(String id) {
        return cachedDistricts.get(id);
    }

    public void saveDistrict(District district) {
        File file = new File(dataFolder, district.getId() + ".yml");
        FileConfiguration config = new YamlConfiguration();

        config.set("id", district.getId());
        config.set("name", district.getName());
        config.set("level", district.getLevel().name());
        config.set("plotIds", new ArrayList<>(district.getPlotIds()));
        config.set("createdAt", district.getCreatedAt());
        config.set("progress.builtBlocks", district.getProgress().getBuiltBlocks());
        config.set("progress.activePlayers", district.getProgress().getActivePlayers());
        config.set("progress.completedGoals", new ArrayList<>(district.getProgress().getCompletedGoals()));
        config.set("progress.lastUpdated", district.getProgress().getLastUpdated());

        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save district " + district.getId() + ": " + e.getMessage());
        }

        cachedDistricts.put(district.getId(), district);
        saveIndex();
    }

    public CompletableFuture<Void> saveDistrictAsync(District district) {
        return CompletableFuture.runAsync(() -> saveDistrict(district));
    }

    public void deleteDistrict(String id) {
        cachedDistricts.remove(id);
        File file = new File(dataFolder, id + ".yml");
        if (file.exists()) {
            file.delete();
        }
        saveIndex();
    }

    private void loadAll() {
        cachedDistricts.clear();
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml") && !name.equals("index.yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            District district = loadDistrict(file);
            if (district != null) {
                cachedDistricts.put(district.getId(), district);
            }
        }
        plugin.getLogger().info("Loaded " + cachedDistricts.size() + " districts from disk");
    }

    private District loadDistrict(File file) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        String id = config.getString("id");
        String name = config.getString("name", id);
        DistrictLevel level = DistrictLevel.valueOf(config.getString("level", DistrictLevel.DORF.name()));
        long createdAt = config.getLong("createdAt", System.currentTimeMillis());

        List<String> plotIds = config.getStringList("plotIds");
        District district = new District(id, name, new HashSet<>(plotIds), level, createdAt);

        district.getProgress().addBuiltBlocks(config.getInt("progress.builtBlocks", 0));
        district.getProgress().setActivePlayers(config.getInt("progress.activePlayers", 0));
        for (String goalId : config.getStringList("progress.completedGoals")) {
            district.getProgress().markGoalCompleted(goalId);
        }

        return district;
    }

    private void saveIndex() {
        FileConfiguration config = new YamlConfiguration();
        config.set("districts", new ArrayList<>(cachedDistricts.keySet()));
        config.set("count", cachedDistricts.size());
        try {
            config.save(indexFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save district index: " + e.getMessage());
        }
    }
}
