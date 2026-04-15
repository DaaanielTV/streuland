package de.streuland.market;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * YAML persistence for market listings and rental contracts.
 */
public class PlotMarketPersistence {
    private final File listingsFile;
    private final File contractsFile;
    private final Logger logger;

    public PlotMarketPersistence(File marketDir, Logger logger) {
        this.logger = logger;
        if (!marketDir.exists()) {
            marketDir.mkdirs();
        }
        this.listingsFile = new File(marketDir, "listings.yml");
        this.contractsFile = new File(marketDir, "contracts.yml");
    }

    public synchronized List<MarketListing> loadListings() {
        if (!listingsFile.exists()) {
            return Collections.emptyList();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(listingsFile);
        if (!yaml.isConfigurationSection("listings")) {
            return Collections.emptyList();
        }
        List<MarketListing> listings = new ArrayList<>();
        for (String key : yaml.getConfigurationSection("listings").getKeys(false)) {
            String base = "listings." + key;
            String plotId = yaml.getString(base + ".plotId");
            String sellerRaw = yaml.getString(base + ".sellerId");
            if (plotId == null || sellerRaw == null) {
                continue;
            }
            try {
                UUID sellerId = UUID.fromString(sellerRaw);
                double price = yaml.getDouble(base + ".price", 0D);
                long createdAt = yaml.getLong(base + ".createdAt", System.currentTimeMillis());
                MarketListing.ListingType listingType = MarketListing.ListingType.valueOf(
                        yaml.getString(base + ".listingType", MarketListing.ListingType.SALE.name())
                );
                int rentDurationDays = yaml.getInt(base + ".rentDurationDays", 0);
                if (price > 0D) {
                    listings.add(new MarketListing(plotId, sellerId, price, createdAt, listingType, rentDurationDays));
                }
            } catch (IllegalArgumentException ignored) {
                logger.warning("Skipping invalid market listing for key " + key);
            }
        }
        return listings;
    }

    public synchronized void saveListings(List<MarketListing> listings) {
        YamlConfiguration yaml = new YamlConfiguration();
        int i = 0;
        for (MarketListing listing : listings) {
            String base = "listings." + i++;
            yaml.set(base + ".plotId", listing.getPlotId());
            yaml.set(base + ".sellerId", listing.getSellerId().toString());
            yaml.set(base + ".price", listing.getPrice());
            yaml.set(base + ".createdAt", listing.getCreatedAt());
            yaml.set(base + ".listingType", listing.getListingType().name());
            yaml.set(base + ".rentDurationDays", listing.getRentDurationDays());
        }
        saveYaml(yaml, listingsFile, "market listings");
    }

    public synchronized List<PlotRentalContract> loadContracts() {
        if (!contractsFile.exists()) {
            return Collections.emptyList();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(contractsFile);
        if (!yaml.isConfigurationSection("contracts")) {
            return Collections.emptyList();
        }
        List<PlotRentalContract> contracts = new ArrayList<>();
        for (String key : yaml.getConfigurationSection("contracts").getKeys(false)) {
            String base = "contracts." + key;
            String contractId = yaml.getString(base + ".contractId");
            String plotId = yaml.getString(base + ".plotId");
            String landlordRaw = yaml.getString(base + ".landlordId");
            String tenantRaw = yaml.getString(base + ".tenantId");
            if (contractId == null || plotId == null || landlordRaw == null || tenantRaw == null) {
                continue;
            }
            try {
                contracts.add(new PlotRentalContract(
                        contractId,
                        plotId,
                        UUID.fromString(landlordRaw),
                        UUID.fromString(tenantRaw),
                        yaml.getDouble(base + ".rentPrice", 0D),
                        yaml.getLong(base + ".createdAt", System.currentTimeMillis()),
                        yaml.getLong(base + ".startsAt", System.currentTimeMillis()),
                        yaml.getLong(base + ".endsAt", System.currentTimeMillis())
                ));
            } catch (IllegalArgumentException ignored) {
                logger.warning("Skipping invalid rental contract for key " + key);
            }
        }
        return contracts;
    }

    public synchronized void saveContracts(List<PlotRentalContract> contracts) {
        YamlConfiguration yaml = new YamlConfiguration();
        int i = 0;
        for (PlotRentalContract contract : contracts) {
            String base = "contracts." + i++;
            yaml.set(base + ".contractId", contract.getContractId());
            yaml.set(base + ".plotId", contract.getPlotId());
            yaml.set(base + ".landlordId", contract.getLandlordId().toString());
            yaml.set(base + ".tenantId", contract.getTenantId().toString());
            yaml.set(base + ".rentPrice", contract.getRentPrice());
            yaml.set(base + ".createdAt", contract.getCreatedAt());
            yaml.set(base + ".startsAt", contract.getStartsAt());
            yaml.set(base + ".endsAt", contract.getEndsAt());
        }
        saveYaml(yaml, contractsFile, "rental contracts");
    }

    private void saveYaml(YamlConfiguration yaml, File target, String descriptor) {
        try {
            yaml.save(target);
        } catch (IOException e) {
            logger.warning("Could not persist " + descriptor + ": " + e.getMessage());
        }
    }
}
