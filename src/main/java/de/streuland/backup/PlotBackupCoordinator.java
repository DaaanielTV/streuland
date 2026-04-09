package de.streuland.backup;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Business-level backup workflow service with plot resolution helpers.
 */
public class PlotBackupCoordinator {
    private final PlotManager plotManager;
    private final SnapshotService snapshotService;

    public PlotBackupCoordinator(PlotManager plotManager, SnapshotService snapshotService) {
        this.plotManager = plotManager;
        this.snapshotService = snapshotService;
    }

    public CompletableFuture<String> takeSnapshot(String plotRef, UUID actorId, String actorName, String note) {
        return resolvePlotId(plotRef)
                .map(plotId -> snapshotService.createRestorePoint(plotId, actorId, actorName, note))
                .orElseGet(() -> CompletableFuture.completedFuture(null));
    }

    public List<String> listSnapshots(String plotRef) {
        return resolvePlotNumber(plotRef)
                .map(snapshotService::listSnapshots)
                .orElse(List.of());
    }

    public CompletableFuture<Boolean> restore(String plotRef, String snapshotId) {
        return resolvePlotNumber(plotRef)
                .map(plotNumber -> snapshotService.restoreSnapshot(plotNumber, snapshotId))
                .orElseGet(() -> CompletableFuture.completedFuture(false));
    }

    public Optional<String> resolvePlotId(String plotRef) {
        if (plotRef == null || plotRef.isBlank()) {
            return Optional.empty();
        }
        for (Plot plot : plotManager.getAllPlots()) {
            if (plot.getPlotId().equalsIgnoreCase(plotRef)) {
                return Optional.of(plot.getPlotId());
            }
        }
        return resolvePlotNumber(plotRef).flatMap(number -> plotManager.getAllPlots().stream()
                .filter(plot -> parseNumericSuffix(plot.getPlotId()).orElse(-1) == number)
                .min(Comparator.comparing(Plot::getPlotId))
                .map(Plot::getPlotId));
    }

    private Optional<Integer> resolvePlotNumber(String plotRef) {
        try {
            return Optional.of(Integer.parseInt(plotRef));
        } catch (NumberFormatException ignored) {
            return resolvePlotIdFromSuffix(plotRef);
        }
    }

    private Optional<Integer> resolvePlotIdFromSuffix(String plotRef) {
        for (Plot plot : plotManager.getAllPlots()) {
            if (plot.getPlotId().equalsIgnoreCase(plotRef)) {
                return parseNumericSuffix(plot.getPlotId());
            }
        }
        return Optional.empty();
    }

    private Optional<Integer> parseNumericSuffix(String plotId) {
        try {
            return Optional.of(Integer.parseInt(plotId));
        } catch (NumberFormatException ignored) {
            int separator = plotId.lastIndexOf('_');
            if (separator >= 0 && separator < plotId.length() - 1) {
                try {
                    return Optional.of(Integer.parseInt(plotId.substring(separator + 1)));
                } catch (NumberFormatException ignoredAgain) {
                    return Optional.empty();
                }
            }
            return Optional.empty();
        }
    }
}
