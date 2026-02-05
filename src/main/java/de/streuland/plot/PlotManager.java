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
 * 
 * Responsibilities:
 * - Create new plots with validation
 * - Find plots by coordinates using SpatialGrid
 * - Check distance constraints
 * - Validate terrain suitability
 */
public class PlotManager {
    private final JavaPlugin plugin;
    private final PlotStorage storage;
    private final World world;
    private final int plotSize;
    private final int minDistance;
    private final int maxSearchRadius;
    private final SpatialGrid spatialGrid;
    
    private int plotCounter = 0;  // Simple counter for plot IDs
    
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
        
        // Rebuild spatial grid from loaded plots
        spatialGrid.rebuild(storage.getAllPlots());
        
        plugin.getLogger().info("PlotManager initialized: world=" + worldName + ", size=" + plotSize + ", minDist=" + minDistance);
    }
    
    /**
     * Attempts to create a new plot for a player at a random location.
     * First tries to find an unclaimed plot, then generates a new one.
     * 
     * Performs async validation, then schedules block changes on main thread.
     * Returns a CompletableFuture that completes with the new plot or null if failed.
     */
    public CompletableFuture<Plot> createPlotAsync(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            // First, try to find an unclaimed plot
            for (Plot plot : storage.getAllPlots()) {
                if (plot.getState() == Plot.PlotState.UNCLAIMED) {
                    // Found an unclaimed plot, claim it for the player
                    Plot claimedPlot = claimPlotForPlayer(plot, playerUUID);
                    plugin.getLogger().info("Player " + playerUUID + " claimed existing unclaimed plot " + plot.getPlotId());
                    return claimedPlot;
                }
            }
            
            // No unclaimed plots found, generate a new one
            // Generate random location and validate it
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
    
    /**
     * Creates a plot at the specified location without validation.
     * Block changes are executed on the main thread.
     * Plot is added to spatial grid after creation.
     */
    private Plot createPlotAtLocation(UUID playerUUID, int centerX, int centerZ) {
        String plotId = "plot_" + (++plotCounter);
        
        // Find safe spawn Y coordinate
        int spawnY = findSafeSpawnY(centerX, centerZ);
        
        // Create unclaimed plot that will be claimed
        Plot plot = new Plot(plotId, centerX, centerZ, plotSize, playerUUID, System.currentTimeMillis(), spawnY, Plot.PlotState.CLAIMED);
        
        // Schedule on main thread to save plot and add to grid
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            storage.savePlot(plot);
            spatialGrid.addPlot(plot);
            plugin.getLogger().info("Created plot " + plotId + " at (" + centerX + "," + centerZ + ") for " + playerUUID + " with spawn Y=" + spawnY);
        });
        
        return plot;
    }
    
    /**
     * Finds a safe Y coordinate within the plot for player spawning.
     * Returns the first solid block, then adds 1 for player height.
     */
    private int findSafeSpawnY(int centerX, int centerZ) {
        // Search in center of plot
        Block highestBlock = world.getHighestBlockAt(centerX, centerZ);
        int y = highestBlock.getY() + 1;  // One block above the highest block
        
        // Ensure it's not in liquid and has solid ground beneath
        Block below = world.getBlockAt(centerX, y - 1, centerZ);
        if (below.isLiquid()) {
            // If liquid below, search upward until we find solid ground
            for (int i = y; i < 256; i++) {
                Block b = world.getBlockAt(centerX, i, centerZ);
                Block below2 = world.getBlockAt(centerX, i - 1, centerZ);
                if (!b.isLiquid() && !below2.isLiquid() && !below2.getType().name().contains("AIR")) {
                    return i;
                }
            }
        }
        
        return y;
    }
    
    /**
     * Validates if a location is suitable for a plot.
     * Checks:
     * - No overlap with existing plots (via spatial grid)
     * - Minimum distance to other plots
     * - Terrain is not in water/lava
     * - Surface is accessible
     */
    private boolean isValidPlotLocation(int x, int z) {
        // Check for overlap using spatial grid
        if (!spatialGrid.isLocationAvailable(x, z, plotSize)) {
            return false;
        }
        
        // Check distance to existing plots
        for (Plot plot : storage.getAllPlots()) {
            double distance = Math.sqrt(
                Math.pow(x - plot.getCenterX(), 2) +
                Math.pow(z - plot.getCenterZ(), 2)
            );
            
            if (distance < minDistance) {
                return false;
            }
        }
        
        // Check terrain
        return isValidTerrain(x, z);
    }
    
    /**
     * Checks if terrain at location is suitable for a plot.
     */
    private boolean isValidTerrain(int x, int z) {
        FileConfiguration config = plugin.getConfig();
        boolean rejectWater = config.getBoolean("terrain.water-rejection", true);
        boolean rejectLava = config.getBoolean("terrain.lava-rejection", true);
        int waterThreshold = config.getInt("terrain.adjacent-water-threshold", 3);
        
        // Get surface block (find first non-air block from top)
        Block block = world.getHighestBlockAt(x, z);
        int y = block.getY();
        
        // Check if underwater
        if (block.isLiquid()) {
            return !rejectWater;
        }
        
        // Check for lava nearby (below surface)
        if (rejectLava) {
            Block belowBlock = world.getBlockAt(x, y - 1, z);
            if (belowBlock.getType().toString().contains("LAVA")) {
                return false;
            }
        }
        
        // Count adjacent water blocks
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
    
    /**
     * Finds a plot at given block coordinates using spatial grid (O(1) lookup).
     */
    public Plot getPlotAt(int x, int z) {
        return spatialGrid.getPlotAt(x, z);
    }
    
    /**
     * Gets the nearest plot to given coordinates.
     */
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
    
    /**
     * Untrusts a player from a plot
     */
    public boolean untrustPlayer(String plotId, UUID owner, UUID playerToUntrust) {
        Plot plot = storage.getPlot(plotId);
        if (plot == null || plot.getOwner() == null || !plot.getOwner().equals(owner)) {
            return false;
        }
        
        plot.removeTrusted(playerToUntrust);
        storage.savePlot(plot);
        return true;
    }
    
    /**
     * Gets all plots
     */
    public Collection<Plot> getAllPlots() {
        return storage.getAllPlots();
    }
    
    /**
     * Gets the world this manager is attached to
     */
    public World getWorld() {
        return world;
    }
    
    public int getPlotSize() {
        return plotSize;
    }
    
    public PlotStorage getStorage() {
        return storage;
    }
    
    public SpatialGrid getSpatialGrid() {
        return spatialGrid;
    }
    
    /**
     * Pre-generates unclaimed plots in a grid pattern.
     * Called during server setup to create a pool of claimable plots.
     */
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
                
                // Create unclaimed plot (owner = null, state = UNCLAIMED)
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
