package de.streuland.commands;

import de.streuland.backup.SnapshotService;
import org.bukkit.command.CommandSender;

import java.util.List;

public class PlotBackupCommand {
    private final SnapshotService snapshotService;

    public PlotBackupCommand(SnapshotService snapshotService) {
        this.snapshotService = snapshotService;
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cVerwendung: /plot backup <take|list|restore> <plotId> [snapshotId]");
            return true;
        }
        String action = args[1].toLowerCase();
        int plotId;
        try {
            plotId = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cPlotId muss eine Zahl sein.");
            return true;
        }

        if ("take".equals(action)) {
            sender.sendMessage("§eBackup wird erstellt...");
            snapshotService.takeSnapshot(plotId).thenAccept(snapshotId -> {
                if (snapshotId == null) {
                    sender.sendMessage("§cPlot nicht gefunden.");
                    return;
                }
                sender.sendMessage("§aBackup erstellt: §f" + snapshotId);
            });
            return true;
        }

        if ("list".equals(action)) {
            List<String> snapshots = snapshotService.listSnapshots(plotId);
            if (snapshots.isEmpty()) {
                sender.sendMessage("§cKeine Backups vorhanden.");
                return true;
            }
            sender.sendMessage("§6=== Backups (Plot " + plotId + ") ===");
            for (String snapshot : snapshots) {
                sender.sendMessage("§e" + snapshot);
            }
            return true;
        }

        if ("restore".equals(action)) {
            if (args.length < 4) {
                sender.sendMessage("§cVerwendung: /plot backup restore <plotId> <snapshotId>");
                return true;
            }
            String snapshotId = args[3];
            sender.sendMessage("§eBackup wird wiederhergestellt...");
            snapshotService.restoreSnapshot(plotId, snapshotId).thenAccept(success -> {
                if (success) {
                    sender.sendMessage("§aBackup wiederhergestellt.");
                } else {
                    sender.sendMessage("§cWiederherstellung fehlgeschlagen.");
                }
            });
            return true;
        }

        sender.sendMessage("§cVerwendung: /plot backup <take|list|restore> <plotId> [snapshotId]");
        return true;
    }
}
