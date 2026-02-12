package de.streuland.neighborhood;

import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Daily scheduler that rebalances neighborhood resources.
 */
public class ResourceSyncScheduler {
    private static final long TICKS_PER_DAY = 20L * 60L * 60L * 24L;

    private final JavaPlugin plugin;
    private final NeighborhoodService neighborhoodService;
    private BukkitTask task;

    public ResourceSyncScheduler(JavaPlugin plugin, NeighborhoodService neighborhoodService) {
        this.plugin = plugin;
        this.neighborhoodService = neighborhoodService;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin,
                neighborhoodService::balanceClusterResources,
                TICKS_PER_DAY,
                TICKS_PER_DAY);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
