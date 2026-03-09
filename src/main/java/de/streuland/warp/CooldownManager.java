package de.streuland.warp;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CooldownManager {
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public boolean isOnCooldown(UUID playerId) {
        return getRemainingMillis(playerId) > 0;
    }

    public long getRemainingMillis(UUID playerId) {
        long expiresAt = cooldowns.getOrDefault(playerId, 0L);
        return Math.max(0L, expiresAt - System.currentTimeMillis());
    }

    public void setCooldown(UUID playerId, long cooldownMillis) {
        cooldowns.put(playerId, System.currentTimeMillis() + Math.max(0L, cooldownMillis));
    }
}
