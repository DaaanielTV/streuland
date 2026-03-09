package de.streuland.plot;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlotMergeService {
    private final JavaPlugin plugin;
    private final PlotManager plotManager;

    public PlotMergeService(JavaPlugin plugin, PlotManager plotManager) {
        this.plugin = plugin;
        this.plotManager = plotManager;
    }

    public CompletableFuture<Boolean> mergePlots(int plotAId, int plotBId, UUID requester) {
        return CompletableFuture.supplyAsync(() -> {
            Plot plotA = findByNumericId(plotAId);
            Plot plotB = findByNumericId(plotBId);
            if (plotA == null || plotB == null) {
                return false;
            }

            World worldA = plotManager.getWorldForPlot(plotA.getPlotId());
            World worldB = plotManager.getWorldForPlot(plotB.getPlotId());
            if (worldA == null || worldB == null || !worldA.getName().equals(worldB.getName())) {
                return false;
            }
            if (!areAdjacent(plotA, plotB)) {
                return false;
            }
            if (!canManage(plotA, requester) || !canManage(plotB, requester)) {
                return false;
            }
            if (!safePasteCheck(plotA, plotB)) {
                return false;
            }

            File backupDir = createBackupDir("merge");
            writePlotBackup(backupDir, plotA);
            writePlotBackup(backupDir, plotB);

            PlotStorage storage = plotManager.getStorage(worldA);
            List<Plot> rollback = new ArrayList<Plot>();
            rollback.add(plotA);
            rollback.add(plotB);

            try {
                Bounds bounds = mergeBounds(plotA, plotB);
                int size = Math.max(bounds.width(), bounds.height());
                int centerX = bounds.minX + size / 2;
                int centerZ = bounds.minZ + size / 2;

                String mergedId = storage.getWorldName() + "_plot_" + storage.getNextPlotNumber();
                UUID owner = plotA.getOwner() != null ? plotA.getOwner() : plotB.getOwner();
                Plot merged = new Plot(mergedId, centerX, centerZ, size, owner, System.currentTimeMillis(), Math.max(plotA.getSpawnY(), plotB.getSpawnY()), Plot.PlotState.CLAIMED);
                merged.addTrusted(plotA.getOwner() == null ? requester : plotA.getOwner());
                merged.addTrusted(plotB.getOwner() == null ? requester : plotB.getOwner());
                for (UUID trusted : plotA.getTrustedPlayers()) {
                    merged.addTrusted(trusted);
                }
                for (UUID trusted : plotB.getTrustedPlayers()) {
                    merged.addTrusted(trusted);
                }
                if (merged.getOwner() != null) {
                    merged.removeTrusted(merged.getOwner());
                }

                storage.deletePlot(plotA.getPlotId());
                storage.deletePlot(plotB.getPlotId());
                storage.savePlot(merged);
                return true;
            } catch (Exception ex) {
                storage.saveMany(rollback);
                return false;
            }
        });
    }

    public CompletableFuture<List<Integer>> splitPlot(int plotId, SplitStrategy strategy, UUID requester) {
        return CompletableFuture.supplyAsync(() -> {
            Plot plot = findByNumericId(plotId);
            if (plot == null || strategy == null || strategy.getType() != SplitStrategy.Type.GRID) {
                return new ArrayList<Integer>();
            }
            if (!canManage(plot, requester)) {
                return new ArrayList<Integer>();
            }
            if (strategy.getRows() <= 0 || strategy.getCols() <= 0) {
                return new ArrayList<Integer>();
            }

            World world = plotManager.getWorldForPlot(plot.getPlotId());
            PlotStorage storage = plotManager.getStorage(world);
            File backupDir = createBackupDir("split");
            writePlotBackup(backupDir, plot);

            List<Plot> generated = splitGrid(plot, strategy, storage);
            if (generated.isEmpty()) {
                return new ArrayList<Integer>();
            }

            try {
                storage.deletePlot(plot.getPlotId());
                storage.saveMany(generated);
                List<Integer> ids = new ArrayList<Integer>();
                for (Plot p : generated) {
                    ids.add(extractNumericSuffix(p.getPlotId()));
                }
                ids.sort(Comparator.naturalOrder());
                return ids;
            } catch (Exception ex) {
                storage.savePlot(plot);
                return new ArrayList<Integer>();
            }
        });
    }

    public static boolean areAdjacent(Plot a, Plot b) {
        boolean horizontalTouch = (a.getMaxX() == b.getMinX() || b.getMaxX() == a.getMinX())
                && rangesOverlap(a.getMinZ(), a.getMaxZ(), b.getMinZ(), b.getMaxZ());
        boolean verticalTouch = (a.getMaxZ() == b.getMinZ() || b.getMaxZ() == a.getMinZ())
                && rangesOverlap(a.getMinX(), a.getMaxX(), b.getMinX(), b.getMaxX());
        return horizontalTouch || verticalTouch;
    }

    private static boolean rangesOverlap(int aMin, int aMax, int bMin, int bMax) {
        return aMin < bMax && bMin < aMax;
    }

    static Bounds mergeBounds(Plot a, Plot b) {
        return new Bounds(
                Math.min(a.getMinX(), b.getMinX()),
                Math.max(a.getMaxX(), b.getMaxX()),
                Math.min(a.getMinZ(), b.getMinZ()),
                Math.max(a.getMaxZ(), b.getMaxZ())
        );
    }

    static class Bounds {
        final int minX;
        final int maxX;
        final int minZ;
        final int maxZ;

        Bounds(int minX, int maxX, int minZ, int maxZ) {
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }

        int width() { return maxX - minX; }
        int height() { return maxZ - minZ; }
    }

    private List<Plot> splitGrid(Plot plot, SplitStrategy strategy, PlotStorage storage) {
        int rows = strategy.getRows();
        int cols = strategy.getCols();
        int cellWidth = (plot.getMaxX() - plot.getMinX()) / cols;
        int cellHeight = (plot.getMaxZ() - plot.getMinZ()) / rows;
        int cellSize = Math.min(cellWidth, cellHeight);
        if (cellSize <= 1) {
            return new ArrayList<Plot>();
        }

        List<Plot> result = new ArrayList<Plot>();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int minX = plot.getMinX() + c * cellWidth;
                int minZ = plot.getMinZ() + r * cellHeight;
                int centerX = minX + cellSize / 2;
                int centerZ = minZ + cellSize / 2;
                String id = storage.getWorldName() + "_plot_" + storage.getNextPlotNumber() + "_" + (r * cols + c + 1);
                Plot sub = new Plot(id, centerX, centerZ, cellSize, plot.getOwner(), System.currentTimeMillis(), plot.getSpawnY(), plot.getState());
                for (UUID trusted : plot.getTrustedPlayers()) {
                    sub.addTrusted(trusted);
                }
                result.add(sub);
            }
        }
        return result;
    }

    private Plot findByNumericId(int id) {
        Collection<Plot> plots = plotManager.getAllPlots();
        for (Plot plot : plots) {
            if (extractNumericSuffix(plot.getPlotId()) == id) {
                return plot;
            }
        }
        return null;
    }

    private int extractNumericSuffix(String plotId) {
        int idx = plotId.lastIndexOf('_');
        if (idx < 0 || idx == plotId.length() - 1) {
            return -1;
        }
        try {
            return Integer.parseInt(plotId.substring(idx + 1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private boolean canManage(Plot plot, UUID requester) {
        if (requester == null) {
            return false;
        }
        if (plot.getOwner() != null && plot.getOwner().equals(requester)) {
            return true;
        }
        Player player = Bukkit.getPlayer(requester);
        return player != null && player.hasPermission("streuland.admin");
    }

    private boolean safePasteCheck(Plot a, Plot b) {
        return a.getState() == b.getState();
    }

    private File createBackupDir(String action) {
        File dir = new File(plugin.getDataFolder(), "backups/" + action + "-" + System.currentTimeMillis());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private void writePlotBackup(File dir, Plot plot) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", plot.getPlotId());
        yaml.set("centerX", plot.getCenterX());
        yaml.set("centerZ", plot.getCenterZ());
        yaml.set("size", plot.getSize());
        yaml.set("owner", plot.getOwner() == null ? null : plot.getOwner().toString());
        yaml.set("state", plot.getState().name());
        yaml.set("trusted", new ArrayList<UUID>(new HashSet<UUID>(plot.getTrustedPlayers())));
        try {
            yaml.save(new File(dir, plot.getPlotId() + ".yml"));
        } catch (IOException e) {
            plugin.getLogger().warning("Backup write failed for plot " + plot.getPlotId() + ": " + e.getMessage());
        }
    }
}
