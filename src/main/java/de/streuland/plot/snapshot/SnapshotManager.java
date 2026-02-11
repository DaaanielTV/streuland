package de.streuland.plot.snapshot;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class SnapshotManager {
    public static final String PERMISSION_SNAPSHOT = "streuland.snapshot.player";
    public static final String PERMISSION_ADMIN_RESTORE = "streuland.snapshot.restore.admin";

    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final SnapshotStorage storage;
    private final int maxSnapshotsPerPlot;
    private final int chunkBatchSize;
    private final int restoreBatchSize;
    private final long restoreBatchDelay;

    public SnapshotManager(JavaPlugin plugin, PlotManager plotManager, SnapshotStorage storage) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.storage = storage;
        FileConfiguration config = plugin.getConfig();
        this.maxSnapshotsPerPlot = config.getInt("snapshot.max-per-plot", 5);
        this.chunkBatchSize = Math.max(1, config.getInt("snapshot.chunk-batch", 2));
        this.restoreBatchSize = Math.max(1, config.getInt("snapshot.restore-batch-size", 500));
        this.restoreBatchDelay = Math.max(1L, config.getLong("snapshot.restore-batch-delay", 1L));
    }

    public CompletableFuture<PlotSnapshot> createSnapshot(Plot plot, UUID creator) {
        int limit = Math.max(0, maxSnapshotsPerPlot - 1);
        storage.enforceMaxSnapshots(plot.getPlotId(), limit);
        World world = plotManager.getWorld();
        Set<ChunkKey> chunks = getPlotChunks(plot);
        Queue<ChunkKey> queue = new ArrayDeque<>(chunks);
        Map<ChunkKey, ChunkSnapshot> chunkSnapshots = new HashMap<>();
        Map<Vector, Map<String, Object>> tileEntities = new HashMap<>();
        CompletableFuture<PlotSnapshot> future = new CompletableFuture<>();
        AtomicInteger taskId = new AtomicInteger(-1);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int processed = 0;
            while (!queue.isEmpty() && processed < chunkBatchSize) {
                ChunkKey key = queue.poll();
                Chunk chunk = world.getChunkAt(key.getX(), key.getZ());
                chunkSnapshots.put(key, chunk.getChunkSnapshot(true, true, true));
                for (BlockState state : chunk.getTileEntities()) {
                    if (state instanceof TileState) {
                        Block block = state.getBlock();
                        if (plot.contains(block.getX(), block.getZ())) {
                            tileEntities.put(new Vector(block.getX(), block.getY(), block.getZ()), state.serialize());
                        }
                    }
                }
                processed++;
            }
            if (queue.isEmpty()) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    PlotSnapshot snapshot = buildSnapshot(plot, creator, chunkSnapshots, tileEntities);
                    storage.saveSnapshotAsync(snapshot).thenRun(() -> future.complete(snapshot));
                });
                Bukkit.getScheduler().cancelTask(taskId.get());
            }
        }, 1L, 1L);
        taskId.set(task.getTaskId());
        return future;
    }

    private PlotSnapshot buildSnapshot(Plot plot, UUID creator, Map<ChunkKey, ChunkSnapshot> chunkSnapshots,
                                      Map<Vector, Map<String, Object>> tileEntities) {
        List<BlockSnapshot> blocks = new ArrayList<>();
        int minX = plot.getMinX();
        int maxX = plot.getMaxX();
        int minZ = plot.getMinZ();
        int maxZ = plot.getMaxZ();
        int maxY = plotManager.getWorld().getMaxHeight();

        for (Map.Entry<ChunkKey, ChunkSnapshot> entry : chunkSnapshots.entrySet()) {
            ChunkKey key = entry.getKey();
            ChunkSnapshot snapshot = entry.getValue();
            int chunkStartX = key.getX() << 4;
            int chunkStartZ = key.getZ() << 4;
            int startX = Math.max(minX, chunkStartX);
            int endX = Math.min(maxX, chunkStartX + 16);
            int startZ = Math.max(minZ, chunkStartZ);
            int endZ = Math.min(maxZ, chunkStartZ + 16);

            for (int x = startX; x < endX; x++) {
                int relX = x - chunkStartX;
                for (int z = startZ; z < endZ; z++) {
                    int relZ = z - chunkStartZ;
                    for (int y = 0; y < maxY; y++) {
                        Material type = snapshot.getBlockType(relX, y, relZ);
                        String blockData = snapshot.getBlockData(relX, y, relZ).getAsString();
                        Vector keyVec = new Vector(x, y, z);
                        Map<String, Object> tileData = tileEntities.get(keyVec);
                        blocks.add(new BlockSnapshot(x, y, z, type.name(), blockData, tileData));
                    }
                }
            }
        }

        String id = "snapshot_" + System.currentTimeMillis();
        return new PlotSnapshot(id, plot.getPlotId(), creator, System.currentTimeMillis(), blocks);
    }

    public CompletableFuture<Void> restoreSnapshot(String plotId, String snapshotId, boolean delayed) {
        return storage.loadSnapshotAsync(plotId, snapshotId).thenCompose(snapshot -> {
            if (snapshot == null) {
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> future = new CompletableFuture<>();
            Bukkit.getScheduler().runTask(plugin, () -> applySnapshot(snapshot, delayed, future));
            return future;
        });
    }

    private void applySnapshot(PlotSnapshot snapshot, boolean delayed, CompletableFuture<Void> completion) {
        List<BlockSnapshot> blocks = new ArrayList<>(snapshot.getBlocks());
        int batchSize = Math.max(1, restoreBatchSize);
        long delay = delayed ? restoreBatchDelay : 1L;
        AtomicInteger taskId = new AtomicInteger(-1);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int processed = 0;
            while (!blocks.isEmpty() && processed < batchSize) {
                BlockSnapshot blockSnapshot = blocks.remove(blocks.size() - 1);
                Block block = plotManager.getWorld().getBlockAt(blockSnapshot.getX(), blockSnapshot.getY(), blockSnapshot.getZ());
                Material material = Material.matchMaterial(blockSnapshot.getType());
                if (material != null) {
                    block.setType(material, false);
                    try {
                        block.setBlockData(Bukkit.createBlockData(blockSnapshot.getBlockData()), false);
                    } catch (IllegalArgumentException ignored) {
                    }
                    Map<String, Object> tileData = blockSnapshot.getTileData();
                    if (tileData != null) {
                        try {
                            Object deserialized = org.bukkit.configuration.serialization.ConfigurationSerialization.deserializeObject(tileData);
                            if (deserialized instanceof BlockState) {
                                BlockState state = (BlockState) deserialized;
                                state.update(true, false);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
                processed++;
            }
            if (blocks.isEmpty()) {
                completion.complete(null);
                Bukkit.getScheduler().cancelTask(taskId.get());
            }
        }, 1L, delay == 0L ? 1L : delay);
        taskId.set(task.getTaskId());
    }

    public List<SnapshotMeta> listSnapshots(String plotId) {
        return storage.listSnapshots(plotId);
    }

    private Set<ChunkKey> getPlotChunks(Plot plot) {
        Set<ChunkKey> chunks = new HashSet<>();
        int minX = plot.getMinX();
        int maxX = plot.getMaxX() - 1;
        int minZ = plot.getMinZ();
        int maxZ = plot.getMaxZ() - 1;
        for (int x = minX; x <= maxX; x += 16) {
            for (int z = minZ; z <= maxZ; z += 16) {
                chunks.add(new ChunkKey(x >> 4, z >> 4));
            }
        }
        chunks.add(new ChunkKey(maxX >> 4, maxZ >> 4));
        return chunks;
    }
}
