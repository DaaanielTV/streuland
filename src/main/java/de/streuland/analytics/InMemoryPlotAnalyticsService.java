package de.streuland.analytics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight analytics service used by runtime systems.
 */
public class InMemoryPlotAnalyticsService implements PlotAnalyticsService {
    private final List<PlotAnalyticsRecord> records = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, String> neighborhoodSummaries = new ConcurrentHashMap<>();

    @Override
    public void record(PlotAnalyticsRecord record) {
        if (record != null) {
            records.add(record);
        }
    }

    @Override
    public String getNeighborhoodSummary(String plotId) {
        return neighborhoodSummaries.getOrDefault(plotId, "Keine Nachbarschaftsdaten verfügbar");
    }

    public void setNeighborhoodSummary(String plotId, String summary) {
        if (plotId == null || summary == null) {
            return;
        }
        neighborhoodSummaries.put(plotId, summary);
    }

    public List<PlotAnalyticsRecord> getRecordsSnapshot() {
        synchronized (records) {
            return new ArrayList<>(records);
        }
    }
}
