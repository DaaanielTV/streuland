package de.streuland.plot.upgrade;

import java.util.Optional;
import java.util.UUID;

/**
 * Service interface for plot progression upgrades.
 */
public interface PlotUpgradeService {
    Optional<PlotProgressionState> getState(String plotId);

    boolean canUpgrade(String plotId, UUID playerId, String upgradeId);

    boolean applyUpgrade(String plotId, UUID playerId, String upgradeId);
}
