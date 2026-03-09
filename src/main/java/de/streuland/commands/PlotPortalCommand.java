package de.streuland.commands;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.warp.PortalManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class PlotPortalCommand {
    private final PlotManager plotManager;
    private final PortalManager portalManager;

    public PlotPortalCommand(PlotManager plotManager, PortalManager portalManager) {
        this.plotManager = plotManager;
        this.portalManager = portalManager;
    }

    public boolean handle(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cVerwendung: /plot portal <create|list|remove|use>");
            return true;
        }

        String action = args[1].toLowerCase();
        if ("create".equals(action)) {
            return handleCreate(player, args);
        }
        if ("list".equals(action)) {
            for (PortalManager.PlotPortal portal : portalManager.listPortals()) {
                player.sendMessage("§e" + portal.getPortalId() + "§7 -> " + portal.getPlotId() + " §8(Preis: " + portal.getCost() + ", CD: " + (portal.getCooldownMillis() / 1000L) + "s)");
            }
            return true;
        }
        if ("remove".equals(action)) {
            if (args.length < 3) {
                player.sendMessage("§cVerwendung: /plot portal remove <id>");
                return true;
            }
            PortalManager.PlotPortal portal = portalManager.getPortal(args[2]);
            if (portal == null) {
                player.sendMessage("§cPortal nicht gefunden.");
                return true;
            }
            if (!canManage(player, portal.getPlotId())) {
                player.sendMessage("§cDu darfst dieses Portal nicht entfernen.");
                return true;
            }
            portalManager.unregisterPortal(args[2]);
            player.sendMessage("§aPortal entfernt: " + args[2]);
            return true;
        }
        if ("use".equals(action)) {
            if (args.length < 3) {
                player.sendMessage("§cVerwendung: /plot portal use <id>");
                return true;
            }
            portalManager.teleport(player, args[2]);
            return true;
        }

        player.sendMessage("§cUnbekannte Portal-Aktion.");
        return true;
    }

    private boolean handleCreate(Player player, String[] args) {
        Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (plot == null) {
            player.sendMessage("§cDu musst in einem Plot stehen.");
            return true;
        }
        if (!canManage(player, plot.getPlotId())) {
            player.sendMessage("§cDu darfst in diesem Plot kein Portal erstellen.");
            return true;
        }

        double cost = args.length >= 3 ? parseDouble(args[2], 0D) : 0D;
        long cooldownSeconds = args.length >= 4 ? (long) parseDouble(args[3], 10D) : 10L;
        Location edge = player.getLocation().getBlock().getLocation();
        String portalId = portalManager.createPortal(plot.getPlotId(), edge, cost, cooldownSeconds * 1000L);
        player.sendMessage("§aPortal erstellt: §e" + portalId);
        return true;
    }

    private boolean canManage(Player player, String plotId) {
        if (player.hasPermission("streuland.portal.admin")) {
            return true;
        }
        Plot plot = plotManager.getStorage(player.getWorld()).getPlot(plotId);
        return plot != null && plot.isAllowed(player.getUniqueId());
    }

    private double parseDouble(String input, double fallback) {
        try {
            return Double.parseDouble(input);
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
