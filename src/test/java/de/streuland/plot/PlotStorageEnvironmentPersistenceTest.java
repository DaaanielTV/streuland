package de.streuland.plot;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlotStorageEnvironmentPersistenceTest {

    @Test
    void savesAndReloadsEnvironmentSettings(@TempDir Path tempDir) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));

        PlotStorage storage = new PlotStorage(plugin, "world_main", new PlotStoragePartitioner(plugin));
        Plot plot = new Plot("world_main_plot_33", 60, 60, 64, UUID.randomUUID(), 1L, 80, Plot.PlotState.CLAIMED);
        storage.savePlot(plot);

        PlotData data = storage.getPlotData(plot.getPlotId());
        data.setSelectedBiome("DESERT");
        data.setWeatherLocked(true);
        data.getEnvironmentCosmetics().put("sky", "warm");
        storage.savePlotData(plot.getPlotId(), data);

        PlotStorage reloaded = new PlotStorage(plugin, "world_main", new PlotStoragePartitioner(plugin));
        PlotData restored = reloaded.getPlotData(plot.getPlotId());

        assertEquals("DESERT", restored.getSelectedBiome());
        assertTrue(restored.isWeatherLocked());
        assertEquals("warm", restored.getEnvironmentCosmetics().get("sky"));
    }
}
