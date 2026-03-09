package de.streuland.transaction;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transaction wrapper for world mutations that can be committed or rolled back.
 */
public class WorldTransaction {
    private final String id;
    private final Location origin;
    private final Map<Location, BlockState> priorStates = new LinkedHashMap<>();
    private final Map<Location, BlockState> appliedStates = new LinkedHashMap<>();
    private final File logFile;
    private boolean finished;

    public WorldTransaction(String id, Location origin) {
        this.id = id;
        this.origin = origin.clone();
        File dir = new File("plugins/Streuland/transactions");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.logFile = new File(dir, id + ".log");
        writeLogLine("BEGIN id=" + id + " origin=" + toLocationString(origin));
    }

    public synchronized void recordBlock(Location loc, BlockState before) {
        ensureOpen();
        if (loc == null || before == null) {
            return;
        }
        Location key = blockLocation(loc);
        priorStates.putIfAbsent(key, before);
    }

    public synchronized void applyChange(Location loc, BlockState after) {
        ensureOpen();
        ensureMainThread();
        if (loc == null || after == null) {
            return;
        }
        Location key = blockLocation(loc);
        appliedStates.put(key, after);
        Block block = key.getBlock();
        block.setType(after.getType(), false);
        block.setBlockData(after.getBlockData(), false);
    }

    public synchronized void commit() {
        ensureOpen();
        finished = true;
        writeLogLine("COMMIT changed=" + appliedStates.size() + " recorded=" + priorStates.size());
    }

    public synchronized void rollback() {
        ensureOpen();
        ensureMainThread();
        for (Map.Entry<Location, BlockState> entry : priorStates.entrySet()) {
            Block block = entry.getKey().getBlock();
            BlockState state = entry.getValue();
            block.setType(state.getType(), false);
            block.setBlockData(state.getBlockData(), false);
            state.update(true, false);
        }
        finished = true;
        writeLogLine("ROLLBACK restored=" + priorStates.size() + " applied=" + appliedStates.size());
    }

    public String getId() {
        return id;
    }

    public Location getOrigin() {
        return origin.clone();
    }

    public File getLogFile() {
        return logFile;
    }

    public synchronized Map<Location, BlockState> getPriorStates() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(priorStates));
    }

    public synchronized Map<Location, BlockState> getAppliedStates() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(appliedStates));
    }

    private void ensureOpen() {
        if (finished) {
            throw new IllegalStateException("Transaction already finished: " + id);
        }
    }

    private static void ensureMainThread() {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("World mutations must run on the server main thread");
        }
    }

    private static Location blockLocation(Location loc) {
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    private synchronized void writeLogLine(String line) {
        try (BufferedWriter writer = Files.newBufferedWriter(
            logFile.toPath(),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )) {
            writer.write(Instant.now() + " " + line);
            writer.newLine();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write transaction log: " + logFile.getAbsolutePath(), e);
        }
    }

    private static String toLocationString(Location location) {
        String worldName = location.getWorld() == null ? "null" : location.getWorld().getName();
        return worldName + ":" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }
}
