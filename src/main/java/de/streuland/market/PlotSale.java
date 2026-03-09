package de.streuland.market;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlotSale {
    public enum SaleType {
        FIXED,
        AUCTION
    }

    private final int plotId;
    private final UUID sellerUuid;
    private final double price;
    private final SaleType saleType;
    private final long auctionEnds;
    private final double highestBid;
    private final UUID highestBidder;

    public PlotSale(int plotId, UUID sellerUuid, double price, SaleType saleType, long auctionEnds, double highestBid, UUID highestBidder) {
        this.plotId = plotId;
        this.sellerUuid = sellerUuid;
        this.price = price;
        this.saleType = saleType;
        this.auctionEnds = auctionEnds;
        this.highestBid = highestBid;
        this.highestBidder = highestBidder;
    }

    public int getPlotId() { return plotId; }
    public UUID getSellerUuid() { return sellerUuid; }
    public double getPrice() { return price; }
    public SaleType getSaleType() { return saleType; }
    public long getAuctionEnds() { return auctionEnds; }
    public double getHighestBid() { return highestBid; }
    public UUID getHighestBidder() { return highestBidder; }

    public PlotSale withBid(double newBid, UUID bidder) {
        return new PlotSale(plotId, sellerUuid, price, saleType, auctionEnds, newBid, bidder);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("plotId", plotId);
        map.put("sellerUuid", sellerUuid.toString());
        map.put("price", price);
        map.put("saleType", saleType.name());
        map.put("auctionEnds", auctionEnds);
        map.put("highestBid", highestBid);
        map.put("highestBidder", highestBidder == null ? null : highestBidder.toString());
        return map;
    }

    public static PlotSale fromMap(Map<String, Object> map) {
        int plotId = ((Number) map.getOrDefault("plotId", 0)).intValue();
        UUID sellerUuid = UUID.fromString(String.valueOf(map.get("sellerUuid")));
        double price = ((Number) map.getOrDefault("price", 0D)).doubleValue();
        SaleType saleType = SaleType.valueOf(String.valueOf(map.getOrDefault("saleType", SaleType.FIXED.name())));
        long auctionEnds = ((Number) map.getOrDefault("auctionEnds", 0L)).longValue();
        double highestBid = ((Number) map.getOrDefault("highestBid", 0D)).doubleValue();
        Object bidderRaw = map.get("highestBidder");
        UUID highestBidder = bidderRaw == null ? null : UUID.fromString(String.valueOf(bidderRaw));
        return new PlotSale(plotId, sellerUuid, price, saleType, auctionEnds, highestBid, highestBidder);
    }

    public String toYamlString() {
        YamlConfiguration config = new YamlConfiguration();
        for (Map.Entry<String, Object> entry : toMap().entrySet()) {
            config.set(entry.getKey(), entry.getValue());
        }
        return config.saveToString();
    }

    public static PlotSale fromYamlString(String yaml) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(yaml);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid sale yaml", e);
        }
        return fromMap(config.getValues(false));
    }
}
