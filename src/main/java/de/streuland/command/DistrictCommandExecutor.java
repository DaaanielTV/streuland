package de.streuland.command;

import de.streuland.district.District;
import de.streuland.district.DistrictLevel;
import de.streuland.district.DistrictManager;
import de.streuland.i18n.MessageProvider;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;

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
            case "info": return handleInfo(player);
            case "progress": return handleProgress(player);
            case "list": return handleList(player);
            case "create": return handleCreate(player, args);
            case "add": return handleAdd(player, args);
            case "remove": return handleRemove(player, args);
            case "rename": return handleRename(player, args);
            case "setrule": return handleSetRule(player, args);
            case "bank": return handleBank(player, args);
            case "setspawn": return handleSetSpawn(player, args);
            default:
                showHelp(player);
                return true;
        }
    }

    private boolean handleInfo(Player player) {
        District district = districtAtPlayer(player);
        if (district == null) {
            player.sendMessage(messageProvider.t(player, "district.error.not_in_district"));
            return true;
        }
        player.sendMessage(messageProvider.t(player, "district.info.header"));
        player.sendMessage(messageProvider.t(player, "district.info.name", district.getName()));
        player.sendMessage(messageProvider.t(player, "district.info.id", district.getId()));
        player.sendMessage(messageProvider.t(player, "district.info.level", district.getLevel().name()));
        player.sendMessage(messageProvider.t(player, "district.info.plots", district.getPlotIds().size()));
        player.sendMessage(messageProvider.t(player, "district.info.bank", district.isSharedBankEnabled() ? district.getSharedBankBalance() : "disabled"));
        player.sendMessage(messageProvider.t(player, "district.info.spawn", district.hasSpawn() ? (district.getSpawnWorld() + " @ " + district.getSpawnX() + ", " + district.getSpawnY() + ", " + district.getSpawnZ()) : "unset"));
        return true;
    }

    private boolean handleProgress(Player player) {
        District district = districtAtPlayer(player);
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
            player.sendMessage(messageProvider.t(player, "district.list.entry", district.getName(), district.getId(), district.getLevel().name(), district.getPlotIds().size()));
        }
        return true;
    }

    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageProvider.t(player, "district.help.create"));
            return true;
        }
        Plot plot = currentPlot(player);
        if (plot == null) {
            player.sendMessage(messageProvider.t(player, "district.error.no_plot"));
            return true;
        }
        District district = districtManager.createDistrict(joinArgs(args, 1), Collections.singletonList(plot));
        player.sendMessage(messageProvider.t(player, "district.manage.created", district.getName(), district.getId()));
        return true;
    }

    private boolean handleAdd(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageProvider.t(player, "district.help.add"));
            return true;
        }
        Plot plot = currentPlot(player);
        if (plot == null) {
            player.sendMessage(messageProvider.t(player, "district.error.no_plot"));
            return true;
        }
        player.sendMessage(districtManager.addPlotToDistrict(args[1], plot)
                ? messageProvider.t(player, "district.manage.added", plot.getPlotId(), args[1])
                : messageProvider.t(player, "district.error.unknown_district", args[1]));
        return true;
    }

    private boolean handleRemove(Player player, String[] args) {
        Plot plot = currentPlot(player);
        District district = districtAtPlayer(player);
        if (plot == null || district == null) {
            player.sendMessage(messageProvider.t(player, "district.error.not_in_district"));
            return true;
        }
        player.sendMessage(districtManager.removePlotFromDistrict(district.getId(), plot.getPlotId())
                ? messageProvider.t(player, "district.manage.removed", plot.getPlotId(), district.getId())
                : messageProvider.t(player, "district.error.not_in_district"));
        return true;
    }

    private boolean handleRename(Player player, String[] args) {
        District district = districtAtPlayer(player);
        if (district == null || args.length < 2) {
            player.sendMessage(messageProvider.t(player, "district.help.rename"));
            return true;
        }
        districtManager.renameDistrict(district.getId(), joinArgs(args, 1));
        player.sendMessage(messageProvider.t(player, "district.manage.renamed", district.getId(), district.getName()));
        return true;
    }

    private boolean handleSetRule(Player player, String[] args) {
        District district = districtAtPlayer(player);
        if (district == null || args.length < 3) {
            player.sendMessage(messageProvider.t(player, "district.help.setrule"));
            return true;
        }
        boolean value = Boolean.parseBoolean(args[2]);
        districtManager.setSharedRule(district.getId(), args[1], value);
        player.sendMessage(messageProvider.t(player, "district.manage.rule", args[1], value));
        return true;
    }

    private boolean handleBank(Player player, String[] args) {
        District district = districtAtPlayer(player);
        if (district == null || args.length < 3) {
            player.sendMessage(messageProvider.t(player, "district.help.bank"));
            return true;
        }
        boolean enabled = Boolean.parseBoolean(args[1]);
        double balance = Double.parseDouble(args[2]);
        districtManager.configureSharedBank(district.getId(), enabled, balance);
        player.sendMessage(messageProvider.t(player, "district.manage.bank", enabled, balance));
        return true;
    }

    private boolean handleSetSpawn(Player player, String[] args) {
        District district = districtAtPlayer(player);
        Plot plot = currentPlot(player);
        if (district == null || plot == null) {
            player.sendMessage(messageProvider.t(player, "district.error.not_in_district"));
            return true;
        }
        districtManager.setDistrictSpawn(district.getId(), plot);
        player.sendMessage(messageProvider.t(player, "district.manage.spawn", district.getId()));
        return true;
    }

    private Plot currentPlot(Player player) {
        return plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
    }

    private District districtAtPlayer(Player player) { return districtManager.getDistrictForPlot(currentPlot(player)); }

    private String joinArgs(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) builder.append(' ');
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private void showHelp(Player player) {
        player.sendMessage(messageProvider.t(player, "district.help.header"));
        player.sendMessage(messageProvider.t(player, "district.help.info"));
        player.sendMessage(messageProvider.t(player, "district.help.progress"));
        player.sendMessage(messageProvider.t(player, "district.help.list"));
        player.sendMessage(messageProvider.t(player, "district.help.create"));
        player.sendMessage(messageProvider.t(player, "district.help.add"));
        player.sendMessage(messageProvider.t(player, "district.help.remove"));
        player.sendMessage(messageProvider.t(player, "district.help.rename"));
        player.sendMessage(messageProvider.t(player, "district.help.setrule"));
        player.sendMessage(messageProvider.t(player, "district.help.bank"));
        player.sendMessage(messageProvider.t(player, "district.help.setspawn"));
    }
}
