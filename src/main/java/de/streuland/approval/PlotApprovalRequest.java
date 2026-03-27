package de.streuland.approval;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class PlotApprovalRequest {
    public enum Status {
        PENDING,
        APPROVED,
        DENIED,
        CANCELED,
        EXPIRED
    }

    private final String id;
    private final PlotApprovalActionType actionType;
    private final String plotId;
    private final UUID requesterId;
    private final String requesterName;
    private final String worldName;
    private final long createdAt;
    private final long expiresAt;
    private final String token;
    private final Map<String, String> details;

    private Status status;
    private UUID reviewedBy;
    private long reviewedAt;
    private String reviewComment;

    public PlotApprovalRequest(String id,
                               PlotApprovalActionType actionType,
                               String plotId,
                               UUID requesterId,
                               String requesterName,
                               String worldName,
                               long createdAt,
                               long expiresAt,
                               String token,
                               Map<String, String> details,
                               Status status,
                               UUID reviewedBy,
                               long reviewedAt,
                               String reviewComment) {
        this.id = id;
        this.actionType = actionType;
        this.plotId = plotId;
        this.requesterId = requesterId;
        this.requesterName = requesterName;
        this.worldName = worldName;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.token = token;
        this.details = details == null ? new LinkedHashMap<>() : new LinkedHashMap<>(details);
        this.status = status == null ? Status.PENDING : status;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = reviewedAt;
        this.reviewComment = reviewComment;
    }

    public static PlotApprovalRequest pending(String id,
                                              PlotApprovalActionType actionType,
                                              String plotId,
                                              UUID requesterId,
                                              String requesterName,
                                              String worldName,
                                              long createdAt,
                                              long expiresAt,
                                              String token,
                                              Map<String, String> details) {
        return new PlotApprovalRequest(id, actionType, plotId, requesterId, requesterName, worldName,
                createdAt, expiresAt, token, details, Status.PENDING, null, 0L, null);
    }

    public String getId() { return id; }

    public PlotApprovalActionType getActionType() { return actionType; }

    public String getPlotId() { return plotId; }

    public UUID getRequesterId() { return requesterId; }

    public UUID getPlayerId() { return requesterId; }

    public String getRequesterName() { return requesterName; }

    public String getPlayerName() { return requesterName; }

    public String getWorldName() { return worldName; }

    public long getCreatedAt() { return createdAt; }

    public long getExpiresAt() { return expiresAt; }

    public String getToken() { return token; }

    public Map<String, String> getDetails() { return Collections.unmodifiableMap(details); }

    public Status getStatus() { return status; }

    public UUID getReviewedBy() { return reviewedBy; }

    public long getReviewedAt() { return reviewedAt; }

    public String getReviewComment() { return reviewComment; }

    public boolean isExpired(long now) {
        return expiresAt > 0 && now > expiresAt;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }

    public void markApproved(UUID reviewerId, long reviewedAt, String comment) {
        mark(Status.APPROVED, reviewerId, reviewedAt, comment);
    }

    public void markDenied(UUID reviewerId, long reviewedAt, String comment) {
        mark(Status.DENIED, reviewerId, reviewedAt, comment);
    }

    public void markCanceled(UUID reviewerId, long reviewedAt, String comment) {
        mark(Status.CANCELED, reviewerId, reviewedAt, comment);
    }

    public void markExpired(long reviewedAt) {
        mark(Status.EXPIRED, null, reviewedAt, "expired");
    }

    private void mark(Status nextStatus, UUID reviewerId, long reviewedAt, String comment) {
        this.status = nextStatus;
        this.reviewedBy = reviewerId;
        this.reviewedAt = reviewedAt;
        this.reviewComment = comment;
    }
}
