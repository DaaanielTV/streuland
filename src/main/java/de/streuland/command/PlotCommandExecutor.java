package de.streuland.command;

import de.streuland.admin.AdminPlotService;
import de.streuland.analytics.PlotAnalyticsService;
import de.streuland.analytics.PlayerEditStats;
import de.streuland.path.PathGenerator;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.biome.BiomeBonusService;
import de.streuland.plot.biome.BiomeRuleSet;
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
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.*;

/**
 * Handles /plot command and subcommands.
 */
public class PlotCommandExecutor implements CommandExecutor {
    private static class DeleteConfirmation {
        private final String plotId;
        private final long expiresAt;

        private DeleteConfirmation(String plotId, long expiresAt) {
            this.plotId = plotId;
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
    private final PlotAnalyticsService plotAnalyticsService;
    private final Map<UUID, DeleteConfirmation> pendingDeletes;
    private final long deleteConfirmTimeoutMs;
    
    public PlotCommandExecutor(JavaPlugin plugin, PlotManager plotManager, PathGenerator pathGenerator,
                               SnapshotManager snapshotManager, RuleEngine ruleEngine, PlotSkinService plotSkinService,
                               BiomeBonusService biomeBonusService, NeighborhoodService neighborhoodService,
                               QuestService questService, QuestTracker questTracker, PlotMarketService plotMarketService,
                               AdminPlotService adminPlotService, PlotAnalyticsService plotAnalyticsService) {
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
        this.adminPlotService = adminPlotService;
        this.plotAnalyticsService = plotAnalyticsService;
        this.pendingDeletes = new HashMap<>();
        this.deleteConfirmTimeoutMs = plugin.getConfig().getLong("plot.delete-confirm-timeout-seconds", 30L) * 1000L;
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
            case "unclaim":
                return handleUnclaim(player, args);
            case "delete":
                return handleDelete(player, args);
            case "confirm":
                return handleConfirmDelete(player);
            case "cancel":
                return handleCancelDelete(player);
            case "generate":
                return handleGenerate(player, args);
            case "stats":
                return handleStats(player);
            case "style":
                return handleStyle(player, args);
            case "biome":
                return handleBiomeBonus(player, args);
            case "neighbor":
                return handleNeighbor(player, args);
            case "quest":
                return handleQuest(player, args);
            case "market":
                return handleMarket(player, args);
            case "inspect":
                return adminPlotService.handleInspect(player, args);
            case "admin":
                return adminPlotService.handleAdmin(player, args);
            default:
                player.sendMessage("§cUnbekannter Befehl. Nutze /plot help");
                return true;
        }
    }

    private boolean handleCreate(Player player) {
        List<Plot> playerPlots = plotManager.getStorage().getPlayerPlots(player.getUniqueId());
        if (playerPlots.size() >= plotManager.getMaxPlotsPerPlayer()) {
            player.sendMessage("§cDu kannst maximal " + plotManager.getMaxPlotsPerPlayer() + " Plots besitzen!");
            return true;
        }

        player.sendMessage("§eSuche nach einem Plot-Ort...");
        plotManager.createPlotAsync(player.getUniqueId()).thenAccept(plot -> {
            if (plot != null) {
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

    private boolean handleClaim(Player player) {
        List<Plot> playerPlots = plotManager.getStorage().getPlayerPlots(player.getUniqueId());
        if (playerPlots.size() >= plotManager.getMaxPlotsPerPlayer()) {
            player.sendMessage("§cDu kannst maximal " + plotManager.getMaxPlotsPerPlayer() + " Plots besitzen!");
            return true;
        }

        Plot claimedPlot = plotManager.claimPlotAt(player.getUniqueId(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
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
        player.sendMessage("§eNachbarschaft: §f" + neighborhoodService.getAnalyticsSummary(plot.getPlotId()));
        return true;
    }

    private boolean handleTrust(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cVerwendung: /plot trust <Spieler>");
            return true;
        }

        Plot plot = plotManager.getPlotAt(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (plot == null || plot.getOwner() == null || !plot.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§cDu besitzt diesen Plot nicht!");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target == null || target.getUniqueId() == null) {
            player.sendMessage("§cSpieler nicht gefunden!");
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cDu bist bereits Besitzer deines Plots.");
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
        if (plot == null || plot.getOwner() == null || !plot.getOwner().equals(player.getUniqueId())) {
            player.sendMessage("§cDu besitzt diesen Plot nicht!");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (target == null || target.getUniqueId() == null) {
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

            plotManager.generateUnclaimedPlots(gridSize, spacing);
            player.sendMessage("§aGenerierung gestartet/abgeschlossen. Prüfe Konsole für Details.");
            return true;
        } catch (NumberFormatException e) {
            player.sendMessage("§cBitte nur Zahlen verwenden.");
            return true;
        }
    }

    private boolean handleStats(Player player) {
        Collection<Plot> allPlots = plotManager.getAllPlots();
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
        player.sendMessage("§eGrid-Zellen: §f" + plotManager.getSpatialGrid().getCellCount());

        Plot currentPlot = plotManager.getPlotAt(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
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
        Plot plot = plotManager.getPlotAt(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
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
        if (args.length < 2 || !"bonus".equalsIgnoreCase(args[1])) {
            player.sendMessage("§cVerwendung: /plot biome bonus");
            return true;
        }
        Plot plot = plotManager.getPlotAt(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
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

    private Plot resolvePlotFromArgsOrLocation(Player player, String[] args, int index) {
        if (args.length > index) {
            return plotManager.getStorage().getPlot(args[index]);
        }
        return plotManager.getPlotAt(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
    }

    private void teleportToPlot(Player player, Plot plot, String message) {
        player.teleport(plotManager.getWorld().getBlockAt(plot.getCenterX(), plot.getSpawnY(), plot.getCenterZ()).getLocation());
        player.sendMessage(message);
    }

    private void showHelp(Player player) {
        player.sendMessage("§6=== Streuland Plot Befehle ===");
        player.sendMessage("§e/plot create§f - Generiere und beanspruche einen neuen Plot");
        player.sendMessage("§e/plot claim§f - Beanspruche einen ungeclaimten Plot unter deinen Füßen");
        player.sendMessage("§e/plot info§f - Zeige Informationen zum aktuellen Plot");
        player.sendMessage("§e/plot trust <Spieler>§f - Vertraue einem Spieler");
        player.sendMessage("§e/plot untrust <Spieler>§f - Entferne Vertrauen von einem Spieler");
        player.sendMessage("§e/plot home [Nummer]§f - Teleportiere dich zu einem eigenen Plot");
        player.sendMessage("§e/plot list§f - Liste deine Plots auf");
        player.sendMessage("§e/plot snapshot <create|list|restore>§f - Plot Snapshot Befehle");
        player.sendMessage("§e/plot rules reload§f - Regeln neu laden");
        player.sendMessage("§e/plot style set <theme>§f - Setze das Plot-Theme");
        player.sendMessage("§e/plot biome bonus§f - Zeigt aktive Biom-Boni");
        player.sendMessage("§e/plot neighbor <add|list|map>§f - Nachbarschaftshandel verwalten");
        player.sendMessage("§e/plot quest <list|progress>§f - Quest-Übersicht und Fortschritt");
        player.sendMessage("§e/plot market <list|sell|buy|history>§f - Spieler-Marktplatz für Plots");
        player.sendMessage("§e/plot inspect <x> <z>§f - Zeige Block-Änderungslog für Koordinaten");
        player.sendMessage("§e/plot admin <rollback|log> ...§f - Admin-Tools für Logs und Rollbacks");
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
            Plot plot = plotManager.getPlotAt(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
            if (plot == null) {
                player.sendMessage("§cDu stehst in keinem Plot!");
                return true;
            }
            player.sendMessage("§6=== Quest Fortschritt (" + plot.getPlotId() + ") ===");
            questTracker.syncDistrictQuest(player, plot);
            de.streuland.plot.PlotData data = plotManager.getStorage().getPlotData(plot.getPlotId());
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
