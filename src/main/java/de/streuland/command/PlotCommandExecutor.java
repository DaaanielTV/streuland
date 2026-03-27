package de.streuland.command;

import de.streuland.admin.AdminPlotService;
import de.streuland.commands.PlotPriceCommand;
import de.streuland.commands.PlotBackupCommand;
import de.streuland.commands.PlotHistoryCommand;
import de.streuland.commands.PlotTeamCommand;
import de.streuland.commands.PlotUpgradeCommand;
import de.streuland.analytics.PlotAnalyticsService;
import de.streuland.approval.PlotApprovalRequest;
import de.streuland.approval.PlotApprovalService;
import de.streuland.discord.DiscordNotifier;
import de.streuland.analytics.PlayerEditStats;
import de.streuland.commands.PlotPortalCommand;
import de.streuland.commands.PlotSchematicCommand;
import de.streuland.district.TraderNpcService;
import de.streuland.flags.Flag;
import de.streuland.flags.PlotFlagManager;
import de.streuland.weather.SeasonalWeatherService;
import de.streuland.path.PathGenerator;
import de.streuland.commands.PlotMergeCommand;
import de.streuland.plot.PlotMergeService;
import de.streuland.plot.SplitStrategy;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.biome.BiomeBonusService;
import de.streuland.plot.biome.BiomeRuleSet;
import de.streuland.plot.environment.PlotEnvironmentService;
import de.streuland.plot.skin.PlotSkinService;
import de.streuland.plot.skin.PlotTheme;
import de.streuland.plot.snapshot.SnapshotManager;
import de.streuland.neighborhood.NeighborhoodService;
import de.streuland.plot.snapshot.SnapshotMeta;
import de.streuland.plot.market.PlotMarketService;
import de.streuland.rules.RuleEngine;
import de.streuland.quest.QuestDefinition;
import de.streuland.quest.QuestProgress;
import de.streuland.quest.QuestService;
import de.streuland.quest.QuestTracker;
import de.streuland.commands.LocaleCommand;
import de.streuland.i18n.MessageProvider;
import de.streuland.commands.PlotMarketCommand;
import de.streuland.economy.PlotEconomyHook;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.*;

/**
 * Handles /plot command and subcommands.
 */
public class PlotCommandExecutor implements CommandExecutor {
    private static final DateTimeFormatter SNAPSHOT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static class DeleteConfirmation {
        private final String plotId;
        private final long expiresAt;

        private DeleteConfirmation(String plotId, long expiresAt) {
            this.plotId = plotId;
            this.expiresAt = expiresAt;
        }
    }

    private static class RestoreConfirmation {
        private final String plotId;
        private final String snapshotId;
        private final boolean delayed;
        private final long expiresAt;

        private RestoreConfirmation(String plotId, String snapshotId, boolean delayed, long expiresAt) {
            this.plotId = plotId;
            this.snapshotId = snapshotId;
            this.delayed = delayed;
            this.expiresAt = expiresAt;
        }
    }

    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final PathGenerator pathGenerator;
    private final SnapshotManager snapshotManager;
    private final RuleEngine ruleEngine;
    private final PlotSkinService plotSkinService;
    private final BiomeBonusService biomeBonusService;
    private final NeighborhoodService neighborhoodService;
    private final QuestService questService;
    private final QuestTracker questTracker;
    private final PlotMarketService plotMarketService;
    private final AdminPlotService adminPlotService;
    private final PlotPriceCommand plotPriceCommand;
    private final PlotAnalyticsService plotAnalyticsService;
    private final TraderNpcService traderNpcService;
    private final SeasonalWeatherService seasonalWeatherService;
    private final PlotFlagManager plotFlagManager;
    private final PlotUpgradeCommand plotUpgradeCommand;
    private final Map<UUID, DeleteConfirmation> pendingDeletes;
    private final Map<UUID, RestoreConfirmation> pendingRestores;
    private final PlotMergeCommand plotMergeCommand;
    private final long deleteConfirmTimeoutMs;
    private final long restoreConfirmTimeoutMs;
    private final Map<UUID, Long> worldTeleportCooldowns;
    
    public PlotCommandExecutor(JavaPlugin plugin, PlotManager plotManager, PathGenerator pathGenerator,
                               SnapshotManager snapshotManager, RuleEngine ruleEngine, PlotSkinService plotSkinService,
                               BiomeBonusService biomeBonusService, NeighborhoodService neighborhoodService,
                               QuestService questService, QuestTracker questTracker, PlotMarketService plotMarketService,
                               AdminPlotService adminPlotService, PlotAnalyticsService plotAnalyticsService,
                               TraderNpcService traderNpcService, SeasonalWeatherService seasonalWeatherService,
                               PlotFlagManager plotFlagManager, PlotUpgradeCommand plotUpgradeCommand) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.pathGenerator = pathGenerator;
        this.snapshotManager = snapshotManager;
        this.ruleEngine = ruleEngine;
        this.plotSkinService = plotSkinService;
        this.biomeBonusService = biomeBonusService;
        this.neighborhoodService = neighborhoodService;
        this.questService = questService;
        this.questTracker = questTracker;
        this.plotMarketService = plotMarketService;
        this.plotPriceCommand = plotPriceCommand;
        this.adminPlotService = adminPlotService;
        this.plotAnalyticsService = plotAnalyticsService;
        this.traderNpcService = traderNpcService;
        this.seasonalWeatherService = seasonalWeatherService;
        this.plotFlagManager = plotFlagManager;
        this.plotUpgradeCommand = plotUpgradeCommand;
        this.pendingDeletes = new HashMap<>();
        this.pendingRestores = new HashMap<>();
        this.plotMergeCommand = new PlotMergeCommand(new PlotMergeService(plugin, plotManager));
        this.deleteConfirmTimeoutMs = plugin.getConfig().getLong("plot.delete-confirm-timeout-seconds", 30L) * 1000L;
        this.restoreConfirmTimeoutMs = plugin.getConfig().getLong("plot.restore-confirm-timeout-seconds", 30L) * 1000L;
        this.worldTeleportCooldowns = new HashMap<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cNur Spieler können Plot-Befehle verwenden!");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showHelp(player);
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        switch (subcommand) {
            case "create":
                return handleCreate(player);
            case "claim":
                return handleClaim(player);
            case "info":
                return handleInfo(player);
            case "team":
                return plotTeamCommand.execute(player, args);
            case "home":
                return handleHome(player, args);
            case "list":
                return handleList(player);
            case "snapshot":
                return handleSnapshot(player, args);
            case "snapshots":
                return handleSnapshot(player, new String[]{"snapshot", "list"});
            case "rollback":
                return handleSnapshot(player, prependSnapshotRestore(args));
            case "restore":
                return handleSnapshot(player, prependSnapshotRestore(args));
            case "rules":
                return handleRules(player, args);
            case "unclaim":
                return handleUnclaim(player, args);
            case "delete":
                return handleDelete(player, args);
            case "confirm":
                if (plotSchematicCommand.confirm(player)) {
                    return true;
                }
                return handleConfirmDelete(player);
            case "cancel":
                return handleCancelDelete(player);
            case "generate":
                return handleGenerate(player, args);
            case "stats":
                return handleStats(player);
            case "backup":
                return plotBackupCommand.handle(player, args);
            case "history":
                return plotHistoryCommand.handle(player, args);
            case "merge":
            case "split":
                return plotMergeCommand.handle(player, args);
            case "style":
                return handleStyle(player, args);
            case "biome":
                return handleBiomeBonus(player, args);
            case "weather":
                return handleWeather(player, args);
            case "neighbor":
                return handleNeighbor(player, args);
            case "quest":
                return handleQuest(player, args);
            case "market":
                return handleMarket(player, args);
            case "price":
                return plotPriceCommand.handle(player, args);
            case "sell":
            case "buy":
            case "auction":
            case "bid":
                return plotMarketCommand.handle(player, args, plotEconomyHook.hasEconomy());
            case "world":
                return handleWorld(player, args);
            case "teleport":
                return handleTeleportWorld(player, args);
            case "trader":
                return traderNpcService.handleTraderCommand(player, args);
            case "inspect":
                return adminPlotService.handleInspect(player, args);
            case "admin":
                return adminPlotService.handleAdmin(player, args);
            case "dashboard":
                return handleDashboardUrl(player, args);
            case "flag":
                return handleFlag(player, args);
            case "upgrade":
            case "upgrades":
                return plotUpgradeCommand.handle(player, args);
            default:
                if ("template".equals(subcommand)) {
                    return plotSchematicCommand.handle(player, args);
                }
                player.sendMessage("§cUnbekannter Befehl. Nutze /plot help");
                return true;
        }
    }

    private boolean handlePendingApprovals(Player player) {
        if (!player.hasPermission("streuland.plot.approval")) {
            player.sendMessage("§cKeine Berechtigung.");
            return true;
        }
        List<PlotApprovalRequest> pending = plotApprovalService.listPending();
        if (pending.isEmpty()) {
            player.sendMessage("§7Keine offenen Plot-Freigaben.");
            return true;
        }
        player.sendMessage("§6Offene Plot-Freigaben: " + pending.size());
        for (PlotApprovalRequest request : pending) {
            player.sendMessage("§e" + request.getId() + " §7- " + request.getPlayerName() + " @ " + request.getWorldName());
        }
        return true;
    }

    private boolean handleApprovalAction(Player player, String[] args, boolean approve) {
        if (!player.hasPermission("streuland.plot.approval")) {
            player.sendMessage("§cKeine Berechtigung.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§cVerwendung: /plot " + (approve ? "approve" : "reject") + " <id>");
            return true;
        }
        boolean success = approve ? plotApprovalService.approve(args[1]) : plotApprovalService.reject(args[1]);
        player.sendMessage(success ? "§aAntrag verarbeitet." : "§cAntrag nicht gefunden.");
        return true;
    }

    private boolean handleCreate(Player player) {
        List<Plot> playerPlots = plotManager.getStorage(player.getWorld()).getPlayerPlots(player.getUniqueId());
        if (playerPlots.size() >= plotManager.getMaxPlotsPerPlayer(player.getWorld())) {
            player.sendMessage("§cDu kannst maximal " + plotManager.getMaxPlotsPerPlayer(player.getWorld()) + " Plots besitzen!");
            return true;
        }

        if (plotApprovalService.requiresApproval(player)) {
            PlotApprovalRequest request = plotApprovalService.createPending(player);
            player.sendMessage("§eDein Plot-Antrag wurde eingereicht: §f" + request.getId());
            player.sendMessage("§7Ein Admin kann mit /plotapprove approve " + request.getId() + " freigeben.");
            return true;
        }

        player.sendMessage("§eSuche nach einem Plot-Ort...");
        plotManager.createPlotAsync(player.getUniqueId(), player.getWorld()).thenAccept(plot -> {
            if (plot != null) {
                List<PathGenerator.BlockPosition> pathBlocks = pathGenerator.generatePath(plot);
                plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                    pathGenerator.buildPathBlocks(pathBlocks);
                    player.sendMessage("§aPlot erstellt und beansprucht! Lage: " + plot.getCenterX() + ", " + plot.getCenterZ());
                    player.sendMessage("§aNutze /plot home um dorthin zu teleportieren");
                    notifyLargeClaim(player.getName(), plot);
                });
            } else {
                player.sendMessage("§cKein geeigneter Ort für deinen Plot gefunden. Versuche es später erneut.");
            }
        });
        return true;
    }

    private void notifyLargeClaim(String playerName, Plot plot) {
        int threshold = plugin.getConfig().getInt("discord.large-claim-threshold", 128);
        if (plot.getSize() < threshold) {
            return;
        }
        Map<String, Object> extras = new HashMap<>();
        extras.put("title", "Large plot claim");
        extras.put("description", playerName + " claimed " + plot.getPlotId() + " (" + plot.getSize() + "x" + plot.getSize() + ")");
        discordNotifier.sendWebhook("plot-alerts", "Large plot claimed", extras);
    }

    private boolean handleClaim(Player player) {
        List<Plot> playerPlots = plotManager.getStorage(player.getWorld()).getPlayerPlots(player.getUniqueId());
        if (playerPlots.size() >= plotManager.getMaxPlotsPerPlayer(player.getWorld())) {
            player.sendMessage("§cDu kannst maximal " + plotManager.getMaxPlotsPerPlayer(player.getWorld()) + " Plots besitzen!");
            return true;
        }

        Plot claimedPlot = plotManager.claimPlotAt(player.getUniqueId(), player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (claimedPlot == null) {
            player.sendMessage("§cHier gibt es keinen beanspruchbaren Plot!");
            return true;
        }

        // Generate and build path
        List<PathGenerator.BlockPosition> pathBlocks = pathGenerator.generatePath(claimedPlot);
        plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            pathGenerator.buildPathBlocks(pathBlocks);
            player.sendMessage("§aPlot beansprucht! Verwende /plot info für mehr Informationen.");
        });
        return true;
    }

    private boolean handleInfo(Player player) {
        Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
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
        player.sendMessage("§eTeammitglieder: §f" + Math.max(0, plot.getRoles().size() - 1) + " Spieler");
        player.sendMessage("§eNachbarschaft: §f" + neighborhoodService.getAnalyticsSummary(plot.getPlotId()));
        return true;
    }

    private boolean handleHome(Player player, String[] args) {
        List<Plot> plots = plotManager.getStorage(player.getWorld()).getPlayerPlots(player.getUniqueId());
        if (plots.isEmpty()) {
            player.sendMessage("§cDu besitzt keinen Plot!");
            return true;
        }

        if (plots.size() == 1) {
            teleportToPlot(player, plots.get(0), "§aDu wurdest zu deinem Plot teleportiert!");
            return true;
        }

        if (args.length > 1) {
            try {
                int plotNumber = Integer.parseInt(args[1]) - 1;
                if (plotNumber < 0 || plotNumber >= plots.size()) {
                    player.sendMessage("§cUngültige Plot-Nummer!");
                    return true;
                }
                teleportToPlot(player, plots.get(plotNumber), "§aDu wurdest zu Plot " + (plotNumber + 1) + " teleportiert!");
                return true;
            } catch (NumberFormatException e) {
                player.sendMessage("§cBitte gib eine Nummer ein: /plot home <Nummer>");
                return true;
            }
        }

        player.sendMessage("§6=== Wähle einen Plot ===");
        for (int i = 0; i < plots.size(); i++) {
            Plot plot = plots.get(i);
            player.sendMessage("§e/plot home " + (i + 1) + "§f: Plot bei " + plot.getCenterX() + ", " + plot.getCenterZ());
        }
        return true;
    }

    private boolean handleList(Player player) {
        List<Plot> plots = plotManager.getStorage(player.getWorld()).getPlayerPlots(player.getUniqueId());
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

    private boolean handleUnclaim(Player player, String[] args) {
        Plot target = resolvePlotFromArgsOrLocation(player, args, 1);
        if (target == null) {
            player.sendMessage("§cKein Plot gefunden. Stehe im Plot oder nutze /plot unclaim <plotId>");
            return true;
        }

        boolean force = player.isOp();
        if (!plotManager.unclaimPlot(target.getPlotId(), player.getUniqueId(), force)) {
            player.sendMessage("§cDu darfst diesen Plot nicht freigeben.");
            return true;
        }

        player.sendMessage("§aPlot " + target.getPlotId() + " wurde freigegeben.");
        return true;
    }

    private boolean handleDelete(Player player, String[] args) {
        Plot target = resolvePlotFromArgsOrLocation(player, args, 1);
        if (target == null) {
            player.sendMessage("§cKein Plot gefunden. Stehe im Plot oder nutze /plot delete <plotId>");
            return true;
        }

        if (!player.isOp() && target.getOwner() != null && !target.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§cDu darfst nur deine eigenen Plots löschen!");
            return true;
        }

        long expiresAt = System.currentTimeMillis() + deleteConfirmTimeoutMs;
        pendingDeletes.put(player.getUniqueId(), new DeleteConfirmation(target.getPlotId(), expiresAt));
        player.sendMessage("§eBestätige mit /plot confirm, um " + target.getPlotId() + " zu löschen.");
        player.sendMessage("§7Abbrechen mit /plot cancel (läuft in " + (deleteConfirmTimeoutMs / 1000L) + "s ab).");
        return true;
    }

    private boolean handleConfirmDelete(Player player) {
        DeleteConfirmation confirmation = pendingDeletes.get(player.getUniqueId());
        if (confirmation == null) {
            player.sendMessage("§cKeine Löschaktion ausstehend.");
            return true;
        }

        if (System.currentTimeMillis() > confirmation.expiresAt) {
            pendingDeletes.remove(player.getUniqueId());
            player.sendMessage("§cLöschbestätigung ist abgelaufen.");
            return true;
        }

        boolean deleted = plotManager.deletePlot(confirmation.plotId, player.getUniqueId(), player.isOp());
        pendingDeletes.remove(player.getUniqueId());

        if (!deleted) {
            player.sendMessage("§cPlot konnte nicht gelöscht werden (evtl. keine Berechtigung mehr). ");
            return true;
        }

        player.sendMessage("§aPlot " + confirmation.plotId + " wurde gelöscht.");
        return true;
    }

    private boolean handleCancelDelete(Player player) {
        if (pendingDeletes.remove(player.getUniqueId()) != null) {
            player.sendMessage("§aAusstehende Löschaktion abgebrochen.");
        } else {
            player.sendMessage("§7Keine ausstehende Löschaktion vorhanden.");
        }
        return true;
    }

    private boolean handleGenerate(Player player, String[] args) {
        if (!player.isOp()) {
            player.sendMessage("§cNur OPs können Plot-Pools generieren.");
            return true;
        }

        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /plot generate <gridSize> <spacing>");
            return true;
        }

        try {
            int gridSize = Integer.parseInt(args[1]);
            int spacing = Integer.parseInt(args[2]);
            if (gridSize < 1 || spacing < plotManager.getPlotSize()) {
                player.sendMessage("§cUngültige Werte. gridSize>=1 und spacing>=plot-size");
                return true;
            }

            plotManager.generateUnclaimedPlots(player.getWorld(), gridSize, spacing);
            player.sendMessage("§aGenerierung gestartet/abgeschlossen. Prüfe Konsole für Details.");
            return true;
        } catch (NumberFormatException e) {
            player.sendMessage("§cBitte nur Zahlen verwenden.");
            return true;
        }
    }

    private boolean handleStats(Player player) {
        Collection<Plot> allPlots = plotManager.getAllPlots(player.getWorld());
        int claimed = 0;
        int unclaimed = 0;

        for (Plot plot : allPlots) {
            if (plot.getState() == Plot.PlotState.CLAIMED) {
                claimed++;
            } else {
                unclaimed++;
            }
        }

        player.sendMessage("§6=== Plot Statistik ===");
        player.sendMessage("§eGesamt: §f" + allPlots.size());
        player.sendMessage("§eBeansprucht: §f" + claimed);
        player.sendMessage("§eUnbeansprucht: §f" + unclaimed);
        player.sendMessage("§eGrid-Zellen: §f" + plotManager.getSpatialGrid(player.getWorld()).getCellCount());

        Plot currentPlot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (currentPlot != null) {
            player.sendMessage("§6=== Edit-Statistik für " + currentPlot.getPlotId() + " ===");
            List<PlayerEditStats> editStats = plotAnalyticsService.getEditStatsForPlot(currentPlot.getPlotId());
            if (editStats.isEmpty()) {
                player.sendMessage("§7Noch keine Edit-Statistik vorhanden.");
            } else {
                int shown = 0;
                for (PlayerEditStats stat : editStats) {
                    if (shown >= 5) {
                        break;
                    }
                    OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(stat.getPlayerId());
                    String name = offlinePlayer.getName() == null ? stat.getPlayerId().toString() : offlinePlayer.getName();
                    player.sendMessage("§e" + name + "§f: Place=" + stat.getPlaces() + ", Break=" + stat.getBreaks() + ", Burn=" + stat.getBurns() + ", Total=" + stat.getTotalEdits());
                    shown++;
                }
            }
        }
        return true;
    }


    private boolean handleStyle(Player player, String[] args) {
        if (args.length < 3 || !"set".equalsIgnoreCase(args[1])) {
            player.sendMessage("§cVerwendung: /plot style set <theme>");
            return true;
        }
        Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (plot == null || plot.getOwner() == null || !plot.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§cDu musst auf deinem eigenen Plot stehen.");
            return true;
        }
        PlotTheme theme = PlotTheme.fromInput(args[2]);
        if (theme == null) {
            player.sendMessage("§cUngültiges Theme. Verfügbar: MODERN, MEDIEVAL, NATURE, STEAMPUNK");
            return true;
        }
        plotSkinService.setTheme(plot, theme);
        player.sendMessage("§aPlot-Stil gesetzt: §f" + theme.getDisplayName());
        return true;
    }



    private boolean handleNeighbor(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cVerwendung: /plot neighbor <add|list|map> [Spieler]");
            return true;
        }

        if ("add".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                player.sendMessage("§cVerwendung: /plot neighbor add <Spieler>");
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            if (target == null || target.getUniqueId() == null) {
                player.sendMessage("§cSpieler nicht gefunden!");
                return true;
            }

            boolean added = neighborhoodService.addTrustedNeighbor(player.getUniqueId(), target.getUniqueId());
            if (!added) {
                player.sendMessage("§cNachbar konnte nicht hinzugefügt werden.");
                return true;
            }

            player.sendMessage("§a" + target.getName() + " als vertrauenswürdigen Nachbarn hinzugefügt.");
            questTracker.onNeighborAdded(player);
            return true;
        }

        if ("list".equalsIgnoreCase(args[1])) {
            for (String line : neighborhoodService.formatNeighborhoodList(player.getUniqueId())) {
                player.sendMessage(line);
            }
            return true;
        }

        if ("map".equalsIgnoreCase(args[1])) {
            neighborhoodService.showNeighborhoodMap(player);
            return true;
        }

        player.sendMessage("§cVerwendung: /plot neighbor <add|list|map> [Spieler]");
        return true;
    }

    private boolean handleBiomeBonus(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cVerwendung: /plot biome <bonus|set>");
            return true;
        }
        if ("set".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                player.sendMessage("§cVerwendung: /plot biome set <biome>");
                return true;
            }
            Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
            PlotEnvironmentService.ChangeResult result = plotEnvironmentService.setBiome(plot, player.getUniqueId(),
                    player.hasPermission(PlotEnvironmentService.PERMISSION_MANAGE), args[2]);
            if (result == PlotEnvironmentService.ChangeResult.SUCCESS) {
                player.sendMessage("§aBiome gesetzt: §f" + args[2].toUpperCase(Locale.ROOT));
            } else if (result == PlotEnvironmentService.ChangeResult.BIOME_NOT_ALLOWED) {
                player.sendMessage("§cBiome nicht erlaubt. Erlaubt: §f" + plotEnvironmentService.getAllowedBiomes());
            } else if (result == PlotEnvironmentService.ChangeResult.UPGRADE_REQUIRED) {
                player.sendMessage("§cDu benötigst ein Plot-Upgrade für diese Funktion.");
            } else if (result == PlotEnvironmentService.ChangeResult.MISSING_PERMISSION) {
                player.sendMessage("§cKeine Berechtigung: " + PlotEnvironmentService.PERMISSION_MANAGE);
            } else if (result == PlotEnvironmentService.ChangeResult.NOT_OWNER) {
                player.sendMessage("§cNur der Plot-Besitzer kann die Umgebung ändern.");
            } else {
                player.sendMessage("§cUngültiges Biom oder kein beanspruchtes Plot.");
            }
            return true;
        }
        if (!"bonus".equalsIgnoreCase(args[1])) {
            player.sendMessage("§cVerwendung: /plot biome <bonus|set>");
            return true;
        }
        Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (plot == null) {
            player.sendMessage("§cDu stehst in keinem Plot!");
            return true;
        }
        org.bukkit.block.Biome biome = player.getLocation().getBlock().getBiome();
        BiomeRuleSet rules = biomeBonusService.getRuleSetForBiome(biome);
        player.sendMessage("§6=== Biome Bonus ===");
        player.sendMessage("§eBiome: §f" + biome.name());
        player.sendMessage("§eAktive Boni: §f" + biomeBonusService.describeBonuses(biome));
        if (!rules.getEnvironmentConstraints().isEmpty()) {
            player.sendMessage("§eBau-Grenzen: §f" + rules.getEnvironmentConstraints());
        }
        return true;
    }



    private boolean handleWeather(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cVerwendung: /plot weather <current|lock|unlock>");
            return true;
        }

        if ("lock".equalsIgnoreCase(args[1]) || "unlock".equalsIgnoreCase(args[1])) {
            boolean lock = "lock".equalsIgnoreCase(args[1]);
            Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
            PlotEnvironmentService.ChangeResult result = plotEnvironmentService.lockWeather(plot, player.getUniqueId(),
                    player.hasPermission(PlotEnvironmentService.PERMISSION_MANAGE), lock);
            if (result == PlotEnvironmentService.ChangeResult.SUCCESS) {
                player.sendMessage(lock ? "§aPlot-Wetter fixiert." : "§aPlot-Wetter wieder dynamisch.");
            } else if (result == PlotEnvironmentService.ChangeResult.UPGRADE_REQUIRED) {
                player.sendMessage("§cDu benötigst ein Plot-Upgrade für diese Funktion.");
            } else if (result == PlotEnvironmentService.ChangeResult.MISSING_PERMISSION) {
                player.sendMessage("§cKeine Berechtigung: " + PlotEnvironmentService.PERMISSION_MANAGE);
            } else if (result == PlotEnvironmentService.ChangeResult.NOT_OWNER) {
                player.sendMessage("§cNur der Plot-Besitzer kann das Wetter verwalten.");
            } else {
                player.sendMessage("§cDu stehst in keinem beanspruchten Plot.");
            }
            return true;
        }

        if (!"current".equalsIgnoreCase(args[1])) {
            player.sendMessage("§cVerwendung: /plot weather <current|lock|unlock>");
            return true;
        }

        player.sendMessage("§6=== Saisonales Wetter ===");
        player.sendMessage("§eAktive Saison: §f" + seasonalWeatherService.getActiveSeason().name());
        player.sendMessage("§eTag: §f" + seasonalWeatherService.getDayInSeason() + " / " + seasonalWeatherService.getSeasonDurationDays());
        player.sendMessage("§eEffekte:");
        for (String line : seasonalWeatherService.getCurrentEffectsSummary()) {
            player.sendMessage("§7- " + line);
        }
        return true;
    }

    private boolean handleWorld(Player player, String[] args) {
        if (args.length < 2 || !"list".equalsIgnoreCase(args[1])) {
            player.sendMessage("§cVerwendung: /plot world list");
            return true;
        }
        List<Plot> plots = plotManager.getAllPlots(player.getWorld());
        long claimed = plots.stream().filter(p -> p.getState() == Plot.PlotState.CLAIMED).count();
        player.sendMessage("§6=== Welt-Plotstatistik: " + player.getWorld().getName() + " ===");
        player.sendMessage("§eGesamt: §f" + plots.size());
        player.sendMessage("§eBeansprucht: §f" + claimed);
        player.sendMessage("§eUnbeansprucht: §f" + (plots.size() - claimed));
        player.sendMessage("§eGrid-Zellen: §f" + plotManager.getSpatialGrid(player.getWorld()).getCellCount());
        return true;
    }

    private boolean handleTeleportWorld(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /plot teleport <world> <plot_id>");
            return true;
        }
        long now = System.currentTimeMillis();
        long cooldown = plugin.getConfig().getLong("plot.world-teleport-cooldown-seconds", 15L) * 1000L;
        long readyAt = worldTeleportCooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (readyAt > now) {
            player.sendMessage("§cTeleport-Cooldown aktiv: " + ((readyAt - now) / 1000L) + "s");
            return true;
        }
        org.bukkit.World targetWorld = Bukkit.getWorld(args[1]);
        if (targetWorld == null) {
            player.sendMessage("§cWelt nicht gefunden: " + args[1]);
            return true;
        }
        Plot plot = plotManager.getStorage(targetWorld).getPlot(args[2]);
        if (plot == null) {
            player.sendMessage("§cPlot nicht gefunden in Welt " + targetWorld.getName());
            return true;
        }
        player.teleport(targetWorld.getBlockAt(plot.getCenterX(), plot.getSpawnY(), plot.getCenterZ()).getLocation());
        worldTeleportCooldowns.put(player.getUniqueId(), now + cooldown);
        player.sendMessage("§aTeleportiert zu " + plot.getPlotId() + " in " + targetWorld.getName());
        return true;
    }

    private Plot resolvePlotFromArgsOrLocation(Player player, String[] args, int index) {
        if (args.length > index) {
            return plotManager.getStorage(player.getWorld()).getPlot(args[index]);
        }
        return plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
    }

    private void teleportToPlot(Player player, Plot plot, String message) {
        org.bukkit.World world = plotManager.getWorldForPlot(plot.getPlotId());
        player.teleport(world.getBlockAt(plot.getCenterX(), plot.getSpawnY(), plot.getCenterZ()).getLocation());
        player.sendMessage(message);
    }

    private void showHelp(Player player) {
        player.sendMessage("§6=== Streuland Plot Befehle ===");
        player.sendMessage("§e/plot create§f - Generiere und beanspruche einen neuen Plot");
        player.sendMessage("§e/plot claim§f - Beanspruche einen ungeclaimten Plot unter deinen Füßen");
        player.sendMessage("§e/plot info§f - Zeige Informationen zum aktuellen Plot");
        player.sendMessage("§e/plot team <...>§f - Verwalte Plot-Teamrollen");
        player.sendMessage("§e/plot home [Nummer]§f - Teleportiere dich zu einem eigenen Plot");
        player.sendMessage("§e/plot list§f - Liste deine Plots auf");
        player.sendMessage("§e/plot snapshot <create|list|restore>§f - Plot Snapshot Befehle"
        );
        player.sendMessage("§e/plot restore <snapshotId|confirm>§f - Rollback mit Bestätigung");
        player.sendMessage("§e/plot backup <take|list|restore> <plotId> [snapshotId]§f - Schematic Backups");
        player.sendMessage(" ");
        player.sendMessage("§e/plot rules reload§f - Regeln neu laden");
        player.sendMessage("§e/plot style set <theme>§f - Setze das Plot-Theme");
        player.sendMessage("§e/plot biome bonus§f - Zeigt aktive Biom-Boni");
        player.sendMessage("§e/plot biome set <biome>§f - Setzt Plot-Biom innerhalb sicherer Grenzen");
        player.sendMessage("§e/plot weather <current|lock|unlock>§f - Saisonstatus und Plot-Wettersteuerung");
        player.sendMessage("§e/plot neighbor <add|list|map>§f - Nachbarschaftshandel verwalten");
        player.sendMessage("§e/plot quest <list|progress>§f - Quest-Übersicht und Fortschritt");
        player.sendMessage("§e/plot market <list|sell|buy|history>§f - Spieler-Marktplatz für Plots"
        );
        player.sendMessage("§e/plot sell <price>§f - Aktuelles Plot zum Festpreis anbieten");
        player.sendMessage("§e/plot buy <plotId>§f - Plot direkt kaufen");
        player.sendMessage("§e/plot auction <price> <durationMin>§f - Auktion starten");
        player.sendMessage("§e/plot bid <plotId> <amount>§f - Auf Auktion bieten");
        player.sendMessage("§e/plot trader <nearest|buy|stock>§f - Distrikt-Händler nutzen/verwalten");
        player.sendMessage("§e/plot world list§f - Statistiken der aktuellen Welt");
        player.sendMessage("§e/plot teleport <world> <plot_id>§f - Teleportiere weltenübergreifend");
        player.sendMessage("§e/plot inspect <x> <z>§f - Zeige Block-Änderungslog für Koordinaten");
        player.sendMessage("§e/plot admin <rollback|log> ...§f - Admin-Tools für Logs und Rollbacks");
        player.sendMessage("§e/plot dashboard url§f - Zeige den Web-Dashboard Link");
        player.sendMessage("§e/plot flag <name> <on|off|default> [plotId]§f - Setzt Plot-Flags");
        player.sendMessage("§e/plot upgrade|upgrades|upgrade buy <id>§f - Verwalte Plot-Upgrades");
    }




    private boolean handleFlag(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /plot flag <name> <on|off|default> [plotId]");
            return true;
        }

        Plot plot = resolvePlotFromArgsOrLocation(player, args, 3);
        if (plot == null || plot.getOwner() == null || !plot.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§cDu kannst nur eigene, beanspruchte Plots konfigurieren.");
            return true;
        }

        Flag flag = PlotFlagManager.parseFlag(args[1]);
        if (flag == null) {
            player.sendMessage("§cUnbekanntes Flag. Erlaubt: " + Arrays.toString(Flag.values()));
            return true;
        }

        String mode = args[2].toLowerCase(Locale.ROOT);
        if (mode.equals("default")) {
            plotFlagManager.clearOverride(plot, flag);
            player.sendMessage("§aFlag " + flag.name() + " auf Standard zurückgesetzt.");
            return true;
        }
        if (mode.equals("on") || mode.equals("true")) {
            plotFlagManager.setFlag(plot, flag, true);
            player.sendMessage("§aFlag " + flag.name() + " aktiviert.");
            return true;
        }
        if (mode.equals("off") || mode.equals("false")) {
            plotFlagManager.setFlag(plot, flag, false);
            player.sendMessage("§aFlag " + flag.name() + " deaktiviert.");
            return true;
        }

        player.sendMessage("§cUngültiger Wert. Nutze on, off oder default.");
        return true;
    }

    private boolean handleDashboardUrl(Player player, String[] args) {
        if (args.length < 2 || !"url".equalsIgnoreCase(args[1])) {
            player.sendMessage("§cVerwendung: /plot dashboard url");
            return true;
        }

        int port = plugin.getConfig().getInt("dashboard.port", 8080);
        String host = plugin.getConfig().getString("dashboard.public-host", "localhost");
        String link = "http://" + host + ":" + port + "/streuland-dashboard";
        player.sendMessage("§aDashboard: §b" + link);
        if (!player.hasPermission("streuland.admin")) {
            player.sendMessage("§7Hinweis: Du hast nur Lesezugriff auf das Dashboard.");
        } else {
            player.sendMessage("§7Admin-Modus aktiv: volle Dashboard-Steuerung verfügbar.");
        }
        return true;
    }

    private boolean handleMarket(Player player, String[] args) {
        return plotMarketService.handleMarketCommand(player, args);
    }


    private boolean handleQuest(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cVerwendung: /plot quest <list|progress>");
            return true;
        }

        if ("list".equalsIgnoreCase(args[1])) {
            player.sendMessage("§6=== Aktive Plot-Quests ===");
            for (QuestDefinition quest : questService.getActiveQuests()) {
                String expired = quest.isExpired() ? "§cABGELAUFEN" : "§aAKTIV";
                player.sendMessage("§e" + quest.getTitle() + " §7(" + quest.getType().name() + ") §8- " + expired);
                player.sendMessage("§7" + quest.getDescription());
            }
            return true;
        }

        if ("progress".equalsIgnoreCase(args[1])) {
            Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
            if (plot == null) {
                player.sendMessage("§cDu stehst in keinem Plot!");
                return true;
            }
            player.sendMessage("§6=== Quest Fortschritt (" + plot.getPlotId() + ") ===");
            questTracker.syncDistrictQuest(player, plot);
            de.streuland.plot.PlotData data = plotManager.getStorage(plotManager.getWorldForPlot(plot.getPlotId())).getPlotData(plot.getPlotId());
            for (QuestDefinition quest : questService.getActiveQuests()) {
                QuestProgress progress = questService.getOrCreateProgress(data, quest.getId());
                int value = Math.min(progress.getValue(), quest.getTarget());
                String bar = buildProgressBar(value, quest.getTarget(), 20);
                int pct = (int) Math.round((value * 100.0D) / Math.max(1, quest.getTarget()));
                String status = progress.isCompleted() ? "§aAbgeschlossen" : "§eIn Arbeit";
                player.sendMessage("§e" + quest.getTitle() + " §7" + bar + " §f" + pct + "% §8(" + value + "/" + quest.getTarget() + ") §7" + status);
            }
            return true;
        }

        player.sendMessage("§cVerwendung: /plot quest <list|progress>");
        return true;
    }

    private String buildProgressBar(int value, int target, int length) {
        int safeTarget = Math.max(1, target);
        double ratio = Math.min(1.0D, value / (double) safeTarget);
        int filled = (int) Math.round(ratio * length);
        StringBuilder builder = new StringBuilder("§8[");
        for (int i = 0; i < length; i++) {
            builder.append(i < filled ? "§a|" : "§7|");
        }
        builder.append("§8]");
        return builder.toString();
    }

    private boolean handleSnapshot(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cVerwendung: /plot snapshot <create|list|restore>");
            player.sendMessage("§7Aliase: /plot snapshots, /plot rollback <id>");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        boolean isAdmin = player.hasPermission(SnapshotManager.PERMISSION_ADMIN_RESTORE);
        if (!player.hasPermission(SnapshotManager.PERMISSION_SNAPSHOT) && !isAdmin) {
            player.sendMessage("§cKeine Berechtigung für Snapshots!");
            return true;
        }
        Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (plot == null) {
            player.sendMessage("§cDu stehst in keinem Plot!");
            return true;
        }
        boolean isOwner = plot.getOwner() != null && plot.getOwner().equals(player.getUniqueId());
        if (!isOwner && !isAdmin) {
            player.sendMessage("§cDu kannst diesen Plot nicht sichern oder zurücksetzen!");
            return true;
        }
        if ("create".equals(action)) {
            String note = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim() : null;
            if (note != null && note.isEmpty()) {
                note = null;
            }
            player.sendMessage("§eSnapshot wird erstellt...");
            snapshotManager.createSnapshot(plot, player.getUniqueId(), player.getName(), note).thenAccept(snapshot -> {
                player.sendMessage("§aSnapshot erstellt: §f" + snapshot.getId());
                if (snapshot.getMetadata() != null && snapshot.getMetadata().getNote() != null) {
                    player.sendMessage("§7Notiz: §f" + snapshot.getMetadata().getNote());
                }
            }).exceptionally(ex -> {
                player.sendMessage("§cSnapshot konnte nicht erstellt werden: " + ex.getMessage());
                return null;
            });
            return true;
        }
        if ("list".equals(action) || "snapshots".equals(action)) {
            List<SnapshotMeta> snapshots = snapshotManager.listSnapshots(plot.getPlotId());
            if (snapshots.isEmpty()) {
                player.sendMessage("§cKeine Snapshots vorhanden!");
                return true;
            }
            player.sendMessage("§6=== Snapshots für " + plot.getPlotId() + " ===");
            for (SnapshotMeta meta : snapshots) {
                String author = meta.getAuthorName() != null ? meta.getAuthorName() : (meta.getCreator() != null ? meta.getCreator().toString() : "SYSTEM");
                String note = meta.getNote() == null || meta.getNote().trim().isEmpty() ? "" : " §8- §7" + meta.getNote();
                player.sendMessage("§e" + meta.getId() + " §7@ " + SNAPSHOT_TIME_FORMAT.format(Instant.ofEpochMilli(meta.getCreatedAt())) + " §fvon " + author + note);
            }
            return true;
        }
        if ("restore".equals(action)) {
            if (args.length < 3) {
                player.sendMessage("§cVerwendung: /plot snapshot restore <id|confirm> [instant]");
                player.sendMessage("§7Aliase: /plot rollback <id>, /plot restore <id>");
                return true;
            }
            if ("confirm".equalsIgnoreCase(args[2])) {
                return handleRestoreConfirm(player, plot);
            }

            String snapshotId = args[2];
            boolean delayed = args.length < 4 || !args[3].equalsIgnoreCase("instant");
            long expiresAt = System.currentTimeMillis() + restoreConfirmTimeoutMs;
            pendingRestores.put(player.getUniqueId(), new RestoreConfirmation(plot.getPlotId(), snapshotId, delayed, expiresAt));
            plugin.getLogger().info("[AUDIT] restore-request player=" + player.getUniqueId() + " plot=" + plot.getPlotId() + " snapshot=" + snapshotId);
            player.sendMessage("§eRollback für Snapshot §f" + snapshotId + " §ewurde vorbereitet.");
            player.sendMessage("§eBitte bestätige mit §f/plot restore confirm§e.");
            player.sendMessage("§7Vor dem Rollback wird automatisch ein Restore-Point erzeugt (Timeout " + (restoreConfirmTimeoutMs / 1000L) + "s).");
            return true;
        }
        player.sendMessage("§cVerwendung: /plot snapshot <create|list|restore>");
        return true;
    }

    private boolean handleRestoreConfirm(Player player, Plot currentPlot) {
        RestoreConfirmation confirmation = pendingRestores.get(player.getUniqueId());
        if (confirmation == null) {
            player.sendMessage("§cKeine Rollback-Aktion ausstehend.");
            return true;
        }
        if (!confirmation.plotId.equals(currentPlot.getPlotId())) {
            player.sendMessage("§cDu musst dich im Ziel-Plot befinden, um den Rollback zu bestätigen.");
            return true;
        }
        if (System.currentTimeMillis() > confirmation.expiresAt) {
            pendingRestores.remove(player.getUniqueId());
            player.sendMessage("§cRollback-Bestätigung ist abgelaufen.");
            return true;
        }

        pendingRestores.remove(player.getUniqueId());
        String restorePointNote = "before rollback to " + confirmation.snapshotId;
        player.sendMessage("§eErstelle Restore-Point vor Rollback...");
        snapshotManager.createRestorePoint(currentPlot, player.getUniqueId(), player.getName(), restorePointNote)
                .thenCompose(restorePoint -> {
                    plugin.getLogger().info("[AUDIT] restore-point-created player=" + player.getUniqueId()
                            + " plot=" + confirmation.plotId
                            + " snapshot=" + restorePoint.getId()
                            + " forTarget=" + confirmation.snapshotId);
                    player.sendMessage("§7Restore-Point: §f" + restorePoint.getId());
                    return snapshotManager.restoreSnapshot(confirmation.plotId, confirmation.snapshotId, confirmation.delayed);
                })
                .thenRun(() -> {
                    plugin.getLogger().info("[AUDIT] restore-success player=" + player.getUniqueId() + " plot=" + confirmation.plotId + " snapshot=" + confirmation.snapshotId);
                    player.sendMessage("§aSnapshot wiederhergestellt!");
                })
                .exceptionally(ex -> {
                    plugin.getLogger().warning("[AUDIT] restore-failed player=" + player.getUniqueId()
                            + " plot=" + confirmation.plotId
                            + " snapshot=" + confirmation.snapshotId
                            + " reason=" + ex.getMessage());
                    player.sendMessage("§cSnapshot konnte nicht wiederhergestellt werden: " + ex.getMessage());
                    return null;
                });
        return true;
    }

    private String[] prependSnapshotRestore(String[] args) {
        String[] translated = new String[Math.max(3, args.length + 1)];
        translated[0] = "snapshot";
        translated[1] = "restore";
        if (args.length > 1) {
            System.arraycopy(args, 1, translated, 2, args.length - 1);
        }
        return translated;
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
