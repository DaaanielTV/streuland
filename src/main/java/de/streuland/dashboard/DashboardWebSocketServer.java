package de.streuland.dashboard;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Pushes real-time dashboard updates.
 */
public class DashboardWebSocketServer extends WebSocketServer {
    private final Logger logger;
    private final DashboardAuthService authService;

    public DashboardWebSocketServer(int port, Logger logger, DashboardAuthService authService) {
        super(new InetSocketAddress(port));
        this.logger = logger;
        this.authService = authService;
        setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        Map<String, String> query = parseQuery(handshake.getResourceDescriptor());
        if (!authService.isAuthorizedToken(query.get("token"))) {
            logger.warning("Dashboard websocket rejected unauthenticated client: " + conn.getRemoteSocketAddress());
            conn.close(4001, "Unauthorized");
            return;
        }
        logger.info("Dashboard websocket connected: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        logger.info("Dashboard websocket closed: " + reason);
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        if ("ping".equalsIgnoreCase(message)) {
            conn.send("{\"type\":\"pong\"}");
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        logger.warning("Dashboard websocket error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        logger.info("Dashboard websocket server started on port " + getPort());
    }

    public void broadcastJson(String payload) {
        broadcast(payload);
    }

    private Map<String, String> parseQuery(String descriptor) {
        Map<String, String> values = new HashMap<String, String>();
        if (descriptor == null) {
            return values;
        }
        int queryStart = descriptor.indexOf('?');
        if (queryStart < 0 || queryStart + 1 >= descriptor.length()) {
            return values;
        }
        String[] parts = descriptor.substring(queryStart + 1).split("&");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                values.put(kv[0], kv[1]);
            }
        }
        return values;
    }
}
