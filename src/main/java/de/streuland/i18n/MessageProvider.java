package de.streuland.i18n;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MessageProvider {
    private static final String PLAYER_LOCALES_FILE = "player_locales.yml";
    private static final String DEFAULT_LOCALE = "en";

    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> bundles = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerLocales = new ConcurrentHashMap<>();
    private final Map<String, Long> bundleModifiedAt = new ConcurrentHashMap<>();

    private String serverLocale;


    MessageProvider(String serverLocale, Map<String, Map<String, String>> testBundles) {
        this.plugin = null;
        this.serverLocale = normalizeLocale(serverLocale);
        for (Map.Entry<String, Map<String, String>> e : testBundles.entrySet()) {
            YamlConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<String, String> kv : e.getValue().entrySet()) {
                cfg.set(kv.getKey(), kv.getValue());
            }
            String normalized = normalizeLocale(e.getKey());
            bundles.put(normalized, cfg);
            bundleModifiedAt.put(normalized, 0L);
        }
    }

    public MessageProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        this.serverLocale = normalizeLocale(plugin.getConfig().getString("i18n.default-locale", DEFAULT_LOCALE));
        ensureDefaultBundleExists("en");
        ensureDefaultBundleExists("de");
        reload();
    }

    public synchronized void reload() {
        bundles.clear();
        bundleModifiedAt.clear();
        loadBundles();
        loadPlayerLocales();
    }

    public String t(UUID playerId, String key, Object... args) {
        if (plugin != null) checkForBundleUpdates();
        String locale = playerId == null ? serverLocale : playerLocales.getOrDefault(playerId, serverLocale);
        return t(locale, key, args);
    }

    public String t(String locale, String key, Object... args) {
        if (plugin != null) checkForBundleUpdates();
        String normalized = normalizeLocale(locale);
        String template = resolveTemplate(normalized, key);
        return MessageFormat.format(template, args == null ? new Object[0] : args);
    }

    public String getServerLocale() {
        return serverLocale;
    }

    public synchronized void setServerLocale(String locale) {
        this.serverLocale = normalizeLocale(locale);
        if (plugin != null) {
            plugin.getConfig().set("i18n.default-locale", this.serverLocale);
            plugin.saveConfig();
        }
    }

    public synchronized void setPlayerLocale(UUID playerId, String locale) {
        playerLocales.put(playerId, normalizeLocale(locale));
        if (plugin != null) savePlayerLocales();
    }

    public Optional<String> getPlayerLocale(UUID playerId) {
        return Optional.ofNullable(playerLocales.get(playerId));
    }

    public Set<String> getAvailableLocales() {
        return new TreeSet<>(bundles.keySet());
    }

    public String t(Player player, String key, Object... args) {
        return t(player == null ? null : player.getUniqueId(), key, args);
    }

    private String resolveTemplate(String locale, String key) {
        YamlConfiguration selected = bundles.get(locale);
        if (selected != null && selected.contains(key)) {
            return selected.getString(key, key);
        }
        YamlConfiguration fallback = bundles.get(serverLocale);
        if (fallback != null && fallback.contains(key)) {
            return fallback.getString(key, key);
        }
        YamlConfiguration english = bundles.get(DEFAULT_LOCALE);
        if (english != null && english.contains(key)) {
            return english.getString(key, key);
        }
        return key;
    }

    private void ensureDefaultBundleExists(String locale) {
        File file = new File(plugin.getDataFolder(), "messages_" + locale + ".yml");
        if (!file.exists()) {
            plugin.saveResource("messages_" + locale + ".yml", false);
        }
    }

    private void loadBundles() {
        File folder = plugin.getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            return;
        }
        File[] files = folder.listFiles((dir, name) -> name.startsWith("messages_") && name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String locale = normalizeLocale(file.getName().substring("messages_".length(), file.getName().length() - ".yml".length()));
            bundles.put(locale, YamlConfiguration.loadConfiguration(file));
            bundleModifiedAt.put(locale, file.lastModified());
        }
    }

    private void checkForBundleUpdates() {
        File[] files = plugin.getDataFolder().listFiles((dir, name) -> name.startsWith("messages_") && name.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String locale = normalizeLocale(file.getName().substring("messages_".length(), file.getName().length() - ".yml".length()));
            long modified = file.lastModified();
            Long known = bundleModifiedAt.get(locale);
            if (known == null || known.longValue() != modified) {
                bundles.put(locale, YamlConfiguration.loadConfiguration(file));
                bundleModifiedAt.put(locale, modified);
            }
        }
    }

    private void loadPlayerLocales() {
        File localeFile = new File(plugin.getDataFolder(), PLAYER_LOCALES_FILE);
        if (!localeFile.exists()) {
            return;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(localeFile);
        for (String key : cfg.getKeys(false)) {
            try {
                playerLocales.put(UUID.fromString(key), normalizeLocale(cfg.getString(key, serverLocale)));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void savePlayerLocales() {
        File localeFile = new File(plugin.getDataFolder(), PLAYER_LOCALES_FILE);
        YamlConfiguration cfg = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : playerLocales.entrySet()) {
            cfg.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            cfg.save(localeFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player locales: " + e.getMessage());
        }
    }

    private static String normalizeLocale(String locale) {
        if (locale == null || locale.trim().isEmpty()) {
            return DEFAULT_LOCALE;
        }
        return locale.trim().toLowerCase(Locale.ROOT);
    }
}
