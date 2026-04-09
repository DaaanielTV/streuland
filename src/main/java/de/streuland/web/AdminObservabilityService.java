package de.streuland.web;

import de.streuland.analytics.InMemoryPlotAnalyticsService;
import de.streuland.analytics.PlotAnalyticsRecord;
import de.streuland.plot.Plot;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Builds lightweight administrative/observability payloads for the web API.
 * Keeps dashboard concerns separate from plot domain services.
 */
public class AdminObservabilityService {
    private static final int DEFAULT_RECENT_LIMIT = 25;

    private final WebServer.PlotGateway plotGateway;
    private final InMemoryPlotAnalyticsService analyticsService;
    private final RuntimeMXBean runtimeMXBean;

    public AdminObservabilityService(WebServer.PlotGateway plotGateway, InMemoryPlotAnalyticsService analyticsService) {
        this(plotGateway, analyticsService, ManagementFactory.getRuntimeMXBean());
    }

    AdminObservabilityService(WebServer.PlotGateway plotGateway,
                              InMemoryPlotAnalyticsService analyticsService,
                              RuntimeMXBean runtimeMXBean) {
        this.plotGateway = plotGateway;
        this.analyticsService = analyticsService;
        this.runtimeMXBean = runtimeMXBean;
    }

    public Map<String, Object> buildPlotStatus() {
        Collection<Plot> plots = plotGateway.getAllPlots();
        int total = 0;
        int claimed = 0;
        int unclaimed = 0;
        int featured = 0;
        int totalTrusted = 0;

        for (Plot plot : plots) {
            total++;
            if (plot.getOwner() == null) {
                unclaimed++;
            } else {
                claimed++;
            }
            if (plotGateway.isFeatured(plot.getPlotId())) {
                featured++;
            }
            totalTrusted += plot.getTrustedPlayers().size();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("total", total);
        payload.put("claimed", claimed);
        payload.put("unclaimed", unclaimed);
        payload.put("featured", featured);
        payload.put("avgTrustedPlayers", total == 0 ? 0.0 : ((double) totalTrusted) / total);
        payload.put("generatedAt", Instant.now().toString());
        return payload;
    }

    public List<Map<String, Object>> buildRecentChanges(Integer requestedLimit) {
        int limit = requestedLimit == null ? DEFAULT_RECENT_LIMIT : Math.max(1, Math.min(200, requestedLimit));
        if (analyticsService == null) {
            return new ArrayList<>();
        }
        List<PlotAnalyticsRecord> records = analyticsService.getRecordsSnapshot();
        records.sort(Comparator.comparing(PlotAnalyticsRecord::getTimestamp).reversed());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (PlotAnalyticsRecord record : records) {
            if (rows.size() >= limit) {
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("plotId", record.getPlotId());
            row.put("eventType", record.getEventType());
            row.put("playerId", record.getPlayerId() == null ? null : record.getPlayerId().toString());
            row.put("value", record.getValue());
            row.put("timestamp", record.getTimestamp() == null ? null : record.getTimestamp().toString());
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> buildHealth() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("maxBytes", maxMemory);
        memory.put("totalBytes", totalMemory);
        memory.put("usedBytes", usedMemory);
        memory.put("usedPercent", totalMemory == 0 ? 0.0 : (usedMemory * 100.0) / totalMemory);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "ok");
        payload.put("uptimeSeconds", TimeUnit.MILLISECONDS.toSeconds(runtimeMXBean.getUptime()));
        payload.put("startedAt", Instant.ofEpochMilli(runtimeMXBean.getStartTime()).toString());
        payload.put("plotsLoaded", plotGateway.getAllPlots().size());
        payload.put("memory", memory);
        payload.put("generatedAt", Instant.now().toString());
        return payload;
    }

    public Map<String, Object> buildSummary(Integer recentLimit) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("plotStatus", buildPlotStatus());
        summary.put("recentChanges", buildRecentChanges(recentLimit));
        summary.put("health", buildHealth());
        return summary;
    }
}
