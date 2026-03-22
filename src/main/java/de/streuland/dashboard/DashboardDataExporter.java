package de.streuland.dashboard;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;
import de.streuland.plot.PlotStorage;
import org.bukkit.World;
import org.bukkit.block.Biome;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

/**
 * Converts plot + PlotData metadata into GeoJSON payloads used by the web dashboard.
 */
public class DashboardDataExporter {
    private final PlotStorage plotStorage;

    public DashboardDataExporter(PlotStorage plotStorage) {
        this.plotStorage = plotStorage;
    }

    public String toGeoJson(Collection<Plot> plots, World world) {
        StringBuilder json = new StringBuilder();
        json.append("{\"type\":\"FeatureCollection\",\"features\":[");
        boolean first = true;
        for (Plot plot : plots) {
            if (!first) {
                json.append(',');
            }
            first = false;
            PlotData plotData = plotStorage.getPlotData(plot.getPlotId());
            Biome biome = world.getBlockAt(plot.getCenterX(), plot.getSpawnY(), plot.getCenterZ()).getBiome();
            double level = plotData == null ? 1D : plotData.getStatBonuses().getOrDefault("plotLevel", 1D);

            json.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[")
                .append(plot.getMinX()).append(',').append(plot.getMinZ()).append("],[")
                .append(plot.getMaxX()).append(',').append(plot.getMinZ()).append("],[")
                .append(plot.getMaxX()).append(',').append(plot.getMaxZ()).append("],[")
                .append(plot.getMinX()).append(',').append(plot.getMaxZ()).append("],[")
                .append(plot.getMinX()).append(',').append(plot.getMinZ())
                .append("]]},\"properties\":{")
                .append("\"plotId\":\"").append(escape(plot.getPlotId())).append("\",")
                .append("\"owner\":\"").append(formatOwner(plot.getOwner())).append("\",")
                .append("\"biome\":\"").append(biome.name()).append("\",")
                .append("\"state\":\"").append(plot.getState().name()).append("\",")
                .append("\"level\":").append(String.format(Locale.US, "%.2f", level)).append(',')
                .append("\"size\":").append(plot.getSize()).append(',')
                .append("\"publicVisit\":").append(plotData != null && plotData.isPublicVisitEnabled()).append(',')
                .append("\"title\":\"").append(escape(plotData == null ? "" : plotData.getShowcaseTitle())).append("\",")
                .append("\"description\":\"").append(escape(plotData == null ? "" : plotData.getShowcaseDescription())).append("\",")
                .append("\"tags\":[").append(formatTags(plotData)).append("],")
                .append("\"spawnX\":").append(resolveSpawnX(plot, plotData)).append(',')
                .append("\"spawnY\":").append(resolveSpawnY(plot, plotData)).append(',')
                .append("\"spawnZ\":").append(resolveSpawnZ(plot, plotData))
                .append("}}");
        }
        json.append("]}");
        return json.toString();
    }

    private String formatOwner(UUID owner) {
        return owner == null ? "UNCLAIMED" : owner.toString();
    }

    private String formatTags(PlotData plotData) {
        if (plotData == null || plotData.getShowcaseTags().isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (String tag : plotData.getShowcaseTags()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("\"").append(escape(tag)).append("\"");
        }
        return builder.toString();
    }

    private int resolveSpawnX(Plot plot, PlotData plotData) { return plotData != null && plotData.hasCustomShowcaseSpawn() ? plotData.getShowcaseSpawnX() : plot.getCenterX(); }
    private int resolveSpawnY(Plot plot, PlotData plotData) { return plotData != null && plotData.hasCustomShowcaseSpawn() ? plotData.getShowcaseSpawnY() : plot.getSpawnY(); }
    private int resolveSpawnZ(Plot plot, PlotData plotData) { return plotData != null && plotData.hasCustomShowcaseSpawn() ? plotData.getShowcaseSpawnZ() : plot.getCenterZ(); }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
