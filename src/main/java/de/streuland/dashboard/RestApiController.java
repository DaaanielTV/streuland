package de.streuland.dashboard;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import de.streuland.analytics.InMemoryPlotAnalyticsService;
import de.streuland.analytics.PlotAnalyticsRecord;
import de.streuland.api.event.PlotUpgradedEvent;
import de.streuland.approval.PlotApprovalService;
import de.streuland.backup.PlotBackupCoordinator;
import de.streuland.district.District;
import de.streuland.district.DistrictManager;
import de.streuland.event.PlotClaimedEvent;
import de.streuland.event.PlotCreatedEvent;
import de.streuland.neighborhood.NeighborhoodService;
import de.streuland.plot.Permission;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final PlotApprovalService approvalService;
    private final PlotDashboardService plotDashboardService;
    private final DashboardAuthService authService;
    private final DistrictManager districtManager;
    private final PlotAuditLogService auditLogService;
    private final PlotBackupOperationsService backupOperationsService;
    private final Gson gson;
    private HttpServer httpServer;
    private DashboardWebSocketServer webSocketServer;

    public RestApiController(JavaPlugin plugin,
                             PlotManager plotManager,
                             NeighborhoodService neighborhoodService,
                             InMemoryPlotAnalyticsService analyticsService,
                             DashboardDataExporter dataExporter,
                             PlotMarketService marketService,
                             PlotApprovalService approvalService,
                             DistrictManager districtManager,
                             PlotBackupCoordinator backupCoordinator,
                             PlotAuditLogService auditLogService) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.neighborhoodService = neighborhoodService;
        this.analyticsService = analyticsService;
        this.dataExporter = dataExporter;
        this.marketService = marketService;
        this.approvalService = approvalService;
        this.districtManager = districtManager;
        this.plotDashboardService = new PlotDashboardService(plotManager, marketService);
        this.authService = new DashboardAuthService(plugin);
        this.auditLogService = auditLogService;
        this.backupOperationsService = new PlotBackupOperationsService(backupCoordinator, auditLogService);
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

        httpServer.createContext("/api/dashboard/plots", new PlotCollectionHandler());
        httpServer.createContext("/api/dashboard/plots/", new PlotDetailHandler());
        httpServer.createContext("/api/dashboard/ops/plots", new OpsPlotCollectionHandler());
        httpServer.createContext("/api/dashboard/ops/plots/", new OpsPlotDetailHandler());
        httpServer.createContext("/api/dashboard/ops/backups", new OpsBackupHandler());
        httpServer.createContext("/api/dashboard/ops/audit", new OpsAuditHandler());

        httpServer.createContext("/streuland-dashboard", new DashboardStaticHandler());
        httpServer.createContext("/api/approval/approve", exchange -> handleApproval(exchange, true));
        httpServer.createContext("/api/approval/reject", exchange -> handleApproval(exchange, false));
        httpServer.start();

        webSocketServer = new DashboardWebSocketServer(wsPort, plugin.getLogger(), authService);
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
        auditLogService.record(plot.getPlotId(), "plot_create", "SYSTEM", Collections.<String, Object>emptyMap());
        broadcastAudit(plot.getPlotId());
        broadcast("plot_created", plot.getPlotId());
    }

    @EventHandler
    public void onPlotClaimed(PlotClaimedEvent event) {
        Plot plot = event.getPlot();
        auditLogService.record(plot.getPlotId(), "claim", "SYSTEM", Collections.<String, Object>emptyMap());
        broadcastAudit(plot.getPlotId());
    }

    @EventHandler
    public void onPlotUpgraded(PlotUpgradedEvent event) {
        analyticsService.record(new PlotAnalyticsRecord(event.getPlotId(), null, "plot_upgraded", Instant.now(), event.getToLevel()));
        Map<String, Object> metadata = new LinkedHashMap<String, Object>();
        metadata.put("fromLevel", event.getFromLevel());
        metadata.put("toLevel", event.getToLevel());
        auditLogService.record(event.getPlotId(), "upgrade_change", "SYSTEM", metadata);
        broadcastAudit(event.getPlotId());
        broadcast("plot_upgraded", event.getPlotId());
    }

    private void broadcast(String type, String plotId) {
        if (webSocketServer == null) {
            return;
        }
        Map<String, Object> event = new HashMap<String, Object>();
        event.put("type", type);
        event.put("plotId", plotId);
        event.put("plot", plotDashboardService.getPlotDetails(plotId));
        event.put("ts", System.currentTimeMillis());
        webSocketServer.broadcastJson(gson.toJson(event));
    }

    private void broadcastAudit(String plotId) {
        if (webSocketServer == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("type", "audit_event");
        payload.put("plotId", plotId);
        payload.put("events", toAuditRows(auditLogService.listByPlot(plotId, 10)));
        payload.put("ts", System.currentTimeMillis());
        webSocketServer.broadcastJson(gson.toJson(payload));
    }

    private String buildLiveHeatmapJson() {
        List<PlotAnalyticsRecord> records = analyticsService.getRecordsSnapshot();
        long cutoff = Instant.now().minusSeconds(15 * 60).toEpochMilli();
        Map<String, Integer> byPlot = new HashMap<String, Integer>();
        for (PlotAnalyticsRecord record : records) {
            if (record.getTimestamp().toEpochMilli() >= cutoff && record.getPlotId() != null) {
                byPlot.put(record.getPlotId(), byPlot.getOrDefault(record.getPlotId(), 0) + 1);
            }
        }

        List<Map<String, Object>> points = new ArrayList<Map<String, Object>>();
        for (Plot plot : plotManager.getAllPlots()) {
            int intensity = byPlot.getOrDefault(plot.getPlotId(), 0);
            if (intensity > 0) {
                Map<String, Object> point = new HashMap<String, Object>();
                point.put("plotId", plot.getPlotId());
                point.put("x", plot.getCenterX());
                point.put("z", plot.getCenterZ());
                point.put("intensity", intensity);
                points.add(point);
            }
        }
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("windowMinutes", 15);
        payload.put("points", points);
        return gson.toJson(payload);
    }

    private String buildMarketListingsJson() {
        List<MarketListing> listings = marketService.getActiveListingsSnapshot();
        List<MarketSale> sales = marketService.getSalesHistorySnapshot();

        List<Map<String, Object>> payload = new ArrayList<Map<String, Object>>();
        for (MarketListing listing : listings) {
            Map<String, Object> row = new HashMap<String, Object>();
            row.put("plotId", listing.getPlotId());
            row.put("price", listing.getPrice());
            row.put("biome", listing.getBiome());
            row.put("level", listing.getLevel());
            row.put("districtTier", listing.getDistrictTier());
            row.put("valuation", listing.getValuation());
            row.put("listedAt", listing.getTimestamp());
            List<Map<String, Object>> history = new ArrayList<Map<String, Object>>();
            for (MarketSale sale : sales) {
                if (listing.getPlotId().equalsIgnoreCase(sale.getPlotId())) {
                    Map<String, Object> p = new HashMap<String, Object>();
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
        Map<String, Map<String, Object>> data = new TreeMap<String, Map<String, Object>>();
        for (Plot plot : plotManager.getAllPlots()) {
            Biome biome = world.getBlockAt(plot.getCenterX(), plot.getSpawnY(), plot.getCenterZ()).getBiome();
            Map<String, Object> aggregate = data.computeIfAbsent(biome.name(), ignored -> {
                Map<String, Object> init = new HashMap<String, Object>();
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

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> aggregate : data.values()) {
            Set<String> owners = (Set<String>) aggregate.remove("owners");
            aggregate.put("playerCount", owners.size());
            rows.add(aggregate);
        }
        return gson.toJson(Collections.singletonMap("biomes", rows));
    }

    private void handleApproval(HttpExchange exchange, boolean approve) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String id = query.get("id");
        String token = query.get("token");
        boolean success = id != null && token != null && (approve ? approvalService.approveByToken(id, token) : approvalService.rejectByToken(id, token));
        if (!success) {
            exchange.sendResponseHeaders(403, -1);
            return;
        }
        writeText(exchange, approve ? "Approval successful" : "Rejection successful", "text/plain");
    }

    private class PlotCollectionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            if (!authService.isAuthorized(exchange)) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            List<Map<String, Object>> plots = plotDashboardService.listPlots(
                    query.get("search"),
                    query.get("owner"),
                    query.get("areaType"),
                    query.get("marketStatus"));
            writeJson(exchange, gson.toJson(Collections.singletonMap("plots", plots)));
        }
    }

    private class PlotDetailHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!authService.isAuthorized(exchange)) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String suffix = path.substring("/api/dashboard/plots/".length());
            String[] parts = suffix.split("/");
            if (parts.length == 0 || parts[0].trim().isEmpty()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            String plotId = parts[0];
            if (parts.length == 1 && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, Object> detail = plotDashboardService.getPlotDetails(plotId);
                if (detail == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                writeJson(exchange, gson.toJson(detail));
                return;
            }

            if (parts.length == 2 && "permissions".equals(parts[1]) && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
                UUID player = toUuid(query.get("player"));
                Permission permission = toPermission(query.get("permission"));
                Map<String, Object> result = plotDashboardService.checkPermission(plotId, player, permission);
                if (result == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                writeJson(exchange, gson.toJson(result));
                return;
            }

            if (parts.length == 2 && "trusted".equals(parts[1]) && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                if (!authService.isAuthorized(exchange, DashboardRole.OPERATOR)) {
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }
                JsonObject payload = gson.fromJson(readBody(exchange), JsonObject.class);
                UUID actor = payload != null && payload.has("actor") ? toUuid(payload.get("actor").getAsString()) : null;
                UUID target = payload != null && payload.has("target") ? toUuid(payload.get("target").getAsString()) : null;
                String action = payload != null && payload.has("action") ? payload.get("action").getAsString() : null;
                boolean success = plotDashboardService.mutateTrustedPlayer(plotId, actor, target, action);
                if (!success) {
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }
                Map<String, Object> metadata = new LinkedHashMap<String, Object>();
                metadata.put("target", target == null ? null : target.toString());
                metadata.put("action", action);
                auditLogService.record(plotId, "add".equalsIgnoreCase(action) ? "trust" : "untrust",
                        actor == null ? "UNKNOWN" : actor.toString(), metadata);
                broadcastAudit(plotId);
                broadcast("plot_updated", plotId);
                writeJson(exchange, gson.toJson(Collections.singletonMap("success", true)));
                return;
            }

            exchange.sendResponseHeaders(404, -1);
        }
    }

    private Map<String, String> parseQuery(String raw) {
        Map<String, String> map = new HashMap<String, String>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        String[] parts = raw.split("&");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0], kv[1]);
            }
        }
        return map;
    }

    private UUID toUuid(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Permission toPermission(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Permission.BUILD;
        }
        try {
            return Permission.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return Permission.BUILD;
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            byte[] bytes = readAllBytes(input);
            return new String(bytes, StandardCharsets.UTF_8);
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
            if (!authService.isAuthorized(exchange)) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }
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

    private class OpsPlotCollectionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!authService.isAuthorized(exchange, DashboardRole.VIEWER)) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            List<Map<String, Object>> rows = plotDashboardService.listPlots(
                    query.get("search"),
                    query.get("owner"),
                    query.get("areaType"),
                    query.get("marketStatus"));
            writeJson(exchange, gson.toJson(Collections.singletonMap("plots", rows)));
        }
    }

    private class OpsPlotDetailHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!authService.isAuthorized(exchange, DashboardRole.VIEWER)) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }
            String path = exchange.getRequestURI().getPath();
            String suffix = path.substring("/api/dashboard/ops/plots/".length());
            String[] parts = suffix.split("/");
            if (parts.length == 0 || parts[0].trim().isEmpty()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            String plotId = parts[0];
            Map<String, Object> detail = plotDashboardService.getPlotDetails(plotId);
            if (detail == null) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }
            detail.put("audit", toAuditRows(auditLogService.listByPlot(plotId, 100)));
            detail.put("district", districtPayload(plotId));
            writeJson(exchange, gson.toJson(detail));
        }
    }

    private class OpsBackupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            String plotId = query.get("plotId");
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                if (!authService.isAuthorized(exchange, DashboardRole.VIEWER)) {
                    exchange.sendResponseHeaders(401, -1);
                    return;
                }
                writeJson(exchange, gson.toJson(Collections.singletonMap("backups", backupOperationsService.listBackups(plotId))));
                return;
            }
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                if (!authService.isAuthorized(exchange, DashboardRole.ADMIN)) {
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }
                String action = query.getOrDefault("action", "create");
                String actor = query.getOrDefault("actor", "dashboard");
                if ("restore".equalsIgnoreCase(action)) {
                    String snapshotId = query.get("snapshotId");
                    Map<String, Object> restored = backupOperationsService.restoreBackup(plotId, snapshotId, UUID.randomUUID(), actor).join();
                    if (restored == null) {
                        exchange.sendResponseHeaders(400, -1);
                        return;
                    }
                    broadcastAudit(plotId);
                    writeJson(exchange, gson.toJson(restored));
                    return;
                }
                Map<String, Object> created = backupOperationsService.createBackup(plotId, UUID.randomUUID(), actor, "dashboard-manual").join();
                if (created == null) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                broadcastAudit(plotId);
                writeJson(exchange, gson.toJson(created));
                return;
            }
            exchange.sendResponseHeaders(405, -1);
        }
    }

    private class OpsAuditHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!authService.isAuthorized(exchange, DashboardRole.VIEWER)) {
                exchange.sendResponseHeaders(401, -1);
                return;
            }
            Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
            int limit = 100;
            try {
                limit = Integer.parseInt(query.getOrDefault("limit", "100"));
            } catch (NumberFormatException ignored) {
            }
            String plotId = query.get("plotId");
            List<PlotAuditEvent> events = plotId == null
                    ? auditLogService.listRecent(limit)
                    : auditLogService.listByPlot(plotId, limit);
            writeJson(exchange, gson.toJson(Collections.singletonMap("events", toAuditRows(events))));
        }
    }

    private List<Map<String, Object>> toAuditRows(List<PlotAuditEvent> events) {
        return events.stream().map(event -> {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("id", event.getEventId());
            row.put("plotId", event.getPlotId());
            row.put("action", event.getAction());
            row.put("actor", event.getActor());
            row.put("timestamp", event.getTimestamp().toString());
            row.put("metadata", event.getMetadata());
            return row;
        }).collect(Collectors.toList());
    }

    private Map<String, Object> districtPayload(String plotId) {
        if (districtManager == null) {
            return null;
        }
        Plot plot = plotManager.getStorage().getPlot(plotId);
        if (plot == null) {
            return null;
        }
        District district = districtManager.getDistrictForPlot(plot);
        if (district == null) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("id", district.getId());
        payload.put("name", district.getName());
        payload.put("level", district.getLevel().name());
        payload.put("plotIds", new ArrayList<String>(district.getPlotIds()));
        return payload;
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
    }
}
