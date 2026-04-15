package de.streuland.market;

import de.streuland.approval.PlotApprovalActionType;
import de.streuland.approval.PlotApprovalWorkflowService;
import de.streuland.economy.PlotEconomyHook;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;
import de.streuland.plot.PlotStorage;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
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
        INVALID_DURATION,
        ALREADY_LISTED,
        NOT_OWNER,
        PROTECTED_PLOT
    }

    public enum BuyResult {
        OK,
        LISTING_NOT_FOUND,
        WRONG_LISTING_TYPE,
        OWN_LISTING,
        INSUFFICIENT_FUNDS,
        TRANSFER_FAILED,
        STALE_LISTING,
        PAYMENT_FAILED,
        APPROVAL_REQUIRED
    }

    public record BuyOutcome(BuyResult result, double chargedAmount, double sellerPayout, double taxAmount) {}

    private final JavaPlugin plugin;
    private final PlotStorage plotStorage;
    private final PlotEconomyHook economyHook;
    private final PlotApprovalWorkflowService approvalWorkflowService;
    private final Map<String, MarketListing> activeListings = new ConcurrentHashMap<>();
    private final Map<String, PlotRentalContract> activeContracts = new ConcurrentHashMap<>();
    private final PlotMarketPersistence persistence;

    public PlotMarketService(JavaPlugin plugin, PlotStorage plotStorage, PlotEconomyHook economyHook) {
        this(plugin, plotStorage, economyHook, null);
    }

    public PlotMarketService(JavaPlugin plugin,
                             PlotStorage plotStorage,
                             PlotEconomyHook economyHook,
                             PlotApprovalWorkflowService approvalWorkflowService) {
        this.plugin = plugin;
        this.plotStorage = plotStorage;
        this.economyHook = economyHook;
        this.approvalWorkflowService = approvalWorkflowService;
        this.persistence = new PlotMarketPersistence(new File(plugin.getDataFolder(), "market"), plugin.getLogger());
        load();
    }

    public synchronized ListResult listPlot(Plot plot, UUID sellerId, double price) {
        return listPlotForSale(plot, sellerId, price);
    }

    public synchronized ListResult listPlotForSale(Plot plot, UUID sellerId, double price) {
        return listPlotInternal(plot, sellerId, price, MarketListing.ListingType.SALE, 0);
    }

    public synchronized ListResult listPlotForRent(Plot plot, UUID sellerId, double rentPrice, int durationDays) {
        if (durationDays <= 0 || durationDays > 365) {
            return ListResult.INVALID_DURATION;
        }
        return listPlotInternal(plot, sellerId, rentPrice, MarketListing.ListingType.RENT, durationDays);
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

    public synchronized List<PlotRentalContract> getContractsSnapshot() {
        Collection<PlotRentalContract> values = new ArrayList<>(activeContracts.values());
        return values.stream()
                .sorted(Comparator.comparingLong(PlotRentalContract::getCreatedAt).reversed())
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
        if (listing.getListingType() != MarketListing.ListingType.SALE) {
            return new BuyOutcome(BuyResult.WRONG_LISTING_TYPE, 0D, 0D, 0D);
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
        if (approvalRequired(price)) {
            return new BuyOutcome(BuyResult.APPROVAL_REQUIRED, price, 0D, 0D);
        }
        return executeSale(buyerId, listing, price);
    }

    public synchronized BuyOutcome buyPlotWithApproval(Player buyer, String plotId) {
        BuyOutcome preview = buyPlot(buyer.getUniqueId(), plotId);
        if (preview.result() != BuyResult.APPROVAL_REQUIRED) {
            return preview;
        }
        if (approvalWorkflowService == null) {
            return new BuyOutcome(BuyResult.PAYMENT_FAILED, preview.chargedAmount(), 0D, 0D);
        }
        MarketListing listing = activeListings.get(plotId);
        if (listing == null) {
            return new BuyOutcome(BuyResult.LISTING_NOT_FOUND, 0D, 0D, 0D);
        }
        approvalWorkflowService.submit(
                buyer,
                PlotApprovalActionType.TRANSFER,
                plotId,
                Map.of("targetPlayerId", buyer.getUniqueId().toString(), "source", "plot-market")
        );
        return preview;
    }

    public synchronized BuyOutcome rentPlot(UUID tenantId, String plotId) {
        MarketListing listing = activeListings.get(plotId);
        if (listing == null) {
            return new BuyOutcome(BuyResult.LISTING_NOT_FOUND, 0D, 0D, 0D);
        }
        if (listing.getListingType() != MarketListing.ListingType.RENT) {
            return new BuyOutcome(BuyResult.WRONG_LISTING_TYPE, 0D, 0D, 0D);
        }
        if (listing.getSellerId().equals(tenantId)) {
            return new BuyOutcome(BuyResult.OWN_LISTING, 0D, 0D, 0D);
        }

        Plot plot = plotStorage.getPlot(plotId);
        if (plot == null || plot.getOwner() == null || !plot.getOwner().equals(listing.getSellerId())) {
            activeListings.remove(plotId);
            save();
            return new BuyOutcome(BuyResult.STALE_LISTING, 0D, 0D, 0D);
        }
        if (activeContracts.containsKey(plotId)) {
            return new BuyOutcome(BuyResult.TRANSFER_FAILED, 0D, 0D, 0D);
        }

        double rentPrice = listing.getPrice();
        double tax = round(getTaxFor(rentPrice));
        double payout = round(rentPrice - tax);

        if (!economyHook.hasEconomy() || economyHook.getBalance(tenantId) < rentPrice) {
            return new BuyOutcome(BuyResult.INSUFFICIENT_FUNDS, rentPrice, payout, tax);
        }
        if (!economyHook.withdraw(tenantId, rentPrice)) {
            return new BuyOutcome(BuyResult.PAYMENT_FAILED, rentPrice, payout, tax);
        }
        if (!economyHook.deposit(listing.getSellerId(), payout)) {
            economyHook.deposit(tenantId, rentPrice);
            return new BuyOutcome(BuyResult.PAYMENT_FAILED, rentPrice, payout, tax);
        }

        long now = System.currentTimeMillis();
        long endAt = now + (listing.getRentDurationDays() * 86_400_000L);
        PlotRentalContract contract = new PlotRentalContract(
                UUID.randomUUID().toString(),
                plotId,
                listing.getSellerId(),
                tenantId,
                rentPrice,
                now,
                now,
                endAt
        );

        activeContracts.put(plotId, contract);
        activeListings.remove(plotId);
        save();
        return new BuyOutcome(BuyResult.OK, rentPrice, payout, tax);
    }

    public double getConfirmationThreshold() {
        return plugin.getConfig().getDouble("market.confirmation-threshold", 25000D);
    }

    private boolean approvalRequired(double amount) {
        double threshold = plugin.getConfig().getDouble("market.approval-threshold", 100000D);
        return amount >= Math.max(0D, threshold);
    }

    private BuyOutcome executeSale(UUID buyerId, MarketListing listing, double price) {
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

        Plot transferred = plotStorage.transferOwnership(listing.getPlotId(), listing.getSellerId(), buyerId);
        if (transferred == null) {
            economyHook.deposit(buyerId, price);
            economyHook.withdraw(listing.getSellerId(), payout);
            return new BuyOutcome(BuyResult.TRANSFER_FAILED, price, payout, tax);
        }

        activeListings.remove(listing.getPlotId());
        save();
        return new BuyOutcome(BuyResult.OK, price, payout, tax);
    }

    private ListResult listPlotInternal(Plot plot,
                                        UUID sellerId,
                                        double price,
                                        MarketListing.ListingType listingType,
                                        int rentDurationDays) {
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

        MarketListing listing = new MarketListing(
                plot.getPlotId(),
                sellerId,
                round(price),
                System.currentTimeMillis(),
                listingType,
                rentDurationDays
        );
        activeListings.put(plot.getPlotId(), listing);
        save();
        return ListResult.OK;
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
        activeListings.clear();
        for (MarketListing listing : persistence.loadListings()) {
            activeListings.put(listing.getPlotId(), listing);
        }

        activeContracts.clear();
        for (PlotRentalContract contract : persistence.loadContracts()) {
            activeContracts.put(contract.getPlotId(), contract);
        }
    }

    private void save() {
        persistence.saveListings(new ArrayList<>(activeListings.values()));
        persistence.saveContracts(new ArrayList<>(activeContracts.values()));
    }

    private double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
