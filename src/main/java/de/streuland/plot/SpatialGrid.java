package de.streuland.plot;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/**
 * Spatial grid index for fast plot lookups by location.
 */
public class SpatialGrid {
    private static final int CELL_SIZE = 25;

    private static final class CellKey {
        private final int x;
        private final int z;

        private CellKey(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CellKey)) {
                return false;
            }
            CellKey cellKey = (CellKey) o;
            return x == cellKey.x && z == cellKey.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, z);
        }
    }

    private final JavaPlugin plugin;
    private final Map<CellKey, Set<Plot>> grid;

    public SpatialGrid(JavaPlugin plugin) {
        this.plugin = plugin;
        this.grid = new HashMap<>();
    }

    public synchronized void rebuild(Collection<Plot> plots) {
        grid.clear();
        if (plots == null || plots.isEmpty()) {
            plugin.getLogger().info("SpatialGrid rebuilt with 0 plots");
            return;
        }
        for (Plot plot : plots) {
            addPlot(plot);
        }
        plugin.getLogger().info("SpatialGrid rebuilt with " + plots.size() + " plots");
    }

    public synchronized void addPlot(Plot plot) {
        if (plot == null) {
            return;
        }
        CellKey cellKey = toCellKey(plot.getCenterX(), plot.getCenterZ());
        grid.computeIfAbsent(cellKey, ignored -> new LinkedHashSet<>()).add(plot);
    }

    public synchronized void removePlot(Plot plot) {
        if (plot == null) {
            return;
        }
        CellKey cellKey = toCellKey(plot.getCenterX(), plot.getCenterZ());
        Set<Plot> cellPlots = grid.get(cellKey);
        if (cellPlots == null) {
            return;
        }
        cellPlots.remove(plot);
        if (cellPlots.isEmpty()) {
            grid.remove(cellKey);
        }
    }

    public synchronized Plot getPlotAt(int x, int z) {
        for (CellKey key : getCellsToCheck(x, z)) {
            Set<Plot> plots = grid.get(key);
            if (plots == null) {
                continue;
            }
            for (Plot plot : plots) {
                if (plot.contains(x, z)) {
                    return plot;
                }
            }
        }
        return null;
    }

    public synchronized boolean isLocationAvailable(int centerX, int centerZ, int plotSize) {
        if (plotSize <= 0) {
            return false;
        }

        int minX = centerX - (plotSize / 2);
        int maxX = centerX + (plotSize / 2);
        int minZ = centerZ - (plotSize / 2);
        int maxZ = centerZ + (plotSize / 2);

        for (CellKey cellKey : getCellsToCheck(centerX, centerZ)) {
            Set<Plot> plots = grid.get(cellKey);
            if (plots == null) {
                continue;
            }
            for (Plot plot : plots) {
                if (!(maxX <= plot.getMinX() || minX >= plot.getMaxX() || maxZ <= plot.getMinZ() || minZ >= plot.getMaxZ())) {
                    return false;
                }
            }
        }

        return true;
    }

    private CellKey toCellKey(int x, int z) {
        int cellX = Math.floorDiv(x, CELL_SIZE);
        int cellZ = Math.floorDiv(z, CELL_SIZE);
        return new CellKey(cellX, cellZ);
    }

    private Set<CellKey> getCellsToCheck(int x, int z) {
        CellKey center = toCellKey(x, z);
        Set<CellKey> adjacent = new HashSet<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                adjacent.add(new CellKey(center.x + dx, center.z + dz));
            }
        }
        return adjacent;
    }

    public synchronized int getCellCount() {
        return grid.size();
    }

    public synchronized int getPlotCount() {
        return grid.values().stream().mapToInt(Set::size).sum();
    }
}
