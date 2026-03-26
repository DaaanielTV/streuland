package de.streuland.plot.environment;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class PlotEnvironmentServiceTest {

    @Test
    void enforcesPermissionAndOwnershipBeforeEnvironmentChanges() {
        PlotManager plotManager = mock(PlotManager.class);
        PlotStorage storage = mock(PlotStorage.class);
        World world = mock(World.class);
        when(plotManager.getStorage(world)).thenReturn(storage);

        UUID owner = UUID.randomUUID();
        Plot plot = new Plot("plot-1", 10, 10, 8, owner, 1L, 64, Plot.PlotState.CLAIMED);

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("plot.environment.biome-options", java.util.Collections.singletonList("PLAINS"));

        PlotEnvironmentService service = new PlotEnvironmentService(plotManager, cfg);

        assertEquals(PlotEnvironmentService.ChangeResult.MISSING_PERMISSION,
                service.setBiome(plot, owner, false, "PLAINS"));

        assertEquals(PlotEnvironmentService.ChangeResult.NOT_OWNER,
                service.setBiome(plot, UUID.randomUUID(), true, "PLAINS"));

        verify(world, never()).setBiome(anyInt(), anyInt(), anyInt(), any(Biome.class));
    }

    @Test
    void persistsBiomeAndWeatherLockState() {
        PlotManager plotManager = mock(PlotManager.class);
        PlotStorage storage = mock(PlotStorage.class);
        World world = mock(World.class);
        PlotData data = new PlotData();

        when(plotManager.getWorldForPlot("plot-1")).thenReturn(world);
        when(plotManager.getStorage(world)).thenReturn(storage);
        when(storage.getPlotData("plot-1")).thenReturn(data);

        UUID owner = UUID.randomUUID();
        Plot plot = new Plot("plot-1", 10, 10, 6, owner, 1L, 64, Plot.PlotState.CLAIMED);

        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("plot.environment.biome-options", java.util.Collections.singletonList("PLAINS"));
        cfg.set("plot.environment.protected-path-border", 1);
        cfg.set("plot.environment.biome-apply-min-y", 0);
        cfg.set("plot.environment.biome-apply-max-y", 0);

        PlotEnvironmentService service = new PlotEnvironmentService(plotManager, cfg);

        assertEquals(PlotEnvironmentService.ChangeResult.SUCCESS,
                service.setBiome(plot, owner, true, "PLAINS"));
        assertEquals("PLAINS", data.getSelectedBiome());

        assertEquals(PlotEnvironmentService.ChangeResult.SUCCESS,
                service.lockWeather(plot, owner, true, true));
        assertEquals(true, data.isWeatherLocked());

        verify(storage, atLeastOnce()).savePlotData(eq("plot-1"), same(data));
        verify(world, atLeastOnce()).setBiome(anyInt(), eq(0), anyInt(), eq(Biome.PLAINS));
    }
}
