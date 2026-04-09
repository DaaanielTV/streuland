package de.streuland.approval;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Workflow façade around PlotApprovalService so command/UI layers remain thin.
 */
public class PlotApprovalWorkflowService {
    private final PlotApprovalService approvalService;

    public PlotApprovalWorkflowService(PlotApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    public PlotApprovalRequest submit(Player requester,
                                      PlotApprovalActionType actionType,
                                      String plotId,
                                      Map<String, String> details) {
        return approvalService.request(
                requester,
                actionType,
                plotId,
                details == null ? Collections.emptyMap() : details
        );
    }

    public List<PlotApprovalRequest> listPendingForReview() {
        return approvalService.listPending();
    }

    public boolean approve(String requestId, Player reviewer, String comment) {
        return approvalService.approve(requestId, reviewer, comment);
    }

    public boolean deny(String requestId, Player reviewer, String reason) {
        return approvalService.deny(requestId, reviewer, reason);
    }
}
