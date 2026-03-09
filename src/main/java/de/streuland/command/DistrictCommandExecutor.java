package de.streuland.command;

import de.streuland.district.District;
import de.streuland.district.DistrictLevel;
import de.streuland.district.DistrictManager;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.i18n.MessageProvider;
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
    private final MessageProvider messageProvider;

    public DistrictCommandExecutor(PlotManager plotManager, DistrictManager districtManager, MessageProvider messageProvider) {
        this.plotManager = plotManager;
        this.districtManager = districtManager;
        this.messageProvider = messageProvider;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messageProvider.t((java.util.UUID) null, "district.error.only_players"));
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
            player.sendMessage(messageProvider.t(player, "district.error.not_in_district"));
            return true;
        }

        player.sendMessage(messageProvider.t(player, "district.info.header"));
        player.sendMessage(messageProvider.t(player, "district.info.name", district.getName()));
        player.sendMessage(messageProvider.t(player, "district.info.id", district.getId()));
        player.sendMessage(messageProvider.t(player, "district.info.level", district.getLevel().name()));
        player.sendMessage(messageProvider.t(player, "district.info.plots", district.getPlotIds().size()));
        return true;
    }

    private boolean handleProgress(Player player) {
        Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        District district = districtManager.getDistrictForPlot(plot);
        if (district == null) {
            player.sendMessage(messageProvider.t(player, "district.error.not_in_district"));
            return true;
        }

        DistrictLevel next = district.getLevel().next();
        player.sendMessage(messageProvider.t(player, "district.progress.header"));
        player.sendMessage(messageProvider.t(player, "district.progress.blocks", district.getProgress().getBuiltBlocks()));
        player.sendMessage(messageProvider.t(player, "district.progress.active_players", district.getProgress().getActivePlayers()));
        player.sendMessage(messageProvider.t(player, "district.progress.goals", district.getProgress().getCompletedGoals().size()));
        if (next != district.getLevel()) {
            player.sendMessage(messageProvider.t(player, "district.progress.next_level", next.name()));
            player.sendMessage(messageProvider.t(player, "district.progress.required", next.getRequiredBuiltBlocks(), next.getRequiredActivePlayers(), next.getRequiredGoals()));
        } else {
            player.sendMessage(messageProvider.t(player, "district.progress.max_level"));
        }
        return true;
    }

    private boolean handleList(Player player) {
        player.sendMessage(messageProvider.t(player, "district.list.header"));
        for (District district : districtManager.getAllDistricts()) {
            player.sendMessage(messageProvider.t(player, "district.list.entry", district.getName(), district.getId(), district.getLevel().name()));
        }
        return true;
    }

    private void showHelp(Player player) {
        player.sendMessage(messageProvider.t(player, "district.help.header"));
        player.sendMessage(messageProvider.t(player, "district.help.info"));
        player.sendMessage(messageProvider.t(player, "district.help.progress"));
        player.sendMessage(messageProvider.t(player, "district.help.list"));
    }
}
