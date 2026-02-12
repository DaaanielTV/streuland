package de.streuland.district;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks district progress using async-friendly batching.
 */
public class DistrictProgressService implements Listener {
    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final DistrictManager districtManager;

    public DistrictProgressService(JavaPlugin plugin, PlotManager plotManager, DistrictManager districtManager) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.districtManager = districtManager;
        startActivePlayerSampler();
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Plot plot = plotManager.getPlotAt(event.getBlock().getWorld(), event.getBlock().getX(), event.getBlock().getZ());
        if (plot == null) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> districtManager.updateBuiltBlocks(plot, 1));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Plot plot = plotManager.getPlotAt(event.getBlock().getWorld(), event.getBlock().getX(), event.getBlock().getZ());
        if (plot == null) {
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> districtManager.updateBuiltBlocks(plot, -1));
    }

    private void startActivePlayerSampler() {
        int intervalTicks = plugin.getConfig().getInt("districts.active-player-interval-ticks", 200);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Map<String, Set<UUID>> districtPlayers = new HashMap<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
                District district = districtManager.getDistrictForPlot(plot);
                if (district == null) {
                    continue;
                }
                districtPlayers.computeIfAbsent(district.getId(), key -> new HashSet<>()).add(player.getUniqueId());
            }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> districtManager.updateActivePlayers(districtPlayers));
        }, intervalTicks, intervalTicks);
    }
}
