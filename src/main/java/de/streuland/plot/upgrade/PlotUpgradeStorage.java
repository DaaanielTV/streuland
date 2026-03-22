package de.streuland.plot.upgrade;

import java.util.Optional;

public interface PlotUpgradeStorage {
    Optional<PlotProgressionState> load(String plotId);
    void save(String plotId, PlotProgressionState state);
}
