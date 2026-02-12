package de.streuland.analytics;

/**
 * Service interface for analytics recording.
 */
public interface PlotAnalyticsService {
    void record(PlotAnalyticsRecord record);

    /**
     * Returns a human-readable neighborhood/economy summary for a plot.
     */
    String getNeighborhoodSummary(String plotId);
}
