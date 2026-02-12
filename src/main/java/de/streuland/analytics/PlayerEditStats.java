package de.streuland.analytics;

import java.util.UUID;

public class PlayerEditStats {
    private final String plotId;
    private final UUID playerId;
    private final int places;
    private final int breaks;
    private final int burns;

    public PlayerEditStats(String plotId, UUID playerId, int places, int breaks, int burns) {
        this.plotId = plotId;
        this.playerId = playerId;
        this.places = places;
        this.breaks = breaks;
        this.burns = burns;
    }

    public String getPlotId() {
        return plotId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getPlaces() {
        return places;
    }

    public int getBreaks() {
        return breaks;
    }

    public int getBurns() {
        return burns;
    }

    public int getTotalEdits() {
        return places + breaks + burns;
    }
}
