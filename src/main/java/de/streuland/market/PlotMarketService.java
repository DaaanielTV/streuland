package de.streuland.market;

import de.streuland.economy.PlotEconomyHook;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;
import de.streuland.plot.PlotStorage;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Player-driven plot market service with YAML persistence and safe ownership transfer.
 */
public class PlotMarketService {
    public enum ListResult {
        OK,
        INVALID_PRICE,
        ALREADY_LISTED,
        NOT_OWNER,
        PROTECTED_PLOT
    }

    public enum BuyResult {
        OK,
        LISTING_NOT_FOUND,
        OWN_LISTING,
        INSUFFICIENT_FUNDS,
        TRANSFER_FAILED,
        STALE_LISTING,
        PAYMENT_FAILED
    }

    public record BuyOutcome(BuyResult result, double chargedAmount, double sellerPayout, double taxAmount) {}

    private final JavaPlugin plugin;
    private final PlotStorage plotStorage;
    private final PlotEconomyHook economyHook;
    private final Map<String, MarketListing> activeListings = new ConcurrentHashMap<>();
    private final File persistenceFile;

    public PlotMarketService(JavaPlugin plugin, PlotStorage plotStorage, PlotEconomyHook economyHook) {
        this.plugin = plugin;
        this.plotStorage = plotStorage;
        this.economyHook = economyHook;
        File marketDir = new File(plugin.getDataFolder(), "market");
        if (!marketDir.exists()) {
            marketDir.mkdirs();
        }
        this.persistenceFile = new File(marketDir, "listings.yml");
        load();
    }

    public synchronized ListResult listPlot(Plot plot, UUID sellerId, double price) {
        if (plot == null || sellerId == null || plot.getOwner() == null || !plot.getOwner().equals(sellerId)) {
            return ListResult.NOT_OWNER;
        }
        if (price <= 0D || Double.isNaN(price) || Double.isInfinite(price)) {
            return ListResult.INVALID_PRICE;
        }
        if (isProtectedOrSystemPlot(plot)) {
            return ListResult.PROTECTED_PLOT;
        }
        if (activeListings.containsKey(plot.getPlotId())) {
            return ListResult.ALREADY_LISTED;
        }

        MarketListing listing = new MarketListing(plot.getPlotId(), sellerId, round(price), System.currentTimeMillis());
        activeListings.put(plot.getPlotId(), listing);
        save();
        return ListResult.OK;
    }

    public synchronized boolean unlistPlot(String plotId, UUID actorId) {
        MarketListing listing = activeListings.get(plotId);
        if (listing == null || actorId == null || !actorId.equals(listing.getSellerId())) {
            return false;
        }
        activeListings.remove(plotId);
        save();
        return true;
    }

    public synchronized List<MarketListing> getListingsSnapshot() {
        Collection<MarketListing> values = new ArrayList<>(activeListings.values());
        return values.stream()
                .sorted(Comparator.comparingDouble(MarketListing::getPrice).thenComparing(MarketListing::getPlotId))
                .collect(Collectors.toList());
    }

    public synchronized MarketListing getListing(String plotId) {
        return activeListings.get(plotId);
    }

    public synchronized BuyOutcome buyPlot(UUID buyerId, String plotId) {
        MarketListing listing = activeListings.get(plotId);
        if (listing == null) {
            return new BuyOutcome(BuyResult.LISTING_NOT_FOUND, 0D, 0D, 0D);
        }
        if (listing.getSellerId().equals(buyerId)) {
            return new BuyOutcome(BuyResult.OWN_LISTING, 0D, 0D, 0D);
        }

        Plot plot = plotStorage.getPlot(plotId);
        if (plot == null || plot.getOwner() == null || !plot.getOwner().equals(listing.getSellerId())) {
            activeListings.remove(plotId);
            save();
            return new BuyOutcome(BuyResult.STALE_LISTING, 0D, 0D, 0D);
        }

        double price = listing.getPrice();
        double tax = round(getTaxFor(price));
        double payout = round(price - tax);

        if (!economyHook.hasEconomy() || economyHook.getBalance(buyerId) < price) {
            return new BuyOutcome(BuyResult.INSUFFICIENT_FUNDS, price, payout, tax);
        }
        if (!economyHook.withdraw(buyerId, price)) {
            return new BuyOutcome(BuyResult.PAYMENT_FAILED, price, payout, tax);
        }
        if (!economyHook.deposit(listing.getSellerId(), payout)) {
            economyHook.deposit(buyerId, price);
            return new BuyOutcome(BuyResult.PAYMENT_FAILED, price, payout, tax);
        }

        Plot transferred = plotStorage.transferOwnership(plotId, listing.getSellerId(), buyerId);
        if (transferred == null) {
            economyHook.deposit(buyerId, price);
            economyHook.withdraw(listing.getSellerId(), payout);
            return new BuyOutcome(BuyResult.TRANSFER_FAILED, price, payout, tax);
        }

        activeListings.remove(plotId);
        save();
        return new BuyOutcome(BuyResult.OK, price, payout, tax);
    }

    public double getConfirmationThreshold() {
        return plugin.getConfig().getDouble("market.confirmation-threshold", 25000D);
    }

    private boolean isProtectedOrSystemPlot(Plot plot) {
        PlotData data = plotStorage.getPlotData(plot.getPlotId());
        if (data == null) {
            return false;
        }
        Map<String, Boolean> overrides = data.getFlagOverrides();
        boolean flaggedProtected = Boolean.TRUE.equals(overrides.get("market_protected"))
                || Boolean.TRUE.equals(overrides.get("protected"))
                || Boolean.TRUE.equals(overrides.get("system_plot"));
        Set<String> blockedIds = Set.copyOf(plugin.getConfig().getStringList("market.blocked-plot-ids"));
        return flaggedProtected || blockedIds.contains(plot.getPlotId());
    }

    private double getTaxFor(double salePrice) {
        double taxRate = plugin.getConfig().getDouble("market.tax-rate", 0.05D);
        double flatFee = plugin.getConfig().getDouble("market.flat-fee", 0D);
        return (salePrice * Math.max(0D, taxRate)) + Math.max(0D, flatFee);
    }

    private void load() {
        if (!persistenceFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(persistenceFile);
        activeListings.clear();
        if (!yaml.isConfigurationSection("listings")) {
            return;
        }
        for (String key : yaml.getConfigurationSection("listings").getKeys(false)) {
            String base = "listings." + key;
            String plotId = yaml.getString(base + ".plotId");
            String sellerRaw = yaml.getString(base + ".sellerId");
            if (plotId == null || sellerRaw == null) {
                continue;
            }
            try {
                UUID sellerId = UUID.fromString(sellerRaw);
                double price = yaml.getDouble(base + ".price", 0D);
                long createdAt = yaml.getLong(base + ".createdAt", System.currentTimeMillis());
                if (price > 0D) {
                    activeListings.put(plotId, new MarketListing(plotId, sellerId, price, createdAt));
                }
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping invalid market listing for key " + key);
            }
        }
    }

    private void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        int i = 0;
        for (MarketListing listing : activeListings.values()) {
            String base = "listings." + i++;
            yaml.set(base + ".plotId", listing.getPlotId());
            yaml.set(base + ".sellerId", listing.getSellerId().toString());
            yaml.set(base + ".price", listing.getPrice());
            yaml.set(base + ".createdAt", listing.getCreatedAt());
        }
        try {
            yaml.save(persistenceFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not persist market listings: " + e.getMessage());
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
