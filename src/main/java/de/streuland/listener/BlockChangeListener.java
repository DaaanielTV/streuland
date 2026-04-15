package de.streuland.listener;

import de.streuland.admin.BlockChangeAction;
import de.streuland.admin.BlockChangeLogger;
import de.streuland.analytics.PlotAnalyticsService;
import de.streuland.history.JournalManager;
import de.streuland.history.PlotChangeJournal;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.upgrade.PlotExperienceResult;
import de.streuland.plot.upgrade.PlotProgressionService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BlockChangeListener implements Listener {
    private final PlotManager plotManager;
    private final BlockChangeLogger blockChangeLogger;
    private final PlotAnalyticsService analyticsService;
    private final PlotChangeJournal plotChangeJournal;
    private final JournalManager journalManager;
    private final PlotProgressionService progressionService;
    private final Map<String, IgniteTrace> recentIgnitions = new ConcurrentHashMap<>();

    public BlockChangeListener(JavaPlugin plugin, PlotManager plotManager,
                               BlockChangeLogger blockChangeLogger,
                               PlotAnalyticsService analyticsService) {
        this(plugin, plotManager, blockChangeLogger, analyticsService, null, null, null);
    }

    public BlockChangeListener(JavaPlugin plugin, PlotManager plotManager,
                               BlockChangeLogger blockChangeLogger,
                               PlotAnalyticsService analyticsService,
                               PlotChangeJournal plotChangeJournal,
                               JournalManager journalManager) {
        this(plugin, plotManager, blockChangeLogger, analyticsService, plotChangeJournal, journalManager, null);
    }

    public BlockChangeListener(JavaPlugin plugin, PlotManager plotManager,
                               BlockChangeLogger blockChangeLogger,
                               PlotAnalyticsService analyticsService,
                               PlotChangeJournal plotChangeJournal,
                               JournalManager journalManager,
                               PlotProgressionService progressionService) {
        this.plotManager = plotManager;
        this.blockChangeLogger = blockChangeLogger;
        this.analyticsService = analyticsService;
        this.plotChangeJournal = plotChangeJournal;
        this.journalManager = journalManager;
        this.progressionService = progressionService;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (journalManager != null && journalManager.isApplyingHistory()) {
            return;
        }
        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();
        blockChangeLogger.logChange(player.getUniqueId(), BlockChangeAction.PLACE,
                block.getX(), block.getY(), block.getZ(),
                event.getBlockReplacedState().getType().name(), block.getType().name());
        if (plotChangeJournal != null) {
            plotChangeJournal.recordChange(block.getLocation(), event.getBlockReplacedState(), block.getState(), player.getUniqueId(), System.currentTimeMillis());
        }
        recordEditMetric(player, block.getX(), block.getZ(), "block_place", block.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (journalManager != null && journalManager.isApplyingHistory()) {
            return;
        }
        Block block = event.getBlock();
        Player player = event.getPlayer();
        blockChangeLogger.logChange(player.getUniqueId(), BlockChangeAction.BREAK,
                block.getX(), block.getY(), block.getZ(), block.getType().name(), Material.AIR.name());
        if (plotChangeJournal != null) {
            plotChangeJournal.recordChange(block.getLocation(), block.getState(), null, player.getUniqueId(), System.currentTimeMillis());
        }
        recordEditMetric(player, block.getX(), block.getZ(), "block_break", block.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (event.getPlayer() == null || event.getBlock() == null) {
            return;
        }
        Block block = event.getBlock();
        recentIgnitions.put(key(block), new IgniteTrace(event.getPlayer().getUniqueId(), System.currentTimeMillis()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (journalManager != null && journalManager.isApplyingHistory()) {
            return;
        }
        Block block = event.getBlock();
        UUID actor = BlockChangeLogger.SYSTEM_UUID;
        IgniteTrace trace = recentIgnitions.remove(key(block));
        if (trace != null && (System.currentTimeMillis() - trace.timestamp) < 30_000L) {
            actor = trace.playerId;
        }
        blockChangeLogger.logChange(actor, BlockChangeAction.BURN,
                block.getX(), block.getY(), block.getZ(), block.getType().name(), Material.AIR.name());
        if (plotChangeJournal != null) {
            plotChangeJournal.recordChange(block.getLocation(), block.getState(), null, actor, System.currentTimeMillis());
        }
        Player sourcePlayer = actor == null ? null : Bukkit.getPlayer(actor);
        recordEditMetric(sourcePlayer, block.getX(), block.getZ(), "block_burn", block.getWorld());
    }

    private void recordEditMetric(Player player, int x, int z, String eventType, org.bukkit.World world) {
        Plot plot = plotManager.getPlotAt(world, x, z);
        if (plot == null) {
            return;
        }
        UUID playerId = player == null ? BlockChangeLogger.SYSTEM_UUID : player.getUniqueId();
        analyticsService.record(new de.streuland.analytics.PlotAnalyticsRecord(
                plot.getPlotId(),
                playerId,
                eventType,
                Instant.now(),
                1.0
        ));
        if (progressionService != null && player != null) {
            PlotExperienceResult result = progressionService.awardExperience(plot.getPlotId(), playerId, eventType, 1);
            if (result.hasLeveledUp()) {
                player.sendMessage("§aDein Plot hat Level §e" + result.getCurrentLevel() + " §aerreicht!");
            }
        }
    }

    private String key(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private static class IgniteTrace {
        private final UUID playerId;
        private final long timestamp;

        private IgniteTrace(UUID playerId, long timestamp) {
            this.playerId = playerId;
            this.timestamp = timestamp;
        }
    }
}
