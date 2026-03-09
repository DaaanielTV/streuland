package de.streuland.market;

import de.streuland.economy.PlotEconomyHook;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotStorage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarketManager {
    private static final Pattern NUM_SUFFIX = Pattern.compile(".*_(\\d+)$");

    private final JavaPlugin plugin;
    private final PlotStorage plotStorage;
    private final PlotEconomyHook economyHook;
    private final File marketFolder;
    private final Map<Integer, PlotSale> activeSales = new ConcurrentHashMap<>();

    public MarketManager(JavaPlugin plugin, PlotStorage plotStorage, PlotEconomyHook economyHook) {
        this.plugin = plugin;
        this.plotStorage = plotStorage;
        this.economyHook = economyHook;
        this.marketFolder = new File(plugin.getDataFolder(), "market");
        if (!marketFolder.exists()) {
            marketFolder.mkdirs();
        }
        loadSales();
    }

    public List<PlotSale> listForSale() {
        return new ArrayList<>(activeSales.values());
    }

    public void createSale(PlotSale sale) {
        activeSales.put(sale.getPlotId(), sale);
        saveSale(sale);
    }

    public PlotSale cancelSale(int plotId) {
        PlotSale removed = activeSales.remove(plotId);
        File saleFile = new File(marketFolder, "plot_" + plotId + ".yml");
        if (saleFile.exists()) {
            saleFile.delete();
        }
        return removed;
    }

    public boolean buyPlot(UUID buyerUuid, int plotId) {
        processEndedAuctions();
        PlotSale sale = activeSales.get(plotId);
        if (sale == null || sale.getSaleType() != PlotSale.SaleType.FIXED || sale.getSellerUuid().equals(buyerUuid)) {
            return false;
        }
        if (!economyHook.hasEconomy() || economyHook.getBalance(buyerUuid) < sale.getPrice()) {
            return false;
        }
        if (!economyHook.withdraw(buyerUuid, sale.getPrice())) {
            return false;
        }
        if (!economyHook.deposit(sale.getSellerUuid(), sale.getPrice())) {
            economyHook.deposit(buyerUuid, sale.getPrice());
            return false;
        }
        boolean transferred = transferOwnership(plotId, sale.getSellerUuid(), buyerUuid);
        if (!transferred) {
            economyHook.deposit(buyerUuid, sale.getPrice());
            economyHook.withdraw(sale.getSellerUuid(), sale.getPrice());
            return false;
        }
        cancelSale(plotId);
        return true;
    }

    public boolean placeBid(UUID bidderUuid, int plotId, double amount) {
        processEndedAuctions();
        PlotSale sale = activeSales.get(plotId);
        if (sale == null || sale.getSaleType() != PlotSale.SaleType.AUCTION) {
            return false;
        }
        if (Instant.now().toEpochMilli() >= sale.getAuctionEnds() || amount <= sale.getHighestBid() || amount < sale.getPrice()) {
            return false;
        }
        if (!economyHook.hasEconomy() || economyHook.getBalance(bidderUuid) < amount) {
            return false;
        }
        PlotSale updated = sale.withBid(amount, bidderUuid);
        activeSales.put(plotId, updated);
        saveSale(updated);
        return true;
    }

    public void processEndedAuctions() {
        long now = Instant.now().toEpochMilli();
        List<PlotSale> ended = new ArrayList<>();
        for (PlotSale sale : activeSales.values()) {
            if (sale.getSaleType() == PlotSale.SaleType.AUCTION && sale.getAuctionEnds() <= now) {
                ended.add(sale);
            }
        }
        for (PlotSale sale : ended) {
            if (sale.getHighestBidder() != null
                    && economyHook.withdraw(sale.getHighestBidder(), sale.getHighestBid())
                    && economyHook.deposit(sale.getSellerUuid(), sale.getHighestBid())) {
                transferOwnership(sale.getPlotId(), sale.getSellerUuid(), sale.getHighestBidder());
            }
            cancelSale(sale.getPlotId());
        }
    }

    private boolean transferOwnership(int numericPlotId, UUID expectedSeller, UUID buyerUuid) {
        for (Plot plot : plotStorage.getAllPlots()) {
            Integer numericId = toNumericId(plot.getPlotId());
            if (numericId != null && numericId == numericPlotId) {
                backupPlotYaml(numericId);
                return plotStorage.transferOwnership(plot.getPlotId(), expectedSeller, buyerUuid) != null;
            }
        }
        return false;
    }

    private Integer toNumericId(String rawPlotId) {
        if (rawPlotId == null) {
            return null;
        }
        if (rawPlotId.matches("\\d+")) {
            return Integer.parseInt(rawPlotId);
        }
        Matcher matcher = NUM_SUFFIX.matcher(rawPlotId);
        if (matcher.matches()) {
            return Integer.parseInt(matcher.group(1));
        }
        return null;
    }

    private void backupPlotYaml(int plotId) {
        File plotFile = new File(plugin.getDataFolder(), "plots/plot_" + plotId + ".yml");
        if (!plotFile.exists()) {
            return;
        }
        File backup = new File(plotFile.getParentFile(), "plot_" + plotId + ".yml.bak." + System.currentTimeMillis());
        try {
            Files.copy(plotFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed market backup for " + plotFile.getName() + ": " + e.getMessage());
        }
    }

    private void loadSales() {
        File[] files = marketFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            PlotSale sale = PlotSale.fromMap(config.getValues(false));
            activeSales.put(sale.getPlotId(), sale);
        }
    }

    private void saveSale(PlotSale sale) {
        File file = new File(marketFolder, "plot_" + sale.getPlotId() + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, Object> entry : sale.toMap().entrySet()) {
            config.set(entry.getKey(), entry.getValue());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save market sale for plot " + sale.getPlotId() + ": " + e.getMessage());
        }
    }
}
