package de.streuland.district;

import java.util.UUID;

public class DistrictMember {
    private final UUID playerId;
    private DistrictRole role;
    private final long joinedAt;

    public DistrictMember(UUID playerId, DistrictRole role, long joinedAt) {
        this.playerId = playerId;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public DistrictRole getRole() {
        return role;
    }

    public void setRole(DistrictRole role) {
        this.role = role;
    }

    public long getJoinedAt() {
        return joinedAt;
    }
}
