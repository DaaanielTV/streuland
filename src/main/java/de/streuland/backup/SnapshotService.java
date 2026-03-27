package de.streuland.backup;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.snapshot.SnapshotManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SnapshotService {
    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final SnapshotManager snapshotManager;
    private final Path rootDirectory;
    private final int retentionDays;
    private final Clock clock;

    public SnapshotService(JavaPlugin plugin, PlotManager plotManager, SnapshotManager snapshotManager) {
        this(plugin, plotManager, snapshotManager, plugin.getDataFolder().toPath().resolve("snapshots"),
                Math.max(1, plugin.getConfig().getInt("backups.retentionDays", 14)), Clock.systemUTC());
    }

    SnapshotService(JavaPlugin plugin, PlotManager plotManager, SnapshotManager snapshotManager, Path rootDirectory,
                    int retentionDays, Clock clock) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.snapshotManager = snapshotManager;
        this.rootDirectory = rootDirectory;
        this.retentionDays = Math.max(1, retentionDays);
        this.clock = clock;
    }

    public CompletableFuture<String> takeSnapshot(int plotId) {
        Optional<Plot> plotOptional = findPlot(plotId);
        if (!plotOptional.isPresent()) {
            return CompletableFuture.completedFuture(null);
        }
        return takeSnapshot(plotOptional.get(), UUID.randomUUID(), null, "manual");
    }

    public CompletableFuture<String> createRestorePoint(String plotId, UUID actor, String actorName, String riskyAction) {
        Optional<Plot> plotOptional = plotManager.getAllPlots().stream()
                .filter(plot -> plot.getPlotId().equals(plotId))
                .findFirst();
        if (!plotOptional.isPresent()) {
            return CompletableFuture.completedFuture(null);
        }
        String note = "restore-point";
        if (riskyAction != null && !riskyAction.trim().isEmpty()) {
            note = note + ": " + riskyAction.trim();
        }
        return takeSnapshot(plotOptional.get(), actor, actorName, note);
    }

    public List<String> listSnapshots(int plotId) {
        Path plotDir = rootDirectory.resolve(String.valueOf(plotId));
        return listSnapshotIds(plotDir);
    }

    public CompletableFuture<Boolean> restoreSnapshot(int plotId, String snapshotId) {
        String id = String.valueOf(plotId);
        ensureLegacySnapshotFile(id, snapshotId);
        return snapshotManager.restoreSnapshot(id, snapshotId, false)
                .thenApply(unused -> true)
                .exceptionally(ex -> false);
    }

    public void runScheduledBackups() {
        for (Plot plot : plotManager.getAllPlots()) {
            takeSnapshot(plot, UUID.randomUUID(), "SYSTEM", "scheduled");
        }
    }

    private CompletableFuture<String> takeSnapshot(Plot plot, UUID creator, String authorName, String note) {
        String plotId = plot.getPlotId();
        return snapshotManager.createSnapshot(plot, creator, authorName, note)
                .thenApply(snapshot -> {
                    persistSnapshotArtifacts(plotId, snapshot.getId());
                    enforceRetention(plotId);
                    plugin.getLogger().info("[AUDIT] Snapshot created plot=" + plotId
                            + " snapshot=" + snapshot.getId()
                            + " note=" + (note == null ? "" : note));
                    return snapshot.getId();
                });
    }

    private Optional<Plot> findPlot(int plotId) {
        for (Plot plot : plotManager.getAllPlots()) {
            Optional<Integer> parsed = parsePlotId(plot.getPlotId());
            if (parsed.isPresent() && parsed.get() == plotId) {
                return Optional.of(plot);
            }
        }
        return Optional.empty();
    }

    private Optional<Integer> parsePlotId(String plotId) {
        try {
            return Optional.of(Integer.parseInt(plotId));
        } catch (NumberFormatException ignored) {
            int index = plotId.lastIndexOf('_');
            if (index >= 0 && index < plotId.length() - 1) {
                try {
                    return Optional.of(Integer.parseInt(plotId.substring(index + 1)));
                } catch (NumberFormatException ignoredAgain) {
                    return Optional.empty();
                }
            }
            return Optional.empty();
        }
    }

    private List<String> listSnapshotIds(Path plotDir) {
        if (!Files.exists(plotDir)) {
            return new ArrayList<>();
        }
        try (Stream<Path> stream = Files.list(plotDir)) {
            return stream
                    .filter(path -> path.getFileName().toString().endsWith(".schem"))
                    .map(path -> path.getFileName().toString().replace(".schem", ""))
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to list snapshots in " + plotDir + ": " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void persistSnapshotArtifacts(String plotId, String snapshotId) {
        try {
            Path plotDir = rootDirectory.resolve(plotId);
            Files.createDirectories(plotDir);
            Path yaml = plotDir.resolve(snapshotId + ".yml");
            if (!Files.exists(yaml)) {
                return;
            }
            Files.copy(yaml, plotDir.resolve(snapshotId + ".schem"), StandardCopyOption.REPLACE_EXISTING);
            Files.copy(yaml, plotDir.resolve(snapshotId + ".snbt"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to persist backup artifacts for " + snapshotId + ": " + e.getMessage());
        }
    }

    private void ensureLegacySnapshotFile(String plotId, String snapshotId) {
        Path plotDir = rootDirectory.resolve(plotId);
        Path yaml = plotDir.resolve(snapshotId + ".yml");
        Path snbt = plotDir.resolve(snapshotId + ".snbt");
        if (!Files.exists(yaml) && Files.exists(snbt)) {
            try {
                Files.copy(snbt, yaml, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to rebuild snapshot file from snbt for " + snapshotId + ": " + e.getMessage());
            }
        }
    }

    private void enforceRetention(String plotId) {
        Instant cutoff = Instant.now(clock).minus(retentionDays, ChronoUnit.DAYS);
        Path plotDir = rootDirectory.resolve(plotId);
        if (!Files.exists(plotDir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(plotDir)) {
            stream.filter(path -> {
                        String fileName = path.getFileName().toString();
                        return fileName.endsWith(".schem") || fileName.endsWith(".snbt") || fileName.endsWith(".yml");
                    })
                    .forEach(path -> {
                        try {
                            Instant modified = Files.getLastModifiedTime(path).toInstant();
                            if (modified.isBefore(cutoff)) {
                                Files.deleteIfExists(path);
                            }
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to enforce retention for plot " + plotId + ": " + e.getMessage());
        }
    }
}
