package de.streuland.market;

import java.util.UUID;

/**
 * Active fixed-price listing for a player-owned plot.
 */
public class MarketListing {
    public enum ListingType {
        SALE,
        RENT
    }

    private final String plotId;
    private final UUID sellerId;
    private final double price;
    private final long createdAt;
    private final ListingType listingType;
    private final int rentDurationDays;

    public MarketListing(String plotId, UUID sellerId, double price, long createdAt) {
        this(plotId, sellerId, price, createdAt, ListingType.SALE, 0);
    }

    public MarketListing(String plotId, UUID sellerId, double price, long createdAt, ListingType listingType, int rentDurationDays) {
        this.plotId = plotId;
        this.sellerId = sellerId;
        this.price = price;
        this.createdAt = createdAt;
        this.listingType = listingType == null ? ListingType.SALE : listingType;
        this.rentDurationDays = Math.max(0, rentDurationDays);
    }

    public String getPlotId() {
        return plotId;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public double getPrice() {
        return price;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public ListingType getListingType() {
        return listingType;
    }

    public int getRentDurationDays() {
        return rentDurationDays;
    }
}
