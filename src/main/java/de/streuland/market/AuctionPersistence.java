package de.streuland.market;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class AuctionPersistence {
    private final File auctionFile;
    private final Logger logger;

    public AuctionPersistence(File marketDir, Logger logger) {
        this.logger = logger;
        if (!marketDir.exists()) {
            marketDir.mkdirs();
        }
        this.auctionFile = new File(marketDir, "auctions.yml");
    }

    public synchronized List<AuctionListing> loadAuctions() {
        if (!auctionFile.exists()) {
            return Collections.emptyList();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(auctionFile);
        if (!yaml.isConfigurationSection("auctions")) {
            return Collections.emptyList();
        }
        List<AuctionListing> auctions = new ArrayList<>();
        for (String key : yaml.getConfigurationSection("auctions").getKeys(false)) {
            String base = "auctions." + key;
            String plotId = yaml.getString(base + ".plotId");
            String sellerRaw = yaml.getString(base + ".sellerId");
            if (plotId == null || sellerRaw == null) {
                continue;
            }
            try {
                UUID sellerId = UUID.fromString(sellerRaw);
                double startingPrice = yaml.getDouble(base + ".startingPrice", 0D);
                double floorPrice = yaml.getDouble(base + ".floorPrice", 0D);
                double decayPerHour = yaml.getDouble(base + ".decayPerHour", 0D);
                long durationHours = yaml.getLong(base + ".durationHours", 24L);
                long extensionMinutes = yaml.getLong(base + ".extensionMinutes", 5L);
                String decayTypeRaw = yaml.getString(base + ".decayType", "LINEAR");
                long createdAt = yaml.getLong(base + ".createdAt", System.currentTimeMillis());
                long endsAt = yaml.getLong(base + ".endsAt", System.currentTimeMillis());
                String statusRaw = yaml.getString(base + ".status", "ACTIVE");
                String winnerRaw = yaml.getString(base + ".winningBidder");

                AuctionListing.DecayType decayType = AuctionListing.DecayType.valueOf(decayTypeRaw);
                AuctionListing.Status status = AuctionListing.Status.valueOf(statusRaw);
                UUID winningBidder = winnerRaw != null ? UUID.fromString(winnerRaw) : null;

                if (startingPrice > 0D && floorPrice > 0D) {
                    auctions.add(new AuctionListing(
                            plotId, sellerId, startingPrice, floorPrice,
                            decayPerHour, durationHours, extensionMinutes,
                            decayType, createdAt, endsAt, status, winningBidder
                    ));
                }
            } catch (IllegalArgumentException ignored) {
                logger.warning("Skipping invalid auction for key " + key);
            }
        }
        return auctions;
    }

    public synchronized void saveAuctions(List<AuctionListing> auctions) {
        YamlConfiguration yaml = new YamlConfiguration();
        int i = 0;
        for (AuctionListing auction : auctions) {
            String base = "auctions." + i++;
            yaml.set(base + ".plotId", auction.getPlotId());
            yaml.set(base + ".sellerId", auction.getSellerId().toString());
            yaml.set(base + ".startingPrice", auction.getStartingPrice());
            yaml.set(base + ".floorPrice", auction.getFloorPrice());
            yaml.set(base + ".decayPerHour", auction.getDecayPerHour());
            yaml.set(base + ".durationHours", auction.getDurationHours());
            yaml.set(base + ".extensionMinutes", auction.getExtensionMinutes());
            yaml.set(base + ".decayType", auction.getDecayType().name());
            yaml.set(base + ".createdAt", auction.getCreatedAt());
            yaml.set(base + ".endsAt", auction.getEndsAt());
            yaml.set(base + ".status", auction.getStatus().name());
            if (auction.getWinningBidder() != null) {
                yaml.set(base + ".winningBidder", auction.getWinningBidder().toString());
            }
        }
        saveYaml(yaml);
    }

    private void saveYaml(YamlConfiguration yaml) {
        try {
            yaml.save(auctionFile);
        } catch (IOException e) {
            logger.warning("Could not persist auctions: " + e.getMessage());
        }
    }
}