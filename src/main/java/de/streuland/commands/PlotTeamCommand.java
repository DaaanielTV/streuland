package de.streuland.commands;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.Role;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;

public class PlotTeamCommand {
    private final PlotManager plotManager;

    public PlotTeamCommand(PlotManager plotManager) {
        this.plotManager = plotManager;
    }

    public boolean execute(Player player, String[] args) {
        Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (plot == null || plot.getState() != Plot.PlotState.CLAIMED) {
            player.sendMessage("§cDu stehst auf keinem beanspruchten Plot.");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage("§cVerwendung: /plot team <invite|remove|list|promote|demote> ...");
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "invite":
                return invite(player, plot, args);
            case "remove":
                return remove(player, plot, args);
            case "list":
                return list(player, plot);
            case "promote":
                return promoteDemote(player, plot, args, true);
            case "demote":
                return promoteDemote(player, plot, args, false);
            default:
                player.sendMessage("§cUnbekannter Team-Befehl.");
                return true;
        }
    }

    private boolean invite(Player player, Plot plot, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§cVerwendung: /plot team invite <player> <role>");
            return true;
        }
        if (!canManage(plot, player.getUniqueId())) {
            player.sendMessage("§cNur Owner oder Co-Owner können Teammitglieder verwalten.");
            return true;
        }
        UUID target = resolvePlayerId(args[2]);
        if (target == null || target.equals(plot.getOwner())) {
            player.sendMessage("§cUngültiger Spieler.");
            return true;
        }
        String roleId = args[3];
        if (Role.fromId(roleId).orElse(null) == Role.OWNER) {
            player.sendMessage("§cOWNER kann nicht eingeladen werden.");
            return true;
        }
        if (plotManager.assignRole(plot.getPlotId(), player.getUniqueId(), target, roleId)) {
            player.sendMessage("§aSpieler eingeladen als " + roleId);
        } else {
            player.sendMessage("§cEinladung fehlgeschlagen.");
        }
        return true;
    }

    private boolean remove(Player player, Plot plot, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /plot team remove <player>");
            return true;
        }
        if (!canManage(plot, player.getUniqueId())) {
            player.sendMessage("§cKeine Berechtigung.");
            return true;
        }
        UUID target = resolvePlayerId(args[2]);
        if (target == null) {
            player.sendMessage("§cSpieler nicht gefunden.");
            return true;
        }
        if (plotManager.removeRole(plot.getPlotId(), player.getUniqueId(), target)) {
            player.sendMessage("§aTeammitglied entfernt.");
        } else {
            player.sendMessage("§cEntfernen fehlgeschlagen.");
        }
        return true;
    }

    private boolean list(Player player, Plot plot) {
        player.sendMessage("§6=== Plot Team ===");
        Map<UUID, Role> roles = plot.getRoles();
        List<Map.Entry<UUID, Role>> entries = new ArrayList<>(roles.entrySet());
        entries.sort(Comparator.comparing(e -> e.getValue().ordinal()));
        for (Map.Entry<UUID, Role> entry : entries) {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(entry.getKey());
            String name = offline.getName() != null ? offline.getName() : entry.getKey().toString();
            player.sendMessage("§e" + name + "§f: " + entry.getValue().name());
        }
        return true;
    }

    private boolean promoteDemote(Player player, Plot plot, String[] args, boolean promote) {
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /plot team " + (promote ? "promote" : "demote") + " <player>");
            return true;
        }
        if (!canManage(plot, player.getUniqueId())) {
            player.sendMessage("§cKeine Berechtigung.");
            return true;
        }
        UUID target = resolvePlayerId(args[2]);
        if (target == null) {
            player.sendMessage("§cSpieler nicht gefunden.");
            return true;
        }
        Role current = plot.getRole(target);
        Role next = promote ? nextHigher(current) : nextLower(current);
        if (next == current || next == Role.OWNER) {
            player.sendMessage("§cRolle kann nicht weiter " + (promote ? "erhöht" : "gesenkt") + " werden.");
            return true;
        }
        if (plotManager.assignRole(plot.getPlotId(), player.getUniqueId(), target, next)) {
            player.sendMessage("§aNeue Rolle: " + next.name());
        } else {
            player.sendMessage("§cÄnderung fehlgeschlagen.");
        }
        return true;
    }

    private Role nextHigher(Role current) {
        switch (current) {
            case VISITOR:
                return Role.BUILDER;
            case BUILDER:
                return Role.MEMBER;
            case MEMBER:
                return Role.CO_OWNER;
            default:
                return current;
        }
    }

    private Role nextLower(Role current) {
        switch (current) {
            case CO_OWNER:
                return Role.MEMBER;
            case MEMBER:
                return Role.BUILDER;
            case BUILDER:
                return Role.VISITOR;
            default:
                return current;
        }
    }

    private boolean canManage(Plot plot, UUID actor) {
        Role role = plot.getRole(actor);
        return role == Role.OWNER || role == Role.CO_OWNER;
    }

    private UUID resolvePlayerId(String name) {
        OfflinePlayer offline = Bukkit.getOfflinePlayer(name);
        return offline == null ? null : offline.getUniqueId();
    }
}
