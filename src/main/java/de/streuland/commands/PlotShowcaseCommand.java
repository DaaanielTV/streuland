package de.streuland.commands;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

/**
 * Handles showcase/public-plot workflows for /plot public and /plot showcase.
 */
public class PlotShowcaseCommand {
    private final PlotManager plotManager;

    public PlotShowcaseCommand(PlotManager plotManager) {
        this.plotManager = plotManager;
    }

    public boolean handle(Player player, String[] args) {
        if (args.length < 2) {
            return handleList(player, "");
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if ("on".equals(action) || "off".equals(action)) {
            return handleToggle(player, action);
        }
        if ("spawn".equals(action)) {
            return handleSpawn(player);
        }
        if ("visit".equals(action)) {
            return handleVisit(player, args);
        }
        if ("search".equals(action)) {
            return handleList(player, joinFrom(args, 2));
        }
        if ("list".equals(action)) {
            return handleList(player, joinFrom(args, 2));
        }
        if ("title".equals(action) || "description".equals(action) || "tags".equals(action)) {
            return handleMetadata(player, action, joinFrom(args, 2));
        }

        return handleList(player, joinFrom(args, 1));
    }

    private boolean handleToggle(Player player, String action) {
        Plot plot = currentOwnedPlot(player);
        if (plot == null) {
            player.sendMessage("§cDu musst auf deinem eigenen Plot stehen.");
            return true;
        }
        PlotStorage storage = plotManager.getStorage(player.getWorld());
        PlotData data = storage.getPlotData(plot.getPlotId());
        boolean enabled = "on".equals(action);
        plotManager.updateShowcase(plot.getPlotId(), player.getUniqueId(), enabled,
                data.getShowcaseTitle(), data.getShowcaseDescription(), data.getShowcaseTags());
        player.sendMessage(enabled ? "§aDein Plot ist jetzt öffentlich besuchbar." : "§eDein Plot ist nicht mehr öffentlich.");
        return true;
    }

    private boolean handleMetadata(Player player, String field, String value) {
        Plot plot = currentOwnedPlot(player);
        if (plot == null) {
            player.sendMessage("§cDu musst auf deinem eigenen Plot stehen.");
            return true;
        }
        PlotStorage storage = plotManager.getStorage(player.getWorld());
        PlotData data = storage.getPlotData(plot.getPlotId());
        if ("title".equals(field)) {
            data.setShowcaseTitle(value);
        } else if ("description".equals(field)) {
            data.setShowcaseDescription(value);
        } else if ("tags".equals(field)) {
            data.setShowcaseTagsFromText(value);
        }
        plotManager.updateShowcase(plot.getPlotId(), player.getUniqueId(), data.isPublicVisitEnabled(),
                data.getShowcaseTitle(), data.getShowcaseDescription(), data.getShowcaseTags());
        player.sendMessage("§aShowcase-" + field + " aktualisiert.");
        return true;
    }

    private boolean handleSpawn(Player player) {
        Plot plot = currentOwnedPlot(player);
        if (plot == null) {
            player.sendMessage("§cDu musst auf deinem eigenen Plot stehen.");
            return true;
        }
        Location location = player.getLocation();
        plotManager.setShowcaseSpawn(plot.getPlotId(), player.getUniqueId(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        player.sendMessage("§aÖffentlicher Spawnpunkt gesetzt.");
        return true;
    }

    private boolean handleVisit(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /plot showcase visit <plotId>");
            return true;
        }
        String plotId = args[2];
        if (!plotManager.canVisitPublicPlot(plotId)) {
            player.sendMessage("§cDieser Plot ist nicht öffentlich besuchbar.");
            return true;
        }
        World world = plotManager.getWorldForPlot(plotId);
        int[] coordinates = plotManager.getTeleportCoordinates(plotId);
        if (coordinates == null) {
            player.sendMessage("§cTeleport fehlgeschlagen.");
            return true;
        }
        player.teleport(world.getBlockAt(coordinates[0], coordinates[1], coordinates[2]).getLocation());
        player.sendMessage("§aTeleportiert zu öffentlichem Plot " + plotId + ".");
        return true;
    }

    private boolean handleList(Player player, String query) {
        List<Plot> plots = plotManager.searchPublicPlots(player.getWorld(), query);
        if (plots.isEmpty()) {
            player.sendMessage(query == null || query.trim().isEmpty()
                    ? "§7Keine öffentlichen Plots gefunden."
                    : "§7Keine öffentlichen Plots für deine Suche gefunden.");
            return true;
        }
        player.sendMessage("§6=== Öffentliche Plots ===");
        PlotStorage storage = plotManager.getStorage(player.getWorld());
        for (Plot plot : plots) {
            PlotData data = storage.getPlotData(plot.getPlotId());
            String title = data.getShowcaseTitle().isEmpty() ? plot.getPlotId() : data.getShowcaseTitle();
            player.sendMessage("§e" + title + "§7 (" + plot.getPlotId() + ") §f- Tags: " + data.getShowcaseTagsAsText());
        }
        return true;
    }

    private Plot currentOwnedPlot(Player player) {
        Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        return plot != null && player.getUniqueId().equals(plot.getOwner()) ? plot : null;
    }

    private String joinFrom(String[] args, int index) {
        if (args.length <= index) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = index; i < args.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }
}
