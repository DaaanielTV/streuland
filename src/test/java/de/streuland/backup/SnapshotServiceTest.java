package de.streuland.backup;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.snapshot.PlotSnapshot;
import de.streuland.plot.snapshot.SnapshotManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class SnapshotServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void takeSnapshotCreatesSchemAndSnbtFiles() {
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        PlotManager plotManager = Mockito.mock(PlotManager.class);
        SnapshotManager snapshotManager = Mockito.mock(SnapshotManager.class);
        FileConfiguration config = Mockito.mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(config.getInt(eq("backups.retentionDays"), eq(14))).thenReturn(14);

        Plot plot = Mockito.mock(Plot.class);
        when(plot.getPlotId()).thenReturn("1");
        when(plotManager.getAllPlots()).thenReturn(Collections.singletonList(plot));

        Path plotDir = tempDir.resolve("1");
        String snapshotId = "snapshot_1";
        try {
            Files.createDirectories(plotDir);
            Files.write(plotDir.resolve(snapshotId + ".yml"), "dummy: true".getBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        PlotSnapshot snapshot = new PlotSnapshot(snapshotId, "1", UUID.randomUUID(), System.currentTimeMillis(), Collections.emptyList());
        when(snapshotManager.createSnapshot(eq(plot), any())).thenReturn(CompletableFuture.completedFuture(snapshot));

        SnapshotService service = new SnapshotService(plugin, plotManager, snapshotManager, tempDir, 14,
                Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC));

        String created = service.takeSnapshot(1).join();

        assertEquals(snapshotId, created);
        assertTrue(Files.exists(plotDir.resolve(snapshotId + ".schem")));
        assertTrue(Files.exists(plotDir.resolve(snapshotId + ".snbt")));
    }

    @Test
    void restoreSnapshotUsesYamlRecoveredFromSnbt() throws Exception {
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        PlotManager plotManager = Mockito.mock(PlotManager.class);
        SnapshotManager snapshotManager = Mockito.mock(SnapshotManager.class);
        FileConfiguration config = Mockito.mock(FileConfiguration.class);
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(config.getInt(eq("backups.retentionDays"), eq(14))).thenReturn(14);
        when(snapshotManager.restoreSnapshot("1", "snapA", false)).thenReturn(CompletableFuture.completedFuture(null));

        Path plotDir = tempDir.resolve("1");
        Files.createDirectories(plotDir);
        Files.write(plotDir.resolve("snapA.snbt"), "serialized".getBytes());

        SnapshotService service = new SnapshotService(plugin, plotManager, snapshotManager, tempDir, 14,
                Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC));

        boolean restored = service.restoreSnapshot(1, "snapA").join();

        assertTrue(restored);
        assertTrue(Files.exists(plotDir.resolve("snapA.yml")));
    }
}
