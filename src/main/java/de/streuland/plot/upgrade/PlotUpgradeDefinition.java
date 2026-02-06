package de.streuland.plot.upgrade;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Defines an upgrade transition between levels with requirements.
 */
public class PlotUpgradeDefinition {
    private final String id;
    private final int fromLevel;
    private final int toLevel;
    private final PlotUpgradeCost cost;
    private final Set<String> requiredUnlocks;

    public PlotUpgradeDefinition(String id, int fromLevel, int toLevel, PlotUpgradeCost cost, Set<String> requiredUnlocks) {
        this.id = id;
        this.fromLevel = fromLevel;
        this.toLevel = toLevel;
        this.cost = cost;
        this.requiredUnlocks = requiredUnlocks == null ? new HashSet<>() : new HashSet<>(requiredUnlocks);
    }

    public String getId() {
        return id;
    }

    public int getFromLevel() {
        return fromLevel;
    }

    public int getToLevel() {
        return toLevel;
    }

    public PlotUpgradeCost getCost() {
        return cost;
    }

    public Set<String> getRequiredUnlocks() {
        return Collections.unmodifiableSet(requiredUnlocks);
    }
}
