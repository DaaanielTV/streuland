package de.streuland.plot.upgrade;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class PlotExperienceResult {
    private static final PlotExperienceResult NONE = new PlotExperienceResult(false, 0, 1, 1, Collections.emptySet());

    private final boolean applied;
    private final int grantedXp;
    private final int previousLevel;
    private final int currentLevel;
    private final Set<Integer> unlockedRewardLevels;

    public PlotExperienceResult(boolean applied, int grantedXp, int previousLevel, int currentLevel, Set<Integer> unlockedRewardLevels) {
        this.applied = applied;
        this.grantedXp = Math.max(0, grantedXp);
        this.previousLevel = Math.max(1, previousLevel);
        this.currentLevel = Math.max(1, currentLevel);
        this.unlockedRewardLevels = unlockedRewardLevels == null ? new LinkedHashSet<>() : new LinkedHashSet<>(unlockedRewardLevels);
    }

    public static PlotExperienceResult none() {
        return NONE;
    }

    public boolean isApplied() {
        return applied;
    }

    public int getGrantedXp() {
        return grantedXp;
    }

    public int getPreviousLevel() {
        return previousLevel;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public boolean hasLeveledUp() {
        return currentLevel > previousLevel;
    }

    public Set<Integer> getUnlockedRewardLevels() {
        return Collections.unmodifiableSet(unlockedRewardLevels);
    }
}
