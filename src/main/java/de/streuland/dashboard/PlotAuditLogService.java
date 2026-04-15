package de.streuland.dashboard;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class PlotAuditLogService {
    private final int maxEntries;
    private final List<PlotAuditEvent> events = new ArrayList<PlotAuditEvent>();

    public PlotAuditLogService(int maxEntries) {
        this.maxEntries = Math.max(100, maxEntries);
    }

    public synchronized PlotAuditEvent record(String plotId, String action, String actor, Map<String, Object> metadata) {
        PlotAuditEvent event = new PlotAuditEvent(
                UUID.randomUUID().toString(),
                plotId,
                action,
                actor,
                Instant.now(),
                metadata == null ? Collections.<String, Object>emptyMap() : new LinkedHashMap<String, Object>(metadata)
        );
        events.add(event);
        if (events.size() > maxEntries) {
            events.remove(0);
        }
        return event;
    }

    public synchronized List<PlotAuditEvent> listByPlot(String plotId, int limit) {
        int normalizedLimit = Math.max(1, Math.min(500, limit));
        List<PlotAuditEvent> matches = new ArrayList<PlotAuditEvent>();
        for (PlotAuditEvent event : events) {
            if (plotId == null || plotId.trim().isEmpty() || plotId.equalsIgnoreCase(event.getPlotId())) {
                matches.add(event);
            }
        }
        matches.sort(Comparator.comparing(PlotAuditEvent::getTimestamp).reversed());
        if (matches.size() > normalizedLimit) {
            return new ArrayList<PlotAuditEvent>(matches.subList(0, normalizedLimit));
        }
        return matches;
    }

    public synchronized List<PlotAuditEvent> listRecent(int limit) {
        return listByPlot(null, limit);
    }

    public synchronized List<PlotAuditEvent> listFiltered(String plotId, String action, String actor, String search, int limit) {
        String actionFilter = normalize(action);
        String actorFilter = normalize(actor);
        String searchFilter = normalize(search);
        List<PlotAuditEvent> scoped = listByPlot(plotId, Math.max(1, Math.min(500, limit * 4)));
        List<PlotAuditEvent> filtered = new ArrayList<PlotAuditEvent>();
        for (PlotAuditEvent event : scoped) {
            if (!actionFilter.isEmpty() && !event.getAction().toLowerCase(Locale.ROOT).contains(actionFilter)) {
                continue;
            }
            if (!actorFilter.isEmpty() && (event.getActor() == null || !event.getActor().toLowerCase(Locale.ROOT).contains(actorFilter))) {
                continue;
            }
            if (!searchFilter.isEmpty()) {
                String combined = (event.getPlotId() + " " + event.getAction() + " " + event.getActor() + " " + event.getMetadata())
                        .toLowerCase(Locale.ROOT);
                if (!combined.contains(searchFilter)) {
                    continue;
                }
            }
            filtered.add(event);
            if (filtered.size() >= Math.max(1, Math.min(500, limit))) {
                break;
            }
        }
        return filtered;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
