package de.streuland.plot.upgrade;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Defines a single plot level and its unlocked capabilities.
 */
public class PlotLevelDefinition {
    private final int level;
    private final int plotSize;
    private final int featureSlots;
    private final Set<String> specialMechanics;

    public PlotLevelDefinition(int level, int plotSize, int featureSlots, Set<String> specialMechanics) {
        this.level = level;
        this.plotSize = plotSize;
        this.featureSlots = featureSlots;
        this.specialMechanics = specialMechanics == null ? new HashSet<>() : new HashSet<>(specialMechanics);
    }

    public int getLevel() {
        return level;
    }

    public int getPlotSize() {
        return plotSize;
    }

    public int getFeatureSlots() {
        return featureSlots;
    }

    public Set<String> getSpecialMechanics() {
        return Collections.unmodifiableSet(specialMechanics);
    }
}
