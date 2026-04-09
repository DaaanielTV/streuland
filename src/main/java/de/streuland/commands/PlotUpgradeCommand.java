package de.streuland.commands;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.upgrade.PlotProgressionService;
import de.streuland.plot.upgrade.PlotUpgradeService;
import de.streuland.plot.upgrade.PlotUpgradeView;
import org.bukkit.entity.Player;

import java.util.List;

public class PlotUpgradeCommand {
    private final PlotManager plotManager;
    private final PlotUpgradeService plotUpgradeService;
    private final PlotProgressionService progressionService;

    public PlotUpgradeCommand(PlotManager plotManager, PlotUpgradeService plotUpgradeService) {
        this.plotManager = plotManager;
        this.plotUpgradeService = plotUpgradeService;
        this.progressionService = new PlotProgressionService(plotUpgradeService);
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

        if (args.length == 1 || "upgrades".equalsIgnoreCase(args[0])) {
            return listUpgrades(player, plot.getPlotId());
        }

        if (args.length >= 2 && "buy".equalsIgnoreCase(args[1])) {
            if (args.length < 3) {
                player.sendMessage("§cVerwendung: /plot upgrade buy <id>");
                return true;
            }
            return buyUpgrade(player, plot.getPlotId(), args[2]);
        }

        return listUpgrades(player, plot.getPlotId());
    }

    private boolean listUpgrades(Player player, String plotId) {
        List<PlotUpgradeView> upgrades = progressionService.listUpgrades(plotId, player.getUniqueId());
        player.sendMessage("§6=== Plot Upgrades ===");
        player.sendMessage("§7Current progression level: §a" + progressionService.getOverallLevel(plotId));
        for (PlotUpgradeView view : upgrades) {
            String status = view.isAvailable() ? "§aKaufbar" : "§7" + view.getReason();
            player.sendMessage("§e" + view.getDefinition().getId() + " §8- §f" + view.getDefinition().getDisplayName() + " §8(" + view.getDefinition().getCost().getVaultCost() + "$) §7" + status);
        }
        return true;
    }

    private boolean buyUpgrade(Player player, String plotId, String upgradeId) {
        java.util.Optional<String> failure = progressionService.buyUpgrade(plotId, player.getUniqueId(), upgradeId);
        if (failure.isPresent()) {
            player.sendMessage("§cUpgrade-Kauf fehlgeschlagen: " + failure.get());
            return true;
        }
        player.sendMessage("§aUpgrade erfolgreich gekauft: §f" + upgradeId);
        player.sendMessage("§7Neues Gesamtlevel: §a" + progressionService.getOverallLevel(plotId));
        return true;
    }
}
