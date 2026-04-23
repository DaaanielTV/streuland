package de.streuland.invite;

import java.util.List;

/**
 * Abstraction for invitation management (MVP).
 * Implementations may be in-memory, DB-backed, etc.
 */
public interface InvitationGateway {
    InvitationCode create(InvitationCode code);
    List<InvitationCode> listAll();
    InvitationCode getById(String id);
    InvitationCode getByCode(String code);
    InvitationCode revoke(String id);
    InvitationCode update(String id, String expiresAt, Integer maxUses, List<String> allowedRoles, Boolean isRevoked);
    void incrementUses(String id);
}
