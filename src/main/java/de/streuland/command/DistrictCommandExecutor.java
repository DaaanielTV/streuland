package de.streuland.command;

import de.streuland.district.District;
import de.streuland.district.DistrictManager;
import de.streuland.district.DistrictMember;
import de.streuland.district.DistrictRole;
import de.streuland.i18n.MessageProvider;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Handles /district commands.
 */
public class DistrictCommandExecutor implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "create", "invite", "join", "leave", "info", "rename", "disband", "claimplot", "unclaimplot", "override"
    );

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
            sender.sendMessage(messageProvider.t((UUID) null, "district.error.only_players"));
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            showHelp(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create": return handleCreate(player, args);
            case "invite": return handleInvite(player, args);
            case "join": return handleJoin(player, args);
            case "leave": return handleLeave(player);
            case "info": return handleInfo(player);
            case "rename": return handleRename(player, args);
            case "disband": return handleDisband(player);
            case "claimplot": return handleClaimPlot(player, args);
            case "unclaimplot": return handleUnclaimPlot(player, args);
            case "override": return handleOverride(player, args);
            default:
                showHelp(player);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return prefixMatches(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && ("invite".equalsIgnoreCase(args[0]))) {
            List<String> names = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                names.add(online.getName());
            }
            return prefixMatches(names, args[1]);
        }
        if (args.length == 2 && ("override".equalsIgnoreCase(args[0]))) {
            return prefixMatches(Arrays.asList("true", "false"), args[1]);
        }
        return Collections.emptyList();
    }

    private boolean handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageProvider.t(player, "district.help.create"));
            return true;
        }
        if (districtManager.getDistrictForPlayer(player.getUniqueId()) != null) {
            player.sendMessage("§cYou are already in a district.");
            return true;
        }
        Plot plot = currentPlot(player);
        if (plot == null) {
            player.sendMessage(messageProvider.t(player, "district.error.no_plot"));
            return true;
        }
        District district = districtManager.createDistrict(joinArgs(args, 1), Collections.singletonList(plot));
        district.upsertMember(player.getUniqueId(), DistrictRole.OWNER);
        districtManager.getStorage().saveDistrict(district);
        player.sendMessage(messageProvider.t(player, "district.manage.created", district.getName(), district.getId()));
        return true;
    }

    private boolean handleInvite(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /district invite <player>");
            return true;
        }
        String inviteCode = districtManager.createInvite(player.getUniqueId());
        if (inviteCode == null) {
            player.sendMessage("§cYou must be a district manager to invite players.");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        player.sendMessage("§aInvite created for " + target.getName() + ": §e" + inviteCode);
        return true;
    }

    private boolean handleJoin(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /district join <inviteCode>");
            return true;
        }
        boolean joined = districtManager.joinByInviteCode(player.getUniqueId(), args[1]);
        player.sendMessage(joined ? "§aYou joined a district." : "§cInvalid invite code or already in a district.");
        return true;
    }

    private boolean handleLeave(Player player) {
        boolean left = districtManager.leaveDistrict(player.getUniqueId());
        player.sendMessage(left ? "§aYou left your district." : "§cYou cannot leave (owners must disband or transfer ownership).");
        return true;
    }

    private boolean handleInfo(Player player) {
        District district = districtManager.getDistrictForPlayer(player.getUniqueId());
        if (district == null) {
            player.sendMessage(messageProvider.t(player, "district.error.not_in_district"));
            return true;
        }
        player.sendMessage(messageProvider.t(player, "district.info.header"));
        player.sendMessage(messageProvider.t(player, "district.info.name", district.getName()));
        player.sendMessage(messageProvider.t(player, "district.info.id", district.getId()));
        player.sendMessage(messageProvider.t(player, "district.info.level", district.getLevel().name()));
        player.sendMessage(messageProvider.t(player, "district.info.plots", district.getPlotIds().size()));
        player.sendMessage("§eRole override: §f" + district.isRoleOverrideEnabled());
        player.sendMessage("§eMembers: §f" + district.getMembers().size());
        for (Map.Entry<UUID, DistrictMember> entry : district.getMembers().entrySet()) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.getKey());
            String name = offline != null && offline.getName() != null ? offline.getName() : entry.getKey().toString();
            player.sendMessage("§7- " + name + " §f(" + entry.getValue().getRole().name() + ")");
        }
        return true;
    }

    private boolean handleRename(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messageProvider.t(player, "district.help.rename"));
            return true;
        }
        boolean renamed = districtManager.renameDistrict(player.getUniqueId(), joinArgs(args, 1));
        player.sendMessage(renamed ? "§aDistrict renamed." : "§cYou do not have permission to rename this district.");
        return true;
    }

    private boolean handleDisband(Player player) {
        boolean disbanded = districtManager.disbandDistrict(player.getUniqueId());
        player.sendMessage(disbanded ? "§aDistrict disbanded." : "§cOnly the district owner can disband.");
        return true;
    }

    private boolean handleClaimPlot(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /district claimplot <plotId>");
            return true;
        }
        boolean claimed = districtManager.claimPlotToActorDistrict(player.getUniqueId(), args[1]);
        player.sendMessage(claimed ? "§aPlot added to your district." : "§cFailed to claim plot for district.");
        return true;
    }

    private boolean handleUnclaimPlot(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /district unclaimplot <plotId>");
            return true;
        }
        boolean unclaimed = districtManager.unclaimPlotFromActorDistrict(player.getUniqueId(), args[1]);
        player.sendMessage(unclaimed ? "§aPlot removed from your district." : "§cFailed to unclaim plot from district.");
        return true;
    }

    private boolean handleOverride(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /district override <true|false>");
            return true;
        }
        boolean enabled = Boolean.parseBoolean(args[1]);
        boolean changed = districtManager.setRoleOverrideEnabled(player.getUniqueId(), enabled);
        player.sendMessage(changed ? "§aDistrict role override set to " + enabled + "." : "§cYou do not have permission.");
        return true;
    }

    private Plot currentPlot(Player player) {
        return plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
    }

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
        player.sendMessage("§e/district create <name>");
        player.sendMessage("§e/district invite <player>");
        player.sendMessage("§e/district join <inviteCode>");
        player.sendMessage("§e/district leave");
        player.sendMessage("§e/district info");
        player.sendMessage("§e/district rename <name>");
        player.sendMessage("§e/district disband");
        player.sendMessage("§e/district claimplot <plotId>");
        player.sendMessage("§e/district unclaimplot <plotId>");
        player.sendMessage("§e/district override <true|false>");
    }

    private List<String> prefixMatches(List<String> candidates, String token) {
        String lower = token == null ? "" : token.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(candidate);
            }
        }
        return matches;
    }
}
