package de.streuland.district;

import java.util.UUID;

/**
 * Adapter interface for NPC integrations (quests, progress, rewards).
 */
public interface DistrictNpcAdapter {
    void assignQuest(UUID playerId, District district);

    void updateProgressDisplay(District district);

    void grantReward(UUID playerId, District district, String rewardId);
}
