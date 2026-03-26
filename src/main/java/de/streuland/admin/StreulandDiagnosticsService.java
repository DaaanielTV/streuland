package de.streuland.admin;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;

import java.util.*;
import java.util.logging.Logger;

public class StreulandDiagnosticsService {
    private final PlotManager plotManager;
    private final Logger logger;

    public StreulandDiagnosticsService(PlotManager plotManager, Logger logger) {
        this.plotManager = plotManager;
        this.logger = logger;
    }

    public DiagnosticsReport scan() {
        DiagnosticsReport report = new DiagnosticsReport();
        Map<String, String> firstOwnerByPlotId = new HashMap<>();

        for (String worldName : plotManager.getManagedWorlds()) {
            PlotStorage storage = plotManager.getStorage(worldName);
            for (Plot plot : storage.getAllPlots()) {
                String previousWorld = firstOwnerByPlotId.putIfAbsent(plot.getPlotId(), worldName);
                if (previousWorld != null && !previousWorld.equals(worldName)) {
                    report.addIssue(new DiagnosticsIssue(
                            DiagnosticsIssueType.DUPLICATE_ID,
                            worldName,
                            plot.getPlotId(),
                            "Duplicate plot id also exists in world " + previousWorld));
                }

                if (plot.getSize() <= 0 || plot.getMinX() > plot.getMaxX() || plot.getMinZ() > plot.getMaxZ() || !plot.contains(plot.getCenterX(), plot.getCenterZ())) {
                    report.addIssue(new DiagnosticsIssue(
                            DiagnosticsIssueType.INVALID_PLOT_BORDER,
                            worldName,
                            plot.getPlotId(),
                            "Invalid bounds size=" + plot.getSize()));
                }

                Set<String> knownRoleIds = plot.getRoleDefinitions().keySet();
                for (Map.Entry<UUID, Set<String>> entry : plot.getRoleAssignments().entrySet()) {
                    for (String roleId : entry.getValue()) {
                        if (!knownRoleIds.contains(roleId)) {
                            report.addIssue(new DiagnosticsIssue(
                                    DiagnosticsIssueType.BROKEN_REFERENCE,
                                    worldName,
                                    plot.getPlotId(),
                                    "Player " + entry.getKey() + " references missing role " + roleId));
                        }
                    }
                }
            }

            for (String dataId : storage.getPlotDataIds()) {
                if (!storage.exists(dataId)) {
                    report.addIssue(new DiagnosticsIssue(
                            DiagnosticsIssueType.ORPHANED_DATA,
                            worldName,
                            dataId,
                            "PlotData exists without matching plot"));
                }
            }
        }

        return report;
    }

    public int repair(DiagnosticsReport report) {
        int repaired = 0;
        Set<String> removedDuplicateIds = new HashSet<>();

        for (DiagnosticsIssue issue : report.getIssues()) {
            PlotStorage storage = plotManager.getStorage(issue.getWorldName());
            switch (issue.getType()) {
                case ORPHANED_DATA:
                    if (storage.deletePlotData(issue.getPlotId())) {
                        repaired++;
                        logger.info("[repair] Removed orphaned PlotData world=" + issue.getWorldName() + " id=" + issue.getPlotId());
                    }
                    break;
                case DUPLICATE_ID:
                    if (removedDuplicateIds.add(issue.getWorldName() + ":" + issue.getPlotId())) {
                        Plot removed = storage.deletePlot(issue.getPlotId());
                        if (removed != null) {
                            plotManager.getSpatialGrid(issue.getWorldName()).removePlot(removed);
                            repaired++;
                            logger.info("[repair] Removed duplicate plot world=" + issue.getWorldName() + " id=" + issue.getPlotId());
                        }
                    }
                    break;
                case BROKEN_REFERENCE:
                    Plot plot = storage.getPlot(issue.getPlotId());
                    if (plot == null) {
                        break;
                    }
                    Map<String, Set<de.streuland.plot.Permission>> roleDefinitions = plot.getRoleDefinitions();
                    Map<UUID, Set<String>> assignments = plot.getRoleAssignments();
                    boolean changed = false;
                    for (Set<String> roleIds : assignments.values()) {
                        if (roleIds.removeIf(id -> !roleDefinitions.containsKey(id))) {
                            changed = true;
                        }
                    }
                    if (changed) {
                        plot.replaceRoleAssignments(assignments);
                        storage.savePlot(plot);
                        repaired++;
                        logger.info("[repair] Removed broken role references world=" + issue.getWorldName() + " id=" + issue.getPlotId());
                    }
                    break;
                case INVALID_PLOT_BORDER:
                    Plot invalid = storage.deletePlot(issue.getPlotId());
                    if (invalid != null) {
                        plotManager.getSpatialGrid(issue.getWorldName()).removePlot(invalid);
                        storage.deletePlotData(issue.getPlotId());
                        repaired++;
                        logger.warning("[repair] Deleted invalid plot border world=" + issue.getWorldName() + " id=" + issue.getPlotId());
                    }
                    break;
                default:
                    break;
            }
        }

        return repaired;
    }

    public List<String> buildDebugDump() {
        List<String> lines = new ArrayList<>();
        lines.add("PlotManager: managedWorlds=" + plotManager.getManagedWorlds().size() + ", totalPlots=" + plotManager.getAllPlots().size());
        for (String worldName : plotManager.getManagedWorlds()) {
            PlotStorage storage = plotManager.getStorage(worldName);
            int plotCount = storage.getAllPlots().size();
            int dataCount = storage.getPlotDataIds().size();
            lines.add("PlotStorage[" + worldName + "]: plots=" + plotCount + ", plotData=" + dataCount);
            lines.add("SpatialGrid[" + worldName + "]: cells=" + plotManager.getSpatialGrid(worldName).getCellCount() + ", indexedPlots=" + plotManager.getSpatialGrid(worldName).getPlotCount());
        }
        return lines;
    }
}
