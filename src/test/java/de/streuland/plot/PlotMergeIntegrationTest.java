package de.streuland.plot;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlotMergeIntegrationTest {
    private ServerMock server;

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void adjacentPlotsCanBeValidatedInMockServerEnvironment() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");

        Plot a = new Plot("world_plot_10", 0, 0, 16, UUID.randomUUID(), System.currentTimeMillis(), 64, Plot.PlotState.CLAIMED);
        Plot b = new Plot("world_plot_11", 16, 0, 16, UUID.randomUUID(), System.currentTimeMillis(), 64, Plot.PlotState.CLAIMED);

        assertTrue(PlotMergeService.areAdjacent(a, b));
    }
}
