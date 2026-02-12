package de.streuland.dashboard;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import de.streuland.analytics.InMemoryPlotAnalyticsService;
import de.streuland.analytics.PlotAnalyticsRecord;
import de.streuland.api.event.PlotUpgradedEvent;
import de.streuland.district.District;
import de.streuland.district.DistrictManager;
import de.streuland.event.PlotCreatedEvent;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Hosts REST endpoints and dashboard assets.
 */
public class RestApiController implements Listener {
    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final DistrictManager districtManager;
    private final InMemoryPlotAnalyticsService analyticsService;
    private final PlotAnalyticsExporter analyticsExporter;
    private HttpServer httpServer;
    private DashboardWebSocketServer webSocketServer;

    public RestApiController(JavaPlugin plugin,
                             PlotManager plotManager,
                             DistrictManager districtManager,
                             InMemoryPlotAnalyticsService analyticsService,
                             PlotAnalyticsExporter analyticsExporter) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.districtManager = districtManager;
        this.analyticsService = analyticsService;
        this.analyticsExporter = analyticsExporter;
    }

    public void start() throws IOException {
        int port = plugin.getConfig().getInt("dashboard.port", 8080);
        int wsPort = plugin.getConfig().getInt("dashboard.websocket-port", 8081);
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext("/api/plots", exchange -> writeJson(exchange, buildPlotsJson()));
        httpServer.createContext("/api/biomes", exchange -> writeJson(exchange, buildBiomeBreakdownJson()));
        httpServer.createContext("/api/upgrades", exchange -> writeJson(exchange, buildUpgradeStatsJson()));
        httpServer.createContext("/api/plots/geojson", exchange -> writeJson(exchange, analyticsExporter.toGeoJson(plotManager.getAllPlots())));
        httpServer.createContext("/api/districts", new DistrictHeatmapHandler());
        httpServer.createContext("/streuland-dashboard", new DashboardStaticHandler());
        httpServer.start();

        webSocketServer = new DashboardWebSocketServer(wsPort, plugin.getLogger());
        webSocketServer.start();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("Dashboard API listening on http://0.0.0.0:" + port + "/streuland-dashboard");
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
        if (webSocketServer != null) {
            try {
                webSocketServer.stop();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @EventHandler
    public void onPlotCreated(PlotCreatedEvent event) {
        Plot plot = event.getPlot();
        analyticsService.record(new PlotAnalyticsRecord(plot.getPlotId(), plot.getOwner(), "plot_created", Instant.now(), 1));
        if (webSocketServer != null) {
            webSocketServer.broadcastJson("{\"type\":\"plot_created\",\"plotId\":\"" + plot.getPlotId() + "\",\"x\":" + plot.getCenterX() + ",\"z\":" + plot.getCenterZ() + "}");
        }
    }

    @EventHandler
    public void onPlotUpgraded(PlotUpgradedEvent event) {
        analyticsService.record(new PlotAnalyticsRecord(event.getPlotId(), null, "plot_upgraded", Instant.now(), event.getToLevel()));
        if (webSocketServer != null) {
            webSocketServer.broadcastJson("{\"type\":\"plot_upgraded\",\"plotId\":\"" + event.getPlotId() + "\",\"fromLevel\":" + event.getFromLevel() + ",\"toLevel\":" + event.getToLevel() + "}");
        }
    }

    private String buildPlotsJson() {
        Collection<Plot> plots = plotManager.getAllPlots();
        List<PlotAnalyticsRecord> records = analyticsService.getRecordsSnapshot();
        int claimed = 0;
        StringBuilder json = new StringBuilder("{\"total\":").append(plots.size()).append(",\"claimed\":");
        for (Plot plot : plots) {
            if (plot.getOwner() != null) {
                claimed++;
            }
        }
        json.append(claimed).append(",\"unclaimed\":").append(plots.size() - claimed).append(",\"plots\":[");
        boolean first = true;
        for (Plot plot : plots) {
            if (!first) {
                json.append(',');
            }
            first = false;
            int eventCount = 0;
            for (PlotAnalyticsRecord record : records) {
                if (plot.getPlotId().equals(record.getPlotId())) {
                    eventCount++;
                }
            }
            json.append("{\"plotId\":\"").append(plot.getPlotId()).append("\",\"x\":").append(plot.getCenterX())
                .append(",\"z\":").append(plot.getCenterZ()).append(",\"size\":").append(plot.getSize())
                .append(",\"state\":\"").append(plot.getState().name()).append("\",\"analyticsEvents\":").append(eventCount).append("}");
        }
        json.append("]}");
        return json.toString();
    }

    private String buildBiomeBreakdownJson() {
        World world = plotManager.getWorld();
        Map<String, Integer> counts = new HashMap<>();
        for (Plot plot : plotManager.getAllPlots()) {
            String biome = world.getBiome(plot.getCenterX(), plot.getCenterZ()).name();
            counts.put(biome, counts.getOrDefault(biome, 0) + 1);
        }
        StringBuilder json = new StringBuilder("{\"biomes\":[");
        boolean first = true;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append("{\"biome\":\"").append(entry.getKey()).append("\",\"plots\":").append(entry.getValue()).append("}");
        }
        json.append("]}");
        return json.toString();
    }

    private String buildUpgradeStatsJson() {
        List<PlotAnalyticsRecord> records = analyticsService.getRecordsSnapshot();
        int totalUpgrades = 0;
        double averageLevel = 0;
        for (PlotAnalyticsRecord record : records) {
            if ("plot_upgraded".equals(record.getEventType())) {
                totalUpgrades++;
                averageLevel += record.getValue();
            }
        }
        if (totalUpgrades > 0) {
            averageLevel /= totalUpgrades;
        }
        return "{\"totalUpgrades\":" + totalUpgrades + ",\"averageUpgradeLevel\":" + String.format(Locale.US, "%.2f", averageLevel) + "}";
    }

    private String buildDistrictHeatmapJson(String districtId) {
        District district = districtManager.getDistrictById(districtId);
        if (district == null || district.getPlotIds().isEmpty()) {
            return "{\"districtId\":\"" + districtId + "\",\"grid\":[],\"cellSize\":64}";
        }

        List<Plot> districtPlots = new ArrayList<>();
        for (String plotId : district.getPlotIds()) {
            for (Plot plot : plotManager.getAllPlots()) {
                if (plotId.equals(plot.getPlotId())) {
                    districtPlots.add(plot);
                    break;
                }
            }
        }

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Plot plot : districtPlots) {
            minX = Math.min(minX, plot.getCenterX());
            maxX = Math.max(maxX, plot.getCenterX());
            minZ = Math.min(minZ, plot.getCenterZ());
            maxZ = Math.max(maxZ, plot.getCenterZ());
        }

        int cellSize = plugin.getConfig().getInt("dashboard.heatmap-cell-size", 64);
        int width = ((maxX - minX) / cellSize) + 1;
        int height = ((maxZ - minZ) / cellSize) + 1;
        int[][] grid = new int[height][width];

        for (Plot plot : districtPlots) {
            int x = (plot.getCenterX() - minX) / cellSize;
            int z = (plot.getCenterZ() - minZ) / cellSize;
            grid[z][x] += 1;
        }

        StringBuilder json = new StringBuilder("{\"districtId\":\"").append(districtId)
                .append("\",\"origin\":{\"x\":").append(minX).append(",\"z\":").append(minZ).append("},\"cellSize\":").append(cellSize).append(",\"grid\":[");
        for (int z = 0; z < height; z++) {
            if (z > 0) {
                json.append(',');
            }
            json.append('[');
            for (int x = 0; x < width; x++) {
                if (x > 0) {
                    json.append(',');
                }
                json.append(grid[z][x]);
            }
            json.append(']');
        }
        json.append("]}");
        return json.toString();
    }

    private void writeJson(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private void writeText(HttpExchange exchange, String text, String contentType) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private class DistrictHeatmapHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            if (parts.length == 5 && "api".equals(parts[1]) && "districts".equals(parts[2]) && "heatmap".equals(parts[4])) {
                writeJson(exchange, buildDistrictHeatmapJson(parts[3]));
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
        }
    }

    private class DashboardStaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/streuland-dashboard".equals(path) || "/streuland-dashboard/".equals(path)) {
                serveResource(exchange, "dashboard/index.html", "text/html");
                return;
            }
            if ("/streuland-dashboard/style.css".equals(path)) {
                serveResource(exchange, "dashboard/style.css", "text/css");
                return;
            }
            if ("/streuland-dashboard/app.js".equals(path)) {
                serveResource(exchange, "dashboard/app.js", "application/javascript");
                return;
            }
            if ("/streuland-dashboard/config.yml".equals(path)) {
                writeText(exchange, plugin.getConfig().saveToString(), "text/yaml");
                return;
            }
            exchange.sendResponseHeaders(404, -1);
        }

        private void serveResource(HttpExchange exchange, String resourcePath, String contentType) throws IOException {
            try (InputStream input = plugin.getResource(resourcePath)) {
                if (input == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                byte[] content = readAllBytes(input);
                exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
                exchange.sendResponseHeaders(200, content.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(content);
                }
            }
        }

        private byte[] readAllBytes(InputStream input) throws IOException {
            byte[] buffer = new byte[4096];
            int read;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
