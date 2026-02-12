package de.streuland.neighborhood;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persists neighborhood relationships into neighborhoods.json.
 */
public class NeighborhoodStorage {
    private static final Pattern ENTRY_PATTERN = Pattern.compile("\\{\\s*\"owner\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"neighbors\"\\s*:\\s*\\[(.*?)]\\s*}");
    private static final Pattern UUID_PATTERN = Pattern.compile("\"([0-9a-fA-F\\-]{36})\"");

    private final JavaPlugin plugin;
    private final File file;

    public NeighborhoodStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "neighborhoods.json");
    }

    public synchronized Map<UUID, Set<UUID>> load() {
        Map<UUID, Set<UUID>> relations = new HashMap<>();
        if (!file.exists()) {
            return relations;
        }

        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Matcher entryMatcher = ENTRY_PATTERN.matcher(content);
            while (entryMatcher.find()) {
                UUID owner = parseUuid(entryMatcher.group(1));
                if (owner == null) {
                    continue;
                }
                Set<UUID> neighbors = new HashSet<>();
                Matcher neighborMatcher = UUID_PATTERN.matcher(entryMatcher.group(2));
                while (neighborMatcher.find()) {
                    UUID neighbor = parseUuid(neighborMatcher.group(1));
                    if (neighbor != null) {
                        neighbors.add(neighbor);
                    }
                }
                relations.put(owner, neighbors);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load neighborhoods.json: " + e.getMessage());
        }
        return relations;
    }

    public synchronized void save(Map<UUID, Set<UUID>> relations) {
        file.getParentFile().mkdirs();
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write("{\n  \"relationships\": [\n");
            List<UUID> owners = new ArrayList<>(relations.keySet());
            Collections.sort(owners);
            for (int i = 0; i < owners.size(); i++) {
                UUID owner = owners.get(i);
                List<UUID> neighbors = new ArrayList<>(relations.getOrDefault(owner, Collections.emptySet()));
                Collections.sort(neighbors);
                writer.write("    {\"owner\":\"");
                writer.write(owner.toString());
                writer.write("\",\"neighbors\":[");
                for (int j = 0; j < neighbors.size(); j++) {
                    writer.write("\"");
                    writer.write(neighbors.get(j).toString());
                    writer.write("\"");
                    if (j < neighbors.size() - 1) {
                        writer.write(",");
                    }
                }
                writer.write("]}");
                if (i < owners.size() - 1) {
                    writer.write(",");
                }
                writer.write("\n");
            }
            writer.write("  ]\n}\n");
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save neighborhoods.json: " + e.getMessage());
        }
    }

    private UUID parseUuid(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
