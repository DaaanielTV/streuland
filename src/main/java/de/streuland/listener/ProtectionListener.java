package de.streuland.listener;

import de.streuland.plot.AreaType;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.i18n.MessageProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles protection of plots, paths, and unclaimed areas with area-type logic.
 */
public class ProtectionListener implements Listener {
    private final PlotManager plotManager;
    private final boolean allowVisitorInteract;
    private final MessageProvider messageProvider;

    public ProtectionListener(JavaPlugin plugin, PlotManager plotManager, MessageProvider messageProvider) {
        this.plotManager = plotManager;
        this.messageProvider = messageProvider;
        this.allowVisitorInteract = plugin.getConfig().getBoolean("protection.allow-visitor-interact", false);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        int x = event.getBlock().getX();
        int y = event.getBlock().getY();
        int z = event.getBlock().getZ();

        AreaType areaType = plotManager.resolveAreaTypeAt(event.getBlock().getWorld(), x, y, z);
        switch (areaType) {
            case PATH:
                event.setCancelled(true);
                player.sendMessage(messageProvider.t(player, "protection.break.path"));
                return;
            case PLOT_UNCLAIMED:
                return;
            case PLOT_CLAIMED:
                Plot plot = plotManager.getPlotAt(event.getBlock().getWorld(), x, z);
                if (plot != null && !plot.isAllowed(player.getUniqueId())) {
                    event.setCancelled(true);
                    player.sendMessage(messageProvider.t(player, "protection.plot.protected"));
                }
                return;
            case WILDERNESS:
            default:
                event.setCancelled(true);
                player.sendMessage(messageProvider.t(player, "protection.break.only_plot"));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        int x = event.getBlock().getX();
        int y = event.getBlock().getY();
        int z = event.getBlock().getZ();

        AreaType areaType = plotManager.resolveAreaTypeAt(event.getBlock().getWorld(), x, y, z);
        switch (areaType) {
            case PATH:
                event.setCancelled(true);
                player.sendMessage(messageProvider.t(player, "protection.place.path"));
                return;
            case PLOT_UNCLAIMED:
                return;
            case PLOT_CLAIMED:
                Plot plot = plotManager.getPlotAt(event.getBlock().getWorld(), x, z);
                if (plot != null && !plot.isAllowed(player.getUniqueId())) {
                    event.setCancelled(true);
                    player.sendMessage(messageProvider.t(player, "protection.plot.protected"));
                }
                return;
            case WILDERNESS:
            default:
                event.setCancelled(true);
                player.sendMessage(messageProvider.t(player, "protection.place.only_plot"));
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();
        int x = event.getClickedBlock().getX();
        int y = event.getClickedBlock().getY();
        int z = event.getClickedBlock().getZ();

        AreaType areaType = plotManager.resolveAreaTypeAt(event.getClickedBlock().getWorld(), x, y, z);
        if (areaType != AreaType.PLOT_CLAIMED) {
            return;
        }

        Plot plot = plotManager.getPlotAt(event.getClickedBlock().getWorld(), x, z);
        if (plot == null || plot.isAllowed(player.getUniqueId())) {
            return;
        }

        if (!allowVisitorInteract && isInteractiveBlock(event.getClickedBlock().getType())) {
            event.setCancelled(true);
            player.sendMessage(messageProvider.t(player, "protection.interact.visitor_blocked"));
        }
    }

    private boolean isInteractiveBlock(Material material) {
        return material.toString().contains("DOOR") ||
                material.toString().contains("BUTTON") ||
                material.toString().contains("LEVER") ||
                material.toString().contains("TRAPDOOR") ||
                material.toString().contains("GATE") ||
                material.toString().contains("CHEST") ||
                material.toString().contains("FURNACE") ||
                material.toString().contains("DISPENSER") ||
                material.toString().contains("DROPPER") ||
                material.toString().contains("REPEATER") ||
                material.toString().contains("COMPARATOR");
    }
}