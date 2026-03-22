package de.streuland.web;

import org.junit.Test;

import java.util.Collections;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;

public class WebServerRoutingTest {
    @Test
    public void routeKey_mapsExpectedEndpoints() {
        WebServer server = new WebServer("127.0.0.1", 0, "token", new EmptyGateway(), Logger.getLogger("test"));

        assertEquals("plots_list", server.routeKey("GET", "/api/plots"));
        assertEquals("plot_detail", server.routeKey("GET", "/api/plots/p1"));
        assertEquals("plot_feature", server.routeKey("POST", "/api/plots/p1/feature"));
        assertEquals("plots_export", server.routeKey("GET", "/api/plots/export.csv"));
        assertEquals("not_found", server.routeKey("DELETE", "/api/plots/p1"));
    }

    private static class EmptyGateway implements WebServer.PlotGateway {
        @Override public java.util.Collection<de.streuland.plot.Plot> getAllPlots() { return Collections.emptyList(); }
        @Override public de.streuland.plot.Plot getPlot(String plotId) { return null; }
        @Override public boolean setFeatured(String plotId, boolean featured) { return false; }
        @Override public boolean isFeatured(String plotId) { return false; }
    }
}
