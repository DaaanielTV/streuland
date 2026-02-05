package de.streuland.plot;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles persistent storage of plots using YAML files.
 *
 * One plot = one YAML file in the data folder.
 * All plots are indexed in an index.yml file for fast lookup.
 */
public class PlotStorage {
    private static final Pattern NUMERIC_SUFFIX = Pattern.compile(".*_(\\d+)$");

    private final JavaPlugin plugin;
    private final File dataFolder;
    private final File indexFile;
    private final Map<String, Plot> cachedPlots;
    private final Map<UUID, Set<String>> ownerToPlotIds;

    public PlotStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFolder = new File(plugin.getDataFolder(), "plots");
        this.indexFile = new File(dataFolder, "index.yml");
        this.cachedPlots = new HashMap<>();
        this.ownerToPlotIds = new HashMap<>();

        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        loadAllPlots();
    }

    /**
     * Saves a plot to disk with state persistence.
     */
    public synchronized void savePlot(Plot plot) {
        File plotFile = new File(dataFolder, plot.getPlotId() + ".yml");
        FileConfiguration config = new YamlConfiguration();

        config.set("id", plot.getPlotId());
        config.set("centerX", plot.getCenterX());
        config.set("centerZ", plot.getCenterZ());
        config.set("size", plot.getSize());
        config.set("owner", plot.getOwner() != null ? plot.getOwner().toString() : null);
        config.set("state", plot.getState().name());
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

        Plot oldPlot = cachedPlots.put(plot.getPlotId(), plot);
        updateOwnerIndexForPlotReplacement(oldPlot, plot);
        saveIndex();
    }

    private synchronized void loadAllPlots() {
        cachedPlots.clear();
        ownerToPlotIds.clear();

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
                addToOwnerIndex(plot);
            }
        }

        plugin.getLogger().info("Loaded " + cachedPlots.size() + " plots from disk");
    }

    private Plot loadPlotFromFile(File file) {
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);

            String id = config.getString("id");
            int centerX = config.getInt("centerX");
            int centerZ = config.getInt("centerZ");
            int size = config.getInt("size");
            String ownerStr = config.getString("owner");
            UUID owner = ownerStr != null && !ownerStr.equals("null") ? UUID.fromString(ownerStr) : null;
            long createdAt = config.getLong("createdAt");
            int spawnY = config.getInt("spawnY", 64);

            String stateStr = config.getString("state", owner == null ? "UNCLAIMED" : "CLAIMED");
            Plot.PlotState state = Plot.PlotState.valueOf(stateStr);

            Plot plot = new Plot(id, centerX, centerZ, size, owner, createdAt, spawnY, state);

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

    public synchronized Plot getPlot(String plotId) {
        return cachedPlots.get(plotId);
    }

    public synchronized List<Plot> getPlayerPlots(UUID player) {
        Set<String> ids = ownerToPlotIds.getOrDefault(player, Collections.emptySet());
        List<Plot> plots = new ArrayList<>();
        for (String id : ids) {
            Plot plot = cachedPlots.get(id);
            if (plot != null) {
                plots.add(plot);
            }
        }
        return plots;
    }

    public synchronized Collection<Plot> getAllPlots() {
        return new ArrayList<>(cachedPlots.values());
    }

    public synchronized boolean exists(String plotId) {
        return cachedPlots.containsKey(plotId);
    }

    /**
     * Claims an unclaimed plot and returns the updated plot instance.
     */
    public synchronized Plot claimPlot(String plotId, UUID player) {
        Plot plot = cachedPlots.get(plotId);
        if (plot == null || plot.getState() != Plot.PlotState.UNCLAIMED) {
            return null;
        }

        Plot claimedPlot = new Plot(plot.getPlotId(), plot.getCenterX(), plot.getCenterZ(),
                plot.getSize(), player, System.currentTimeMillis(), plot.getSpawnY(),
                Plot.PlotState.CLAIMED);

        for (UUID trusted : plot.getTrustedPlayers()) {
            claimedPlot.addTrusted(trusted);
        }

        savePlot(claimedPlot);
        plugin.getLogger().info("Plot " + plotId + " claimed by " + player);
        return claimedPlot;
    }

    /**
     * Releases a claimed plot back to unclaimed state.
     */
    public synchronized Plot unclaimPlot(String plotId) {
        Plot plot = cachedPlots.get(plotId);
        if (plot == null || plot.getState() != Plot.PlotState.CLAIMED) {
            return null;
        }

        Plot unclaimedPlot = new Plot(plot.getPlotId(), plot.getCenterX(), plot.getCenterZ(),
                plot.getSize(), null, System.currentTimeMillis(), plot.getSpawnY(),
                Plot.PlotState.UNCLAIMED);

        savePlot(unclaimedPlot);
        plugin.getLogger().info("Plot " + plotId + " released and is now UNCLAIMED");
        return unclaimedPlot;
    }

    /**
     * Deletes a plot from storage and returns the removed plot if present.
     */
    public synchronized Plot deletePlot(String plotId) {
        Plot plot = cachedPlots.remove(plotId);
        if (plot != null) {
            removeFromOwnerIndex(plot);
            File plotFile = new File(dataFolder, plotId + ".yml");
            if (plotFile.exists() && !plotFile.delete()) {
                plugin.getLogger().warning("Failed to delete plot file: " + plotFile.getName());
            }
            saveIndex();
        }
        return plot;
    }

    /**
     * Determines the next plot number based on existing plot IDs.
     */
    public synchronized int getNextPlotNumber() {
        int max = 0;
        for (String plotId : cachedPlots.keySet()) {
            Matcher matcher = NUMERIC_SUFFIX.matcher(plotId);
            if (matcher.matches()) {
                try {
                    int value = Integer.parseInt(matcher.group(1));
                    if (value > max) {
                        max = value;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max + 1;
    }

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

    private void updateOwnerIndexForPlotReplacement(Plot oldPlot, Plot newPlot) {
        if (oldPlot != null) {
            removeFromOwnerIndex(oldPlot);
        }
        addToOwnerIndex(newPlot);
    }

    private void addToOwnerIndex(Plot plot) {
        UUID owner = plot.getOwner();
        if (owner == null) {
            return;
        }
        ownerToPlotIds.computeIfAbsent(owner, ignored -> new HashSet<>()).add(plot.getPlotId());
    }

    private void removeFromOwnerIndex(Plot plot) {
        UUID owner = plot.getOwner();
        if (owner == null) {
            return;
        }
        Set<String> ids = ownerToPlotIds.get(owner);
        if (ids == null) {
            return;
        }
        ids.remove(plot.getPlotId());
        if (ids.isEmpty()) {
            ownerToPlotIds.remove(owner);
        }
    }
}
