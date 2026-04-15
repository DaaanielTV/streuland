package de.streuland.commands;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.upgrade.PlotProgressionService;
import de.streuland.plot.upgrade.PlotProgressionState;
import de.streuland.plot.upgrade.PlotUpgradeService;
import de.streuland.plot.upgrade.PlotUpgradeView;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class PlotUpgradeCommand {
    private final PlotManager plotManager;
    private final PlotProgressionService progressionService;

    public PlotUpgradeCommand(PlotManager plotManager, PlotUpgradeService plotUpgradeService) {
        this.plotManager = plotManager;
        this.progressionService = new PlotProgressionService(plotUpgradeService);
    }

    public PlotProgressionService getProgressionService() {
        return progressionService;
    }

    public boolean handle(Player player, String[] args) {
        if (!player.hasPermission("streuland.plot.upgrade")) {
            player.sendMessage("§cKeine Berechtigung für Plot-Upgrades.");
            return true;
        }

        Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (plot == null || plot.getOwner() == null) {
            player.sendMessage("§cDu musst auf einem beanspruchten Plot stehen.");
            return true;
        }

        String action = args.length <= 1 ? "list" : args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "buy":
                if (args.length < 3) {
                    player.sendMessage("§cNutzung: /plot upgrade buy <upgradeId>");
                    return true;
                }
                return buyUpgrade(player, plot.getPlotId(), args[2]);
            case "prestige":
                return prestige(player, plot.getPlotId());
            case "info":
            case "level":
                sendPlotProgressInfo(player, plot.getPlotId());
                return true;
            case "list":
            case "upgrades":
            case "upgrade":
            default:
                return listUpgrades(player, plot.getPlotId());
        }
    }

    public void sendPlotProgressInfo(Player player, String plotId) {
        PlotProgressionState state = progressionService.getState(plotId);
        player.sendMessage("§6=== Plot Progression ===");
        player.sendMessage("§eLevel: §a" + state.getOverallLevel() + " §8| §ePrestige: §b" + state.getPrestigeLevel());
        player.sendMessage("§eXP: §f" + state.getProgressionPoints() + " §8| §eSpent: §f" + String.format(Locale.US, "%.2f", state.getLifetimeCurrencySpent()));

        if (!state.getAwardedRewardLevels().isEmpty()) {
            player.sendMessage("§eReward levels unlocked: §a" + state.getAwardedRewardLevels());
        }

        if (!state.getUpgradeLevels().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Integer> entry : state.getUpgradeLevels().entrySet()) {
                if (sb.length() > 0) {
                    sb.append("§7, ");
                }
                sb.append("§f").append(entry.getKey()).append(" §8(L").append(entry.getValue()).append(")");
            }
            player.sendMessage("§ePurchased upgrades: " + sb);
        } else {
            player.sendMessage("§ePurchased upgrades: §7none");
        }
    }

    private boolean listUpgrades(Player player, String plotId) {
        List<PlotUpgradeView> upgrades = progressionService.listUpgrades(plotId, player.getUniqueId());
        sendPlotProgressInfo(player, plotId);
        player.sendMessage("§6=== Available Upgrades ===");
        for (PlotUpgradeView view : upgrades) {
            String status = view.isAvailable() ? "§aKaufbar" : "§7" + humanReason(view.getReason());
            player.sendMessage("§e" + view.getDefinition().getId() + " §8- §f" + view.getDefinition().getDisplayName()
                    + " §8(" + view.getDefinition().getCost().getVaultCost() + "$) §7" + status);
        }
        return true;
    }

    private boolean buyUpgrade(Player player, String plotId, String upgradeId) {
        Optional<String> failure = progressionService.buyUpgrade(plotId, player.getUniqueId(), upgradeId);
        if (failure.isPresent()) {
            player.sendMessage("§cUpgrade-Kauf fehlgeschlagen: " + humanReason(failure.get()));
            return true;
        }
        player.sendMessage("§aUpgrade erfolgreich gekauft: §f" + upgradeId);
        sendPlotProgressInfo(player, plotId);
        return true;
    }

    private boolean prestige(Player player, String plotId) {
        Optional<String> prestigeFailure = progressionService.prestige(plotId, player.getUniqueId());
        if (prestigeFailure.isPresent()) {
            player.sendMessage("§cPrestige fehlgeschlagen: " + humanReason(prestigeFailure.get()));
            return true;
        }
        player.sendMessage("§aPrestige durchgeführt!");
        sendPlotProgressInfo(player, plotId);
        return true;
    }

    private String humanReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "nicht verfügbar";
        }
        switch (reason) {
            case "not-owner": return "Du bist nicht der Eigentümer";
            case "already-owned": return "Bereits freigeschaltet";
            case "plot-level-too-low": return "Plot-Level zu niedrig";
            case "prestige-level-too-low": return "Prestige-Level zu niedrig";
            case "economy-unavailable": return "Economy nicht verfügbar";
            case "insufficient-funds": return "Nicht genug Guthaben";
            default:
                if (reason.startsWith("missing-requirement:")) {
                    return "Voraussetzung fehlt: " + reason.substring("missing-requirement:".length());
                }
                return reason;
        }
    }
}
