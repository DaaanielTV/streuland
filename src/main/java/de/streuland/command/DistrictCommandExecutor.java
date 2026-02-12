package de.streuland.command;

import de.streuland.district.District;
import de.streuland.district.DistrictLevel;
import de.streuland.district.DistrictManager;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles /district commands.
 */
public class DistrictCommandExecutor implements CommandExecutor {
    private final PlotManager plotManager;
    private final DistrictManager districtManager;

    public DistrictCommandExecutor(PlotManager plotManager, DistrictManager districtManager) {
        this.plotManager = plotManager;
        this.districtManager = districtManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cNur Spieler können District-Befehle verwenden!");
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "info":
                return handleInfo(player);
            case "progress":
                return handleProgress(player);
            case "list":
                return handleList(player);
            default:
                showHelp(player);
                return true;
        }
    }

    private boolean handleInfo(Player player) {
        Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        District district = districtManager.getDistrictForPlot(plot);
        if (district == null) {
            player.sendMessage("§cDu stehst in keinem Stadtteil!");
            return true;
        }

        player.sendMessage("§6=== Stadtteil ===");
        player.sendMessage("§eName: §f" + district.getName());
        player.sendMessage("§eID: §f" + district.getId());
        player.sendMessage("§eLevel: §f" + district.getLevel().name());
        player.sendMessage("§ePlots: §f" + district.getPlotIds().size());
        return true;
    }

    private boolean handleProgress(Player player) {
        Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        District district = districtManager.getDistrictForPlot(plot);
        if (district == null) {
            player.sendMessage("§cDu stehst in keinem Stadtteil!");
            return true;
        }

        DistrictLevel next = district.getLevel().next();
        player.sendMessage("§6=== Fortschritt ===");
        player.sendMessage("§eBlöcke: §f" + district.getProgress().getBuiltBlocks());
        player.sendMessage("§eAktive Spieler: §f" + district.getProgress().getActivePlayers());
        player.sendMessage("§eZiele: §f" + district.getProgress().getCompletedGoals().size());
        if (next != district.getLevel()) {
            player.sendMessage("§eNächstes Level: §f" + next.name());
            player.sendMessage("§7Benötigt: " + next.getRequiredBuiltBlocks() + " Blöcke, "
                    + next.getRequiredActivePlayers() + " Spieler, "
                    + next.getRequiredGoals() + " Ziele");
        } else {
            player.sendMessage("§aMaximales Level erreicht.");
        }
        return true;
    }

    private boolean handleList(Player player) {
        player.sendMessage("§6=== Stadtteile ===");
        for (District district : districtManager.getAllDistricts()) {
            player.sendMessage("§e" + district.getName() + "§f (" + district.getId() + ") - " + district.getLevel().name());
        }
        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage("§6=== Stadtteil Befehle ===");
        player.sendMessage("§e/district info§f - Info zum aktuellen Stadtteil");
        player.sendMessage("§e/district progress§f - Fortschritt anzeigen");
        player.sendMessage("§e/district list§f - Liste aller Stadtteile");
    }
}
