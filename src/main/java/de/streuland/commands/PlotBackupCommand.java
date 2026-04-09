package de.streuland.commands;

import de.streuland.backup.PlotBackupCoordinator;
import de.streuland.backup.SnapshotService;
import de.streuland.plot.PlotManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class PlotBackupCommand {
    private final PlotBackupCoordinator backupCoordinator;

    public PlotBackupCommand(PlotManager plotManager, SnapshotService snapshotService) {
        this.backupCoordinator = new PlotBackupCoordinator(plotManager, snapshotService);
    }

    public boolean handle(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage("§cVerwendung: /plot backup <take|list|restore> <plotId> [snapshotId]");
            return true;
        }
        String action = args[1].toLowerCase();
        String plotRef = args[2];

        if ("take".equals(action)) {
            sender.sendMessage("§eBackup wird erstellt...");
            UUID actorId = sender instanceof Player ? ((Player) sender).getUniqueId() : UUID.randomUUID();
            String actorName = sender instanceof Player ? sender.getName() : "CONSOLE";
            backupCoordinator.takeSnapshot(plotRef, actorId, actorName, "manual-backup").thenAccept(snapshotId -> {
                if (snapshotId == null) {
                    sender.sendMessage("§cPlot nicht gefunden.");
                    return;
                }
                sender.sendMessage("§aBackup erstellt: §f" + snapshotId);
            });
            return true;
        }

        if ("list".equals(action)) {
            List<String> snapshots = backupCoordinator.listSnapshots(plotRef);
            if (snapshots.isEmpty()) {
                sender.sendMessage("§cKeine Backups vorhanden.");
                return true;
            }
            sender.sendMessage("§6=== Backups (Plot " + plotRef + ") ===");
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
            backupCoordinator.restore(plotRef, snapshotId).thenAccept(success -> {
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
