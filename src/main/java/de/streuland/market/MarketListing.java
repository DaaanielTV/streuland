package de.streuland.market;

import java.util.UUID;

/**
 * Active fixed-price listing for a player-owned plot.
 */
public class MarketListing {
    private final String plotId;
    private final UUID sellerId;
    private final double price;
    private final long createdAt;

    public MarketListing(String plotId, UUID sellerId, double price, long createdAt) {
        this.plotId = plotId;
        this.sellerId = sellerId;
        this.price = price;
        this.createdAt = createdAt;
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
}
