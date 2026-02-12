package de.streuland.dashboard;

import de.streuland.analytics.InMemoryPlotAnalyticsService;
import de.streuland.analytics.PlotAnalyticsRecord;
import de.streuland.plot.Plot;

import java.util.Collection;
import java.util.List;

/**
 * Converts plot and analytics information to GeoJSON for dashboard visualizations.
 */
public class PlotAnalyticsExporter {
    private final InMemoryPlotAnalyticsService analyticsService;

    public PlotAnalyticsExporter(InMemoryPlotAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    public String toGeoJson(Collection<Plot> plots) {
        List<PlotAnalyticsRecord> records = analyticsService.getRecordsSnapshot();
        StringBuilder json = new StringBuilder();
        json.append("{\"type\":\"FeatureCollection\",\"features\":[");
        boolean first = true;
        for (Plot plot : plots) {
            if (!first) {
                json.append(',');
            }
            first = false;
            int events = 0;
            double totalValue = 0;
            for (PlotAnalyticsRecord record : records) {
                if (plot.getPlotId().equals(record.getPlotId())) {
                    events++;
                    totalValue += record.getValue();
                }
            }
            json.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"Point\",\"coordinates\":[")
                .append(plot.getCenterX()).append(',').append(plot.getCenterZ())
                .append("]},\"properties\":{")
                .append("\"plotId\":\"").append(escape(plot.getPlotId())).append("\",")
                .append("\"state\":\"").append(plot.getState().name()).append("\",")
                .append("\"size\":").append(plot.getSize()).append(',')
                .append("\"analyticsEvents\":").append(events).append(',')
                .append("\"analyticsValue\":").append(String.format(java.util.Locale.US, "%.2f", totalValue))
                .append("}}");
        }
        json.append("]}");
        return json.toString();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
