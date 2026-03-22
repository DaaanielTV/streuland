package de.streuland.dashboard;

import de.streuland.district.District;
import de.streuland.district.DistrictManager;
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
    private final DistrictManager districtManager;

    public DashboardDataExporter(PlotStorage plotStorage) {
        this(plotStorage, null);
    }

    public DashboardDataExporter(PlotStorage plotStorage, DistrictManager districtManager) {
        this.plotStorage = plotStorage;
        this.districtManager = districtManager;
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
            District district = districtManager == null ? null : districtManager.getDistrictForPlot(plot);

            json.append("{\"type\":\"Feature\",\"geometry\":{\"type\":\"Polygon\",\"coordinates\":[[[")
                .append(plot.getMinX()).append(',').append(plot.getMinZ()).append("],[")
                .append(plot.getMaxX()).append(',').append(plot.getMinZ()).append("],[")
                .append(plot.getMaxX()).append(',').append(plot.getMaxZ()).append("],[")
                .append(plot.getMinX()).append(',').append(plot.getMaxZ()).append("],[")
                .append(plot.getMinX()).append(',').append(plot.getMinZ())
                .append("]]]},\"properties\":{")
                .append("\"plotId\":\"").append(escape(plot.getPlotId())).append("\",")
                .append("\"owner\":\"").append(formatOwner(plot.getOwner())).append("\",")
                .append("\"biome\":\"").append(biome.name()).append("\",")
                .append("\"state\":\"").append(plot.getState().name()).append("\",")
                .append("\"level\":").append(String.format(Locale.US, "%.2f", level)).append(',')
                .append("\"size\":").append(plot.getSize()).append(',')
                .append("\"districtId\":\"").append(district == null ? "" : escape(district.getId())).append("\",")
                .append("\"districtName\":\"").append(district == null ? "" : escape(district.getName())).append("\",")
                .append("\"districtLevel\":\"").append(district == null ? "" : district.getLevel().name()).append("\",")
                .append("\"districtPlots\":").append(district == null ? 0 : district.getPlotIds().size()).append(',')
                .append("\"districtSharedBank\":").append(district != null && district.isSharedBankEnabled()).append(',')
                .append("\"districtHasSpawn\":").append(district != null && district.hasSpawn())
                .append("}}");
        }
        json.append("]}");
        return json.toString();
    }

    private String formatOwner(UUID owner) {
        return owner == null ? "UNCLAIMED" : owner.toString();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
