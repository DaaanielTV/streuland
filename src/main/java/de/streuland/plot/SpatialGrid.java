package de.streuland.plot;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Spatial grid index for O(1) plot lookups by location.
 * 
 * Divides the world into grid cells of size CELL_SIZE x CELL_SIZE.
 * Each cell stores plots whose centers fall within that cell.
 * 
 * Grid coordinates: cell_key = (floor(x / CELL_SIZE), floor(z / CELL_SIZE))
 */
public class SpatialGrid {
    private static final int CELL_SIZE = 25;
    
    private final JavaPlugin plugin;
    private final Map<String, List<Plot>> grid;  // Key format: "cellX,cellZ"
    
    public SpatialGrid(JavaPlugin plugin) {
        this.plugin = plugin;
        this.grid = new HashMap<>();
    }
    
    /**
     * Rebuilds the grid from a collection of plots.
     * Call this after loading plots from disk.
     */
    public void rebuild(Collection<Plot> plots) {
        grid.clear();
        for (Plot plot : plots) {
            addPlot(plot);
        }
        plugin.getLogger().info("SpatialGrid rebuilt with " + plots.size() + " plots");
    }
    
    /**
     * Adds a plot to the grid index.
     */
    public void addPlot(Plot plot) {
        String cellKey = getCellKey(plot.getCenterX(), plot.getCenterZ());
        grid.computeIfAbsent(cellKey, k -> new ArrayList<>()).add(plot);
    }
    
    /**
     * Removes a plot from the grid index.
     */
    public void removePlot(Plot plot) {
        String cellKey = getCellKey(plot.getCenterX(), plot.getCenterZ());
        List<Plot> cellPlots = grid.get(cellKey);
        if (cellPlots != null) {
            cellPlots.remove(plot);
            if (cellPlots.isEmpty()) {
                grid.remove(cellKey);
            }
        }
    }
    
    /**
     * Finds a plot at the given block coordinates.
     * First checks the cell containing the coordinates, then adjacent cells.
     * Returns null if no plot found.
     */
    public Plot getPlotAt(int x, int z) {
        // Check primary cell
        String primaryCell = getCellKey(x, z);
        List<Plot> primaryPlots = grid.get(primaryCell);
        if (primaryPlots != null) {
            for (Plot plot : primaryPlots) {
                if (plot.contains(x, z)) {
                    return plot;
                }
            }
        }
        
        // Check adjacent cells (in case block is near plot boundary)
        for (String adjCell : getAdjacentCellKeys(x, z)) {
            List<Plot> adjPlots = grid.get(adjCell);
            if (adjPlots != null) {
                for (Plot plot : adjPlots) {
                    if (plot.contains(x, z)) {
                        return plot;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Checks if a location would be valid for a new plot (no overlap with existing plots).
     * Checks the cell containing the plot center and adjacent cells.
     */
    public boolean isLocationAvailable(int centerX, int centerZ, int plotSize) {
        // Get all plots in and around the target location
        String primaryCell = getCellKey(centerX, centerZ);
        Set<String> cellsToCheck = new HashSet<>();
        cellsToCheck.add(primaryCell);
        cellsToCheck.addAll(getAdjacentCellKeys(centerX, centerZ));
        
        // Define plot boundaries
        int minX = centerX - (plotSize / 2);
        int maxX = centerX + (plotSize / 2);
        int minZ = centerZ - (plotSize / 2);
        int maxZ = centerZ + (plotSize / 2);
        
        // Check for overlaps
        for (String cellKey : cellsToCheck) {
            List<Plot> plots = grid.get(cellKey);
            if (plots != null) {
                for (Plot plot : plots) {
                    // Check if boundaries overlap
                    if (!(maxX <= plot.getMinX() || minX >= plot.getMaxX() ||
                          maxZ <= plot.getMinZ() || minZ >= plot.getMaxZ())) {
                        return false;  // Overlap detected
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * Gets the cell key string for given coordinates.
     * Format: "cellX,cellZ" where cell coordinates are floors of block coords / CELL_SIZE
     */
    private String getCellKey(int x, int z) {
        int cellX = (int) Math.floor((double) x / CELL_SIZE);
        int cellZ = (int) Math.floor((double) z / CELL_SIZE);
        return cellX + "," + cellZ;
    }
    
    /**
     * Gets all adjacent cell keys (including diagonals) for a given location.
     * Returns 8 cell keys (the 8 surrounding cells).
     */
    private List<String> getAdjacentCellKeys(int x, int z) {
        int cellX = (int) Math.floor((double) x / CELL_SIZE);
        int cellZ = (int) Math.floor((double) z / CELL_SIZE);
        
        List<String> adjacent = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;  // Skip primary cell
                adjacent.add((cellX + dx) + "," + (cellZ + dz));
            }
        }
        return adjacent;
    }
    
    /**
     * Gets the total number of cells in the grid.
     */
    public int getCellCount() {
        return grid.size();
    }
    
    /**
     * Gets the total number of plots indexed.
     */
    public int getPlotCount() {
        return grid.values().stream().mapToInt(List::size).sum();
    }
}
