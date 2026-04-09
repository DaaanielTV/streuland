package de.streuland.commands;

import de.streuland.approval.PlotApprovalActionType;
import de.streuland.approval.PlotApprovalRequest;
import de.streuland.approval.PlotApprovalService;
import de.streuland.approval.PlotApprovalWorkflowService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlotApprovalCommand implements CommandExecutor {
    private final PlotApprovalService approvalService;
    private final PlotApprovalWorkflowService workflowService;

    public PlotApprovalCommand(PlotApprovalService approvalService) {
        this.approvalService = approvalService;
        this.workflowService = new PlotApprovalWorkflowService(approvalService);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || "list".equalsIgnoreCase(args[0]) || "approvals".equalsIgnoreCase(args[0])) {
            return handleList(sender);
        }
        if ("request-delete".equalsIgnoreCase(args[0])) {
            return handleRequest(sender, args, PlotApprovalActionType.DELETE);
        }
        if ("request-unclaim".equalsIgnoreCase(args[0])) {
            return handleRequest(sender, args, PlotApprovalActionType.UNCLAIM);
        }
        if ("request-resize".equalsIgnoreCase(args[0])) {
            return handleRequest(sender, args, PlotApprovalActionType.RESIZE);
        }
        if ("request-transfer".equalsIgnoreCase(args[0])) {
            return handleRequest(sender, args, PlotApprovalActionType.TRANSFER);
        }

        if (args.length < 2) {
            sender.sendMessage("§cVerwendung: /plot approvals | /plot approve <id> | /plot deny <id>");
            return true;
        }

        String id = args[1];
        if ("approve".equalsIgnoreCase(args[0])) {
            return handleApprove(sender, id);
        }
        if ("deny".equalsIgnoreCase(args[0]) || "reject".equalsIgnoreCase(args[0])) {
            return handleDeny(sender, id);
        }

        sender.sendMessage("§cVerwendung: /plot approvals | /plot approve <id> | /plot deny <id>");
        return true;
    }

    private boolean handleRequest(CommandSender sender, String[] args, PlotApprovalActionType actionType) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cNur Spieler können Anträge stellen.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage("§cVerwendung: /plot request-" + actionType.name().toLowerCase() + " <plotId> [targetPlayerUuid]");
            return true;
        }
        String plotId = args[1];
        Map<String, String> details = Collections.emptyMap();
        if (actionType == PlotApprovalActionType.TRANSFER) {
            if (args.length < 3) {
                sender.sendMessage("§cVerwendung: /plot request-transfer <plotId> <targetPlayerUuid>");
                return true;
            }
            details = new HashMap<>();
            details.put("targetPlayerId", args[2]);
        }
        PlotApprovalRequest request = workflowService.submit((Player) sender, actionType, plotId, details);
        sender.sendMessage("§eAntrag eingereicht: §f" + request.getId() + " §7(" + actionType.name().toLowerCase() + ")");
        return true;
    }

    private boolean handleList(CommandSender sender) {
        if (!sender.hasPermission("streuland.plot.approval")) {
            sender.sendMessage("§cKeine Berechtigung.");
            return true;
        }
        List<PlotApprovalRequest> pending = workflowService.listPendingForReview();
        if (pending.isEmpty()) {
            sender.sendMessage("§7Keine offenen Plot-Freigaben.");
            return true;
        }
        sender.sendMessage("§6Offene Plot-Freigaben: " + pending.size());
        for (PlotApprovalRequest request : pending) {
            sender.sendMessage("§e" + request.getId() + " §7- " + request.getActionType().name().toLowerCase() + " by " + request.getRequesterName());
        }
        return true;
    }

    private boolean handleApprove(CommandSender sender, String id) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(approvalService.approve(id) ? "§aAntrag genehmigt." : "§cAntrag nicht gefunden.");
            return true;
        }
        Player player = (Player) sender;
        boolean success = workflowService.approve(id, player, "approved in command");
        sender.sendMessage(success ? "§aAntrag genehmigt." : "§cAntrag nicht gefunden oder keine Berechtigung.");
        return true;
    }

    private boolean handleDeny(CommandSender sender, String id) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(approvalService.reject(id) ? "§eAntrag abgelehnt." : "§cAntrag nicht gefunden.");
            return true;
        }
        Player player = (Player) sender;
        boolean success = workflowService.deny(id, player, "denied in command");
        sender.sendMessage(success ? "§eAntrag abgelehnt." : "§cAntrag nicht gefunden oder keine Berechtigung.");
        return true;
    }
}
