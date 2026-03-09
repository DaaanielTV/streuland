package de.streuland.pricing;

import de.streuland.neighborhood.NeighborhoodService;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PricingEngineTest {

    @Test
    void calculatorComputesExpectedPrice() {
        double price = PricingEngine.Calculator.computePrice(1000D, 1.2D, 1.1D, 1.05D, 1.3D);
        assertEquals(1801.8D, price, 0.0001D);
    }

    @Test
    void recentSalesIncreaseNearbyPriceEstimate() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        PlotManager plotManager = mock(PlotManager.class);
        NeighborhoodService neighborhoodService = mock(NeighborhoodService.class);
        PlotStorage storage = mock(PlotStorage.class);
        World world = mock(World.class);

        File dataFolder = new File("target/test-plugin-data");
        dataFolder.mkdirs();
        FileConfiguration cfg = new YamlConfiguration();
        cfg.set("pricing.base-price", 1000D);
        cfg.set("pricing.nearby-sales-decay", 0.5D);
        cfg.set("pricing.featured-boost", 0.0D);
        cfg.set("pricing.proximity-to-spawn-multiplier", 0.0D);
        cfg.set("pricing.scarcity-factor", 0.0D);
        cfg.set("pricing.sales-lookback-days", 30);
        cfg.set("pricing.nearby-sales-radius", 300D);

        Plot subject = new Plot("world_main_plot_1", 0, 0, 64, UUID.randomUUID(), System.currentTimeMillis(), 64, Plot.PlotState.CLAIMED);
        Plot soldNearby = new Plot("world_main_plot_2", 20, 20, 64, UUID.randomUUID(), System.currentTimeMillis(), 64, Plot.PlotState.CLAIMED);
        Plot available = new Plot("world_main_plot_3", 250, 250, 64, null, System.currentTimeMillis(), 64, Plot.PlotState.UNCLAIMED);

        when(plugin.getConfig()).thenReturn(cfg);
        when(plugin.getDataFolder()).thenReturn(dataFolder);
        when(plotManager.getStorage(any(World.class))).thenReturn(storage);
        when(plotManager.getStorage()).thenReturn(storage);
        when(plotManager.getWorldForPlot(anyString())).thenReturn(world);
        when(plotManager.getAllPlots()).thenReturn(Arrays.asList(subject, soldNearby, available));
        when(plotManager.getAllPlots(world)).thenReturn(Arrays.asList(subject, soldNearby, available));
        when(storage.getPlot("world_main_plot_1")).thenReturn(subject);
        when(storage.getPlot("world_main_plot_2")).thenReturn(soldNearby);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0, 80, 0));

        PricingEngine engine = new PricingEngine(plugin, plotManager, neighborhoodService);
        double before = engine.estimatePrice("world_main_plot_1");

        engine.recordSale("world_main_plot_2", 2200D);

        double after = engine.estimatePrice("world_main_plot_1");
        assertTrue(after > before);
        assertTrue(after > 1000D);
    }
}
