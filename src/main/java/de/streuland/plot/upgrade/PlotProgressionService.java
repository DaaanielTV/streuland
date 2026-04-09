package de.streuland.plot.upgrade;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * High-level progression module that centralizes level/upgrade queries.
 */
public class PlotProgressionService {
    private final PlotUpgradeService upgradeService;

    public PlotProgressionService(PlotUpgradeService upgradeService) {
        this.upgradeService = upgradeService;
    }

    public PlotProgressionState getStateOrInitial(String plotId) {
        return upgradeService.getState(plotId).orElse(PlotProgressionState.initial());
    }

    public int getOverallLevel(String plotId) {
        return getStateOrInitial(plotId).getOverallLevel();
    }

    public List<PlotUpgradeView> listUpgrades(String plotId, UUID playerId) {
        return upgradeService.getAvailableUpgrades(plotId, playerId);
    }

    public Optional<String> buyUpgrade(String plotId, UUID playerId, String upgradeId) {
        if (!upgradeService.canUpgrade(plotId, playerId, upgradeId)) {
            return Optional.of("Upgrade requirements not met.");
        }
        return upgradeService.applyUpgrade(plotId, playerId, upgradeId)
                ? Optional.empty()
                : Optional.of("Upgrade purchase failed during apply.");
    }
}
