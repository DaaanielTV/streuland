package de.streuland.plot.snapshot;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;
import de.streuland.plot.PlotStorage;
import de.streuland.plot.PlotStoragePartitioner;
import de.streuland.plot.Role;
import de.streuland.plot.skin.PlotTheme;
import de.streuland.quest.QuestProgress;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class SnapshotStorageRollbackTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndLoadsSnapshotTimelineMetadata() {
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        SnapshotStorage storage = new SnapshotStorage(plugin);
        UUID owner = UUID.randomUUID();
        Plot plot = new Plot("world_plot_7", 100, 100, 32, owner, 1234L, 70, Plot.PlotState.CLAIMED);
        plot.assignRole(UUID.randomUUID(), Role.BUILDER);

        PlotData plotData = new PlotData(PlotTheme.MODERN);
        plotData.setBonusStorageSlots(8);
        plotData.getUnlockedAbilities().add("double_harvest");
        plotData.getFlagOverrides().put("PVP", true);
        QuestProgress progress = new QuestProgress();
        progress.setValue(9);
        progress.setCompleted(true);
        progress.setCompletedAt(4321L);
        plotData.getQuestProgress().put("q1", progress);

        PlotSnapshot snapshot = new PlotSnapshot(
                "snapshot_123",
                plot.getPlotId(),
                owner,
                9999L,
                Collections.emptyList(),
                new PlotSnapshotMetadata(plot, plotData, "Alice", "before rollback")
        );

        storage.saveSnapshotAsync(snapshot).join();

        List<SnapshotMeta> timeline = storage.listSnapshots(plot.getPlotId());
        assertEquals(1, timeline.size());
        assertEquals("Alice", timeline.get(0).getAuthorName());
        assertEquals("before rollback", timeline.get(0).getNote());

        PlotSnapshot loaded = storage.loadSnapshot(plot.getPlotId(), "snapshot_123");
        assertNotNull(loaded);
        assertNotNull(loaded.getMetadata());
        assertEquals("Alice", loaded.getMetadata().getAuthorName());
        assertEquals("before rollback", loaded.getMetadata().getNote());
        assertEquals(PlotTheme.MODERN, loaded.getMetadata().getPlotData().getTheme());
        assertEquals(8, loaded.getMetadata().getPlotData().getBonusStorageSlots());
        assertTrue(loaded.getMetadata().getPlotData().getFlagOverrides().get("PVP"));
        assertEquals(9, loaded.getMetadata().getPlotData().getQuestProgress().get("q1").getValue());
    }

    @Test
    void restoresPlotDataFromSnapshotMetadata() {
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        PlotStorage plotStorage = new PlotStorage(plugin, "world_main", new PlotStoragePartitioner(plugin));
        UUID owner = UUID.randomUUID();
        Plot original = new Plot("world_main_plot_2", 20, 20, 32, owner, 100L, 70, Plot.PlotState.CLAIMED);
        plotStorage.savePlot(original);

        PlotData current = plotStorage.getPlotData(original.getPlotId());
        current.setTheme(PlotTheme.NATURE);
        current.setFeatured(false);
        current.getFlagOverrides().put("FLY", false);
        plotStorage.savePlotData(original.getPlotId(), current);

        Plot restoredPlot = new Plot("world_main_plot_2", 20, 20, 32, owner, 100L, 70, Plot.PlotState.CLAIMED);
        restoredPlot.assignRole(UUID.randomUUID(), Role.CO_OWNER);
        PlotData restoredData = new PlotData(PlotTheme.STEAMPUNK);
        restoredData.setFeatured(true);
        restoredData.getFlagOverrides().put("FLY", true);
        restoredData.getUnlockedAbilities().add("smelter");

        boolean restored = plotStorage.restoreSnapshotMetadata(
                original.getPlotId(),
                new PlotSnapshotMetadata(restoredPlot, restoredData, "Admin", "rollback")
        );

        assertTrue(restored);
        assertEquals(PlotTheme.STEAMPUNK, plotStorage.getPlotData(original.getPlotId()).getTheme());
        assertTrue(plotStorage.getPlotData(original.getPlotId()).isFeatured());
        assertTrue(plotStorage.getPlotData(original.getPlotId()).getFlagOverrides().get("FLY"));
        assertTrue(plotStorage.getPlotData(original.getPlotId()).getUnlockedAbilities().contains("smelter"));
        assertFalse(plotStorage.getPlot(original.getPlotId()).getRoles().isEmpty());
        assertTrue(new File(tempDir.toFile(), "plots/world_main/world_main_plot_2.yml").exists());
    }

    @Test
    void retainsNewestSnapshotsWhenMaxSnapshotsExceeded() {
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        SnapshotStorage storage = new SnapshotStorage(plugin);
        String plotId = "world_plot_9";
        UUID creator = UUID.randomUUID();

        storage.saveSnapshotAsync(new PlotSnapshot("snapshot_oldest", plotId, creator, 1000L, Collections.emptyList())).join();
        storage.saveSnapshotAsync(new PlotSnapshot("snapshot_mid", plotId, creator, 2000L, Collections.emptyList())).join();
        storage.saveSnapshotAsync(new PlotSnapshot("snapshot_newest", plotId, creator, 3000L, Collections.emptyList())).join();

        storage.enforceMaxSnapshots(plotId, 2);

        List<SnapshotMeta> metas = storage.listSnapshots(plotId);
        assertEquals(2, metas.size());
        assertEquals(Arrays.asList("snapshot_newest", "snapshot_mid"),
                Arrays.asList(metas.get(0).getId(), metas.get(1).getId()));
        assertNull(storage.loadSnapshot(plotId, "snapshot_oldest"));
    }

    @Test
    void loadsBlockPayloadForRollbackCorrectly() {
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        SnapshotStorage storage = new SnapshotStorage(plugin);
        String plotId = "world_plot_10";
        UUID creator = UUID.randomUUID();

        BlockSnapshot chest = new BlockSnapshot(10, 64, 10, "CHEST", "minecraft:chest[facing=north,type=single,waterlogged=false]", Collections.singletonMap("custom", "value"));
        BlockSnapshot stone = new BlockSnapshot(11, 64, 10, "STONE", "minecraft:stone", null);
        PlotSnapshot snapshot = new PlotSnapshot("snapshot_blocks", plotId, creator, 4444L, Arrays.asList(chest, stone));
        storage.saveSnapshotAsync(snapshot).join();

        PlotSnapshot loaded = storage.loadSnapshot(plotId, "snapshot_blocks");
        assertNotNull(loaded);
        assertEquals(2, loaded.getBlocks().size());
        assertEquals("CHEST", loaded.getBlocks().get(0).getType());
        assertEquals("minecraft:chest[facing=north,type=single,waterlogged=false]", loaded.getBlocks().get(0).getBlockData());
        assertEquals("value", loaded.getBlocks().get(0).getTileData().get("custom"));
        assertEquals("STONE", loaded.getBlocks().get(1).getType());
    }
}
