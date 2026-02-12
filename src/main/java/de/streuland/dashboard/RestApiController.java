package de.streuland.dashboard;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import de.streuland.analytics.InMemoryPlotAnalyticsService;
import de.streuland.analytics.PlotAnalyticsRecord;
import de.streuland.api.event.PlotUpgradedEvent;
import de.streuland.event.PlotCreatedEvent;
import de.streuland.neighborhood.NeighborhoodService;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.market.MarketListing;
import de.streuland.plot.market.MarketSale;
import de.streuland.plot.market.PlotMarketService;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * Hosts dashboard REST endpoints and static assets.
 */
public class RestApiController implements Listener {
    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final NeighborhoodService neighborhoodService;
    private final InMemoryPlotAnalyticsService analyticsService;
    private final DashboardDataExporter dataExporter;
    private final PlotMarketService marketService;
    private final Gson gson;
    private HttpServer httpServer;
    private DashboardWebSocketServer webSocketServer;

    public RestApiController(JavaPlugin plugin,
                             PlotManager plotManager,
                             NeighborhoodService neighborhoodService,
                             InMemoryPlotAnalyticsService analyticsService,
                             DashboardDataExporter dataExporter,
                             PlotMarketService marketService) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.neighborhoodService = neighborhoodService;
        this.analyticsService = analyticsService;
        this.dataExporter = dataExporter;
        this.marketService = marketService;
        this.gson = new Gson();
    }

    public void start() throws IOException {
        int port = plugin.getConfig().getInt("dashboard.port", 8080);
        int wsPort = plugin.getConfig().getInt("dashboard.websocket-port", 8081);
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.createContext("/api/map/plots", exchange -> writeJson(exchange, dataExporter.toGeoJson(plotManager.getAllPlots(), plotManager.getWorld())));
        httpServer.createContext("/api/map/heatmap", exchange -> writeJson(exchange, buildLiveHeatmapJson()));
        httpServer.createContext("/api/neighborhoods", new NeighborhoodHandler());
        httpServer.createContext("/api/market/listings", exchange -> writeJson(exchange, buildMarketListingsJson()));
        httpServer.createContext("/api/biomes/stats", exchange -> writeJson(exchange, buildBiomeStatsJson()));
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
        broadcast("plot_created", plot.getPlotId());
    }

    @EventHandler
    public void onPlotUpgraded(PlotUpgradedEvent event) {
        analyticsService.record(new PlotAnalyticsRecord(event.getPlotId(), null, "plot_upgraded", Instant.now(), event.getToLevel()));
        broadcast("plot_upgraded", event.getPlotId());
    }

    private void broadcast(String type, String plotId) {
        if (webSocketServer == null) {
            return;
        }
        Map<String, Object> event = new HashMap<>();
        event.put("type", type);
        event.put("plotId", plotId);
        event.put("ts", System.currentTimeMillis());
        webSocketServer.broadcastJson(gson.toJson(event));
    }

    private String buildLiveHeatmapJson() {
        List<PlotAnalyticsRecord> records = analyticsService.getRecordsSnapshot();
        long cutoff = Instant.now().minusSeconds(15 * 60).toEpochMilli();
        Map<String, Integer> byPlot = new HashMap<>();
        for (PlotAnalyticsRecord record : records) {
            if (record.getTimestamp().toEpochMilli() >= cutoff && record.getPlotId() != null) {
                byPlot.put(record.getPlotId(), byPlot.getOrDefault(record.getPlotId(), 0) + 1);
            }
        }

        List<Map<String, Object>> points = new ArrayList<>();
        for (Plot plot : plotManager.getAllPlots()) {
            int intensity = byPlot.getOrDefault(plot.getPlotId(), 0);
            if (intensity > 0) {
                Map<String, Object> point = new HashMap<>();
                point.put("plotId", plot.getPlotId());
                point.put("x", plot.getCenterX());
                point.put("z", plot.getCenterZ());
                point.put("intensity", intensity);
                points.add(point);
            }
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("windowMinutes", 15);
        payload.put("points", points);
        return gson.toJson(payload);
    }

    private String buildMarketListingsJson() {
        List<MarketListing> listings = marketService.getActiveListingsSnapshot();
        List<MarketSale> sales = marketService.getSalesHistorySnapshot();

        List<Map<String, Object>> payload = new ArrayList<>();
        for (MarketListing listing : listings) {
            Map<String, Object> row = new HashMap<>();
            row.put("plotId", listing.getPlotId());
            row.put("price", listing.getPrice());
            row.put("biome", listing.getBiome());
            row.put("level", listing.getLevel());
            row.put("districtTier", listing.getDistrictTier());
            row.put("valuation", listing.getValuation());
            row.put("listedAt", listing.getTimestamp());
            List<Map<String, Object>> history = new ArrayList<>();
            for (MarketSale sale : sales) {
                if (listing.getPlotId().equalsIgnoreCase(sale.getPlotId())) {
                    Map<String, Object> p = new HashMap<>();
                    p.put("price", sale.getPrice());
                    p.put("timestamp", sale.getTimestamp());
                    history.add(p);
                }
            }
            row.put("priceHistory", history);
            payload.add(row);
        }
        return gson.toJson(Collections.singletonMap("listings", payload));
    }

    private String buildBiomeStatsJson() {
        World world = plotManager.getWorld();
        Map<String, Map<String, Object>> data = new TreeMap<>();
        for (Plot plot : plotManager.getAllPlots()) {
            Biome biome = world.getBlockAt(plot.getCenterX(), plot.getSpawnY(), plot.getCenterZ()).getBiome();
            Map<String, Object> aggregate = data.computeIfAbsent(biome.name(), ignored -> {
                Map<String, Object> init = new HashMap<>();
                init.put("biome", biome.name());
                init.put("totalArea", 0);
                init.put("playerCount", 0);
                init.put("owners", new HashSet<String>());
                return init;
            });
            aggregate.put("totalArea", (Integer) aggregate.get("totalArea") + (plot.getSize() * plot.getSize()));
            if (plot.getOwner() != null) {
                ((Set<String>) aggregate.get("owners")).add(plot.getOwner().toString());
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> aggregate : data.values()) {
            Set<String> owners = (Set<String>) aggregate.remove("owners");
            aggregate.put("playerCount", owners.size());
            rows.add(aggregate);
        }
        return gson.toJson(Collections.singletonMap("biomes", rows));
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

    private class NeighborhoodHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            if (parts.length == 4) {
                String plotId = parts[3];
                writeJson(exchange, gson.toJson(neighborhoodService.buildNeighborhoodGraph(plotId)));
                return;
            }
            exchange.sendResponseHeaders(404, -1);
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
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            while ((read = input.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
