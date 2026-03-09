package de.streuland.market;

import de.streuland.economy.PlotEconomyHook;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotStorage;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MarketManagerTest {
    private JavaPlugin plugin;
    private PlotStorage storage;
    private PlotEconomyHook economy;
    private File dataDir;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(JavaPlugin.class);
        storage = mock(PlotStorage.class);
        economy = mock(PlotEconomyHook.class);
        dataDir = Files.createTempDirectory("market-test").toFile();
        when(plugin.getDataFolder()).thenReturn(dataDir);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
    }

    @Test
    void buyerWithEnoughBalanceChangesOwner() {
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();

        Plot plot = mock(Plot.class);
        when(plot.getPlotId()).thenReturn("plot_1");
        when(storage.getAllPlots()).thenReturn(Collections.singletonList(plot));
        when(storage.transferOwnership("plot_1", seller, buyer)).thenReturn(plot);

        when(economy.hasEconomy()).thenReturn(true);
        when(economy.getBalance(buyer)).thenReturn(250D);
        when(economy.withdraw(buyer, 100D)).thenReturn(true);
        when(economy.deposit(seller, 100D)).thenReturn(true);

        MarketManager manager = new MarketManager(plugin, storage, economy);
        manager.createSale(new PlotSale(1, seller, 100D, PlotSale.SaleType.FIXED, 0L, 0D, null));

        assertTrue(manager.buyPlot(buyer, 1));
        verify(storage).transferOwnership("plot_1", seller, buyer);
    }

    @Test
    void insufficientBalanceDoesNotChangeOwner() {
        UUID seller = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();

        when(economy.hasEconomy()).thenReturn(true);
        when(economy.getBalance(buyer)).thenReturn(10D);

        MarketManager manager = new MarketManager(plugin, storage, economy);
        manager.createSale(new PlotSale(5, seller, 100D, PlotSale.SaleType.FIXED, 0L, 0D, null));

        assertFalse(manager.buyPlot(buyer, 5));
        verify(storage, never()).transferOwnership(any(), any(), any());
    }

    @Test
    void endedAuctionAwardsHighestBidder() {
        UUID seller = UUID.randomUUID();
        UUID bidder = UUID.randomUUID();

        Plot plot = mock(Plot.class);
        when(plot.getPlotId()).thenReturn("plot_9");
        when(storage.getAllPlots()).thenReturn(Collections.singletonList(plot));
        when(storage.transferOwnership("plot_9", seller, bidder)).thenReturn(plot);

        when(economy.withdraw(bidder, 300D)).thenReturn(true);
        when(economy.deposit(seller, 300D)).thenReturn(true);

        MarketManager manager = new MarketManager(plugin, storage, economy);
        manager.createSale(new PlotSale(9, seller, 100D, PlotSale.SaleType.AUCTION,
                Instant.now().minusSeconds(5).toEpochMilli(), 300D, bidder));

        manager.processEndedAuctions();

        verify(storage).transferOwnership("plot_9", seller, bidder);
        assertTrue(manager.listForSale().isEmpty());
    }
}
