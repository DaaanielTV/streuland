package de.streuland.listener;

import de.streuland.flags.Flag;
import de.streuland.flags.PlotFlagManager;
import de.streuland.plot.AreaType;
import de.streuland.plot.Permission;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.i18n.MessageProvider;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockRedstoneEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles protection of plots, paths, and unclaimed areas with area-type logic.
 */
public class ProtectionListener implements Listener {
    private final PlotManager plotManager;
    private final PlotFlagManager plotFlagManager;
    private final boolean allowVisitorInteract;
    private final MessageProvider messageProvider;

    public ProtectionListener(JavaPlugin plugin, PlotManager plotManager, MessageProvider messageProvider) {
        this.plotManager = plotManager;
        this.messageProvider = messageProvider;
    public ProtectionListener(JavaPlugin plugin, PlotManager plotManager, PlotFlagManager plotFlagManager) {
        this.plotManager = plotManager;
        this.plotFlagManager = plotFlagManager;
        this.allowVisitorInteract = plugin.getConfig().getBoolean("protection.allow-visitor-interact", false);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getDamager() instanceof Player)) {
            return;
        }
        Plot plot = plotManager.getPlotAt(event.getEntity().getWorld(), event.getEntity().getLocation().getBlockX(), event.getEntity().getLocation().getBlockZ());
        if (plot != null && !plotFlagManager.isFlagEnabled(plot, Flag.PVP)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobSpawn(CreatureSpawnEvent event) {
        Plot plot = plotManager.getPlotAt(event.getLocation().getWorld(), event.getLocation().getBlockX(), event.getLocation().getBlockZ());
        if (plot != null && !plotFlagManager.isFlagEnabled(plot, Flag.MOB_SPAWN)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        Plot plot = plotManager.getPlotAt(event.getLocation().getWorld(), event.getLocation().getBlockX(), event.getLocation().getBlockZ());
        if (plot != null && !plotFlagManager.isFlagEnabled(plot, Flag.EXPLOSIONS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        Plot plot = plotManager.getPlotAt(event.getBlock().getWorld(), event.getBlock().getX(), event.getBlock().getZ());
        if (plot != null && !plotFlagManager.isFlagEnabled(plot, Flag.EXPLOSIONS)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBurn(BlockBurnEvent event) {
        Plot plot = plotManager.getPlotAt(event.getBlock().getWorld(), event.getBlock().getX(), event.getBlock().getZ());
        if (plot != null && !plotFlagManager.isFlagEnabled(plot, Flag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onFireSpread(BlockSpreadEvent event) {
        if (event.getSource().getType() != Material.FIRE) {
            return;
        }
        Plot plot = plotManager.getPlotAt(event.getBlock().getWorld(), event.getBlock().getX(), event.getBlock().getZ());
        if (plot != null && !plotFlagManager.isFlagEnabled(plot, Flag.FIRE_SPREAD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onRedstone(BlockRedstoneEvent event) {
        Plot plot = plotManager.getPlotAt(event.getBlock().getWorld(), event.getBlock().getX(), event.getBlock().getZ());
        if (plot != null && !plotFlagManager.isFlagEnabled(plot, Flag.REDSTONE)) {
            event.setNewCurrent(0);
        }
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
                if (plot != null && !plot.isAllowed(player.getUniqueId(), Permission.BREAK)) {
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
                if (plot != null && !plot.isAllowed(player.getUniqueId(), Permission.BUILD)) {
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
        if (plot == null || plot.isAllowed(player.getUniqueId(), Permission.INTERACT)) {
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
