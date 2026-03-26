package de.streuland.admin;

import de.streuland.plot.Permission;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
import de.streuland.plot.SpatialGrid;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class StreulandDiagnosticsServiceTest {

    @Test
    void scanDetectsCorruptionTypes() {
        PlotManager plotManager = mock(PlotManager.class);
        PlotStorage worldAStorage = mock(PlotStorage.class);
        PlotStorage worldBStorage = mock(PlotStorage.class);

        Plot brokenBorderPlot = mock(Plot.class);
        when(brokenBorderPlot.getPlotId()).thenReturn("plot-1");
        when(brokenBorderPlot.getSize()).thenReturn(0);
        when(brokenBorderPlot.getMinX()).thenReturn(10);
        when(brokenBorderPlot.getMaxX()).thenReturn(5);
        when(brokenBorderPlot.getMinZ()).thenReturn(10);
        when(brokenBorderPlot.getMaxZ()).thenReturn(5);
        when(brokenBorderPlot.getCenterX()).thenReturn(7);
        when(brokenBorderPlot.getCenterZ()).thenReturn(7);
        when(brokenBorderPlot.contains(anyInt(), anyInt())).thenReturn(false);
        when(brokenBorderPlot.getRoleDefinitions()).thenReturn(new HashMap<String, Set<Permission>>());
        when(brokenBorderPlot.getRoleAssignments()).thenReturn(Collections.<UUID, Set<String>>emptyMap());

        Plot duplicateAndBrokenRefPlot = mock(Plot.class);
        when(duplicateAndBrokenRefPlot.getPlotId()).thenReturn("plot-1");
        when(duplicateAndBrokenRefPlot.getSize()).thenReturn(64);
        when(duplicateAndBrokenRefPlot.getMinX()).thenReturn(0);
        when(duplicateAndBrokenRefPlot.getMaxX()).thenReturn(64);
        when(duplicateAndBrokenRefPlot.getMinZ()).thenReturn(0);
        when(duplicateAndBrokenRefPlot.getMaxZ()).thenReturn(64);
        when(duplicateAndBrokenRefPlot.getCenterX()).thenReturn(32);
        when(duplicateAndBrokenRefPlot.getCenterZ()).thenReturn(32);
        when(duplicateAndBrokenRefPlot.contains(anyInt(), anyInt())).thenReturn(true);
        Map<String, Set<Permission>> roleDefs = new HashMap<>();
        roleDefs.put("owner", EnumSet.of(Permission.BUILD));
        when(duplicateAndBrokenRefPlot.getRoleDefinitions()).thenReturn(roleDefs);
        Map<UUID, Set<String>> assignments = new HashMap<>();
        assignments.put(UUID.randomUUID(), new HashSet<>(Arrays.asList("owner", "missing")));
        when(duplicateAndBrokenRefPlot.getRoleAssignments()).thenReturn(assignments);

        when(plotManager.getManagedWorlds()).thenReturn(Arrays.asList("world_a", "world_b"));
        when(plotManager.getStorage("world_a")).thenReturn(worldAStorage);
        when(plotManager.getStorage("world_b")).thenReturn(worldBStorage);
        when(worldAStorage.getAllPlots()).thenReturn(Collections.singletonList(brokenBorderPlot));
        when(worldBStorage.getAllPlots()).thenReturn(Collections.singletonList(duplicateAndBrokenRefPlot));
        when(worldAStorage.getPlotDataIds()).thenReturn(new HashSet<>(Arrays.asList("plot-1", "orphaned")));
        when(worldBStorage.getPlotDataIds()).thenReturn(new HashSet<>(Collections.singletonList("plot-1")));
        when(worldAStorage.exists("plot-1")).thenReturn(true);
        when(worldAStorage.exists("orphaned")).thenReturn(false);
        when(worldBStorage.exists("plot-1")).thenReturn(true);

        StreulandDiagnosticsService service = new StreulandDiagnosticsService(plotManager, Logger.getLogger("test"));

        DiagnosticsReport report = service.scan();

        assertEquals(4, report.getTotalIssues());
        assertTrue(report.issueCounts().containsKey(DiagnosticsIssueType.INVALID_PLOT_BORDER));
        assertTrue(report.issueCounts().containsKey(DiagnosticsIssueType.ORPHANED_DATA));
        assertTrue(report.issueCounts().containsKey(DiagnosticsIssueType.DUPLICATE_ID));
        assertTrue(report.issueCounts().containsKey(DiagnosticsIssueType.BROKEN_REFERENCE));
    }

    @Test
    void repairCleansCorruption() {
        PlotManager plotManager = mock(PlotManager.class);
        PlotStorage storage = mock(PlotStorage.class);
        SpatialGrid spatialGrid = mock(SpatialGrid.class);

        Plot duplicatePlot = mock(Plot.class);
        Plot invalidPlot = mock(Plot.class);
        Plot brokenRefPlot = mock(Plot.class);

        when(plotManager.getStorage("world")).thenReturn(storage);
        when(plotManager.getSpatialGrid("world")).thenReturn(spatialGrid);
        when(storage.deletePlotData("orphaned")).thenReturn(true);
        when(storage.deletePlot("duplicate")).thenReturn(duplicatePlot);
        when(storage.deletePlot("invalid")).thenReturn(invalidPlot);
        when(storage.getPlot("broken")).thenReturn(brokenRefPlot);

        Map<String, Set<Permission>> roleDefs = new HashMap<>();
        roleDefs.put("member", EnumSet.of(Permission.BUILD));
        Map<UUID, Set<String>> brokenAssignments = new HashMap<>();
        brokenAssignments.put(UUID.randomUUID(), new HashSet<>(Arrays.asList("member", "ghost")));
        when(brokenRefPlot.getRoleDefinitions()).thenReturn(roleDefs);
        when(brokenRefPlot.getRoleAssignments()).thenReturn(brokenAssignments);

        DiagnosticsReport report = new DiagnosticsReport();
        report.addIssue(new DiagnosticsIssue(DiagnosticsIssueType.ORPHANED_DATA, "world", "orphaned", ""));
        report.addIssue(new DiagnosticsIssue(DiagnosticsIssueType.DUPLICATE_ID, "world", "duplicate", ""));
        report.addIssue(new DiagnosticsIssue(DiagnosticsIssueType.INVALID_PLOT_BORDER, "world", "invalid", ""));
        report.addIssue(new DiagnosticsIssue(DiagnosticsIssueType.BROKEN_REFERENCE, "world", "broken", ""));

        StreulandDiagnosticsService service = new StreulandDiagnosticsService(plotManager, Logger.getLogger("test"));

        int repaired = service.repair(report);

        assertEquals(4, repaired);
        verify(storage).savePlot(brokenRefPlot);
        verify(spatialGrid).removePlot(duplicatePlot);
        verify(spatialGrid).removePlot(invalidPlot);
        verify(storage).deletePlotData("invalid");
    }
}
