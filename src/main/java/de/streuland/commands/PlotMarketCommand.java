package de.streuland.commands;

import de.streuland.market.MarketListing;
import de.streuland.market.MarketManager;
import de.streuland.market.PlotMarketFilter;
import de.streuland.market.PlotMarketService;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PlotMarketCommand {
    private static final long CONFIRM_WINDOW_MS = 20_000L;

    private record PendingBuy(String plotId, double quotedPrice, long expiresAt) {}

    private final PlotManager plotManager;
    private final PlotMarketService marketService;
    private final Map<UUID, PendingBuy> pendingBuys = new HashMap<>();
    private final PlotMarketFilter marketFilter = new PlotMarketFilter();

    public PlotMarketCommand(PlotManager plotManager, PlotMarketService marketService) {
        this.plotManager = plotManager;
        this.marketService = marketService;
    }

    // Backward-compatible constructor for partially migrated bootstrap code.
    public PlotMarketCommand(PlotManager plotManager, MarketManager ignoredLegacyManager) {
        this.plotManager = plotManager;
        this.marketService = null;
    }

    public boolean handle(Player player, String[] args, boolean economyEnabled) {
        if (!economyEnabled) {
            player.sendMessage("§cEconomy disabled (Vault not available).");
            return true;
        }
        if (marketService == null) {
            player.sendMessage("§cPlot market service is not initialized.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("§cUsage: /plot <sell|unsell|market|buy>");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "sell" -> sell(player, args);
            case "unsell" -> unsell(player, args);
            case "market" -> market(player, args);
            case "buy" -> buy(player, args);
            default -> false;
        };
    }

    private boolean sell(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /plot sell <price>");
            return true;
        }
        double price = parseDouble(args[1]);
        if (price <= 0D) {
            player.sendMessage("§cInvalid price.");
            return true;
        }

        Plot plot = currentOwnedPlot(player);
        if (plot == null) {
            player.sendMessage("§cYou must stand on your own plot.");
            return true;
        }

        PlotMarketService.ListResult result = marketService.listPlot(plot, player.getUniqueId(), price);
        switch (result) {
            case OK -> player.sendMessage("§aListed " + plot.getPlotId() + " for " + format(price));
            case ALREADY_LISTED -> player.sendMessage("§cThis plot is already listed.");
            case INVALID_PRICE -> player.sendMessage("§cInvalid price.");
            case NOT_OWNER -> player.sendMessage("§cYou are not the owner.");
            case PROTECTED_PLOT -> player.sendMessage("§cProtected/system plots cannot be sold.");
        }
        return true;
    }

    private boolean unsell(Player player, String[] args) {
        Plot owned = currentOwnedPlot(player);
        if (owned == null) {
            player.sendMessage("§cYou must stand on your own listed plot to unsell it.");
            return true;
        }
        boolean removed = marketService.unlistPlot(owned.getPlotId(), player.getUniqueId());
        player.sendMessage(removed ? "§aListing removed." : "§cNo active listing found for this plot.");
        return true;
    }

    private boolean market(Player player, String[] args) {
        List<MarketListing> listings = marketService.getListingsSnapshot();
        PlotMarketFilter.Query query = parseMarketQuery(args);
        List<MarketListing> filtered = marketFilter.apply(listings, query);
        if (filtered.isEmpty()) {
            player.sendMessage("§7No market listings matched the current filter.");
            return true;
        }
        player.sendMessage("§6=== Plot Market ===");
        player.sendMessage("§7Filters: min=" + nullable(query.minPrice()) + " max=" + nullable(query.maxPrice())
                + " sort=" + query.sort().name().toLowerCase(Locale.ROOT)
                + " limit=" + query.limit());
        for (MarketListing listing : filtered) {
            player.sendMessage("§e" + listing.getPlotId() + " §7- §f" + format(listing.getPrice()));
        }
        return true;
    }

    private boolean buy(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /plot buy <plotId>");
            return true;
        }

        String plotId = normalizePlotId(args[1]);
        if (plotId == null) {
            player.sendMessage("§cUnknown plot id.");
            return true;
        }

        MarketListing listing = marketService.getListing(plotId);
        if (listing == null) {
            player.sendMessage("§cThat plot is no longer listed.");
            pendingBuys.remove(player.getUniqueId());
            return true;
        }

        PendingBuy pending = pendingBuys.get(player.getUniqueId());
        if (listing.getPrice() >= marketService.getConfirmationThreshold()
                && (pending == null || pending.expiresAt < System.currentTimeMillis()
                || !pending.plotId.equals(plotId)
                || Math.abs(pending.quotedPrice - listing.getPrice()) > 0.009D)) {
            pendingBuys.put(player.getUniqueId(), new PendingBuy(plotId, listing.getPrice(), System.currentTimeMillis() + CONFIRM_WINDOW_MS));
            player.sendMessage("§eThis purchase is expensive (" + format(listing.getPrice()) + ").");
            player.sendMessage("§eRepeat /plot buy " + plotId + " within 20 seconds to confirm.");
            return true;
        }

        PlotMarketService.BuyOutcome outcome = marketService.buyPlot(player.getUniqueId(), plotId);
        pendingBuys.remove(player.getUniqueId());

        switch (outcome.result()) {
            case OK -> {
                player.sendMessage("§aPurchase complete: " + plotId + " for " + format(outcome.chargedAmount()));
                player.sendMessage("§7Tax/Fee: " + format(outcome.taxAmount()) + " | Seller payout: " + format(outcome.sellerPayout()));
            }
            case OWN_LISTING -> player.sendMessage("§cYou cannot buy your own listing.");
            case INSUFFICIENT_FUNDS -> player.sendMessage("§cInsufficient funds. Need: " + format(outcome.chargedAmount()));
            case STALE_LISTING -> player.sendMessage("§cListing became invalid (seller changed/offline transfer). It was removed.");
            case TRANSFER_FAILED -> player.sendMessage("§cOwnership transfer failed; transaction was reverted.");
            case PAYMENT_FAILED -> player.sendMessage("§cPayment failed; no ownership change was made.");
            case LISTING_NOT_FOUND -> player.sendMessage("§cThat plot is no longer listed.");
            case WRONG_LISTING_TYPE -> player.sendMessage("§cThat listing is not a purchase listing.");
            case APPROVAL_REQUIRED -> player.sendMessage("§eThis purchase requires staff approval.");
        }
        return true;
    }

    private PlotMarketFilter.Query parseMarketQuery(String[] args) {
        Double minPrice = null;
        Double maxPrice = null;
        String plotIdContains = null;
        PlotMarketFilter.Sort sort = PlotMarketFilter.Sort.PRICE_ASC;
        int limit = 25;

        for (int i = 1; i < args.length; i++) {
            String token = args[i];
            if (token.startsWith("min=")) {
                minPrice = parseOptionalDouble(token.substring(4));
            } else if (token.startsWith("max=")) {
                maxPrice = parseOptionalDouble(token.substring(4));
            } else if (token.startsWith("id=")) {
                plotIdContains = token.substring(3).trim();
            } else if (token.startsWith("sort=")) {
                sort = PlotMarketFilter.Sort.fromInput(token.substring(5));
            } else if (token.startsWith("limit=")) {
                Integer parsedLimit = parseOptionalInt(token.substring(6));
                if (parsedLimit != null) {
                    limit = parsedLimit;
                }
            }
        }
        return new PlotMarketFilter.Query(minPrice, maxPrice, plotIdContains, sort, limit);
    }

    private Double parseOptionalDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Integer parseOptionalInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String nullable(Double value) {
        return value == null ? "-" : format(value);
    }

    private Plot currentOwnedPlot(Player player) {
        Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        UUID owner = plot == null ? null : plot.getOwner();
        return owner != null && owner.equals(player.getUniqueId()) ? plot : null;
    }

    private String normalizePlotId(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        Plot direct = plotManager.getStorage().getPlot(input);
        if (direct != null) {
            return direct.getPlotId();
        }
        for (Plot plot : plotManager.getStorage().getAllPlots()) {
            if (plot.getPlotId().equalsIgnoreCase(input)) {
                return plot.getPlotId();
            }
            String numericSuffix = plot.getPlotId().replaceAll("^.*_(\\d+)$", "$1");
            if (numericSuffix.equalsIgnoreCase(input)) {
                return plot.getPlotId();
            }
        }
        return null;
    }

    private double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return -1D;
        }
    }

    private String format(double amount) {
        return String.format(Locale.US, "%.2f$", amount);
    }
}
