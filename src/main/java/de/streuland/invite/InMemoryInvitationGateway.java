package de.streuland.invite;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class InMemoryInvitationGateway implements InvitationGateway {
    @Override
    public InvitationCode create(InvitationCode code) {
        if (code == null) return null;
        if (code.createdAt == null) code.createdAt = Instant.now().toString();
        // Normalize: ensure an ID exists and code is unique via store
        if (code.id == null) code.id = java.util.UUID.randomUUID().toString();
        return InMemoryInvitationStore.create(code);
    }

    @Override
    public List<InvitationCode> listAll() {
        return InMemoryInvitationStore.listAll();
    }

    @Override
    public InvitationCode getById(String id) {
        return InMemoryInvitationStore.findById(id);
    }

    @Override
    public InvitationCode getByCode(String code) {
        return InMemoryInvitationStore.findByCode(code);
    }

    @Override
    public InvitationCode revoke(String id) {
        return InMemoryInvitationStore.revoke(id);
    }

    @Override
    public InvitationCode update(String id, String expiresAt, Integer maxUses, List<String> allowedRoles, Boolean isRevoked) {
        return InMemoryInvitationStore.update(id, expiresAt, maxUses, allowedRoles, isRevoked);
    }

    @Override
    public void incrementUses(String id) {
        InMemoryInvitationStore.incrementUsesById(id);
    }
}
