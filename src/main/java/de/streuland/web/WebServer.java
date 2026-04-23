package de.streuland.web;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import de.streuland.plot.Plot;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

public class WebServer {
    public interface PlotGateway {
        Collection<Plot> getAllPlots();
        Plot getPlot(String plotId);
        boolean setFeatured(String plotId, boolean featured);
        boolean isFeatured(String plotId);
    }

    private final String host;
    private final int port;
    private final String token;
    private final PlotGateway plotGateway;
    private final AdminObservabilityService observabilityService;
    // Optional: invitation store/backend (MVP in-memory)
    private final de.streuland.invite.InvitationGateway invitationGateway;
    private final de.streuland.auth.UserGateway userGateway;
    private final Logger logger;
    private final Gson gson = new Gson();
    private HttpServer server;

    // Composite handler for signup-with-code (GET serves page, POST processes signup)
    private class SignUpCompositeHandler implements HttpHandler {
        private final InvitationGateway invitationGateway;
        private final UserGateway userGateway;
        private final de.streuland.auth.SignUpWithCodeHandler delegate;

        SignUpCompositeHandler(InvitationGateway invitationGateway, UserGateway userGateway) {
            this.invitationGateway = invitationGateway;
            this.userGateway = userGateway;
            // Use a dedicated handler for POST to actually process signup
            this.delegate = new de.streuland.auth.SignUpWithCodeHandler(invitationGateway, userGateway);
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                String lang = Optional.ofNullable(exchange.getRequestHeaders().getFirst("Accept-Language")).orElse("");
                String resource = lang.toLowerCase().contains("de") ? "web/static/signup_de.html" : "web/static/signup.html";
                try {
                    serveResource(exchange, resource, "text/html");
                } catch (Exception e) {
                    exchange.sendResponseHeaders(500, -1);
                }
                return;
            }
            // POST: delegate to actual signup handler
            delegate.handle(exchange);
        }
    }

    public WebServer(String host, int port, String token, PlotGateway plotGateway, Logger logger) {
        this(host, port, token, plotGateway, null, null, null, logger);
    }

    public WebServer(String host, int port, String token, PlotGateway plotGateway, AdminObservabilityService observabilityService, Logger logger) {
        this.host = host;
        this.port = port;
        this.token = token == null ? "" : token.trim();
        this.plotGateway = plotGateway;
        this.observabilityService = observabilityService == null ? new AdminObservabilityService(plotGateway, null) : observabilityService;
        this.logger = logger;
        this.invitationGateway = null;
        this.userGateway = null;
    }

    // Extended constructor to allow wiring an invitation gateway (optional)
    public WebServer(String host, int port, String token, PlotGateway plotGateway, AdminObservabilityService observabilityService, de.streuland.invite.InvitationGateway invitationGateway, de.streuland.auth.UserGateway userGateway, Logger logger) {
        this.host = host;
        this.port = port;
        this.token = token == null ? "" : token.trim();
        this.plotGateway = plotGateway;
        this.observabilityService = observabilityService == null ? new AdminObservabilityService(plotGateway, null) : observabilityService;
        this.logger = logger;
        this.invitationGateway = invitationGateway;
        this.userGateway = userGateway;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/", new RootHandler());
        server.createContext("/api/", new ApiHandler());
        // Sign-up flow endpoints (Sign-Up via Invitation Code)
        if (invitationGateway != null && userGateway != null) {
            server.createContext("/auth/signup-with-code", new SignUpCompositeHandler(invitationGateway, userGateway));
            server.createContext("/auth/validate-invitation", new de.streuland.auth.ValidateInvitationHandler(invitationGateway));
        }
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if ("/".equals(path) || "/index.html".equals(path)) {
                serveResource(exchange, "web/static/index.html", "text/html");
                return;
            }
            sendJson(exchange, 404, error("not_found", "Unknown endpoint"));
        }
    }


    String routeKey(String method, String path) {
        if ("GET".equalsIgnoreCase(method) && "/api/plots".equals(path)) return "plots_list";
        if ("GET".equalsIgnoreCase(method) && "/api/plots/export.csv".equals(path)) return "plots_export";
        if ("GET".equalsIgnoreCase(method) && "/api/admin/plot-status".equals(path)) return "admin_plot_status";
        if ("GET".equalsIgnoreCase(method) && "/api/admin/recent-changes".equals(path)) return "admin_recent_changes";
        if ("GET".equalsIgnoreCase(method) && "/api/admin/health".equals(path)) return "admin_health";
        if ("GET".equalsIgnoreCase(method) && "/api/admin/dashboard".equals(path)) return "admin_dashboard";
        if (path.startsWith("/api/plots/")) {
            String tail = path.substring("/api/plots/".length());
            if (tail.endsWith("/feature") && "POST".equalsIgnoreCase(method)) return "plot_feature";
            if ("GET".equalsIgnoreCase(method) && !tail.contains("/")) return "plot_detail";
        }
        return "not_found";
    }

    private class ApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthorized(exchange)) {
                logUnauthorized(exchange);
                sendJson(exchange, 401, error("unauthorized", "Missing or invalid admin token"));
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();

            String route = routeKey(method, path);
            if ("plots_list".equals(route)) {
                sendJson(exchange, 200, gson.toJson(toSummaries(plotGateway.getAllPlots())));
                return;
            }
            if ("plots_export".equals(route)) {
                sendCsv(exchange, 200, buildCsv(plotGateway.getAllPlots()));
                return;
            }
            if ("plot_feature".equals(route)) {
                String tail = path.substring("/api/plots/".length());
                String id = tail.substring(0, tail.length() - "/feature".length());
                handleToggle(exchange, id);
                return;
            }
            if ("admin_plot_status".equals(route)) {
                sendJson(exchange, 200, gson.toJson(observabilityService.buildPlotStatus()));
                return;
            }
            if ("admin_recent_changes".equals(route)) {
                Integer limit = parseLimit(exchange.getRequestURI().getRawQuery());
                sendJson(exchange, 200, gson.toJson(observabilityService.buildRecentChanges(limit)));
                return;
            }
            if ("admin_health".equals(route)) {
                sendJson(exchange, 200, gson.toJson(observabilityService.buildHealth()));
                return;
            }
            if ("admin_dashboard".equals(route)) {
                Integer limit = parseLimit(exchange.getRequestURI().getRawQuery());
                sendJson(exchange, 200, gson.toJson(observabilityService.buildSummary(limit)));
                return;
            }
            if ("plot_detail".equals(route)) {
                String id = path.substring("/api/plots/".length());
                handleDetail(exchange, id);
                return;
            }
            sendJson(exchange, 404, error("not_found", "Unknown endpoint"));
        }
    }

    private void handleDetail(HttpExchange exchange, String plotId) throws IOException {
        Plot plot = plotGateway.getPlot(plotId);
        if (plot == null) {
            sendJson(exchange, 404, error("not_found", "Plot not found"));
            return;
        }
        Map<String, Object> row = baseRow(plot);
        row.put("featured", plotGateway.isFeatured(plotId));
        List<String> trusted = new ArrayList<>();
        for (UUID uuid : plot.getTrustedPlayers()) trusted.add(uuid.toString());
        Collections.sort(trusted);
        row.put("trustedPlayers", trusted);
        sendJson(exchange, 200, gson.toJson(row));
    }

    private void handleToggle(HttpExchange exchange, String plotId) throws IOException {
        Plot plot = plotGateway.getPlot(plotId);
        if (plot == null) {
            sendJson(exchange, 404, error("not_found", "Plot not found"));
            return;
        }
        boolean next = !plotGateway.isFeatured(plotId);
        if (!plotGateway.setFeatured(plotId, next)) {
            sendJson(exchange, 500, error("update_failed", "Could not update featured state"));
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", plotId);
        payload.put("featured", next);
        sendJson(exchange, 200, gson.toJson(payload));
    }

    private List<Map<String, Object>> toSummaries(Collection<Plot> plots) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Plot plot : plots) {
            Map<String, Object> row = baseRow(plot);
            row.put("featured", plotGateway.isFeatured(plot.getPlotId()));
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> baseRow(Plot plot) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", plot.getPlotId());
        row.put("coords", plot.getCenterX() + "," + plot.getCenterZ());
        row.put("owner", plot.getOwner() == null ? null : plot.getOwner().toString());
        row.put("areaType", plot.getAreaType().name());
        row.put("trustedCount", plot.getTrustedPlayers().size());
        return row;
    }

    private String buildCsv(Collection<Plot> plots) {
        StringBuilder sb = new StringBuilder();
        sb.append("id,coords,owner,areaType,trustedCount,featured\n");
        for (Plot plot : plots) {
            String owner = plot.getOwner() == null ? "" : plot.getOwner().toString();
            sb.append(q(plot.getPlotId())).append(',')
                    .append(q(plot.getCenterX() + "," + plot.getCenterZ())).append(',')
                    .append(q(owner)).append(',')
                    .append(q(plot.getAreaType().name())).append(',')
                    .append(plot.getTrustedPlayers().size()).append(',')
                    .append(plotGateway.isFeatured(plot.getPlotId()))
                    .append('\n');
        }
        return sb.toString();
    }

    private String q(String in) {
        return '"' + in.replace("\"", "\"\"") + '"';
    }

    private Integer parseLimit(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return null;
        }
        for (String part : rawQuery.split("&")) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length != 2 || !"limit".equalsIgnoreCase(keyValue[0])) {
                continue;
            }
            try {
                return Integer.parseInt(keyValue[1]);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isAuthorized(HttpExchange exchange) {
        if (isLocal(exchange.getRemoteAddress().getAddress())) {
            return true;
        }
        if (token.isEmpty()) {
            return false;
        }
        String auth = exchange.getRequestHeaders().getFirst("Authorization");
        return ("Bearer " + token).equals(auth);
    }

    private boolean isLocal(InetAddress address) {
        return address != null && (address.isAnyLocalAddress() || address.isLoopbackAddress());
    }

    private void logUnauthorized(HttpExchange exchange) {
        logger.warning("Unauthorized web API request from " + exchange.getRemoteAddress() + " " + exchange.getRequestMethod() + " " + exchange.getRequestURI());
    }

    private String error(String code, String message) {
        Map<String, String> map = new HashMap<>();
        map.put("error", code);
        map.put("message", message);
        return gson.toJson(map);
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private void sendCsv(HttpExchange exchange, int status, String csv) throws IOException {
        byte[] body = csv.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/csv; charset=utf-8");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=plots.csv");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private void serveResource(HttpExchange exchange, String path, String contentType) throws IOException {
        try (InputStream input = WebServer.class.getClassLoader().getResourceAsStream(path)) {
            if (input == null) {
                sendJson(exchange, 404, error("not_found", "Resource not found"));
                return;
            }
            byte[] data = readAllBytes(input);
            exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(data);
            }
        }
    }

    private byte[] readAllBytes(InputStream input) throws IOException {
        byte[] buffer = new byte[4096];
        int read;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    public static class PlotGatewayAdapter implements PlotGateway {
        private final de.streuland.plot.PlotManager plotManager;

        public PlotGatewayAdapter(de.streuland.plot.PlotManager plotManager) {
            this.plotManager = plotManager;
        }

        @Override
        public Collection<Plot> getAllPlots() {
            return plotManager.getAllPlots();
        }

        @Override
        public Plot getPlot(String plotId) {
            return plotManager.getStorage(plotManager.getWorldForPlot(plotId)).getPlot(plotId);
        }

        @Override
        public boolean setFeatured(String plotId, boolean featured) {
            de.streuland.plot.PlotStorage storage = plotManager.getStorage(plotManager.getWorldForPlot(plotId));
            PlotData data = storage.getPlotData(plotId);
            data.setFeatured(featured);
            storage.savePlotData(plotId, data);
            return true;
        }

        @Override
        public boolean isFeatured(String plotId) {
            de.streuland.plot.PlotStorage storage = plotManager.getStorage(plotManager.getWorldForPlot(plotId));
            return storage.getPlotData(plotId).isFeatured();
        }
    }
}
