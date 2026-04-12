package de.streuland.command;

import de.streuland.admin.DiagnosticsReport;
import de.streuland.admin.StreulandDiagnosticsService;
import de.streuland.backup.PlotBackupCoordinator;
import de.streuland.dashboard.PlotAuditEvent;
import de.streuland.dashboard.PlotAuditLogService;
import de.streuland.dashboard.PlotBackupOperationsService;
import de.streuland.plot.PlotManager;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class StreulandCommandExecutor implements CommandExecutor {
    public static final String PERMISSION = "streuland.admin.diagnostics";

    private final PlotManager plotManager;
    private final StreulandDiagnosticsService diagnosticsService;
    private final PlotBackupOperationsService backupOperationsService;
    private final PlotAuditLogService auditLogService;

    public StreulandCommandExecutor(PlotManager plotManager,
                                    StreulandDiagnosticsService diagnosticsService,
                                    PlotBackupCoordinator backupCoordinator,
                                    PlotAuditLogService auditLogService) {
        this.plotManager = plotManager;
        this.diagnosticsService = diagnosticsService;
        this.auditLogService = auditLogService;
        this.backupOperationsService = new PlotBackupOperationsService(backupCoordinator, auditLogService);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§cKeine Berechtigung.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§eVerwendung: /streuland <debug|reload|scan|repair|backup|audit>");
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "debug":
                for (String line : diagnosticsService.buildDebugDump()) {
                    sender.sendMessage("§7" + line);
                }
                return true;
            case "reload":
                plotManager.reloadAllWorlds();
                sender.sendMessage("§aPlotStorage und SpatialGrid wurden neu geladen.");
                return true;
            case "scan":
                DiagnosticsReport report = diagnosticsService.scan();
                sender.sendMessage("§eDiagnose beendet. Gefundene Probleme: §f" + report.getTotalIssues());
                report.issueCounts().forEach((type, count) -> sender.sendMessage("§7- " + type + ": " + count));
                return true;
            case "repair":
                DiagnosticsReport scan = diagnosticsService.scan();
                int repaired = diagnosticsService.repair(scan);
                sender.sendMessage("§aReparatur beendet. Reparierte Einträge: §f" + repaired + "§a / Gefundene Probleme: §f" + scan.getTotalIssues());
                return true;
            case "backup":
                return handleBackup(sender, args);
            case "audit":
                return handleAudit(sender, args);
            default:
                sender.sendMessage("§cUnbekannter Subcommand. Nutze /streuland <debug|reload|scan|repair|backup|audit>");
                return true;
        }
    }

    private boolean handleBackup(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eVerwendung: /streuland backup <create|list|restore> <plotId> [snapshotId]");
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if ("create".equals(action)) {
            if (args.length < 3) {
                sender.sendMessage("§cBitte Plot-ID angeben.");
                return true;
            }
            String plotId = args[2];
            UUID actor = sender instanceof Player ? ((Player) sender).getUniqueId() : UUID.randomUUID();
            String actorName = sender.getName();
            Map<String, Object> created = backupOperationsService.createBackup(plotId, actor, actorName, "command").join();
            if (created == null) {
                sender.sendMessage("§cBackup konnte nicht erstellt werden.");
                return true;
            }
            sender.sendMessage("§aBackup erstellt: §f" + created.get("snapshotId") + " §7(" + created.get("plotId") + ")");
            return true;
        }
        if ("list".equals(action)) {
            if (args.length < 3) {
                sender.sendMessage("§cBitte Plot-ID angeben.");
                return true;
            }
            List<Map<String, Object>> backups = backupOperationsService.listBackups(args[2]);
            sender.sendMessage("§eBackups für " + args[2] + ": §f" + backups.size());
            for (Map<String, Object> backup : backups) {
                sender.sendMessage("§7- " + backup.get("snapshotId") + " (" + backup.get("version") + ")");
            }
            return true;
        }
        if ("restore".equals(action)) {
            if (args.length < 4) {
                sender.sendMessage("§cVerwendung: /streuland backup restore <plotId> <snapshotId>");
                return true;
            }
            UUID actor = sender instanceof Player ? ((Player) sender).getUniqueId() : UUID.randomUUID();
            Map<String, Object> restored = backupOperationsService.restoreBackup(args[2], args[3], actor, sender.getName()).join();
            if (restored == null) {
                sender.sendMessage("§cRestore fehlgeschlagen.");
                return true;
            }
            sender.sendMessage("§aRestore abgeschlossen. Rollback-Snapshot: §f" + restored.get("rollbackSnapshotId"));
            return true;
        }
        sender.sendMessage("§cUnbekannter Backup-Befehl.");
        return true;
    }

    private boolean handleAudit(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§eVerwendung: /streuland audit <plotId>");
            return true;
        }
        List<PlotAuditEvent> events = auditLogService.listByPlot(args[1], 20);
        sender.sendMessage("§eAudit für " + args[1] + " (" + events.size() + "):");
        for (PlotAuditEvent event : events) {
            sender.sendMessage("§7- " + event.getTimestamp() + " §f" + event.getAction() + " §8actor=" + event.getActor());
        }
        return true;
    }
}
