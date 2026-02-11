package de.streuland.district;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Mutable progress snapshot for a district.
 */
public class DistrictProgress {
    private int builtBlocks;
    private int activePlayers;
    private final Set<String> completedGoals = new HashSet<>();
    private long lastUpdated;

    public int getBuiltBlocks() {
        return builtBlocks;
    }

    public void addBuiltBlocks(int delta) {
        builtBlocks = Math.max(0, builtBlocks + delta);
        lastUpdated = System.currentTimeMillis();
    }

    public int getActivePlayers() {
        return activePlayers;
    }

    public void setActivePlayers(int activePlayers) {
        this.activePlayers = Math.max(0, activePlayers);
        lastUpdated = System.currentTimeMillis();
    }

    public Set<String> getCompletedGoals() {
        return Collections.unmodifiableSet(completedGoals);
    }

    public void markGoalCompleted(String goalId) {
        completedGoals.add(goalId);
        lastUpdated = System.currentTimeMillis();
    }

    public long getLastUpdated() {
        return lastUpdated;
    }
}
