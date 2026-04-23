package de.streuland.invite;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Very small in-memory store for InvitationCode instances.
 * This MVP keeps data only for the lifetime of the process.
 */
public class InMemoryInvitationStore {
    // key: id, value: InvitationCode
    private static final Map<String, InvitationCode> CODE_MAP = new ConcurrentHashMap<>();
    // quick lookup by code string (case-sensitive)
    private static final Map<String, String> CODE_TO_ID = new ConcurrentHashMap<>();

    public static InvitationCode create(InvitationCode code) {
        if (code == null) return null;
        // Ensure unique by id/code; code field should be unique, if not, generate a new one
        if (code.code == null || code.code.isEmpty()) {
            code.code = generateRandomCode(32);
        }
        // If exists, generate a new code to avoid collision
        while (CODE_TO_ID.containsKey(code.code)) {
            code.code = generateRandomCode(32);
        }
        CODE_MAP.put(code.id, code);
        CODE_TO_ID.put(code.code, code.id);
        return code;
    }

    public static InvitationCode findById(String id) {
        return CODE_MAP.get(id);
    }

    public static InvitationCode findByCode(String code) {
        String id = CODE_TO_ID.get(code);
        return id == null ? null : CODE_MAP.get(id);
    }

    public static List<InvitationCode> listAll() {
        return new ArrayList<>(CODE_MAP.values());
    }

    public static InvitationCode revoke(String id) {
        InvitationCode c = CODE_MAP.get(id);
        if (c == null) return null;
        c.isRevoked = true;
        return c;
    }

    public static InvitationCode update(String id, String expiresAt, Integer maxUses, List<String> allowedRoles, Boolean isRevoked) {
        InvitationCode c = CODE_MAP.get(id);
        if (c == null) return null;
        if (expiresAt != null) c.expiresAt = expiresAt;
        if (maxUses != null) c.maxUses = maxUses;
        if (allowedRoles != null) c.allowedRoles = allowedRoles;
        if (isRevoked != null) c.isRevoked = isRevoked;
        return c;
    }

    public static void incrementUses(String code) {
        InvitationCode c = findByCode(code);
        if (c != null && !c.isRevoked) {
            c.uses += 1;
        }
    }

    // Increment uses by invitation id (for DB-backed gateway with ID-based reference)
    public static void incrementUsesById(String id) {
        InvitationCode c = CODE_MAP.get(id);
        if (c != null && !c.isRevoked) {
            c.uses += 1;
        }
    }

    private static String generateRandomCode(int length) {
        // URL-safe base62-like characters
        final String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // exclude confusing chars
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = (int) (Math.random() * chars.length());
            sb.append(chars.charAt(idx));
        }
        return sb.toString();
    }
}
