package de.streuland.approval;

import java.util.UUID;

public class PlotApprovalRequest {
    private final String id;
    private final UUID playerId;
    private final String playerName;
    private final String worldName;
    private final long createdAt;
    private final String token;

    public PlotApprovalRequest(String id, UUID playerId, String playerName, String worldName, long createdAt, String token) {
        this.id = id;
        this.playerId = playerId;
        this.playerName = playerName;
        this.worldName = worldName;
        this.createdAt = createdAt;
        this.token = token;
    }

    public String getId() { return id; }
    public UUID getPlayerId() { return playerId; }
    public String getPlayerName() { return playerName; }
    public String getWorldName() { return worldName; }
    public long getCreatedAt() { return createdAt; }
    public String getToken() { return token; }
}
