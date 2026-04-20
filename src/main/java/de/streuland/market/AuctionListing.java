package de.streuland.market;

import java.util.UUID;

public class AuctionListing {
    public enum Status {
        ACTIVE,
        SOLD,
        CANCELLED,
        EXPIRED
    }

    public enum DecayType {
        LINEAR,
        EXPONENTIAL
    }

    private final String plotId;
    private final UUID sellerId;
    private final double startingPrice;
    private final double floorPrice;
    private final DecayType decayType;
    private final double decayPerHour;
    private final long createdAt;
    private final long endsAt;
    private final long extensionMinutes;
    private final long durationHours;
    private UUID winningBidder;
    private Status status;

    public AuctionListing(String plotId,
                       UUID sellerId,
                       double startingPrice,
                       double floorPrice,
                       double decayPerHour,
                       long durationHours,
                       long extensionMinutes,
                       DecayType decayType) {
        this.plotId = plotId;
        this.sellerId = sellerId;
        this.startingPrice = startingPrice;
        this.floorPrice = floorPrice;
        this.decayPerHour = decayPerHour;
        this.decayType = decayType;
        this.durationHours = durationHours;
        this.createdAt = System.currentTimeMillis();
        this.endsAt = this.createdAt + (durationHours * 3_600_000L);
        this.extensionMinutes = extensionMinutes;
        this.status = Status.ACTIVE;
    }

    public AuctionListing(String plotId,
                       UUID sellerId,
                       double startingPrice,
                       double floorPrice,
                       double decayPerHour,
                       long durationHours,
                       long extensionMinutes,
                       DecayType decayType,
                       long createdAt,
                       long endsAt,
                       Status status,
                       UUID winningBidder) {
        this.plotId = plotId;
        this.sellerId = sellerId;
        this.startingPrice = startingPrice;
        this.floorPrice = floorPrice;
        this.decayPerHour = decayPerHour;
        this.decayType = decayType;
        this.durationHours = durationHours;
        this.createdAt = createdAt;
        this.endsAt = endsAt;
        this.extensionMinutes = extensionMinutes;
        this.status = status;
        this.winningBidder = winningBidder;
    }

    public String getPlotId() {
        return plotId;
    }

    public UUID getSellerId() {
        return sellerId;
    }

    public double getStartingPrice() {
        return startingPrice;
    }

    public double getFloorPrice() {
        return floorPrice;
    }

    public double getCurrentPrice() {
        return calculatePrice();
    }

    public DecayType getDecayType() {
        return decayType;
    }

    public double getDecayPerHour() {
        return decayPerHour;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getEndsAt() {
        return endsAt;
    }

    public long getExtensionMinutes() {
        return extensionMinutes;
    }

    public long getDurationHours() {
        return durationHours;
    }

    public UUID getWinningBidder() {
        return winningBidder;
    }

    public Status getStatus() {
        return status;
    }

    public void setWinningBidder(UUID winningBidder) {
        this.winningBidder = winningBidder;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public double calculatePrice() {
        if (status != Status.ACTIVE) {
            return startingPrice;
        }
        long now = System.currentTimeMillis();
        if (now < createdAt) {
            return startingPrice;
        }
        double hoursElapsed = (now - createdAt) / 3_600_000.0;
        if (hoursElapsed <= 0) {
            return startingPrice;
        }

        double price;
        if (decayType == DecayType.EXPONENTIAL) {
            double decayRate = Math.log(startingPrice / Math.max(1, floorPrice)) / Math.max(1, durationHours);
            price = startingPrice * Math.exp(-decayRate * hoursElapsed);
        } else {
            price = startingPrice - (hoursElapsed * decayPerHour);
        }

        return Math.max(floorPrice, Math.min(startingPrice, round(price)));
    }

    public boolean isSnipePhase() {
        if (status != Status.ACTIVE) {
            return false;
        }
        long remaining = endsAt - System.currentTimeMillis();
        return remaining > 0 && remaining <= (extensionMinutes * 60_000L);
    }

    public void extendTime() {
        if (isSnipePhase()) {
            this.endsAt = endsAt + (extensionMinutes * 60_000L);
        }
    }

    public boolean isExpired() {
        return status == Status.ACTIVE && System.currentTimeMillis() >= endsAt;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}