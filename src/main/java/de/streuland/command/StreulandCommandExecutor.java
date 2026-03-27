package de.streuland.command;

import de.streuland.admin.DiagnosticsReport;
import de.streuland.admin.StreulandDiagnosticsService;
import de.streuland.plot.PlotManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Locale;

public class StreulandCommandExecutor implements CommandExecutor {
    public static final String PERMISSION = "streuland.admin.diagnostics";

    private final PlotManager plotManager;
    private final StreulandDiagnosticsService diagnosticsService;

    public StreulandCommandExecutor(PlotManager plotManager, StreulandDiagnosticsService diagnosticsService) {
        this.plotManager = plotManager;
        this.diagnosticsService = diagnosticsService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage("§cKeine Berechtigung.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§eVerwendung: /streuland <debug|reload|scan|repair>");
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
            default:
                sender.sendMessage("§cUnbekannter Subcommand. Nutze /streuland <debug|reload|scan|repair>");
                return true;
        }
    }
}
