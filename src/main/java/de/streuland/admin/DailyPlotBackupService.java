package de.streuland.admin;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.snapshot.PlotSnapshot;
import de.streuland.plot.snapshot.SnapshotManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;

public class DailyPlotBackupService {
    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final SnapshotManager snapshotManager;
    private BukkitTask task;

    public DailyPlotBackupService(JavaPlugin plugin, PlotManager plotManager, SnapshotManager snapshotManager) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.snapshotManager = snapshotManager;
    }

    public void start() {
        long intervalTicks = 24L * 60L * 60L * 20L;
        long initialDelay = Math.max(20L, plugin.getConfig().getLong("admin.daily-backup-delay-ticks", 20L * 60L));
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::runBackup, initialDelay, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void runBackup() {
        List<Plot> claimedPlots = new ArrayList<>();
        for (Plot plot : plotManager.getAllPlots()) {
            if (plot.getOwner() != null) {
                claimedPlots.add(plot);
            }
        }
        if (claimedPlots.isEmpty()) {
            return;
        }
        plugin.getLogger().info("Starting daily plot backup for " + claimedPlots.size() + " claimed plots.");
        for (Plot plot : claimedPlots) {
            snapshotManager.createSnapshot(plot, BlockChangeLogger.SYSTEM_UUID)
                    .thenAccept(this::onSnapshotSaved);
        }
    }

    private void onSnapshotSaved(PlotSnapshot snapshot) {
        plugin.getLogger().info("Daily backup snapshot created: " + snapshot.getId() + " (" + snapshot.getPlotId() + ")");
    }
}
