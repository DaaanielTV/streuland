package de.streuland.plot.upgrade;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Rewards automatically granted when a plot reaches a specific progression level. */
public class PlotLevelReward {
    private final int level;
    private final int storageSlots;
    private final Set<String> unlockedAbilities;
    private final Set<String> cosmetics;
    private final Map<String, Double> statBonuses;

    public PlotLevelReward(int level,
                           int storageSlots,
                           Set<String> unlockedAbilities,
                           Set<String> cosmetics,
                           Map<String, Double> statBonuses) {
        this.level = Math.max(1, level);
        this.storageSlots = Math.max(0, storageSlots);
        this.unlockedAbilities = unlockedAbilities == null ? new LinkedHashSet<>() : new LinkedHashSet<>(unlockedAbilities);
        this.cosmetics = cosmetics == null ? new LinkedHashSet<>() : new LinkedHashSet<>(cosmetics);
        this.statBonuses = statBonuses == null ? new LinkedHashMap<>() : new LinkedHashMap<>(statBonuses);
    }

    public int getLevel() {
        return level;
    }

    public int getStorageSlots() {
        return storageSlots;
    }

    public Set<String> getUnlockedAbilities() {
        return Collections.unmodifiableSet(unlockedAbilities);
    }

    public Set<String> getCosmetics() {
        return Collections.unmodifiableSet(cosmetics);
    }

    public Map<String, Double> getStatBonuses() {
        return Collections.unmodifiableMap(statBonuses);
    }
}
