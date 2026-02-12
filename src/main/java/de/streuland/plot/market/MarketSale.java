package de.streuland.plot.market;

import java.util.UUID;

public class MarketSale {
    private String plotId;
    private UUID sellerId;
    private UUID buyerId;
    private double price;
    private double fee;
    private long timestamp;
    private String biome;
    private int level;
    private int districtTier;

    public MarketSale(String plotId, UUID sellerId, UUID buyerId, double price, double fee, long timestamp, String biome, int level, int districtTier) {
        this.plotId = plotId;
        this.sellerId = sellerId;
        this.buyerId = buyerId;
        this.price = price;
        this.fee = fee;
        this.timestamp = timestamp;
        this.biome = biome;
        this.level = level;
        this.districtTier = districtTier;
    }

    public String getPlotId() { return plotId; }
    public UUID getSellerId() { return sellerId; }
    public UUID getBuyerId() { return buyerId; }
    public double getPrice() { return price; }
    public double getFee() { return fee; }
    public long getTimestamp() { return timestamp; }
    public String getBiome() { return biome; }
    public int getLevel() { return level; }
    public int getDistrictTier() { return districtTier; }
}
