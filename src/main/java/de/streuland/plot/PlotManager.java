package de.streuland.plot;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Central orchestrator for plot management.
 */
public class PlotManager {
    private final JavaPlugin plugin;
    private final PlotStorage storage;
    private final World world;
    private final int plotSize;
    private final int minDistance;
    private final int maxSearchRadius;
    private final int maxPlotsPerPlayer;
    private final SpatialGrid spatialGrid;

    private int plotCounter;

    public PlotManager(JavaPlugin plugin, PlotStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.spatialGrid = new SpatialGrid(plugin);

        FileConfiguration config = plugin.getConfig();
        String worldName = config.getString("world.name", "world");
        this.world = Bukkit.getWorld(worldName);
        if (this.world == null) {
            throw new IllegalStateException("World '" + worldName + "' not found!");
        }

        this.plotSize = config.getInt("plot.size", 64);
        this.minDistance = config.getInt("plot.min-distance", 100);
        this.maxSearchRadius = config.getInt("plot.max-search-radius", 5000);
        this.maxPlotsPerPlayer = config.getInt("plot.max-plots-per-player", 4);

        this.plotCounter = storage.getNextPlotNumber();
        
        // Rebuild spatial grid from loaded plots
        spatialGrid.rebuild(storage.getAllPlots());

        plugin.getLogger().info("PlotManager initialized: world=" + worldName + ", size=" + plotSize + ", minDist=" + minDistance);
    }

    /**
     * Creates or claims a plot for a player.
     */
    public CompletableFuture<Plot> createPlotAsync(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            for (Plot plot : storage.getAllPlots()) {
                if (plot.getAreaType() == AreaType.PLOT_UNCLAIMED) {
                    // Found an unclaimed plot, claim it for the player
                    Plot claimedPlot = claimPlotForPlayer(plot, playerUUID);
                    plugin.getLogger().info("Player " + playerUUID + " claimed existing unclaimed plot " + plot.getPlotId());
                    return claimedPlot;
                }
            }

            for (int attempts = 0; attempts < 10; attempts++) {
                int x = random(-maxSearchRadius, maxSearchRadius);
                int z = random(-maxSearchRadius, maxSearchRadius);
                if (isValidPlotLocation(x, z)) {
                    return createPlotAtLocation(playerUUID, x, z);
                }
            }

            plugin.getLogger().warning("Failed to find valid plot location for " + playerUUID + " after 10 attempts");
            return null;
        });
    }

    private Plot createPlotAtLocation(UUID playerUUID, int centerX, int centerZ) {
        String plotId = "plot_" + plotCounter++;
        int spawnY = findSafeSpawnY(centerX, centerZ);

        Plot plot = new Plot(plotId, centerX, centerZ, plotSize, playerUUID, System.currentTimeMillis(), spawnY, Plot.PlotState.CLAIMED);
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            storage.savePlot(plot);
            spatialGrid.addPlot(plot);
            Bukkit.getPluginManager().callEvent(new de.streuland.event.PlotCreatedEvent(plot));
            plugin.getLogger().info("Created plot " + plotId + " at (" + centerX + "," + centerZ + ") for " + playerUUID + " with spawn Y=" + spawnY);
        });

        return plot;
    }

    private int findSafeSpawnY(int centerX, int centerZ) {
        Block highestBlock = world.getHighestBlockAt(centerX, centerZ);
        int y = highestBlock.getY() + 1;

        Block below = world.getBlockAt(centerX, y - 1, centerZ);
        if (below.isLiquid()) {
            for (int i = y; i < 256; i++) {
                Block current = world.getBlockAt(centerX, i, centerZ);
                Block belowCurrent = world.getBlockAt(centerX, i - 1, centerZ);
                if (!current.isLiquid() && !belowCurrent.isLiquid() && !belowCurrent.getType().name().contains("AIR")) {
                    return i;
                }
            }
        }

        return y;
    }

    private boolean isValidPlotLocation(int x, int z) {
        if (!spatialGrid.isLocationAvailable(x, z, plotSize)) {
            return false;
        }

        for (Plot plot : storage.getAllPlots()) {
            double distance = Math.sqrt(Math.pow(x - plot.getCenterX(), 2) + Math.pow(z - plot.getCenterZ(), 2));
            if (distance < minDistance) {
                return false;
            }
        }

        return isValidTerrain(x, z);
    }

    private boolean isValidTerrain(int x, int z) {
        FileConfiguration config = plugin.getConfig();
        boolean rejectWater = config.getBoolean("terrain.water-rejection", true);
        boolean rejectLava = config.getBoolean("terrain.lava-rejection", true);
        int waterThreshold = config.getInt("terrain.adjacent-water-threshold", 3);

        Block block = world.getHighestBlockAt(x, z);
        int y = block.getY();

        if (block.isLiquid()) {
            return !rejectWater;
        }

        if (rejectLava) {
            Block belowBlock = world.getBlockAt(x, y - 1, z);
            if (belowBlock.getType().toString().contains("LAVA")) {
                return false;
            }
        }

        if (rejectWater && waterThreshold > 0) {
            int waterCount = 0;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Block adjacent = world.getBlockAt(x + dx, y, z + dz);
                    if (adjacent.isLiquid()) {
                        waterCount++;
                    }
                }
            }
            if (waterCount >= waterThreshold) {
                return false;
            }
        }

        return true;
    }

    public Plot getPlotAt(int x, int z) {
        return spatialGrid.getPlotAt(x, z);
    }

    public Plot getNearestPlot(int x, int z) {
        double minDist = Double.MAX_VALUE;
        Plot nearest = null;

        for (Plot plot : storage.getAllPlots()) {
            double dx = x - plot.getCenterX();
            double dz = z - plot.getCenterZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist < minDist) {
                minDist = dist;
                nearest = plot;
            }
        }

        return nearest;
    }

    
    /**
     * Claims an unclaimed plot and updates both storage and spatial index.
     */
    public Plot claimPlotForPlayer(Plot plot, UUID player) {
        Plot claimedPlot = storage.claimPlot(plot.getPlotId(), player);
        if (claimedPlot != null && claimedPlot != plot) {
            spatialGrid.removePlot(plot);
            spatialGrid.addPlot(claimedPlot);
        }
        return claimedPlot;
    }

    /**
     * Trusts a player on a plot (if caller is owner)
     */
    public boolean trustPlayer(String plotId, UUID owner, UUID playerToTrust) {
        Plot plot = storage.getPlot(plotId);
        if (plot == null || plot.getOwner() == null || !plot.getOwner().equals(owner)) {
            return false;
        }

        plot.addTrusted(playerToTrust);
        storage.savePlot(plot);
        return true;
    }

    public boolean untrustPlayer(String plotId, UUID owner, UUID playerToUntrust) {
        Plot plot = storage.getPlot(plotId);
        if (plot == null || plot.getOwner() == null || !plot.getOwner().equals(owner)) {
            return false;
        }

        plot.removeTrusted(playerToUntrust);
        storage.savePlot(plot);
        return true;
    }

    public Plot claimPlotAt(UUID player, int x, int z) {
        Plot plot = getPlotAt(x, z);
        if (plot == null || plot.getAreaType() != AreaType.PLOT_UNCLAIMED) {
            return null;
        }
        return claimPlotForPlayer(plot, player);
    }

    public boolean unclaimPlot(String plotId, UUID requester, boolean force) {
        Plot plot = storage.getPlot(plotId);
        if (plot == null || plot.getAreaType() != AreaType.PLOT_CLAIMED) {
            return false;
        }
        if (!force && (plot.getOwner() == null || !plot.getOwner().equals(requester))) {
            return false;
        }
        return storage.unclaimPlot(plotId) != null;
    }

    public boolean deletePlot(String plotId, UUID requester, boolean force) {
        Plot plot = storage.getPlot(plotId);
        if (plot == null) {
            return false;
        }
        if (!force && plot.getOwner() != null && !plot.getOwner().equals(requester)) {
            return false;
        }

        Plot removed = storage.deletePlot(plotId);
        if (removed != null) {
            spatialGrid.removePlot(removed);
            return true;
        }
        return false;
    }

    public AreaType resolveAreaTypeAt(int x, int y, int z) {
        if (isPathCoordinate(x, y, z)) {
            return AreaType.PATH;
        }

        Plot plot = getPlotAt(x, z);
        if (plot == null) {
            return AreaType.WILDERNESS;
        }

        return plot.getAreaType();
    }

    private boolean isPathCoordinate(int x, int y, int z) {
        if (y < 63 || y > 67) {
            return false;
        }

        if (y > 63) {
            return true;
        }

        Plot plot = getPlotAt(x, z);
        if (plot != null) {
            return false;
        }

        return true;
    }

    public Collection<Plot> getAllPlots() {
        return storage.getAllPlots();
    }

    public World getWorld() {
        return world;
    }

    public int getPlotSize() {
        return plotSize;
    }

    public int getMaxPlotsPerPlayer() {
        return maxPlotsPerPlayer;
    }

    public PlotStorage getStorage() {
        return storage;
    }

    public SpatialGrid getSpatialGrid() {
        return spatialGrid;
    }

    public void generateUnclaimedPlots(int gridSize, int spacing) {
        plugin.getLogger().info("Generating " + (gridSize * gridSize) + " unclaimed plots in grid...");
        int generated = 0;

        for (int i = -gridSize; i <= gridSize; i++) {
            for (int j = -gridSize; j <= gridSize; j++) {
                int centerX = i * spacing;
                int centerZ = j * spacing;

                if (!isValidPlotLocation(centerX, centerZ)) {
                    continue;
                }

                String plotId = "unclaimed_" + plotCounter++;
                int spawnY = findSafeSpawnY(centerX, centerZ);
                Plot unclaimedPlot = new Plot(plotId, centerX, centerZ, plotSize, null, System.currentTimeMillis(), spawnY, Plot.PlotState.UNCLAIMED);
                storage.savePlot(unclaimedPlot);
                spatialGrid.addPlot(unclaimedPlot);
                generated++;
            }
        }

        plugin.getLogger().info("Generated " + generated + " unclaimed plots successfully!");
    }

    private int random(int min, int max) {
        return min + (int) (Math.random() * (max - min + 1));
    }
}
