package de.streuland.web;

import de.streuland.plot.Plot;
import org.junit.After;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.logging.Logger;

import static org.junit.Assert.*;

public class WebServerIntegrationTest {
    private WebServer server;

    @After
    public void tearDown() {
        if (server != null) server.stop();
    }

    @Test
    public void servesEndpoints_withValidToken_andRejectsInvalid() throws Exception {
        InMemoryGateway gateway = new InMemoryGateway();
        Plot p = new Plot("plot_1", 10, 20, 64, UUID.randomUUID(), System.currentTimeMillis(), 64, Plot.PlotState.CLAIMED);
        p.addTrusted(UUID.randomUUID());
        gateway.put(p, false);

        int port = 18123;
        server = new WebServer("0.0.0.0", port, "secret", gateway, Logger.getLogger("test"));
        server.start();

        String host = InetAddress.getLocalHost().getHostAddress();

        HttpURLConnection unauthorized = conn(host, port, "/api/plots", "GET", "Bearer wrong");
        assertEquals(401, unauthorized.getResponseCode());

        HttpURLConnection list = conn(host, port, "/api/plots", "GET", "Bearer secret");
        assertEquals(200, list.getResponseCode());
        String listBody = read(list);
        assertTrue(listBody.contains("plot_1"));

        HttpURLConnection detail = conn(host, port, "/api/plots/plot_1", "GET", "Bearer secret");
        assertEquals(200, detail.getResponseCode());
        assertTrue(read(detail).contains("trustedPlayers"));

        HttpURLConnection toggle = conn(host, port, "/api/plots/plot_1/feature", "POST", "Bearer secret");
        assertEquals(200, toggle.getResponseCode());
        assertTrue(gateway.featured.get("plot_1"));

        HttpURLConnection csv = conn(host, port, "/api/plots/export.csv", "GET", "Bearer secret");
        assertEquals(200, csv.getResponseCode());
        assertTrue(read(csv).startsWith("id,coords"));
    }

    private HttpURLConnection conn(String host, int port, String path, String method, String auth) throws Exception {
        URL url = new URL("http://" + host + ":" + port + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Authorization", auth);
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        conn.setDoInput(true);
        return conn;
    }

    private String read(HttpURLConnection conn) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                conn.getResponseCode() >= 400 ? conn.getErrorStream() : conn.getInputStream(),
                StandardCharsets.UTF_8
        ));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private static class InMemoryGateway implements WebServer.PlotGateway {
        private final Map<String, Plot> plots = new HashMap<>();
        private final Map<String, Boolean> featured = new HashMap<>();

        void put(Plot plot, boolean isFeatured) {
            plots.put(plot.getPlotId(), plot);
            featured.put(plot.getPlotId(), isFeatured);
        }

        @Override public Collection<Plot> getAllPlots() { return plots.values(); }
        @Override public Plot getPlot(String plotId) { return plots.get(plotId); }
        @Override public boolean setFeatured(String plotId, boolean isFeatured) { featured.put(plotId, isFeatured); return true; }
        @Override public boolean isFeatured(String plotId) { return featured.getOrDefault(plotId, false); }
    }
}
