package de.streuland.analytics;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for analytics recording.
 */
public interface PlotAnalyticsService {
    void record(PlotAnalyticsRecord record);

    /**
     * Returns a human-readable neighborhood/economy summary for a plot.
     */
    String getNeighborhoodSummary(String plotId);

    List<PlayerEditStats> getEditStatsForPlot(String plotId);

    PlayerEditStats getEditStatsForPlotAndPlayer(String plotId, UUID playerId);
}
