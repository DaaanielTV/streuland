package de.streuland.dashboard;

import com.sun.net.httpserver.HttpExchange;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * Minimal token-based auth for dashboard HTTP and websocket access.
 */
public class DashboardAuthService {
    private final String token;

    public DashboardAuthService(JavaPlugin plugin) {
        String configured = plugin.getConfig().getString("dashboard.auth.token", "");
        if (configured == null || configured.trim().isEmpty()) {
            configured = plugin.getConfig().getString("web.token", "");
        }
        this.token = configured == null ? "" : configured.trim();
    }

    public boolean isEnabled() {
        return !token.isEmpty();
    }

    public boolean isAuthorized(HttpExchange exchange) {
        if (!isEnabled()) {
            return true;
        }
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            return token.equals(authHeader.substring(7).trim());
        }
        String apiKey = exchange.getRequestHeaders().getFirst("X-API-Key");
        return token.equals(apiKey);
    }

    public boolean isAuthorizedToken(String candidate) {
        if (!isEnabled()) {
            return true;
        }
        return token.equals(candidate == null ? "" : candidate.trim());
    }
}
