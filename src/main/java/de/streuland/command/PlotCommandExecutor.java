package de.streuland.command;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.path.PathGenerator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;
import java.util.List;

/**
 * Handles /plot command and subcommands.
 * 
 * Subcommands:
 * - /plot create - Create a new plot
 * - /plot info - Get plot info
 * - /plot trust - Trust a player
 * - /plot untrust - Untrust a player
 * - /plot home - Teleport to plot
 */
public class PlotCommandExecutor implements CommandExecutor {
    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final PathGenerator pathGenerator;
    
    public PlotCommandExecutor(JavaPlugin plugin, PlotManager plotManager, PathGenerator pathGenerator) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.pathGenerator = pathGenerator;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cNur Spieler können Plot-Befehle verwenden!");
            return true;
        }
        
        Player player = (Player) sender;
        
        if (args.length == 0) {
            showHelp(player);
            return true;
        }
        
        String subcommand = args[0].toLowerCase();
        
        switch (subcommand) {
            case "create":
                return handleCreate(player);
            case "claim":
                return handleClaim(player);
            case "info":
                return handleInfo(player);
            case "trust":
                return handleTrust(player, args);
            case "untrust":
                return handleUntrust(player, args);
            case "home":
                return handleHome(player, args);
            case "list":
                return handleList(player);
            default:
                player.sendMessage("§cUnbekannter Befehl. Nutze /plot help");
                return true;
        }
    }
    
    private boolean handleCreate(Player player) {
        // Check if player has reached plot limit (4 plots)
        List<Plot> playerPlots = plotManager.getStorage().getPlayerPlots(player.getUniqueId());
        if (playerPlots.size() >= 4) {
            player.sendMessage("§cDu kannst maximal 4 Plots besitzen!");
            return true;
        }
        
        player.sendMessage("§eSuche nach einem Plot-Ort...");
        
        // Run async plot creation
        plotManager.createPlotAsync(player.getUniqueId()).thenAccept(plot -> {
            if (plot != null) {
                // Generate path on main thread
                List<PathGenerator.BlockPosition> pathBlocks = pathGenerator.generatePath(plot);
                
                plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                    pathGenerator.buildPathBlocks(pathBlocks);
                    player.sendMessage("§aPlot erstellt! Lage: " + plot.getCenterX() + ", " + plot.getCenterZ());
                    player.sendMessage("§aNutze /plot home um dorthin zu teleportieren");
                });
            } else {
                player.sendMessage("§cKein geeigneter Ort für deinen Plot gefunden. Versuche es später erneut.");
            }
        });
        
        return true;
    }
    
    private boolean handleClaim(Player player) {
        // Get the plot the player is standing on
        Plot plot = plotManager.getPlotAt(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        
        if (plot == null) {
            player.sendMessage("§cDu stehst auf keinem Plot!");
            return true;
        }
        
        // Check if plot already has an owner
        if (plot.getOwner() != null) {
            player.sendMessage("§cDieser Plot ist bereits beansprucht!");
            return true;
        }
        
        // Check if player has reached plot limit (4 plots)
        List<Plot> playerPlots = plotManager.getStorage().getPlayerPlots(player.getUniqueId());
        if (playerPlots.size() >= 4) {
            player.sendMessage("§cDu kannst maximal 4 Plots besitzen!");
            return true;
        }
        
        // Claim the plot and generate path
        plotManager.getStorage().claimPlot(plot.getPlotId(), player.getUniqueId());
        
        // Generate and build path
        List<PathGenerator.BlockPosition> pathBlocks = pathGenerator.generatePath(plot);
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            pathGenerator.buildPathBlocks(pathBlocks);
            player.sendMessage("§aPlot beansprucht! Verwende /plot info für mehr Informationen.");
        });
        
        return true;
    }
    
    private boolean handleInfo(Player player) {
        Plot plot = plotManager.getPlotAt(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        
        if (plot == null) {
            player.sendMessage("§cDu stehst auf keinem Plot!");
            return true;
        }
        
        player.sendMessage("§6=== Plot Informationen ===");
        player.sendMessage("§eID: §f" + plot.getPlotId());
        player.sendMessage("§ePosition: §f" + plot.getCenterX() + ", " + plot.getCenterZ());
        player.sendMessage("§eGröße: §f" + plot.getSize() + "x" + plot.getSize());
        player.sendMessage("§eEigentümer: §f" + plot.getOwner());
        player.sendMessage("§eVertraut: §f" + plot.getTrustedPlayers().size() + " Spieler");
        
        return true;
    }
    
    private boolean handleTrust(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cVerwendung: /plot trust <Spieler>");
            return true;
        }
        
        Plot plot = plotManager.getPlotAt(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        
        if (plot == null) {
            player.sendMessage("§cDu stehst auf keinem Plot!");
            return true;
        }
        
        if (!plot.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§cDu besitzt diesen Plot nicht!");
            return true;
        }
        
        String targetName = args[1];
        Player target = plugin.getServer().getPlayer(targetName);
        
        if (target == null) {
            player.sendMessage("§cSpieler nicht gefunden!");
            return true;
        }
        
        plotManager.trustPlayer(plot.getPlotId(), player.getUniqueId(), target.getUniqueId());
        player.sendMessage("§a" + target.getName() + " ist jetzt vertraut!");
        
        return true;
    }
    
    private boolean handleUntrust(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cVerwendung: /plot untrust <Spieler>");
            return true;
        }
        
        Plot plot = plotManager.getPlotAt(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        
        if (plot == null) {
            player.sendMessage("§cDu stehst auf keinem Plot!");
            return true;
        }
        
        if (!plot.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§cDu besitzt diesen Plot nicht!");
            return true;
        }
        
        String targetName = args[1];
        Player target = plugin.getServer().getPlayer(targetName);
        
        if (target == null) {
            player.sendMessage("§cSpieler nicht gefunden!");
            return true;
        }
        
        plotManager.untrustPlayer(plot.getPlotId(), player.getUniqueId(), target.getUniqueId());
        player.sendMessage("§a" + target.getName() + " ist nicht mehr vertraut!");
        
        return true;
    }
    
    private boolean handleHome(Player player, String[] args) {
        List<Plot> plots = plotManager.getStorage().getPlayerPlots(player.getUniqueId());
        
        if (plots.isEmpty()) {
            player.sendMessage("§cDu besitzt keinen Plot!");
            return true;
        }
        
        // If player has only one plot, teleport directly
        if (plots.size() == 1) {
            Plot plot = plots.get(0);
            player.teleport(plotManager.getWorld().getBlockAt(plot.getCenterX(), plot.getSpawnY(), plot.getCenterZ()).getLocation());
            player.sendMessage("§aDu wurdest zu deinem Plot teleportiert!");
            return true;
        }
        
        // If player provided a number, teleport to that plot
        if (args.length > 1) {
            try {
                int plotNumber = Integer.parseInt(args[1]) - 1;  // Convert to 0-indexed
                if (plotNumber < 0 || plotNumber >= plots.size()) {
                    player.sendMessage("§cUngültige Plot-Nummer!");
                    return true;
                }
                Plot plot = plots.get(plotNumber);
                player.teleport(plotManager.getWorld().getBlockAt(plot.getCenterX(), plot.getSpawnY(), plot.getCenterZ()).getLocation());
                player.sendMessage("§aDu wurdest zu Plot " + (plotNumber + 1) + " teleportiert!");
                return true;
            } catch (NumberFormatException e) {
                player.sendMessage("§cBitte gib eine Nummer ein: /plot home <Nummer>");
                return true;
            }
        }
        
        // If player has multiple plots, show selection
        player.sendMessage("§6=== Wähle einen Plot ===");
        for (int i = 0; i < plots.size(); i++) {
            Plot plot = plots.get(i);
            player.sendMessage("§e/plot home " + (i + 1) + "§f: Plot bei " + plot.getCenterX() + ", " + plot.getCenterZ());
        }
        
        return true;
    }
    
    private boolean handleList(Player player) {
        List<Plot> plots = plotManager.getStorage().getPlayerPlots(player.getUniqueId());
        
        if (plots.isEmpty()) {
            player.sendMessage("§cDu besitzt keinen Plot!");
            return true;
        }
        
        player.sendMessage("§6=== Deine Plots ===");
        for (Plot plot : plots) {
            player.sendMessage("§e" + plot.getPlotId() + "§f: " + plot.getCenterX() + ", " + plot.getCenterZ());
        }
        
        return true;
    }
    
    private void showHelp(Player player) {
        player.sendMessage("§6=== Streuland Plot Befehle ===");
        player.sendMessage("§e/plot create§f - Erstelle einen neuen Plot");
        player.sendMessage("§e/plot claim§f - Beanspruche einen Plot unter deinen Füßen");
        player.sendMessage("§e/plot info§f - Zeige Informationen zum aktuellen Plot");
        player.sendMessage("§e/plot trust <Spieler>§f - Vertraue einem Spieler");
        player.sendMessage("§e/plot untrust <Spieler>§f - Entferne Vertrauen von einem Spieler");
        player.sendMessage("§e/plot home§f - Teleportiere dich zu deinem Plot");
        player.sendMessage("§e/plot list§f - Liste deine Plots auf");
    }
}
