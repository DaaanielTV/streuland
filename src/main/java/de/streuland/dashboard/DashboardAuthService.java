package de.streuland.dashboard;

import com.sun.net.httpserver.HttpExchange;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Token-based auth + role checks for dashboard HTTP and websocket access.
 */
public class DashboardAuthService {
    private final Map<String, DashboardRole> tokenRoles = new LinkedHashMap<String, DashboardRole>();

    public DashboardAuthService(JavaPlugin plugin) {
        register(plugin.getConfig().getString("dashboard.auth.tokens.viewer", ""), DashboardRole.VIEWER);
        register(plugin.getConfig().getString("dashboard.auth.tokens.operator", ""), DashboardRole.OPERATOR);
        register(plugin.getConfig().getString("dashboard.auth.tokens.admin", ""), DashboardRole.ADMIN);

        String legacy = plugin.getConfig().getString("dashboard.auth.token", "");
        if (legacy == null || legacy.trim().isEmpty()) {
            legacy = plugin.getConfig().getString("web.token", "");
        }
        register(legacy, DashboardRole.ADMIN);
    }

    private void register(String token, DashboardRole role) {
        if (token == null || token.trim().isEmpty()) {
            return;
        }
        tokenRoles.put(token.trim(), role);
    }

    public boolean isEnabled() {
        return !tokenRoles.isEmpty();
    }

    public boolean isAuthorized(HttpExchange exchange) {
        return isAuthorized(exchange, DashboardRole.VIEWER);
    }

    public boolean isAuthorized(HttpExchange exchange, DashboardRole requiredRole) {
        DashboardRole actual = resolveRole(exchange);
        return actual != null && actual.allows(requiredRole);
    }

    public DashboardRole resolveRole(HttpExchange exchange) {
        if (!isEnabled()) {
            return DashboardRole.ADMIN;
        }
        String token = extractToken(exchange);
        return token == null ? null : tokenRoles.get(token);
    }

    public boolean isAuthorizedToken(String candidate) {
        if (!isEnabled()) {
            return true;
        }
        return tokenRoles.containsKey(candidate == null ? "" : candidate.trim());
    }

    private String extractToken(HttpExchange exchange) {
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            return authHeader.substring(7).trim();
        }
        String apiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            return apiKey.trim();
        }
        return null;
    }
}
