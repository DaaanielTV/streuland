package de.streuland.commands;

import de.streuland.approval.PlotApprovalRequest;
import de.streuland.approval.PlotApprovalService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class PlotApprovalCommand implements CommandExecutor {
    private final PlotApprovalService approvalService;

    public PlotApprovalCommand(PlotApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("streuland.plot.approval")) {
            sender.sendMessage("§cKeine Berechtigung.");
            return true;
        }
        if (args.length == 0 || "list".equalsIgnoreCase(args[0])) {
            List<PlotApprovalRequest> pending = approvalService.listPending();
            if (pending.isEmpty()) {
                sender.sendMessage("§7Keine offenen Plot-Freigaben.");
                return true;
            }
            sender.sendMessage("§6Offene Plot-Freigaben: " + pending.size());
            for (PlotApprovalRequest request : pending) {
                sender.sendMessage("§e" + request.getId() + " §7- " + request.getPlayerName() + " @ " + request.getWorldName());
            }
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cVerwendung: /plotapprove <list|approve|reject> <id>");
            return true;
        }

        String id = args[1];
        if ("approve".equalsIgnoreCase(args[0])) {
            sender.sendMessage(approvalService.approve(id) ? "§aAntrag genehmigt." : "§cAntrag nicht gefunden.");
            return true;
        }
        if ("reject".equalsIgnoreCase(args[0])) {
            sender.sendMessage(approvalService.reject(id) ? "§eAntrag abgelehnt." : "§cAntrag nicht gefunden.");
            return true;
        }

        sender.sendMessage("§cVerwendung: /plotapprove <list|approve|reject> <id>");
        return true;
    }
}
