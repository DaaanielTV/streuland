package de.streuland.dashboard;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.logging.Logger;

/**
 * Pushes real-time dashboard updates.
 */
public class DashboardWebSocketServer extends WebSocketServer {
    private final Logger logger;

    public DashboardWebSocketServer(int port, Logger logger) {
        super(new InetSocketAddress(port));
        this.logger = logger;
        setReuseAddr(true);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
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
}
