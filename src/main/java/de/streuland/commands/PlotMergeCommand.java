package de.streuland.commands;

import de.streuland.plot.PlotMergeService;
import de.streuland.plot.SplitStrategy;
import org.bukkit.entity.Player;

public class PlotMergeCommand {
    private final PlotMergeService mergeService;

    public PlotMergeCommand(PlotMergeService mergeService) {
        this.mergeService = mergeService;
    }

    public boolean handle(Player player, String[] args) {
        if (args.length < 2) {
            return false;
        }
        if ("merge".equalsIgnoreCase(args[0])) {
            return handleMerge(player, args);
        }
        if ("split".equalsIgnoreCase(args[0])) {
            return handleSplit(player, args);
        }
        return false;
    }

    private boolean handleMerge(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§cVerwendung: /plot merge <plotIdA> <plotIdB>");
            return true;
        }
        int a;
        int b;
        try {
            a = Integer.parseInt(args[1]);
            b = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cDie Plot-IDs müssen numerisch sein.");
            return true;
        }

        mergeService.mergePlots(a, b, player.getUniqueId()).thenAccept(success -> {
            if (success) {
                player.sendMessage("§aPlots wurden erfolgreich verschmolzen.");
            } else {
                player.sendMessage("§cMerge fehlgeschlagen. Prüfe Nachbarschaft, Berechtigungen und Zustand.");
            }
        });
        return true;
    }

    private boolean handleSplit(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage("§cVerwendung: /plot split <plotId> <rows> <cols>");
            return true;
        }
        int id;
        int rows;
        int cols;
        try {
            id = Integer.parseInt(args[1]);
            rows = Integer.parseInt(args[2]);
            cols = Integer.parseInt(args[3]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cPlot-ID, Rows und Cols müssen numerisch sein.");
            return true;
        }

        mergeService.splitPlot(id, SplitStrategy.grid(rows, cols), player.getUniqueId()).thenAccept(ids -> {
            if (ids.isEmpty()) {
                player.sendMessage("§cSplit fehlgeschlagen.");
            } else {
                player.sendMessage("§aSplit erfolgreich. Neue Subplots: §f" + ids);
            }
        });
        return true;
    }
}
