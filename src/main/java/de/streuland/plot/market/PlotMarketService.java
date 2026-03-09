package de.streuland.plot.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.streuland.analytics.PlotAnalyticsRecord;
import de.streuland.analytics.PlotAnalyticsService;
import de.streuland.district.District;
import de.streuland.district.DistrictManager;
import de.streuland.discord.DiscordNotifier;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.pricing.PricingEngine;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Marketplace service for player driven plot transactions.
 */
public class PlotMarketService {
    private static final int MIN_LIST_SIZE = 5;
    private static final int MAX_LIST_SIZE = 50;
    private static final double FEE_RATE = 0.05D;
    private static final long EXPIRY_MILLIS = Duration.ofDays(30).toMillis();

    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final DistrictManager districtManager;
    private final PlotAnalyticsService analyticsService;
    private final Economy economy;
    private final PricingEngine pricingEngine;
    private final Gson gson;
    private final DiscordNotifier discordNotifier;
    private final java.io.File marketFile;

    private final Map<String, MarketListing> listings;
    private final List<MarketSale> salesHistory;

    public PlotMarketService(JavaPlugin plugin,
                             PlotManager plotManager,
                             DistrictManager districtManager,
                             PlotAnalyticsService analyticsService,
                             Economy economy, DiscordNotifier discordNotifier) {
                             Economy economy,
                             PricingEngine pricingEngine) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.districtManager = districtManager;
        this.analyticsService = analyticsService;
        this.economy = economy;
        this.pricingEngine = pricingEngine;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.discordNotifier = discordNotifier;
        this.marketFile = new java.io.File(plugin.getDataFolder(), "market.json");
        this.listings = new HashMap<>();
        this.salesHistory = new ArrayList<>();
        load();
    }

    public boolean isEconomyReady() {
        return economy != null;
    }

    public boolean handleMarketCommand(Player player, String[] args) {
        if (!isEconomyReady()) {
            player.sendMessage("§cVault/Economy nicht verfügbar. Markt ist deaktiviert.");
            return true;
        }
        pruneExpiredListings();

        if (args.length < 2) {
            player.sendMessage("§cVerwendung: /plot market <list|sell|buy|history>");
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list":
                return handleList(player, args);
            case "sell":
                return handleSell(player, args);
            case "buy":
                return handleBuy(player, args);
            case "history":
                return handleHistory(player, args);
            default:
                player.sendMessage("§cVerwendung: /plot market <list|sell|buy|history>");
                return true;
        }
    }

    private boolean handleList(Player player, String[] args) {
        int limit = 10;
        String sortBy = "price";
        if (args.length >= 3) {
            try {
                limit = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sortBy = args[2].toLowerCase(Locale.ROOT);
            }
        }
        if (args.length >= 4) {
            sortBy = args[3].toLowerCase(Locale.ROOT);
        }
        limit = Math.max(MIN_LIST_SIZE, Math.min(MAX_LIST_SIZE, limit));

        Comparator<MarketListing> comparator;
        switch (sortBy) {
            case "biome":
                comparator = Comparator.comparing(MarketListing::getBiome).thenComparingDouble(MarketListing::getPrice);
                break;
            case "level":
                comparator = Comparator.comparingInt(MarketListing::getLevel).reversed().thenComparingDouble(MarketListing::getPrice);
                break;
            case "price":
            default:
                comparator = Comparator.comparingDouble(MarketListing::getPrice);
                sortBy = "price";
                break;
        }

        List<MarketListing> visible = listings.values().stream()
                .sorted(comparator)
                .limit(limit)
                .collect(Collectors.toList());

        if (visible.isEmpty()) {
            player.sendMessage("§7Keine aktiven Plot-Angebote verfügbar.");
            return true;
        }

        player.sendMessage("§6=== Plot Markt (Sortierung: " + sortBy + ") ===");
        for (MarketListing listing : visible) {
            player.sendMessage("§e" + listing.getPlotId() + " §7| §f" + formatMoney(listing.getPrice())
                    + " §7| §b" + listing.getBiome()
                    + " §7| Level §a" + listing.getLevel()
                    + " §7| Wert " + formatMoney(listing.getValuation()));
        }
        return true;
    }

    private boolean handleSell(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /plot market sell <price>");
            return true;
        }

        Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (plot == null || plot.getOwner() == null || !plot.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§cDu musst auf deinem eigenen Plot stehen.");
            return true;
        }

        double price;
        try {
            price = Double.parseDouble(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cUngültiger Preis.");
            return true;
        }

        if (price <= 0D) {
            player.sendMessage("§cPreis muss größer als 0 sein.");
            return true;
        }

        if (listings.containsKey(plot.getPlotId())) {
            player.sendMessage("§cDieser Plot ist bereits im Marktplatz gelistet.");
            return true;
        }

        int level = resolveLevel(plot);
        int districtTier = resolveDistrictTier(plot);
        String biome = resolveBiome(plot).name();
        double valuation = calculateValuation(level, districtTier, biome);

        MarketListing listing = new MarketListing(plot.getPlotId(), player.getUniqueId(), null, price, biome, level, districtTier,
                valuation, Instant.now().toEpochMilli());
        listings.put(plot.getPlotId(), listing);
        save();

        player.sendMessage("§aPlot " + plot.getPlotId() + " wurde für " + formatMoney(price) + " gelistet.");
        player.sendMessage("§7Geschätzter Wert: " + formatMoney(valuation) + " (Level*Tier*Biome)");
        return true;
    }

    private boolean handleBuy(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /plot market buy <plot_id>");
            return true;
        }

        String plotId = args[2];
        MarketListing listing = listings.get(plotId);
        if (listing == null) {
            player.sendMessage("§cKein aktives Angebot für Plot " + plotId + ".");
            return true;
        }

        if (player.getUniqueId().equals(listing.getSellerId())) {
            player.sendMessage("§cDu kannst dein eigenes Listing nicht kaufen.");
            return true;
        }

        Plot plot = plotManager.getStorage().getPlot(plotId);
        if (plot == null || plot.getOwner() == null || !plot.getOwner().equals(listing.getSellerId())) {
            listings.remove(plotId);
            save();
            player.sendMessage("§cListing war veraltet und wurde entfernt.");
            return true;
        }

        OfflinePlayer buyer = player;
        OfflinePlayer seller = Bukkit.getOfflinePlayer(listing.getSellerId());
        double price = listing.getPrice();
        double fee = round(price * FEE_RATE);
        double sellerPayout = round(price - fee);

        EconomyResponse withdrawResponse = economy.withdrawPlayer(buyer, price);
        if (!withdrawResponse.transactionSuccess()) {
            player.sendMessage("§cNicht genug Guthaben. Benötigt: " + formatMoney(price));
            return true;
        }

        EconomyResponse sellerResponse = economy.depositPlayer(seller, sellerPayout);
        if (!sellerResponse.transactionSuccess()) {
            economy.depositPlayer(buyer, price);
            player.sendMessage("§cTransaktion fehlgeschlagen (Verkäuferauszahlung). Abgebrochen.");
            return true;
        }

        boolean transferred = plotManager.transferPlotOwnership(plotId, listing.getSellerId(), player.getUniqueId());
        if (!transferred) {
            economy.depositPlayer(buyer, price);
            economy.withdrawPlayer(seller, sellerPayout);
            player.sendMessage("§cPlot konnte nicht übertragen werden. Geld wurde zurückerstattet.");
            return true;
        }

        listings.remove(plotId);
        MarketSale sale = new MarketSale(plotId, listing.getSellerId(), player.getUniqueId(), price, fee,
                Instant.now().toEpochMilli(), listing.getBiome(), listing.getLevel(), listing.getDistrictTier());
        salesHistory.add(sale);
        if (pricingEngine != null) {
            pricingEngine.recordSale(plotId, price);
        }
        save();

        analyticsService.record(new PlotAnalyticsRecord(plotId, player.getUniqueId(), "MARKET_SALE", Instant.now(), price));

        Map<String, Object> extras = new HashMap<>();
        extras.put("title", "Auction finished");
        extras.put("description", plotId + " sold for " + formatMoney(price));
        discordNotifier.sendWebhook("plot-market", "Plot auction finished: " + plotId, extras);

        player.sendMessage("§aPlot gekauft: " + plotId + " für " + formatMoney(price));
        player.sendMessage("§7Gebühr: " + formatMoney(fee) + " (5%), Verkäufer erhielt " + formatMoney(sellerPayout));
        return true;
    }

    private boolean handleHistory(Player player, String[] args) {
        String plotId;
        if (args.length >= 3) {
            plotId = args[2];
        } else {
            Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
            if (plot == null) {
                player.sendMessage("§cVerwendung: /plot market history <plot_id> (oder auf einem Plot stehen)");
                return true;
            }
            plotId = plot.getPlotId();
        }

        List<MarketSale> sales = salesHistory.stream()
                .filter(sale -> sale.getPlotId().equalsIgnoreCase(plotId))
                .sorted(Comparator.comparingLong(MarketSale::getTimestamp))
                .collect(Collectors.toList());

        if (sales.isEmpty()) {
            player.sendMessage("§7Keine Verkaufshistorie für " + plotId + ".");
            return true;
        }

        player.sendMessage("§6=== Markt-Historie: " + plotId + " ===");
        double firstPrice = sales.get(0).getPrice();
        double lastPrice = sales.get(sales.size() - 1).getPrice();
        double trend = firstPrice <= 0D ? 0D : ((lastPrice - firstPrice) / firstPrice) * 100D;
        player.sendMessage("§7Preis-Trend: " + String.format(Locale.US, "%.2f", trend) + "%");

        int flips = 0;
        for (int i = 1; i < sales.size(); i++) {
            if (sales.get(i).getSellerId().equals(sales.get(i - 1).getBuyerId())
                    && (sales.get(i).getTimestamp() - sales.get(i - 1).getTimestamp()) < Duration.ofDays(7).toMillis()) {
                flips++;
            }
        }
        player.sendMessage("§7Flip-Detektion: " + (flips > 0 ? "§c" + flips + " schnelle Resales" : "§aKeine Auffälligkeiten"));

        int start = Math.max(0, sales.size() - 8);
        for (int i = start; i < sales.size(); i++) {
            MarketSale sale = sales.get(i);
            String when = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(sale.getTimestamp()));
            player.sendMessage("§e" + when + " §7- §f" + formatMoney(sale.getPrice()) + " §8(" + shortId(sale.getSellerId())
                    + " -> " + shortId(sale.getBuyerId()) + ")");
        }
        return true;
    }

    public synchronized List<MarketListing> getActiveListingsSnapshot() {
        pruneExpiredListings();
        return new ArrayList<>(listings.values());
    }

    public synchronized List<MarketSale> getSalesHistorySnapshot() {
        return new ArrayList<>(salesHistory);
    }

    public void pruneExpiredListings() {
        long now = System.currentTimeMillis();
        boolean changed = listings.values().removeIf(listing -> (now - listing.getTimestamp()) > EXPIRY_MILLIS);
        if (changed) {
            save();
        }
    }

    private int resolveLevel(Plot plot) {
        if (plot == null) {
            return 1;
        }
        Number level = plotManager.getStorage().getPlotData(plot.getPlotId()).getStatBonuses().get("plotLevel");
        return Math.max(1, level == null ? 1 : level.intValue());
    }

    private int resolveDistrictTier(Plot plot) {
        District district = districtManager.getDistrictForPlot(plot);
        return district == null ? 1 : district.getLevel().ordinal() + 1;
    }

    private Biome resolveBiome(Plot plot) {
        return plotManager.getWorld().getBlockAt(plot.getCenterX(), plot.getSpawnY(), plot.getCenterZ()).getBiome();
    }

    public double calculateValuation(int level, int districtTier, String biome) {
        return round(level * districtTier * biomeMultiplier(biome));
    }

    private double biomeMultiplier(String biomeName) {
        if (biomeName == null) {
            return 1.0D;
        }
        String biome = biomeName.toUpperCase(Locale.ROOT);
        if (biome.contains("MUSHROOM") || biome.contains("BADLANDS")) {
            return 2.3D;
        }
        if (biome.contains("JUNGLE") || biome.contains("CHERRY") || biome.contains("MEADOW")) {
            return 1.7D;
        }
        if (biome.contains("DESERT") || biome.contains("SNOW") || biome.contains("ICE")) {
            return 1.3D;
        }
        if (biome.contains("OCEAN") || biome.contains("SWAMP")) {
            return 0.9D;
        }
        return 1.1D;
    }

    private void load() {
        if (!marketFile.exists()) {
            save();
            return;
        }
        try {
            String raw = new String(Files.readAllBytes(marketFile.toPath()), StandardCharsets.UTF_8);
            Type stateType = new TypeToken<MarketState>() { }.getType();
            MarketState state = gson.fromJson(raw, stateType);
            listings.clear();
            salesHistory.clear();
            if (state != null) {
                if (state.activeListings != null) {
                    for (MarketListing listing : state.activeListings) {
                        listings.put(listing.getPlotId(), listing);
                    }
                }
                if (state.salesHistory != null) {
                    salesHistory.addAll(state.salesHistory);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte market.json nicht laden: " + e.getMessage());
        }
    }

    private void save() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            MarketState state = new MarketState();
            state.activeListings = new ArrayList<>(listings.values());
            state.salesHistory = new ArrayList<>(salesHistory);
            Files.write(marketFile.toPath(), gson.toJson(state).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte market.json nicht speichern: " + e.getMessage());
        }
    }

    private String formatMoney(double amount) {
        return String.format(Locale.US, "%.2f$", amount);
    }

    private double round(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    private static class MarketState {
        private List<MarketListing> activeListings;
        private List<MarketSale> salesHistory;
    }
}
