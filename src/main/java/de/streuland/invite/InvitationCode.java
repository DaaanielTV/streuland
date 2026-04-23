package de.streuland.invite;

import java.util.List;
import java.util.UUID;

/**
 * Lightweight Invitation Code entity for MVP MVP.
 * Serializable via Gson for REST responses.
 */
public class InvitationCode {
    public String id;
    public String code;
    public String issuerUserId;
    public String createdAt;
    public String expiresAt; // ISO-8601 string or null
    public Integer maxUses; // null means unlimited
    public int uses;
    public boolean isRevoked;
    public List<String> allowedRoles;
    public String serverId;
    public String targetServer; // optional scope

    public InvitationCode() {
        // default constructor for Gson
    }

    public static InvitationCode create(String code, String issuerUserId, String createdAt, String expiresAt,
                                        Integer maxUses, List<String> allowedRoles, String serverId, String targetServer) {
        InvitationCode inv = new InvitationCode();
        inv.id = UUID.randomUUID().toString();
        inv.code = code;
        inv.issuerUserId = issuerUserId;
        inv.createdAt = createdAt;
        inv.expiresAt = expiresAt;
        inv.maxUses = maxUses;
        inv.uses = 0;
        inv.isRevoked = false;
        inv.allowedRoles = allowedRoles;
        inv.serverId = serverId;
        inv.targetServer = targetServer;
        return inv;
    }
}
