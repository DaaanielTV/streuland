package de.streuland.market;

import de.streuland.economy.PlotEconomyHook;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;
import de.streuland.plot.PlotStorage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlotMarketServiceTest {
    private JavaPlugin plugin;
    private PlotStorage storage;
    private PlotEconomyHook economy;
    private FileConfiguration config;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(JavaPlugin.class);
        storage = mock(PlotStorage.class);
        economy = mock(PlotEconomyHook.class);
        config = mock(FileConfiguration.class);

        File dataDir = Files.createTempDirectory("plot-market-service").toFile();
        when(plugin.getDataFolder()).thenReturn(dataDir);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        when(plugin.getConfig()).thenReturn(config);

        when(config.getDouble(eq("market.tax-rate"), anyDouble())).thenReturn(0.10D);
        when(config.getDouble(eq("market.flat-fee"), anyDouble())).thenReturn(5D);
        when(config.getDouble(eq("market.confirmation-threshold"), anyDouble())).thenReturn(500D);
        when(config.getDouble(eq("market.approval-threshold"), anyDouble())).thenReturn(1000D);
        when(config.getStringList("market.blocked-plot-ids")).thenReturn(java.util.Collections.emptyList());
    }

    @Test
    void purchaseTransfersOwnershipAndAppliesTax() {
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        Plot plot = mock(Plot.class);
        when(plot.getPlotId()).thenReturn("plot_1");
        when(plot.getOwner()).thenReturn(seller);

        PlotData data = new PlotData();
        when(storage.getPlotData("plot_1")).thenReturn(data);

        PlotMarketService service = new PlotMarketService(plugin, storage, economy);
        assertEquals(PlotMarketService.ListResult.OK, service.listPlot(plot, seller, 100D));

        when(storage.getPlot("plot_1")).thenReturn(plot);
        when(economy.hasEconomy()).thenReturn(true);
        when(economy.getBalance(buyer)).thenReturn(100D);
        when(economy.withdraw(buyer, 100D)).thenReturn(true);
        when(economy.deposit(eq(seller), eq(85D))).thenReturn(true);
        when(storage.transferOwnership("plot_1", seller, buyer)).thenReturn(plot);

        PlotMarketService.BuyOutcome outcome = service.buyPlot(buyer, "plot_1");
        assertEquals(PlotMarketService.BuyResult.OK, outcome.result());
        assertEquals(15D, outcome.taxAmount());
    }

    @Test
    void staleListingIsRemovedIfOwnerChanged() {
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        Plot listedPlot = mock(Plot.class);
        Plot changedPlot = mock(Plot.class);
        when(listedPlot.getPlotId()).thenReturn("plot_3");
        when(listedPlot.getOwner()).thenReturn(seller);
        when(changedPlot.getOwner()).thenReturn(UUID.randomUUID());

        when(storage.getPlotData("plot_3")).thenReturn(new PlotData());

        PlotMarketService service = new PlotMarketService(plugin, storage, economy);
        assertEquals(PlotMarketService.ListResult.OK, service.listPlot(listedPlot, seller, 30D));

        when(storage.getPlot("plot_3")).thenReturn(changedPlot);
        PlotMarketService.BuyOutcome outcome = service.buyPlot(buyer, "plot_3");

        assertEquals(PlotMarketService.BuyResult.STALE_LISTING, outcome.result());
        assertTrue(service.getListingsSnapshot().isEmpty());
    }

    @Test
    void rentFlowCreatesContractAndPersists() {
        UUID seller = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        Plot plot = mock(Plot.class);
        when(plot.getPlotId()).thenReturn("plot_9");
        when(plot.getOwner()).thenReturn(seller);

        when(storage.getPlotData("plot_9")).thenReturn(new PlotData());

        PlotMarketService service = new PlotMarketService(plugin, storage, economy);
        assertEquals(PlotMarketService.ListResult.OK, service.listPlotForRent(plot, seller, 40D, 7));

        when(storage.getPlot("plot_9")).thenReturn(plot);
        when(economy.hasEconomy()).thenReturn(true);
        when(economy.getBalance(tenant)).thenReturn(40D);
        when(economy.withdraw(tenant, 40D)).thenReturn(true);
        when(economy.deposit(eq(seller), eq(31D))).thenReturn(true);

        PlotMarketService.BuyOutcome outcome = service.rentPlot(tenant, "plot_9");
        assertEquals(PlotMarketService.BuyResult.OK, outcome.result());
        assertFalse(service.getContractsSnapshot().isEmpty());

        PlotMarketService reloaded = new PlotMarketService(plugin, storage, economy);
        assertEquals(1, reloaded.getContractsSnapshot().size());
    }

    @Test
    void highValueSaleRequiresApproval() {
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        Plot plot = mock(Plot.class);
        when(plot.getPlotId()).thenReturn("plot_12");
        when(plot.getOwner()).thenReturn(seller);
        when(storage.getPlotData("plot_12")).thenReturn(new PlotData());

        PlotMarketService service = new PlotMarketService(plugin, storage, economy);
        assertEquals(PlotMarketService.ListResult.OK, service.listPlot(plot, seller, 2000D));
        when(storage.getPlot("plot_12")).thenReturn(plot);

        PlotMarketService.BuyOutcome outcome = service.buyPlot(buyer, "plot_12");
        assertEquals(PlotMarketService.BuyResult.APPROVAL_REQUIRED, outcome.result());
    }
}
