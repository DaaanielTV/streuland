package de.streuland.command;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.path.PathGenerator;
import de.streuland.plot.snapshot.SnapshotManager;
import de.streuland.plot.snapshot.SnapshotMeta;
import de.streuland.rules.RuleEngine;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Handles /plot command and subcommands.
 * 
 * Subcommands:
 * - /plot create - Generate and claim a new plot (admin/on-demand generation)
 * - /plot claim - Claim an existing unclaimed plot at player location
 * - /plot info - Get plot info at current location
 * - /plot trust - Trust a player on your plot
 * - /plot untrust - Untrust a player from your plot
 * - /plot home - Teleport to your plot
 * - /plot list - List your plots
 */
public class PlotCommandExecutor implements CommandExecutor {
    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final PathGenerator pathGenerator;
    private final SnapshotManager snapshotManager;
    private final RuleEngine ruleEngine;
    
    public PlotCommandExecutor(JavaPlugin plugin, PlotManager plotManager, PathGenerator pathGenerator,
                               SnapshotManager snapshotManager, RuleEngine ruleEngine) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.pathGenerator = pathGenerator;
        this.snapshotManager = snapshotManager;
        this.ruleEngine = ruleEngine;
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
            case "snapshot":
                return handleSnapshot(player, args);
            case "rules":
                return handleRules(player, args);
            default:
                player.sendMessage("§cUnbekannter Befehl. Nutze /plot help");
                return true;
        }
    }
    
    /**
     * /plot create - Generate a NEW plot at a random location (on-demand generation).
     * This command:
     * - Does NOT check plot limit (allows admin/system to generate pools)
     * - Generates a new plot location
     * - Immediately claims it for the player
     * - Counts against the player's 4-plot limit after claiming
     */
    private boolean handleCreate(Player player) {
        // Check if player has reached plot limit BEFORE creating
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
                    player.sendMessage("§aPlot erstellt und beansprucht! Lage: " + plot.getCenterX() + ", " + plot.getCenterZ());
                    player.sendMessage("§aNutze /plot home um dorthin zu teleportieren");
                });
            } else {
                player.sendMessage("§cKein geeigneter Ort für deinen Plot gefunden. Versuche es später erneut.");
            }
        });
        
        return true;
    }
    
    /**
     * /plot claim - Claim an EXISTING unclaimed plot at player's current location.
     * This command:
     * - Is POSITION-BASED (player must stand in plot)
     * - Does NOT generate any plot
     * - Requires plot to be in UNCLAIMED state
     * - Transitions plot from UNCLAIMED to CLAIMED
     * - Counts against the player's 4-plot limit
     */
    private boolean handleClaim(Player player) {
        // Get the plot the player is standing on
        Plot plot = plotManager.getPlotAt(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        
        if (plot == null) {
            player.sendMessage("§cDu stehst in keinem Plot!");
            return true;
        }
        
        // Check if plot is unclaimed
        if (plot.getState() != Plot.PlotState.UNCLAIMED) {
            if (plot.getOwner() != null) {
                player.sendMessage("§cDieser Plot gehört bereits " + plot.getOwner() + "!");
            } else {
                player.sendMessage("§cDieser Plot kann nicht beansprucht werden!");
            }
            return true;
        }
        
        // Check if player has reached plot limit
        List<Plot> playerPlots = plotManager.getStorage().getPlayerPlots(player.getUniqueId());
        if (playerPlots.size() >= 4) {
            player.sendMessage("§cDu kannst maximal 4 Plots besitzen!");
            return true;
        }
        
        // Claim the plot (transitions from UNCLAIMED to CLAIMED)
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
            player.sendMessage("§cDu stehst in keinem Plot!");
            return true;
        }
        
        player.sendMessage("§6=== Plot Informationen ===");
        player.sendMessage("§eID: §f" + plot.getPlotId());
        player.sendMessage("§ePosition: §f" + plot.getCenterX() + ", " + plot.getCenterZ());
        player.sendMessage("§eGröße: §f" + plot.getSize() + "x" + plot.getSize());
        player.sendMessage("§eZustand: §f" + (plot.getState() == Plot.PlotState.UNCLAIMED ? "§eUNBEANSPRUCHT" : "§aBEANSPRUCHT"));
        player.sendMessage("§eEigentümer: §f" + (plot.getOwner() != null ? plot.getOwner() : "Niemand"));
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
            player.sendMessage("§cDu stehst in keinem Plot!");
            return true;
        }
        
        if (plot.getOwner() == null || !plot.getOwner().equals(player.getUniqueId())) {
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
            player.sendMessage("§cDu stehst in keinem Plot!");
            return true;
        }
        
        if (plot.getOwner() == null || !plot.getOwner().equals(player.getUniqueId())) {
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
        player.sendMessage("§e/plot create§f - Generiere und beanspruche einen neuen Plot");
        player.sendMessage("§e/plot claim§f - Beanspruche einen ungeclaimten Plot unter deinen Füßen");
        player.sendMessage("§e/plot info§f - Zeige Informationen zum aktuellen Plot");
        player.sendMessage("§e/plot trust <Spieler>§f - Vertraue einem Spieler");
        player.sendMessage("§e/plot untrust <Spieler>§f - Entferne Vertrauen von einem Spieler");
        player.sendMessage("§e/plot home§f - Teleportiere dich zu deinem Plot");
        player.sendMessage("§e/plot list§f - Liste deine Plots auf");
        player.sendMessage("§e/plot snapshot <create|list|restore>§f - Plot Snapshot Befehle");
        player.sendMessage("§e/plot rules reload§f - Regeln neu laden");
    }

    private boolean handleSnapshot(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cVerwendung: /plot snapshot <create|list|restore>");
            return true;
        }
        String action = args[1].toLowerCase();
        boolean isAdmin = player.hasPermission(SnapshotManager.PERMISSION_ADMIN_RESTORE);
        if (!player.hasPermission(SnapshotManager.PERMISSION_SNAPSHOT) && !isAdmin) {
            player.sendMessage("§cKeine Berechtigung für Snapshots!");
            return true;
        }
        Plot plot = plotManager.getPlotAt(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (plot == null) {
            player.sendMessage("§cDu stehst in keinem Plot!");
            return true;
        }
        boolean isOwner = plot.getOwner() != null && plot.getOwner().equals(player.getUniqueId());
        if (!isOwner && !isAdmin) {
            player.sendMessage("§cDu kannst diesen Plot nicht sichern!");
            return true;
        }
        if ("create".equals(action)) {
            player.sendMessage("§eSnapshot wird erstellt...");
            snapshotManager.createSnapshot(plot, player.getUniqueId()).thenAccept(snapshot -> {
                player.sendMessage("§aSnapshot erstellt: §f" + snapshot.getId());
            });
            return true;
        }
        if ("list".equals(action)) {
            List<SnapshotMeta> snapshots = snapshotManager.listSnapshots(plot.getPlotId());
            if (snapshots.isEmpty()) {
                player.sendMessage("§cKeine Snapshots vorhanden!");
                return true;
            }
            player.sendMessage("§6=== Snapshots ===");
            for (SnapshotMeta meta : snapshots) {
                player.sendMessage("§e" + meta.getId() + "§f - " + meta.getCreatedAt());
            }
            return true;
        }
        if ("restore".equals(action)) {
            if (args.length < 3) {
                player.sendMessage("§cVerwendung: /plot snapshot restore <id> [instant]");
                return true;
            }
            String snapshotId = args[2];
            boolean delayed = args.length < 4 || !args[3].equalsIgnoreCase("instant");
            player.sendMessage("§eSnapshot wird wiederhergestellt...");
            snapshotManager.restoreSnapshot(plot.getPlotId(), snapshotId, delayed).thenRun(() -> {
                player.sendMessage("§aSnapshot wiederhergestellt!");
            });
            return true;
        }
        player.sendMessage("§cVerwendung: /plot snapshot <create|list|restore>");
        return true;
    }

    private boolean handleRules(Player player, String[] args) {
        if (args.length < 2 || !"reload".equalsIgnoreCase(args[1])) {
            player.sendMessage("§cVerwendung: /plot rules reload");
            return true;
        }
        if (!player.hasPermission("streuland.rules.reload")) {
            player.sendMessage("§cKeine Berechtigung!");
            return true;
        }
        ruleEngine.reload();
        player.sendMessage("§aRegeln neu geladen!");
        return true;
    }
}
