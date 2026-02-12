package de.streuland.analytics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight analytics service used by runtime systems.
 */
public class InMemoryPlotAnalyticsService implements PlotAnalyticsService {
    private final List<PlotAnalyticsRecord> records = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, String> neighborhoodSummaries = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, MutableEditStats>> editStatsByPlot = new ConcurrentHashMap<>();

    @Override
    public void record(PlotAnalyticsRecord record) {
        if (record != null) {
            records.add(record);
            trackEditStat(record);
        }
    }

    @Override
    public String getNeighborhoodSummary(String plotId) {
        return neighborhoodSummaries.getOrDefault(plotId, "Keine Nachbarschaftsdaten verfügbar");
    }

    @Override
    public List<PlayerEditStats> getEditStatsForPlot(String plotId) {
        Map<UUID, MutableEditStats> perPlayer = editStatsByPlot.getOrDefault(plotId, Collections.emptyMap());
        List<PlayerEditStats> snapshot = new ArrayList<>();
        for (Map.Entry<UUID, MutableEditStats> entry : perPlayer.entrySet()) {
            MutableEditStats mutable = entry.getValue();
            snapshot.add(new PlayerEditStats(plotId, entry.getKey(), mutable.places, mutable.breaks, mutable.burns));
        }
        snapshot.sort((a, b) -> Integer.compare(b.getTotalEdits(), a.getTotalEdits()));
        return snapshot;
    }

    @Override
    public PlayerEditStats getEditStatsForPlotAndPlayer(String plotId, UUID playerId) {
        Map<UUID, MutableEditStats> perPlayer = editStatsByPlot.getOrDefault(plotId, Collections.emptyMap());
        MutableEditStats stats = perPlayer.get(playerId);
        if (stats == null) {
            return new PlayerEditStats(plotId, playerId, 0, 0, 0);
        }
        return new PlayerEditStats(plotId, playerId, stats.places, stats.breaks, stats.burns);
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

    private void trackEditStat(PlotAnalyticsRecord record) {
        if (record.getPlotId() == null || record.getPlayerId() == null) {
            return;
        }
        String type = record.getEventType();
        if (type == null) {
            return;
        }
        if (!"block_place".equals(type) && !"block_break".equals(type) && !"block_burn".equals(type)) {
            return;
        }
        Map<UUID, MutableEditStats> perPlayer = editStatsByPlot.computeIfAbsent(record.getPlotId(), ignored -> new ConcurrentHashMap<>());
        MutableEditStats stats = perPlayer.computeIfAbsent(record.getPlayerId(), ignored -> new MutableEditStats());
        if ("block_place".equals(type)) {
            stats.places += (int) Math.max(1, record.getValue());
        } else if ("block_break".equals(type)) {
            stats.breaks += (int) Math.max(1, record.getValue());
        } else if ("block_burn".equals(type)) {
            stats.burns += (int) Math.max(1, record.getValue());
        }
    }

    private static class MutableEditStats {
        private int places;
        private int breaks;
        private int burns;
    }
}
