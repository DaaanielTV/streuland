package de.streuland.analytics;

import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot of analytics data points for a plot event.
 */
public class PlotAnalyticsRecord {
    private final String plotId;
    private final UUID playerId;
    private final String eventType;
    private final Instant timestamp;
    private final double value;

    public PlotAnalyticsRecord(String plotId, UUID playerId, String eventType, Instant timestamp, double value) {
        this.plotId = plotId;
        this.playerId = playerId;
        this.eventType = eventType;
        this.timestamp = timestamp;
        this.value = value;
    }

    public String getPlotId() {
        return plotId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public double getValue() {
        return value;
    }
}
