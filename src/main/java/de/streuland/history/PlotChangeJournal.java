package de.streuland.history;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.GZIPOutputStream;

public class PlotChangeJournal {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final Path journalsDir;
    private final Map<String, ConcurrentLinkedQueue<String>> buffers = new ConcurrentHashMap<>();
    private final long rotateThresholdBytes;

    public PlotChangeJournal(JavaPlugin plugin, PlotManager plotManager) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.journalsDir = plugin.getDataFolder().toPath().resolve("journals");
        this.rotateThresholdBytes = plugin.getConfig().getLong("history.journal-rotate-bytes", 5L * 1024L * 1024L);
        try {
            Files.createDirectories(journalsDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create journals folder", e);
        }
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, this::flushAll, 20L, 20L);
    }

    public void recordChange(Location location, BlockState before, BlockState after, UUID actor, long timestamp) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Plot plot = plotManager.getPlotAt(location.getWorld(), location.getBlockX(), location.getBlockZ());
        if (plot == null) {
            return;
        }
        JournalEntry entry = new JournalEntry();
        entry.plotId = plot.getPlotId();
        entry.world = location.getWorld().getName();
        entry.x = location.getBlockX();
        entry.y = location.getBlockY();
        entry.z = location.getBlockZ();
        entry.actor = actor == null ? new UUID(0L, 0L).toString() : actor.toString();
        entry.timestamp = timestamp;
        entry.before = captureState(before);
        entry.after = captureState(after);
        buffers.computeIfAbsent(entry.plotId, k -> new ConcurrentLinkedQueue<>()).add(GSON.toJson(entry));
    }

    public Path getActiveLogPath(String plotId) {
        return journalsDir.resolve(plotId + ".log");
    }

    public synchronized void flushAll() {
        for (Map.Entry<String, ConcurrentLinkedQueue<String>> entry : buffers.entrySet()) {
            flushQueue(entry.getKey(), entry.getValue());
        }
    }

    private void flushQueue(String plotId, ConcurrentLinkedQueue<String> queue) {
        if (queue.isEmpty()) {
            return;
        }
        Path logPath = getActiveLogPath(plotId);
        try {
            Files.createDirectories(logPath.getParent());
            List<String> batch = new ArrayList<>();
            String line;
            while ((line = queue.poll()) != null) {
                batch.add(line);
                if (batch.size() >= 200) {
                    writeBatch(logPath, batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                writeBatch(logPath, batch);
            }
            rotateIfNeeded(logPath);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to flush journal for plot " + plotId + ": " + e.getMessage());
        }
    }

    private void writeBatch(Path logPath, List<String> batch) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(logPath, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (String line : batch) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    private void rotateIfNeeded(Path logPath) throws IOException {
        if (!Files.exists(logPath) || Files.size(logPath) < rotateThresholdBytes) {
            return;
        }
        Path rotated = logPath.resolveSibling(logPath.getFileName() + "." + System.currentTimeMillis() + ".log");
        Files.move(logPath, rotated);
        Path gz = rotated.resolveSibling(rotated.getFileName() + ".gz");
        try (GZIPOutputStream out = new GZIPOutputStream(Files.newOutputStream(gz))) {
            Files.copy(rotated, out);
            out.finish();
        }
        Files.deleteIfExists(rotated);
    }

    private MinimalBlockState captureState(BlockState state) {
        MinimalBlockState minimal = new MinimalBlockState();
        if (state == null) {
            minimal.type = Material.AIR.name();
            minimal.data = Material.AIR.createBlockData().getAsString();
            return minimal;
        }
        minimal.type = state.getType().name();
        minimal.data = state.getBlockData().getAsString();
        minimal.tileNbtJson = extractTileJson(state);
        return minimal;
    }

    private String extractTileJson(BlockState state) {
        if (!(state instanceof Chest)) {
            return null;
        }
        Chest chest = (Chest) state;
        Map<String, Object> payload = new HashMap<>();
        payload.put("customName", chest.getCustomName());
        List<String> items = new ArrayList<>();
        for (ItemStack stack : chest.getBlockInventory().getContents()) {
            items.add(stack == null ? null : Base64.getEncoder().encodeToString(stack.serializeAsBytes()));
        }
        payload.put("items", items);
        return GSON.toJson(payload);
    }

    public static class JournalEntry {
        public String plotId;
        public String world;
        public int x;
        public int y;
        public int z;
        public String actor;
        public long timestamp;
        public MinimalBlockState before;
        public MinimalBlockState after;
    }

    public static class MinimalBlockState {
        public String type;
        public String data;
        public String tileNbtJson;
    }
}
