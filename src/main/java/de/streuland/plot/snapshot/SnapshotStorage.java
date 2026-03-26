package de.streuland.plot.snapshot;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;
import de.streuland.plot.Role;
import de.streuland.plot.skin.PlotTheme;
import de.streuland.quest.QuestProgress;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class SnapshotStorage {
    private final JavaPlugin plugin;
    private final File rootFolder;
    private final Object indexLock = new Object();

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
        writeMetadata(config, snapshot.getMetadata());

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
            saveAtomically(config, snapshotFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save snapshot " + snapshot.getId() + ": " + e.getMessage());
        }

        updateIndex(snapshot.getPlotId(), snapshot.getId(), snapshot.getCreatedAt(), snapshot.getCreator(), snapshot.getMetadata());
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
            int x = ((Number) entry.get("x")).intValue();
            int y = ((Number) entry.get("y")).intValue();
            int z = ((Number) entry.get("z")).intValue();
            String type = (String) entry.get("type");
            String data = (String) entry.get("data");
            Map<String, Object> tile = null;
            Object tileObj = entry.get("tile");
            if (tileObj instanceof Map) {
                tile = (Map<String, Object>) tileObj;
            }
            blockSnapshots.add(new BlockSnapshot(x, y, z, type, data, tile));
        }
        return new PlotSnapshot(id, plot, creator, createdAt, blockSnapshots, readMetadata(config));
    }

    public List<SnapshotMeta> listSnapshots(String plotId) {
        File indexFile = getIndexFile(plotId);
        if (!indexFile.exists()) {
            return new ArrayList<>();
        }
        List<SnapshotMeta> result = new ArrayList<>();
        synchronized (indexLock) {
            FileConfiguration config = YamlConfiguration.loadConfiguration(indexFile);
            List<Map<?, ?>> entries = config.getMapList("snapshots");
            for (Map<?, ?> entry : entries) {
                String id = (String) entry.get("id");
                long createdAt = ((Number) entry.get("createdAt")).longValue();
                String creatorStr = (String) entry.get("creator");
                UUID creator = creatorStr != null ? UUID.fromString(creatorStr) : null;
                String authorName = (String) entry.get("authorName");
                String note = (String) entry.get("note");
                result.add(new SnapshotMeta(id, createdAt, creator, authorName, note));
            }
        }
        result.sort(Comparator.comparingLong(SnapshotMeta::getCreatedAt).reversed());
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

    private void updateIndex(String plotId, String snapshotId, long createdAt, UUID creator, PlotSnapshotMetadata metadata) {
        synchronized (indexLock) {
            File indexFile = getIndexFile(plotId);
            FileConfiguration config = indexFile.exists() ? YamlConfiguration.loadConfiguration(indexFile) : new YamlConfiguration();
            Map<String, Map<String, Object>> deduplicated = new LinkedHashMap<>();
            for (Map<?, ?> entry : config.getMapList("snapshots")) {
                Map<String, Object> copy = new HashMap<>();
                copy.put("id", entry.get("id"));
                copy.put("createdAt", entry.get("createdAt"));
                copy.put("creator", entry.get("creator"));
                copy.put("authorName", entry.get("authorName"));
                copy.put("note", entry.get("note"));
                deduplicated.put(String.valueOf(entry.get("id")), copy);
            }
            Map<String, Object> newEntry = new HashMap<>();
            newEntry.put("id", snapshotId);
            newEntry.put("createdAt", createdAt);
            newEntry.put("creator", creator != null ? creator.toString() : null);
            newEntry.put("authorName", metadata != null ? metadata.getAuthorName() : null);
            newEntry.put("note", metadata != null ? metadata.getNote() : null);
            deduplicated.put(snapshotId, newEntry);
            config.set("snapshots", new ArrayList<>(deduplicated.values()));
            try {
                saveAtomically(config, indexFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to update snapshot index: " + e.getMessage());
            }
        }
    }

    private void removeFromIndex(String plotId, String snapshotId) {
        synchronized (indexLock) {
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
                    copy.put("authorName", entry.get("authorName"));
                    copy.put("note", entry.get("note"));
                    updated.add(copy);
                }
            }
            config.set("snapshots", updated);
            try {
                saveAtomically(config, indexFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to update snapshot index: " + e.getMessage());
            }
        }
    }

    private void saveAtomically(FileConfiguration config, File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        File temp = new File(file.getAbsolutePath() + ".tmp");
        config.save(temp);
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private void writeMetadata(FileConfiguration config, PlotSnapshotMetadata metadata) {
        if (metadata == null) {
            return;
        }
        config.set("metadata.authorName", metadata.getAuthorName());
        config.set("metadata.note", metadata.getNote());
        Plot plot = metadata.getPlot();
        if (plot != null) {
            config.set("metadata.plot.id", plot.getPlotId());
            config.set("metadata.plot.centerX", plot.getCenterX());
            config.set("metadata.plot.centerZ", plot.getCenterZ());
            config.set("metadata.plot.size", plot.getSize());
            config.set("metadata.plot.owner", plot.getOwner() != null ? plot.getOwner().toString() : null);
            config.set("metadata.plot.state", plot.getState().name());
            config.set("metadata.plot.createdAt", plot.getCreatedAt());
            config.set("metadata.plot.spawnY", plot.getSpawnY());
            Map<String, String> roles = new HashMap<>();
            for (Map.Entry<UUID, Role> roleEntry : plot.getRoles().entrySet()) {
                roles.put(roleEntry.getKey().toString(), roleEntry.getValue().name());
            }
            config.set("metadata.plot.roles", roles);
        }
        PlotData plotData = metadata.getPlotData();
        if (plotData != null) {
            config.set("metadata.data.theme", plotData.getTheme().name());
            config.set("metadata.data.bonusStorageSlots", plotData.getBonusStorageSlots());
            config.set("metadata.data.unlockedAbilities", new ArrayList<>(plotData.getUnlockedAbilities()));
            config.set("metadata.data.cosmeticInventory", new ArrayList<>(plotData.getCosmeticInventory()));
            config.set("metadata.data.statBonuses", new HashMap<>(plotData.getStatBonuses()));
            config.set("metadata.data.flagOverrides", new HashMap<>(plotData.getFlagOverrides()));
            config.set("metadata.data.featured", plotData.isFeatured());
            for (Map.Entry<String, QuestProgress> entry : plotData.getQuestProgress().entrySet()) {
                String base = "metadata.data.questProgress." + entry.getKey();
                QuestProgress progress = entry.getValue();
                config.set(base + ".value", progress.getValue());
                config.set(base + ".completed", progress.isCompleted());
                config.set(base + ".completedAt", progress.getCompletedAt());
            }
        }
    }

    private PlotSnapshotMetadata readMetadata(FileConfiguration config) {
        if (!config.isConfigurationSection("metadata")) {
            return null;
        }
        String authorName = config.getString("metadata.authorName");
        String note = config.getString("metadata.note");
        Plot plot = null;
        if (config.isConfigurationSection("metadata.plot")) {
            String id = config.getString("metadata.plot.id");
            int centerX = config.getInt("metadata.plot.centerX");
            int centerZ = config.getInt("metadata.plot.centerZ");
            int size = config.getInt("metadata.plot.size");
            String ownerStr = config.getString("metadata.plot.owner");
            UUID owner = ownerStr == null ? null : UUID.fromString(ownerStr);
            long createdAt = config.getLong("metadata.plot.createdAt");
            int spawnY = config.getInt("metadata.plot.spawnY");
            Plot.PlotState state = Plot.PlotState.valueOf(config.getString("metadata.plot.state", owner == null ? "UNCLAIMED" : "CLAIMED").toUpperCase(Locale.ROOT));
            plot = new Plot(id, centerX, centerZ, size, owner, createdAt, spawnY, state);
            Map<UUID, Role> roles = new HashMap<>();
            ConfigurationSection roleSection = config.getConfigurationSection("metadata.plot.roles");
            if (roleSection != null) {
                for (String key : roleSection.getKeys(false)) {
                    roles.put(UUID.fromString(key), Role.valueOf(roleSection.getString(key, Role.VISITOR.name()).toUpperCase(Locale.ROOT)));
                }
            }
            plot.replaceRoles(roles);
        }
        PlotData plotData = new PlotData(PlotTheme.fromInput(config.getString("metadata.data.theme", PlotTheme.NATURE.name())));
        plotData.setBonusStorageSlots(config.getInt("metadata.data.bonusStorageSlots", 0));
        plotData.getUnlockedAbilities().addAll(config.getStringList("metadata.data.unlockedAbilities"));
        plotData.getCosmeticInventory().addAll(config.getStringList("metadata.data.cosmeticInventory"));
        ConfigurationSection stats = config.getConfigurationSection("metadata.data.statBonuses");
        if (stats != null) {
            for (String key : stats.getKeys(false)) {
                plotData.getStatBonuses().put(key, stats.getDouble(key));
            }
        }
        ConfigurationSection flags = config.getConfigurationSection("metadata.data.flagOverrides");
        if (flags != null) {
            for (String key : flags.getKeys(false)) {
                plotData.getFlagOverrides().put(key, flags.getBoolean(key));
            }
        }
        plotData.setFeatured(config.getBoolean("metadata.data.featured", false));
        ConfigurationSection quests = config.getConfigurationSection("metadata.data.questProgress");
        if (quests != null) {
            for (String questId : quests.getKeys(false)) {
                QuestProgress progress = new QuestProgress();
                progress.setValue(config.getInt("metadata.data.questProgress." + questId + ".value", 0));
                progress.setCompleted(config.getBoolean("metadata.data.questProgress." + questId + ".completed", false));
                progress.setCompletedAt(config.getLong("metadata.data.questProgress." + questId + ".completedAt", 0L));
                plotData.getQuestProgress().put(questId, progress);
            }
        }
        return new PlotSnapshotMetadata(plot, plotData, authorName, note);
    }

    private File getPlotFolder(String plotId) {
        return new File(rootFolder, plotId);
    }

    private File getIndexFile(String plotId) {
        return new File(getPlotFolder(plotId), "index.yml");
    }
}
