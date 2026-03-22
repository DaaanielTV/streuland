package de.streuland.plot.upgrade;

import java.util.Objects;

public class PlotUpgradeRequirement {
    private final String upgradeId;
    private final int minimumLevel;

    public PlotUpgradeRequirement(String upgradeId, int minimumLevel) {
        this.upgradeId = Objects.requireNonNull(upgradeId, "upgradeId");
        this.minimumLevel = Math.max(0, minimumLevel);
    }

    public String getUpgradeId() { return upgradeId; }
    public int getMinimumLevel() { return minimumLevel; }
}
