package de.streuland.discord;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DiscordNotifierTest {

    @Test
    void sendsWebhookPayload() throws IOException {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/hook", exchange -> {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int n;
            while ((n = exchange.getRequestBody().read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            body.set(new String(out.toByteArray(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();

        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        FileConfiguration cfg = Mockito.mock(FileConfiguration.class);
        Mockito.when(plugin.getConfig()).thenReturn(cfg);
        Mockito.when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
        Mockito.when(cfg.getString("discord.webhook-url", "")).thenReturn("http://127.0.0.1:" + server.getAddress().getPort() + "/hook");
        Mockito.when(cfg.getString("discord.bot-token", "")).thenReturn("");

        DiscordNotifier notifier = new DiscordNotifier(plugin);
        Map<String, Object> extras = new HashMap<>();
        extras.put("title", "Approval");

        assertTrue(notifier.sendWebhook("alerts", "hello", extras));
        assertNotNull(body.get());
        Map<?, ?> payload = new Gson().fromJson(body.get(), Map.class);
        assertEquals("hello", payload.get("content"));

        server.stop(0);
    }

    @Test
    void noConfigDoesNotSend() {
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        FileConfiguration cfg = Mockito.mock(FileConfiguration.class);
        Mockito.when(plugin.getConfig()).thenReturn(cfg);
        Mockito.when(cfg.getString("discord.webhook-url", "")).thenReturn("");
        Mockito.when(cfg.getString("discord.bot-token", "")).thenReturn("");
        DiscordNotifier notifier = new DiscordNotifier(plugin);
        assertFalse(notifier.sendWebhook("c", "m", Collections.<String, Object>emptyMap()));
    }
}
