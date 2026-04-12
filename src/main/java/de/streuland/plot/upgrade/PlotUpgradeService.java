package de.streuland.plot.upgrade;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlotUpgradeService {
    Optional<PlotProgressionState> getState(String plotId);
    List<PlotUpgradeView> getAvailableUpgrades(String plotId, UUID playerId);
    boolean canUpgrade(String plotId, UUID playerId, String upgradeId);
    boolean applyUpgrade(String plotId, UUID playerId, String upgradeId);
    boolean canPrestige(String plotId, UUID playerId);
    boolean prestige(String plotId, UUID playerId);
}
