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
    private final PlotAccessController accessController;

    public ProtectionListener(JavaPlugin plugin, PlotManager plotManager, PlotFlagManager plotFlagManager, MessageProvider messageProvider) {
        this.plotManager = plotManager;
        this.plotFlagManager = plotFlagManager;
        this.messageProvider = messageProvider;
        this.allowVisitorInteract = plugin.getConfig().getBoolean("protection.allow-visitor-interact", false);
        this.messageProvider = new MessageProvider(plugin);
        this.accessController = new PlotAccessController(plotManager);
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
        PlotManager.AccessDecision decision = plotManager.evaluateAccess(
                event.getBlock().getWorld(),
                event.getBlock().getX(),
                event.getBlock().getY(),
                event.getBlock().getZ(),
                player.getUniqueId(),
                Permission.BREAK
        );
        switch (decision.getAreaType()) {
            case PATH:
                event.setCancelled(true);
                player.sendMessage(messageProvider.t(player, "protection.break.path"));
                return;
            case PLOT_UNCLAIMED:
                return;
            case PLOT_CLAIMED:
                if (!decision.isAllowed()) {
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
        PlotManager.AccessDecision decision = plotManager.evaluateAccess(
                event.getBlock().getWorld(),
                event.getBlock().getX(),
                event.getBlock().getY(),
                event.getBlock().getZ(),
                player.getUniqueId(),
                Permission.BUILD
        );
        switch (decision.getAreaType()) {
            case PATH:
                event.setCancelled(true);
                player.sendMessage(messageProvider.t(player, "protection.place.path"));
                return;
            case PLOT_UNCLAIMED:
                return;
            case PLOT_CLAIMED:
                if (!decision.isAllowed()) {
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
        Material material = event.getClickedBlock().getType();
        Permission permission = resolveInteractPermission(material);
        PlotManager.AccessDecision decision = plotManager.evaluateAccess(
                event.getClickedBlock().getWorld(),
                event.getClickedBlock().getX(),
                event.getClickedBlock().getY(),
                event.getClickedBlock().getZ(),
                player.getUniqueId(),
                permission
        );
        if (decision.getAreaType() != AreaType.PLOT_CLAIMED) {
            return;
        }

        if (decision.isAllowed()) {
            return;
        }

        if (!allowVisitorInteract
                && decision.getActor() == PlotManager.AccessActor.UNAUTHORIZED
                && isInteractiveBlock(material)) {
            event.setCancelled(true);
            player.sendMessage(messageProvider.t(player, "protection.interact.visitor_blocked"));
        }
    }

    private Permission resolveInteractPermission(Material material) {
        return isContainerBlock(material) ? Permission.CONTAINER_ACCESS : Permission.INTERACT;
    }

    private boolean isContainerBlock(Material material) {
        String name = material.toString();
        return name.contains("CHEST")
                || name.contains("BARREL")
                || name.contains("SHULKER_BOX")
                || name.contains("HOPPER")
                || name.contains("DROPPER")
                || name.contains("DISPENSER")
                || name.contains("FURNACE")
                || name.contains("BREWING_STAND")
                || name.contains("BLAST_FURNACE")
                || name.contains("SMOKER");
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
