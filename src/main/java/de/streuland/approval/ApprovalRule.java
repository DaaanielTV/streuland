package de.streuland.approval;

public enum ApprovalRule {
    OWNER_ONLY,
    MODERATOR_ONLY,
    CONFIGURABLE;

    public static ApprovalRule fromConfig(String value, ApprovalRule fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return ApprovalRule.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
