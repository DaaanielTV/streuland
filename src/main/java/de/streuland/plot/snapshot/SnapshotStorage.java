package de.streuland.plot.snapshot;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SnapshotStorage {
    private final JavaPlugin plugin;
    private final File rootFolder;

    public SnapshotStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.rootFolder = new File(plugin.getDataFolder(), "snapshots");
        if (!rootFolder.exists()) {
            rootFolder.mkdirs();
        }
    }

    public CompletableFuture<Void> saveSnapshotAsync(PlotSnapshot snapshot) {
        return CompletableFuture.runAsync(() -> saveSnapshot(snapshot));
    }

    private void saveSnapshot(PlotSnapshot snapshot) {
        File plotFolder = getPlotFolder(snapshot.getPlotId());
        if (!plotFolder.exists()) {
            plotFolder.mkdirs();
        }
        File snapshotFile = new File(plotFolder, snapshot.getId() + ".yml");
        FileConfiguration config = new YamlConfiguration();
        config.set("id", snapshot.getId());
        config.set("plotId", snapshot.getPlotId());
        config.set("creator", snapshot.getCreator() != null ? snapshot.getCreator().toString() : null);
        config.set("createdAt", snapshot.getCreatedAt());

        List<Map<String, Object>> blocks = new ArrayList<>();
        for (BlockSnapshot block : snapshot.getBlocks()) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("x", block.getX());
            entry.put("y", block.getY());
            entry.put("z", block.getZ());
            entry.put("type", block.getType());
            entry.put("data", block.getBlockData());
            if (block.getTileData() != null && !block.getTileData().isEmpty()) {
                entry.put("tile", block.getTileData());
            }
            blocks.add(entry);
        }
        config.set("blocks", blocks);

        try {
            config.save(snapshotFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save snapshot " + snapshot.getId() + ": " + e.getMessage());
        }

        updateIndex(snapshot.getPlotId(), snapshot.getId(), snapshot.getCreatedAt(), snapshot.getCreator());
    }

    public CompletableFuture<PlotSnapshot> loadSnapshotAsync(String plotId, String snapshotId) {
        return CompletableFuture.supplyAsync(() -> loadSnapshot(plotId, snapshotId));
    }

    public PlotSnapshot loadSnapshot(String plotId, String snapshotId) {
        File snapshotFile = new File(getPlotFolder(plotId), snapshotId + ".yml");
        if (!snapshotFile.exists()) {
            return null;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(snapshotFile);
        String id = config.getString("id");
        String plot = config.getString("plotId");
        String creatorStr = config.getString("creator");
        UUID creator = creatorStr != null ? UUID.fromString(creatorStr) : null;
        long createdAt = config.getLong("createdAt");
        List<Map<?, ?>> blocks = config.getMapList("blocks");
        List<BlockSnapshot> blockSnapshots = new ArrayList<>(blocks.size());
        for (Map<?, ?> entry : blocks) {
            int x = (int) entry.get("x");
            int y = (int) entry.get("y");
            int z = (int) entry.get("z");
            String type = (String) entry.get("type");
            String data = (String) entry.get("data");
            Map<String, Object> tile = null;
            Object tileObj = entry.get("tile");
            if (tileObj instanceof Map) {
                tile = (Map<String, Object>) tileObj;
            }
            blockSnapshots.add(new BlockSnapshot(x, y, z, type, data, tile));
        }
        return new PlotSnapshot(id, plot, creator, createdAt, blockSnapshots);
    }

    public List<SnapshotMeta> listSnapshots(String plotId) {
        File indexFile = getIndexFile(plotId);
        if (!indexFile.exists()) {
            return new ArrayList<>();
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(indexFile);
        List<Map<?, ?>> entries = config.getMapList("snapshots");
        List<SnapshotMeta> result = new ArrayList<>();
        for (Map<?, ?> entry : entries) {
            String id = (String) entry.get("id");
            long createdAt = ((Number) entry.get("createdAt")).longValue();
            String creatorStr = (String) entry.get("creator");
            UUID creator = creatorStr != null ? UUID.fromString(creatorStr) : null;
            result.add(new SnapshotMeta(id, createdAt, creator));
        }
        return result;
    }

    public void deleteSnapshot(String plotId, String snapshotId) {
        File snapshotFile = new File(getPlotFolder(plotId), snapshotId + ".yml");
        if (snapshotFile.exists()) {
            snapshotFile.delete();
        }
        removeFromIndex(plotId, snapshotId);
    }

    public void enforceMaxSnapshots(String plotId, int maxSnapshots) {
        if (maxSnapshots <= 0) {
            return;
        }
        List<SnapshotMeta> snapshots = listSnapshots(plotId);
        if (snapshots.size() <= maxSnapshots) {
            return;
        }
        snapshots.sort(Comparator.comparingLong(SnapshotMeta::getCreatedAt));
        int toDelete = snapshots.size() - maxSnapshots;
        for (int i = 0; i < toDelete; i++) {
            deleteSnapshot(plotId, snapshots.get(i).getId());
        }
    }

    private void updateIndex(String plotId, String snapshotId, long createdAt, UUID creator) {
        File indexFile = getIndexFile(plotId);
        FileConfiguration config = indexFile.exists() ? YamlConfiguration.loadConfiguration(indexFile) : new YamlConfiguration();
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Map<?, ?> entry : config.getMapList("snapshots")) {
            Map<String, Object> copy = new HashMap<>();
            copy.put("id", entry.get("id"));
            copy.put("createdAt", entry.get("createdAt"));
            copy.put("creator", entry.get("creator"));
            entries.add(copy);
        }
        Map<String, Object> newEntry = new HashMap<>();
        newEntry.put("id", snapshotId);
        newEntry.put("createdAt", createdAt);
        newEntry.put("creator", creator != null ? creator.toString() : null);
        entries.add(newEntry);
        config.set("snapshots", entries);
        try {
            config.save(indexFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to update snapshot index: " + e.getMessage());
        }
    }

    private void removeFromIndex(String plotId, String snapshotId) {
        File indexFile = getIndexFile(plotId);
        if (!indexFile.exists()) {
            return;
        }
        FileConfiguration config = YamlConfiguration.loadConfiguration(indexFile);
        List<Map<?, ?>> entries = config.getMapList("snapshots");
        List<Map<String, Object>> updated = new ArrayList<>();
        for (Map<?, ?> entry : entries) {
            String id = (String) entry.get("id");
            if (!snapshotId.equals(id)) {
                Map<String, Object> copy = new HashMap<>();
                copy.put("id", entry.get("id"));
                copy.put("createdAt", entry.get("createdAt"));
                copy.put("creator", entry.get("creator"));
                updated.add(copy);
            }
        }
        config.set("snapshots", updated);
        try {
            config.save(indexFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to update snapshot index: " + e.getMessage());
        }
    }

    private File getPlotFolder(String plotId) {
        return new File(rootFolder, plotId);
    }

    private File getIndexFile(String plotId) {
        return new File(getPlotFolder(plotId), "index.yml");
    }
}
