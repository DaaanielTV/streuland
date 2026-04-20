package de.streuland.market;

import de.streuland.economy.PlotEconomyHook;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;
import de.streuland.plot.PlotStorage;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class PlotAuctionService {
    public enum CreateResult {
        OK,
        NOT_OWNER,
        INVALID_PRICE,
        FLOOR_HIGHER_THAN_START,
        ALREADY_LISTED,
        PLOT_PROTECTED,
        INVALID_DURATION
    }

    public enum AcceptResult {
        OK,
        NOT_FOUND,
        OWN_AUCTION,
        INSUFFICIENT_FUNDS,
        PAYMENT_FAILED,
        TRANSFER_FAILED,
        AUCTION_ENDED,
        SELLER_SAME_AS_BUYER
    }

    public enum CancelResult {
        OK,
        NOT_FOUND,
        NOT_OWNER,
        ALREADY_SOLD
    }

    public record AcceptOutcome(AcceptResult result, double chargedAmount, double sellerPayout, double taxAmount) {}

    private final PlotStorage plotStorage;
    private final PlotEconomyHook economyHook;
    private final AuctionPersistence persistence;
    private final Map<String, AuctionListing> activeAuctions = new ConcurrentHashMap<>();
    private final JavaPlugin plugin;

    public PlotAuctionService(JavaPlugin plugin, PlotStorage plotStorage, PlotEconomyHook economyHook) {
        this.plugin = plugin;
        this.plotStorage = plotStorage;
        this.economyHook = economyHook;
        this.persistence = new AuctionPersistence(new File(plugin.getDataFolder(), "market"), plugin.getLogger());
        load();
    }

    public synchronized CreateResult createAuction(Plot plot, UUID sellerId, double startingPrice, double floorPrice, long durationHours) {
        if (plot == null || plot.getOwner() == null || !plot.getOwner().equals(sellerId)) {
            return CreateResult.NOT_OWNER;
        }
        if (startingPrice <= 0 || floorPrice <= 0 || Double.isNaN(startingPrice) || Double.isNaN(floorPrice)) {
            return CreateResult.INVALID_PRICE;
        }
        if (floorPrice >= startingPrice) {
            return CreateResult.FLOOR_HIGHER_THAN_START;
        }
        if (durationHours < 1 || durationHours > 168) {
            return CreateResult.INVALID_DURATION;
        }
        if (isProtectedPlot(plot)) {
            return CreateResult.PLOT_PROTECTED;
        }
        if (activeAuctions.containsKey(plot.getPlotId())) {
            return CreateResult.ALREADY_LISTED;
        }

        double decayPerHour = (startingPrice - floorPrice) / durationHours;
        long extensionMinutes = getExtensionMinutes();
        AuctionListing.DecayType decayType = getDecayType();

        AuctionListing auction = new AuctionListing(
                plot.getPlotId(), sellerId, startingPrice, floorPrice,
                decayPerHour, durationHours, extensionMinutes, decayType
        );
        activeAuctions.put(plot.getPlotId(), auction);
        save();
        return CreateResult.OK;
    }

    public synchronized CancelResult cancelAuction(String plotId, UUID actorId) {
        AuctionListing auction = activeAuctions.get(plotId);
        if (auction == null) {
            return CancelResult.NOT_FOUND;
        }
        if (!actorId.equals(auction.getSellerId())) {
            return CancelResult.NOT_OWNER;
        }
        if (auction.getStatus() == AuctionListing.Status.SOLD) {
            return CancelResult.ALREADY_SOLD;
        }

        auction.setStatus(AuctionListing.Status.CANCELLED);
        activeAuctions.remove(plotId);
        save();
        return CancelResult.OK;
    }

    public synchronized AcceptOutcome acceptAuction(UUID buyerId, String plotId) {
        AuctionListing auction = activeAuctions.get(plotId);
        if (auction == null) {
            return new AcceptOutcome(AcceptResult.NOT_FOUND, 0D, 0D, 0D);
        }
        if (auction.getStatus() != AuctionListing.Status.ACTIVE) {
            return new AcceptOutcome(AcceptResult.AUCTION_ENDED, 0D, 0D, 0D);
        }
        if (auction.getSellerId().equals(buyerId)) {
            return new AcceptOutcome(AcceptResult.SELLER_SAME_AS_BUYER, 0D, 0D, 0D);
        }

        if (buyerId.equals(auction.getSellerId())) {
            return new AcceptOutcome(AcceptResult.OWN_AUCTION, 0D, 0D, 0D);
        }

        Plot plot = plotStorage.getPlot(plotId);
        if (plot == null || plot.getOwner() == null || !plot.getOwner().equals(auction.getSellerId())) {
            auction.setStatus(AuctionListing.Status.EXPIRED);
            activeAuctions.remove(plotId);
            save();
            return new AcceptOutcome(AcceptResult.NOT_FOUND, 0D, 0D, 0D);
        }

        double price = auction.calculatePrice();
        double tax = round(getTaxFor(price));
        double payout = round(price - tax);

        if (!economyHook.hasEconomy() || economyHook.getBalance(buyerId) < price) {
            return new AcceptOutcome(AcceptResult.INSUFFICIENT_FUNDS, price, payout, tax);
        }
        if (!economyHook.withdraw(buyerId, price)) {
            return new AcceptOutcome(AcceptResult.PAYMENT_FAILED, price, payout, tax);
        }
        if (!economyHook.deposit(auction.getSellerId(), payout)) {
            economyHook.deposit(buyerId, price);
            return new AcceptOutcome(AcceptResult.PAYMENT_FAILED, price, payout, tax);
        }

        Plot transferred = plotStorage.transferOwnership(plotId, auction.getSellerId(), buyerId);
        if (transferred == null) {
            economyHook.deposit(buyerId, price);
            economyHook.withdraw(auction.getSellerId(), payout);
            return new AcceptOutcome(AcceptResult.TRANSFER_FAILED, price, payout, tax);
        }

        auction.setWinningBidder(buyerId);
        auction.setStatus(AuctionListing.Status.SOLD);
        auction.setStatus(AuctionListing.Status.SOLD);
        activeAuctions.remove(plotId);
        save();
        return new AcceptOutcome(AcceptResult.OK, price, payout, tax);
    }

    public synchronized List<AuctionListing> getActiveAuctions() {
        return activeAuctions.values().stream()
                .filter(a -> a.getStatus() == AuctionListing.Status.ACTIVE)
                .sorted(Comparator.comparingLong(AuctionListing::getEndsAt))
                .collect(Collectors.toList());
    }

    public synchronized List<AuctionListing> getAllAuctions() {
        return new ArrayList<>(activeAuctions.values());
    }

    public synchronized AuctionListing getAuction(String plotId) {
        return activeAuctions.get(plotId);
    }

    public synchronized void tick() {
        long now = System.currentTimeMillis();
        for (AuctionListing auction : activeAuctions.values()) {
            if (auction.getStatus() != AuctionListing.Status.ACTIVE) {
                continue;
            }
            if (now >= auction.getEndsAt()) {
                auction.setStatus(AuctionListing.Status.EXPIRED);
                activeAuctions.remove(auction.getPlotId());
            }
        }
        if (!activeAuctions.isEmpty()) {
            save();
        }
    }

    public double getConfirmationThreshold() {
        return plugin.getConfig().getDouble("auction.confirmation-threshold", 25000D);
    }

    private long getExtensionMinutes() {
        return Math.max(1L, plugin.getConfig().getLong("auction.extension-minutes", 5L));
    }

    private AuctionListing.DecayType getDecayType() {
        String type = plugin.getConfig().getString("auction.decay-type", "LINEAR");
        try {
            return AuctionListing.DecayType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AuctionListing.DecayType.LINEAR;
        }
    }

    private boolean isProtectedPlot(Plot plot) {
        PlotData data = plotStorage.getPlotData(plot.getPlotId());
        if (data == null) {
            return false;
        }
        Map<String, Boolean> overrides = data.getFlagOverrides();
        boolean flaggedProtected = Boolean.TRUE.equals(overrides.get("market_protected"))
                || Boolean.TRUE.equals(overrides.get("protected"))
                || Boolean.TRUE.equals(overrides.get("system_plot"));
        Set<String> blockedIds = Set.copyOf(plugin.getConfig().getStringList("auction.blocked-plot-ids"));
        return flaggedProtected || blockedIds.contains(plot.getPlotId());
    }

    private double getTaxFor(double salePrice) {
        double taxRate = plugin.getConfig().getDouble("auction.tax-rate", 0.05D);
        double flatFee = plugin.getConfig().getDouble("auction.flat-fee", 0D);
        return (salePrice * Math.max(0D, taxRate)) + Math.max(0D, flatFee);
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private void load() {
        activeAuctions.clear();
        for (AuctionListing auction : persistence.loadAuctions()) {
            if (auction.getStatus() == AuctionListing.Status.ACTIVE) {
                activeAuctions.put(auction.getPlotId(), auction);
            }
        }
    }

    private void save() {
        persistence.saveAuctions(new ArrayList<>(activeAuctions.values()));
    }
}