package de.streuland.flags;

import de.streuland.plot.AreaType;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.OptionalInt;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlotFlagManagerTest {

    @Test
    void defaultsAreDefinedForAllAreaTypes() {
        for (Flag flag : Flag.values()) {
            for (AreaType areaType : AreaType.values()) {
                assertDoesNotThrow(() -> flag.isEnabledByDefault(areaType));
            }
        }
    }

    @Test
    void overrideBeatsDefault() {
        PlotManager plotManager = mock(PlotManager.class);
        PlotStorage storage = mock(PlotStorage.class);
        World world = mock(World.class);
        PlotData data = new PlotData();

        Plot plot = new Plot("world_plot_12", 0, 0, 32, UUID.randomUUID(), System.currentTimeMillis(), 64, Plot.PlotState.CLAIMED);

        when(plotManager.getWorldForPlot(plot.getPlotId())).thenReturn(world);
        when(plotManager.getStorage(world)).thenReturn(storage);
        when(storage.getPlotData(plot.getPlotId())).thenReturn(data);
        when(plotManager.getAllPlots()).thenReturn(Arrays.asList(plot));

        PlotFlagManager manager = new PlotFlagManager(plotManager);
        assertTrue(manager.isFlagEnabled(plot, Flag.PVP));

        manager.setFlag(plot, Flag.PVP, false);
        assertFalse(manager.isFlagEnabled(plot, Flag.PVP));
        assertFalse(manager.isFlagEnabled(12, Flag.PVP));

        manager.clearOverride(plot, Flag.PVP);
        assertTrue(manager.isFlagEnabled(plot, Flag.PVP));
        verify(storage, atLeastOnce()).savePlotData(eq(plot.getPlotId()), any(PlotData.class));
    }

    @Test
    void parseNumericIdFromPlotId() {
        PlotFlagManager manager = new PlotFlagManager(mock(PlotManager.class));
        OptionalInt id = manager.parseNumericPlotId("world_plot_77");
        assertTrue(id.isPresent());
        assertEquals(77, id.getAsInt());
        assertFalse(manager.parseNumericPlotId("plot_alpha").isPresent());
    }
}
