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
 * Handles protection of plots, paths, and unclaimed areas with three-zone logic.
 * 
 * Zones:
 * 1. PATH (Y=63-67, specific materials): Public, indestructible
 * 2. PLOT - UNCLAIMED: Public, anyone can build
 * 3. PLOT - CLAIMED: Only owner/trusted can build
 * 4. WILDERNESS: No building allowed outside all above zones
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
        
        // ZONE 1: PATH blocks (Y=63-67, specific materials) - always deny
        if (isPathBlock(event.getBlock())) {
            event.setCancelled(true);
            player.sendMessage("§cDu kannst Path-Blöcke nicht abbauen!");
            return;
        }
        
        // ZONE 2 & 3: Check if in a plot
        Plot plot = plotManager.getPlotAt(x, z);
        
        if (plot != null) {
            // Block is in a plot
            if (plot.getState() == Plot.PlotState.UNCLAIMED) {
                // ZONE 2: UNCLAIMED plot - allow any player to break
                return;
            } else if (plot.getState() == Plot.PlotState.CLAIMED) {
                // ZONE 3: CLAIMED plot - only owner/trusted allowed
                if (!plot.isAllowed(player.getUniqueId())) {
                    event.setCancelled(true);
                    player.sendMessage("§cDieser Plot gehört " + plot.getOwner() + "!");
                    return;
                }
            }
        } else {
            // ZONE 4: WILDERNESS - no building allowed outside all plots
            event.setCancelled(true);
            player.sendMessage("§cDu kannst nur in deinem Plot abbauen!");
        }
    }
    
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        int x = event.getBlock().getX();
        int z = event.getBlock().getZ();
        
        // ZONE 1: PATH blocks (Y=63-67) - always deny
        if (isPathBlock(event.getBlock())) {
            event.setCancelled(true);
            player.sendMessage("§cDu kannst nicht auf Paths bauen!");
            return;
        }
        
        // ZONE 2 & 3: Check if in a plot
        Plot plot = plotManager.getPlotAt(x, z);
        
        if (plot != null) {
            // Block is in a plot
            if (plot.getState() == Plot.PlotState.UNCLAIMED) {
                // ZONE 2: UNCLAIMED plot - allow any player to place
                return;
            } else if (plot.getState() == Plot.PlotState.CLAIMED) {
                // ZONE 3: CLAIMED plot - only owner/trusted allowed
                if (!plot.isAllowed(player.getUniqueId())) {
                    event.setCancelled(true);
                    player.sendMessage("§cDieser Plot gehört " + plot.getOwner() + "!");
                    return;
                }
            }
        } else {
            // ZONE 4: WILDERNESS - no building allowed outside all plots
            event.setCancelled(true);
            player.sendMessage("§cDu kannst nur in deinem Plot bauen!");
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
            if (plot.getState() == Plot.PlotState.UNCLAIMED) {
                // UNCLAIMED plots: allow interaction for anyone
                return;
            } else if (plot.getState() == Plot.PlotState.CLAIMED) {
                // CLAIMED plots: check if player is allowed
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
