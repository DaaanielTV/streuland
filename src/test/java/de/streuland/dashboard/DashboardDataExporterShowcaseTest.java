package de.streuland.dashboard;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;
import de.streuland.plot.PlotStorage;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class DashboardDataExporterShowcaseTest {

    @Test
    void geoJsonIncludesShowcaseFields() {
        PlotStorage storage = mock(PlotStorage.class);
        World world = mock(World.class);
        Block block = mock(Block.class);
        Plot plot = new Plot("world_main_plot_7", 10, 20, 64, UUID.randomUUID(), 1L, 70, Plot.PlotState.CLAIMED);
        PlotData data = new PlotData();
        data.setPublicVisitEnabled(true);
        data.setShowcaseTitle("Garden Walk");
        data.setShowcaseDescription("Open for visitors");
        data.setShowcaseTagsFromText("garden, flowers");
        data.setShowcaseSpawn(12, 71, 22);

        when(storage.getPlotData(plot.getPlotId())).thenReturn(data);
        when(world.getBlockAt(plot.getCenterX(), plot.getSpawnY(), plot.getCenterZ())).thenReturn(block);
        when(block.getBiome()).thenReturn(Biome.PLAINS);

        String json = new DashboardDataExporter(storage).toGeoJson(Collections.singletonList(plot), world);

        assertTrue(json.contains("\"publicVisit\":true"));
        assertTrue(json.contains("\"title\":\"Garden Walk\""));
        assertTrue(json.contains("\"spawnX\":12"));
        assertTrue(json.contains("\"tags\":[\"garden\",\"flowers\"]"));
    }
}
