package de.streuland.history;

import com.google.gson.Gson;
import de.streuland.history.PlotChangeJournal.JournalEntry;
import de.streuland.history.PlotChangeJournal.MinimalBlockState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class JournalManager {
    private static final Gson GSON = new Gson();

    private final JavaPlugin plugin;
    private final PlotChangeJournal journal;
    private final AtomicBoolean applyingHistory = new AtomicBoolean(false);

    public JournalManager(JavaPlugin plugin, PlotChangeJournal journal) {
        this.plugin = plugin;
        this.journal = journal;
    }

    public boolean isApplyingHistory() {
        return applyingHistory.get();
    }

    public List<JournalEntry> load(String plotId) {
        journal.flushAll();
        Path path = journal.getActiveLogPath(plotId);
        List<JournalEntry> entries = new ArrayList<>();
        if (!Files.exists(path)) {
            return entries;
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                JournalEntry entry = GSON.fromJson(line, JournalEntry.class);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to read journal for plot " + plotId + ": " + e.getMessage());
        }
        return entries;
    }

    public List<JournalEntry> rollbackTo(String plotId, long timestamp, boolean dryRun) {
        List<JournalEntry> entries = load(plotId);
        List<JournalEntry> affected = new ArrayList<>();
        for (JournalEntry entry : entries) {
            if (entry.timestamp > timestamp) {
                affected.add(entry);
            }
        }
        affected.sort(Comparator.comparingLong((JournalEntry e) -> e.timestamp).reversed());
        if (dryRun || affected.isEmpty()) {
            return affected;
        }
        applyBatch(affected, true);
        return affected;
    }

    public List<JournalEntry> between(String plotId, long fromTimestamp, long toTimestamp) {
        List<JournalEntry> entries = load(plotId);
        List<JournalEntry> selected = new ArrayList<>();
        for (JournalEntry entry : entries) {
            if (entry.timestamp >= fromTimestamp && entry.timestamp <= toTimestamp) {
                selected.add(entry);
            }
        }
        selected.sort(Comparator.comparingLong(e -> e.timestamp));
        return selected;
    }

    public void replay(String plotId, long fromTimestamp, long toTimestamp, Player viewer) {
        List<JournalEntry> selected = between(plotId, fromTimestamp, toTimestamp);
        if (selected.isEmpty()) {
            viewer.sendMessage("§eKeine Journal-Einträge im Zeitraum.");
            return;
        }
        applyingHistory.set(true);
        final int total = selected.size();
        final int[] index = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (index[0] >= total || !viewer.isOnline()) {
                applyingHistory.set(false);
                task.cancel();
                if (viewer.isOnline()) {
                    viewer.sendTitle("§aReplay fertig", "§7" + total + " Änderungen angewandt", 5, 30, 10);
                }
                return;
            }
            JournalEntry entry = selected.get(index[0]);
            apply(entry, false);
            index[0]++;
            viewer.sendTitle("§6Plot Replay", "§7" + index[0] + "/" + total, 0, 10, 0);
        }, 1L, 2L);
    }

    private void applyBatch(List<JournalEntry> entries, boolean rollback) {
        applyingHistory.set(true);
        try {
            for (JournalEntry entry : entries) {
                apply(entry, rollback);
            }
        } finally {
            applyingHistory.set(false);
        }
    }

    private void apply(JournalEntry entry, boolean rollback) {
        World world = Bukkit.getWorld(entry.world);
        if (world == null) {
            return;
        }
        Block block = world.getBlockAt(entry.x, entry.y, entry.z);
        applyState(block, rollback ? entry.before : entry.after);
    }

    private void applyState(Block block, MinimalBlockState state) {
        if (state == null) {
            block.setType(Material.AIR, false);
            return;
        }
        Material material = Material.matchMaterial(state.type);
        if (material == null) {
            material = Material.AIR;
        }
        block.setType(material, false);
        if (state.data != null) {
            try {
                block.setBlockData(Bukkit.createBlockData(state.data), false);
            } catch (Exception ignored) {
            }
        }
        BlockState refreshed = block.getState();
        refreshed.update(true, false);
    }
}
