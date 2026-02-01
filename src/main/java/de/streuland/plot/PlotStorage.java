package de.streuland.plot;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Handles persistent storage of plots using YAML files.
 * 
 * One plot = one YAML file in the data folder.
 * All plots are indexed in an index.yml file for fast lookup.
 */
public class PlotStorage {
    private final JavaPlugin plugin;
    private final File dataFolder;
    private final File indexFile;
    private Map<String, Plot> cachedPlots;
    private Map<UUID, String> ownerToPlotId;  // Fast lookup: owner UUID -> plot IDs
    
    public PlotStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "plots");
        this.indexFile = new File(dataFolder, "index.yml");
        this.cachedPlots = new HashMap<>();
        this.ownerToPlotId = new HashMap<>();
        
        // Create folders if they don't exist
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        
        loadAllPlots();
    }
    
    /**
     * Saves a plot to disk
     */
    public void savePlot(Plot plot) {
        File plotFile = new File(dataFolder, plot.getPlotId() + ".yml");
        FileConfiguration config = new YamlConfiguration();
        
        config.set("id", plot.getPlotId());
        config.set("centerX", plot.getCenterX());
        config.set("centerZ", plot.getCenterZ());
        config.set("size", plot.getSize());
        config.set("owner", plot.getOwner().toString());
        config.set("createdAt", plot.getCreatedAt());
        config.set("spawnY", plot.getSpawnY());
        config.set("trusted", plot.getTrustedPlayers().stream()
                .map(UUID::toString)
                .collect(java.util.stream.Collectors.toList()));
        
        try {
            config.save(plotFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save plot " + plot.getPlotId() + ": " + e.getMessage());
        }
        
        // Update cache and index
        cachedPlots.put(plot.getPlotId(), plot);
        ownerToPlotId.putIfAbsent(plot.getOwner(), plot.getPlotId());
        
        saveIndex();
    }
    
    /**
     * Loads all plots from disk into memory
     */
    private void loadAllPlots() {
        cachedPlots.clear();
        ownerToPlotId.clear();
        
        if (!dataFolder.exists()) {
            return;
        }
        
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml") && !name.equals("index.yml"));
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            Plot plot = loadPlotFromFile(file);
            if (plot != null) {
                cachedPlots.put(plot.getPlotId(), plot);
                ownerToPlotId.putIfAbsent(plot.getOwner(), plot.getPlotId());
            }
        }
        
        plugin.getLogger().info("Loaded " + cachedPlots.size() + " plots from disk");
    }
    
    /**
     * Loads a single plot from file
     */
    private Plot loadPlotFromFile(File file) {
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);
            
            String id = config.getString("id");
            int centerX = config.getInt("centerX");
            int centerZ = config.getInt("centerZ");
            int size = config.getInt("size");
            UUID owner = UUID.fromString(config.getString("owner"));
            long createdAt = config.getLong("createdAt");
            int spawnY = config.getInt("spawnY", 64);  // Default to 64 if not set
            
            Plot plot = new Plot(id, centerX, centerZ, size, owner, createdAt, spawnY);
            
            // Load trusted players
            List<String> trustedList = config.getStringList("trusted");
            for (String uuidStr : trustedList) {
                try {
                    plot.addTrusted(UUID.fromString(uuidStr));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in trusted list for plot " + id + ": " + uuidStr);
                }
            }
            
            return plot;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load plot from " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Gets a plot by ID
     */
    public Plot getPlot(String plotId) {
        return cachedPlots.get(plotId);
    }
    
    /**
     * Gets all plots owned by a player
     */
    public List<Plot> getPlayerPlots(UUID player) {
        List<Plot> plots = new ArrayList<>();
        for (Plot plot : cachedPlots.values()) {
            if (plot.getOwner().equals(player)) {
                plots.add(plot);
            }
        }
        return plots;
    }
    
    /**
     * Gets all plots in the system
     */
    public Collection<Plot> getAllPlots() {
        return new ArrayList<>(cachedPlots.values());
    }
    
    /**
     * Checks if a plot ID already exists
     */
    public boolean exists(String plotId) {
        return cachedPlots.containsKey(plotId);
    }
    
    /**
     * Claims an unclaimed plot for a player
     */
    public void claimPlot(String plotId, UUID player) {
        Plot plot = cachedPlots.get(plotId);
        if (plot != null) {
            // Create a new plot with the claiming player as owner
            Plot claimedPlot = new Plot(plot.getPlotId(), plot.getCenterX(), plot.getCenterZ(), 
                                       plot.getSize(), player, System.currentTimeMillis(), plot.getSpawnY());
            
            // Transfer trusted players if any
            for (UUID trusted : plot.getTrustedPlayers()) {
                claimedPlot.addTrusted(trusted);
            }
            
            // Update cache and save
            cachedPlots.put(plotId, claimedPlot);
            savePlot(claimedPlot);
            saveIndex();
            
            plugin.getLogger().info("Plot " + plotId + " claimed by " + player);
        }
    }
    
    /**
     * Deletes a plot from storage
     */
    public void deletePlot(String plotId) {
        Plot plot = cachedPlots.remove(plotId);
        if (plot != null) {
            File plotFile = new File(dataFolder, plotId + ".yml");
            if (plotFile.exists()) {
                plotFile.delete();
            }
            saveIndex();
        }
    }
    
    /**
     * Saves the plot index for quick reference
     */
    private void saveIndex() {
        FileConfiguration config = new YamlConfiguration();
        config.set("plots", new ArrayList<>(cachedPlots.keySet()));
        config.set("count", cachedPlots.size());
        config.set("last-updated", System.currentTimeMillis());
        
        try {
            config.save(indexFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save plot index: " + e.getMessage());
        }
    }
}
