package de.streuland.plot.market;

import java.util.UUID;

public class MarketListing {
    private String plotId;
    private UUID sellerId;
    private UUID buyerId;
    private double price;
    private String biome;
    private int level;
    private int districtTier;
    private double valuation;
    private long timestamp;

    public MarketListing(String plotId, UUID sellerId, UUID buyerId, double price, String biome, int level, int districtTier, double valuation, long timestamp) {
        this.plotId = plotId;
        this.sellerId = sellerId;
        this.buyerId = buyerId;
        this.price = price;
        this.biome = biome;
        this.level = level;
        this.districtTier = districtTier;
        this.valuation = valuation;
        this.timestamp = timestamp;
    }

    public String getPlotId() { return plotId; }
    public UUID getSellerId() { return sellerId; }
    public UUID getBuyerId() { return buyerId; }
    public double getPrice() { return price; }
    public String getBiome() { return biome; }
    public int getLevel() { return level; }
    public int getDistrictTier() { return districtTier; }
    public double getValuation() { return valuation; }
    public long getTimestamp() { return timestamp; }
}
