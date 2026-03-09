package de.streuland.discord;

import com.google.gson.Gson;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public class DiscordNotifier {
    private final JavaPlugin plugin;
    private final Gson gson;

    public DiscordNotifier(JavaPlugin plugin) {
        this.plugin = plugin;
        this.gson = new Gson();
    }

    public boolean sendWebhook(String channel, String message, Map<String, Object> extras) {
        FileConfiguration config = plugin.getConfig();
        String webhookUrl = config.getString("discord.webhook-url", "").trim();
        String botToken = config.getString("discord.bot-token", "").trim();
        if (webhookUrl.isEmpty() && botToken.isEmpty()) {
            return false;
        }
        if (webhookUrl.isEmpty()) {
            plugin.getLogger().warning("Discord bot-token configured but bot sending is not implemented yet.");
            return false;
        }
        try {
            postWebhook(webhookUrl, buildPayload(channel, message, extras));
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to send Discord webhook notification", e);
            return false;
        }
    }

    Map<String, Object> buildPayload(String channel, String message, Map<String, Object> extras) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("content", message);
        if (channel != null && !channel.trim().isEmpty()) {
            payload.put("thread_name", channel);
        }
        if (extras != null && !extras.isEmpty()) {
            payload.put("embeds", new Object[]{new LinkedHashMap<String, Object>(extras)});
        }
        return payload;
    }

    private void postWebhook(String webhookUrl, Map<String, Object> payload) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(webhookUrl).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        byte[] body = gson.toJson(payload).getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(body);
        }
        int code = connection.getResponseCode();
        if (code < 200 || code >= 300) {
            throw new IOException("Discord webhook responded with HTTP " + code);
        }
    }
}
