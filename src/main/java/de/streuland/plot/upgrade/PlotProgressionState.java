package de.streuland.plot.upgrade;

import java.time.Instant;

/**
 * Tracks progression-related state for a plot.
 */
public class PlotProgressionState {
    private final String plotId;
    private final int level;
    private final Instant lastUpgradeAt;

    public PlotProgressionState(String plotId, int level, Instant lastUpgradeAt) {
        this.plotId = plotId;
        this.level = level;
        this.lastUpgradeAt = lastUpgradeAt;
    }

    public String getPlotId() {
        return plotId;
    }

    public int getLevel() {
        return level;
    }

    public Instant getLastUpgradeAt() {
        return lastUpgradeAt;
    }
}
