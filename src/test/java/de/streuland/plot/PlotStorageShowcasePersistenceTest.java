package de.streuland.plot;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlotStorageShowcasePersistenceTest {

    @Test
    void savesAndReloadsShowcaseMetadata(@TempDir Path tempDir) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        PlotStorage storage = new PlotStorage(plugin, "world_main", new PlotStoragePartitioner(plugin));
        UUID owner = UUID.randomUUID();
        Plot plot = new Plot("world_main_plot_12", 100, 200, 64, owner, 1L, 80, Plot.PlotState.CLAIMED);
        storage.savePlot(plot);

        PlotData data = storage.getPlotData(plot.getPlotId());
        data.setPublicVisitEnabled(true);
        data.setShowcaseTitle("Sunset Garden");
        data.setShowcaseDescription("Best flowers in town");
        data.setShowcaseTags(new LinkedHashSet<>(Arrays.asList("garden", "flowers")));
        data.setShowcaseSpawn(101, 81, 202);
        storage.savePlotData(plot.getPlotId(), data);

        PlotStorage reloaded = new PlotStorage(plugin, "world_main", new PlotStoragePartitioner(plugin));
        PlotData restored = reloaded.getPlotData(plot.getPlotId());

        assertTrue(restored.isPublicVisitEnabled());
        assertEquals("Sunset Garden", restored.getShowcaseTitle());
        assertEquals("Best flowers in town", restored.getShowcaseDescription());
        assertEquals(new LinkedHashSet<>(Arrays.asList("garden", "flowers")), restored.getShowcaseTags());
        assertEquals(101, restored.getShowcaseSpawnX());
        assertEquals(81, restored.getShowcaseSpawnY());
        assertEquals(202, restored.getShowcaseSpawnZ());
    }
}
