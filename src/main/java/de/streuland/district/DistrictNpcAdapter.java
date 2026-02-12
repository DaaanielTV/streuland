package de.streuland.district;

import java.util.UUID;

/**
 * Adapter interface for district NPC integrations.
 */
public interface DistrictNpcAdapter {
    void assignQuest(UUID playerId, District district);

    void updateProgressDisplay(District district);

    void grantReward(UUID playerId, District district, String rewardId);

    void spawnOrUpdateTrader(District district, TraderNpcService.TraderNpc trader);

    void openTraderDialogue(UUID playerId, District district, TraderNpcService.TraderNpc trader);
}
