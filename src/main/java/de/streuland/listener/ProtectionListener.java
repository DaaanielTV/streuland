package de.streuland.listener;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.path.PathGenerator;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Set;

/**
 * Handles protection of plots and paths.
 * 
 * Rules:
 * - Only owner/trusted can build in plots
 * - Visitors can walk through but not interact
 * - Paths are public and indestructible
 */
public class ProtectionListener implements Listener {
    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final Set<Material> pathBlocks;
    private final boolean allowVisitorInteract;
    
    public ProtectionListener(JavaPlugin plugin, PlotManager plotManager) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.allowVisitorInteract = plugin.getConfig().getBoolean("protection.allow-visitor-interact", false);
        this.pathBlocks = new java.util.HashSet<>(java.util.Arrays.asList(
            Material.STONE,
            Material.GRANITE,
            Material.DIORITE,
            Material.ANDESITE,
            Material.MOSSY_COBBLESTONE,
            Material.COBBLESTONE,
            Material.DIRT,
            Material.GRAVEL,
            Material.STONE_BRICKS
        ));
        
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        int x = event.getBlock().getX();
        int z = event.getBlock().getZ();
        
        // Check if breaking a path block (always denied) - paths at Y=63 only
        if (isPathBlock(event.getBlock())) {
            event.setCancelled(true);
            player.sendMessage("§cDu kannst Path-Blöcke nicht abbauen!");
            return;
        }
        
        // Check if in a plot
        Plot plot = plotManager.getPlotAt(x, z);
        if (plot != null) {
            // Check if player is allowed to build
            if (!plot.isAllowed(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage("§cDu darfst hier nicht bauen!");
                return;
            }
        } else {
            // Not in any plot - deny breaking outside plots
            event.setCancelled(true);
            player.sendMessage("§cDu kannst nur in deinem Plot abbauen!");
            return;
        }
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        int x = event.getBlock().getX();
        int z = event.getBlock().getZ();
        
        // Check if placing a path block (at Y=63)
        if (isPathBlock(event.getBlock())) {
            event.setCancelled(true);
            player.sendMessage("§cDu kannst nicht auf Paths bauen!");
            return;
        }
        
        // Check if in a plot
        Plot plot = plotManager.getPlotAt(x, z);
        if (plot != null) {
            // Check if player is allowed to build in this plot
            if (!plot.isAllowed(player.getUniqueId())) {
                event.setCancelled(true);
                player.sendMessage("§cDu darfst hier nicht bauen!");
                return;
            }
        } else {
            // Not in any plot - deny building outside plots
            event.setCancelled(true);
            player.sendMessage("§cDu kannst nur in deinem Plot bauen!");
            return;
        }
    }
    
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        
        if (event.getClickedBlock() == null) {
            return;
        }
        
        int x = event.getClickedBlock().getX();
        int z = event.getClickedBlock().getZ();
        
        Plot plot = plotManager.getPlotAt(x, z);
        if (plot != null) {
            // Player is in a plot
            if (!plot.isAllowed(player.getUniqueId())) {
                // Visitor - check if interaction is allowed
                if (!allowVisitorInteract && isInteractiveBlock(event.getClickedBlock().getType())) {
                    event.setCancelled(true);
                    player.sendMessage("§cBesucher können hier nicht interagieren!");
                    return;
                }
            }
        }
    }
    
    /**
     * Checks if a block type is interactive (doors, buttons, etc)
     */
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
    
    /**
     * Check if a block is a path block or in the clearance zone above path (Y=63-67).
     */
    private boolean isPathBlock(org.bukkit.block.Block block) {
        int y = block.getY();
        // Protect path at Y=63 and 4 blocks above (Y=64-67) = 5 blocks total
        if (y < 63 || y > 67) return false;
        
        String blockName = block.getType().name();
        // Only check material for the actual path block (Y=63)
        if (y == 63) {
            return blockName.equals("COBBLESTONE") || 
                   blockName.equals("MOSSY_COBBLESTONE");
        }
        // Blocks above path (Y=64-67) are protected as clearance
        return true;
    }
}
