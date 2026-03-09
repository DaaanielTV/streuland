package de.streuland.pricing;

import de.streuland.neighborhood.NeighborhoodService;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PricingEngine {
    private static final Duration DEFAULT_SALES_LOOKBACK = Duration.ofDays(14);
    private static final double MIN_PRICE = 1.0D;

    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final NeighborhoodService neighborhoodService;
    private final File salesHistoryFile;
    private final List<SaleRecord> salesHistory;

    public PricingEngine(JavaPlugin plugin, PlotManager plotManager, NeighborhoodService neighborhoodService) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.neighborhoodService = neighborhoodService;
        File marketDir = new File(plugin.getDataFolder(), "market");
        if (!marketDir.exists()) {
            marketDir.mkdirs();
        }
        this.salesHistoryFile = new File(marketDir, "sales_history.yml");
        this.salesHistory = new ArrayList<>();
        loadSalesHistory();
    }

    public double estimatePrice(int plotId) {
        String token = "_" + plotId;
        for (Plot plot : plotManager.getAllPlots()) {
            if (plot.getPlotId().endsWith(token) || plot.getPlotId().equals(String.valueOf(plotId))) {
                return estimatePrice(plot.getPlotId());
            }
        }
        return getBasePrice();
    }

    public double estimatePrice(String plotId) {
        return buildEstimate(plotId).getEstimatedPrice();
    }

    public PriceEstimate buildEstimate(String plotId) {
        Plot plot = plotManager.getStorage(plotManager.getWorldForPlot(plotId)).getPlot(plotId);
        if (plot == null) {
            return new PriceEstimate(plotId, getBasePrice(), getBasePrice(), 1.0D, 1.0D, 1.0D, 1.0D, 0.0D);
        }

        double basePrice = getBasePrice();
        double nearbySales = computeNearbySalesMultiplier(plot);
        double featuredBoost = computeFeaturedBoostMultiplier(plot);
        double spawnMultiplier = computeSpawnMultiplier(plot);
        double scarcityMultiplier = computeScarcityMultiplier(plotManager.getWorldForPlot(plotId));

        double estimated = Calculator.computePrice(basePrice, nearbySales, featuredBoost, spawnMultiplier, scarcityMultiplier);
        return new PriceEstimate(plotId, estimated, basePrice, nearbySales, featuredBoost, spawnMultiplier,
                scarcityMultiplier, countRecentNearbySales(plot));
    }

    public synchronized void recordSale(String plotId, double salePrice) {
        Plot plot = plotManager.getStorage(plotManager.getWorldForPlot(plotId)).getPlot(plotId);
        if (plot == null) {
            return;
        }
        salesHistory.add(new SaleRecord(plotId, plot.getCenterX(), plot.getCenterZ(), salePrice, Instant.now().toEpochMilli()));
        pruneOldSales();
        saveSalesHistory();
    }

    public synchronized boolean setFactor(String name, double value) {
        String key;
        switch (name.toLowerCase(Locale.ROOT)) {
            case "baseprice":
                key = "pricing.base-price";
                break;
            case "nearbysalesdecay":
                key = "pricing.nearby-sales-decay";
                break;
            case "featuredboost":
                key = "pricing.featured-boost";
                break;
            case "proximitytospawn":
            case "proximitytospawnmultiplier":
                key = "pricing.proximity-to-spawn-multiplier";
                break;
            case "scarcityfactor":
                key = "pricing.scarcity-factor";
                break;
            case "lookbackdays":
                key = "pricing.sales-lookback-days";
                break;
            default:
                return false;
        }
        plugin.getConfig().set(key, value);
        plugin.saveConfig();
        return true;
    }

    private double computeNearbySalesMultiplier(Plot plot) {
        double decayFactor = plugin.getConfig().getDouble("pricing.nearby-sales-decay", 0.25D);
        double sum = 0D;
        Instant now = Instant.now();
        long lookbackDays = Math.max(1L, plugin.getConfig().getLong("pricing.sales-lookback-days", DEFAULT_SALES_LOOKBACK.toDays()));
        double radius = Math.max(16D, plugin.getConfig().getDouble("pricing.nearby-sales-radius", 256D));
        for (SaleRecord record : snapshotSales()) {
            long ageDays = Duration.between(Instant.ofEpochMilli(record.timestamp), now).toDays();
            if (ageDays > lookbackDays) {
                continue;
            }
            double dist = Math.hypot(plot.getCenterX() - record.x, plot.getCenterZ() - record.z);
            if (dist > radius) {
                continue;
            }
            double ageWeight = Math.exp(-(double) ageDays / lookbackDays);
            double distWeight = Math.max(0D, 1D - (dist / radius));
            sum += ageWeight * distWeight;
        }
        return 1D + (sum * decayFactor);
    }

    private double countRecentNearbySales(Plot plot) {
        Instant now = Instant.now();
        long lookbackDays = Math.max(1L, plugin.getConfig().getLong("pricing.sales-lookback-days", DEFAULT_SALES_LOOKBACK.toDays()));
        double radius = Math.max(16D, plugin.getConfig().getDouble("pricing.nearby-sales-radius", 256D));
        int count = 0;
        for (SaleRecord record : snapshotSales()) {
            long ageDays = Duration.between(Instant.ofEpochMilli(record.timestamp), now).toDays();
            if (ageDays > lookbackDays) {
                continue;
            }
            double dist = Math.hypot(plot.getCenterX() - record.x, plot.getCenterZ() - record.z);
            if (dist <= radius) {
                count++;
            }
        }
        return count;
    }

    private double computeFeaturedBoostMultiplier(Plot plot) {
        double featuredBoost = plugin.getConfig().getDouble("pricing.featured-boost", 0.15D);
        double neighborhoodPopularity = estimateNeighborhoodPopularity(plot);
        return 1D + (featuredBoost * neighborhoodPopularity);
    }

    private double estimateNeighborhoodPopularity(Plot plot) {
        List<Plot> claimed = new ArrayList<>();
        for (Plot candidate : plotManager.getAllPlots(plotManager.getWorldForPlot(plot.getPlotId()))) {
            if (candidate.getOwner() != null) {
                claimed.add(candidate);
            }
        }
        if (claimed.isEmpty()) {
            return 0D;
        }

        int nearby = 0;
        for (Plot candidate : claimed) {
            if (candidate.getPlotId().equals(plot.getPlotId())) {
                continue;
            }
            if (plot.distance(candidate) <= NeighborhoodService.CLUSTER_DISTANCE_BLOCKS) {
                nearby++;
            }
        }
        int maxPossible = Math.max(1, claimed.size() - 1);
        return Math.min(1D, (double) nearby / maxPossible);
    }

    private double computeSpawnMultiplier(Plot plot) {
        World world = plotManager.getWorldForPlot(plot.getPlotId());
        Location spawn = world.getSpawnLocation();
        double dist = Math.hypot(plot.getCenterX() - spawn.getBlockX(), plot.getCenterZ() - spawn.getBlockZ());
        double maxDistance = Math.max(500D, plugin.getConfig().getDouble("pricing.spawn-falloff-distance", 5000D));
        double factor = plugin.getConfig().getDouble("pricing.proximity-to-spawn-multiplier", 0.35D);
        double normalized = Math.max(0D, 1D - (dist / maxDistance));
        return 1D + (normalized * factor);
    }

    private double computeScarcityMultiplier(World world) {
        double scarcityFactor = plugin.getConfig().getDouble("pricing.scarcity-factor", 0.6D);
        List<Plot> plots = plotManager.getAllPlots(world);
        if (plots.isEmpty()) {
            return 1D;
        }
        long available = plots.stream().filter(p -> p.getOwner() == null).count();
        double scarcity = 1D - ((double) available / (double) plots.size());
        return 1D + (scarcity * scarcityFactor);
    }

    private double getBasePrice() {
        return Math.max(MIN_PRICE, plugin.getConfig().getDouble("pricing.base-price", 1000D));
    }

    private synchronized List<SaleRecord> snapshotSales() {
        return new ArrayList<>(salesHistory);
    }

    private synchronized void loadSalesHistory() {
        salesHistory.clear();
        if (!salesHistoryFile.exists()) {
            return;
        }
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(salesHistoryFile);
        List<?> entries = cfg.getList("sales", Collections.emptyList());
        for (Object obj : entries) {
            if (!(obj instanceof Map)) {
                continue;
            }
            Map<?, ?> map = (Map<?, ?>) obj;
            String plotId = String.valueOf(map.get("plotId"));
            int x = toInt(map.get("x"));
            int z = toInt(map.get("z"));
            double price = toDouble(map.get("price"));
            long ts = toLong(map.get("timestamp"));
            salesHistory.add(new SaleRecord(plotId, x, z, price, ts));
        }
        pruneOldSales();
    }

    private int toInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : 0;
    }

    private long toLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private double toDouble(Object value) {
        return value instanceof Number ? ((Number) value).doubleValue() : 0D;
    }

    private synchronized void saveSalesHistory() {
        YamlConfiguration cfg = new YamlConfiguration();
        List<Map<String, Object>> serializable = new ArrayList<>();
        for (SaleRecord record : salesHistory) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("plotId", record.plotId);
            entry.put("x", record.x);
            entry.put("z", record.z);
            entry.put("price", record.salePrice);
            entry.put("timestamp", record.timestamp);
            serializable.add(entry);
        }
        cfg.set("sales", serializable);
        try {
            cfg.save(salesHistoryFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte sales_history.yml nicht speichern: " + e.getMessage());
        }
    }

    private synchronized void pruneOldSales() {
        long lookbackDays = Math.max(1L, plugin.getConfig().getLong("pricing.sales-lookback-days", DEFAULT_SALES_LOOKBACK.toDays()));
        long cutoff = Instant.now().minus(Duration.ofDays(lookbackDays * 2L)).toEpochMilli();
        salesHistory.removeIf(sale -> sale.timestamp < cutoff);
        salesHistory.sort(Comparator.comparingLong(s -> s.timestamp));
    }

    private static class SaleRecord {
        private final String plotId;
        private final int x;
        private final int z;
        private final double salePrice;
        private final long timestamp;

        private SaleRecord(String plotId, int x, int z, double salePrice, long timestamp) {
            this.plotId = plotId;
            this.x = x;
            this.z = z;
            this.salePrice = salePrice;
            this.timestamp = timestamp;
        }
    }

    public static final class PriceEstimate {
        private final String plotId;
        private final double estimatedPrice;
        private final double basePrice;
        private final double nearbySalesMultiplier;
        private final double featuredBoostMultiplier;
        private final double proximityMultiplier;
        private final double scarcityMultiplier;
        private final double recentNearbySalesCount;

        public PriceEstimate(String plotId, double estimatedPrice, double basePrice, double nearbySalesMultiplier,
                             double featuredBoostMultiplier, double proximityMultiplier, double scarcityMultiplier,
                             double recentNearbySalesCount) {
            this.plotId = plotId;
            this.estimatedPrice = estimatedPrice;
            this.basePrice = basePrice;
            this.nearbySalesMultiplier = nearbySalesMultiplier;
            this.featuredBoostMultiplier = featuredBoostMultiplier;
            this.proximityMultiplier = proximityMultiplier;
            this.scarcityMultiplier = scarcityMultiplier;
            this.recentNearbySalesCount = recentNearbySalesCount;
        }

        public String getPlotId() { return plotId; }
        public double getEstimatedPrice() { return estimatedPrice; }
        public double getBasePrice() { return basePrice; }
        public double getNearbySalesMultiplier() { return nearbySalesMultiplier; }
        public double getFeaturedBoostMultiplier() { return featuredBoostMultiplier; }
        public double getProximityMultiplier() { return proximityMultiplier; }
        public double getScarcityMultiplier() { return scarcityMultiplier; }
        public double getRecentNearbySalesCount() { return recentNearbySalesCount; }
    }

    public static final class Calculator {
        private Calculator() {
        }

        public static double computePrice(double basePrice, double nearbySalesMultiplier,
                                          double featuredBoostMultiplier, double proximityMultiplier,
                                          double scarcityMultiplier) {
            double value = basePrice * nearbySalesMultiplier * featuredBoostMultiplier * proximityMultiplier * scarcityMultiplier;
            return Math.max(MIN_PRICE, Math.round(value * 100.0D) / 100.0D);
        }
    }
}
