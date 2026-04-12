package de.streuland.plot.upgrade;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Tracks progression, purchased upgrades, and prestige state for a plot. */
public class PlotProgressionState {
    private final int overallLevel;
    private final int progressionPoints;
    private final int prestigeLevel;
    private final double lifetimeCurrencySpent;
    private final Instant lastUpgradeAt;
    private final Map<String, Integer> upgradeLevels;
    private final Map<String, String> activeSettings;

    public PlotProgressionState(int overallLevel,
                                Instant lastUpgradeAt,
                                Map<String, Integer> upgradeLevels,
                                Map<String, String> activeSettings) {
        this(overallLevel, 0, 0, 0D, lastUpgradeAt, upgradeLevels, activeSettings);
    }

    public PlotProgressionState(int overallLevel,
                                int progressionPoints,
                                int prestigeLevel,
                                double lifetimeCurrencySpent,
                                Instant lastUpgradeAt,
                                Map<String, Integer> upgradeLevels,
                                Map<String, String> activeSettings) {
        this.overallLevel = Math.max(1, overallLevel);
        this.progressionPoints = Math.max(0, progressionPoints);
        this.prestigeLevel = Math.max(0, prestigeLevel);
        this.lifetimeCurrencySpent = Math.max(0D, lifetimeCurrencySpent);
        this.lastUpgradeAt = lastUpgradeAt;
        this.upgradeLevels = upgradeLevels == null ? new LinkedHashMap<>() : new LinkedHashMap<>(upgradeLevels);
        this.activeSettings = activeSettings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(activeSettings);
    }

    public static PlotProgressionState initial() {
        return new PlotProgressionState(1, 0, 0, 0D, null, Collections.emptyMap(), Collections.emptyMap());
    }

    public int getOverallLevel() { return overallLevel; }
    public int getProgressionPoints() { return progressionPoints; }
    public int getPrestigeLevel() { return prestigeLevel; }
    public double getLifetimeCurrencySpent() { return lifetimeCurrencySpent; }
    public Instant getLastUpgradeAt() { return lastUpgradeAt; }
    public Map<String, Integer> getUpgradeLevels() { return Collections.unmodifiableMap(upgradeLevels); }
    public Map<String, String> getActiveSettings() { return Collections.unmodifiableMap(activeSettings); }
    public int getLevel(String upgradeId) { return upgradeLevels.getOrDefault(upgradeId, 0); }
    public String getSetting(String key) { return activeSettings.get(key); }

    public PlotProgressionState withUpgrade(PlotUpgradeDefinition definition,
                                            Instant upgradedAt,
                                            PlotProgressionTrack track,
                                            int gainedPoints,
                                            double spentCurrency) {
        Objects.requireNonNull(definition, "definition");
        Map<String, Integer> newLevels = new LinkedHashMap<>(upgradeLevels);
        newLevels.put(definition.getId(), Math.max(getLevel(definition.getId()), definition.getLevel()));
        Map<String, String> newSettings = new LinkedHashMap<>(activeSettings);
        newSettings.putAll(definition.getSettings());

        int nextPoints = progressionPoints + Math.max(0, gainedPoints);
        double nextSpent = lifetimeCurrencySpent + Math.max(0D, spentCurrency);
        int progressionValue = track.getMode() == PlotProgressionTrack.Mode.XP
                ? nextPoints
                : (int) Math.floor(nextSpent);
        int calculatedLevel = track.resolveLevel(progressionValue);

        return new PlotProgressionState(
                Math.max(overallLevel, calculatedLevel),
                nextPoints,
                prestigeLevel,
                nextSpent,
                upgradedAt,
                newLevels,
                newSettings
        );
    }

    public PlotProgressionState withPrestigeReset() {
        return new PlotProgressionState(1, 0, prestigeLevel + 1, lifetimeCurrencySpent, Instant.now(), Collections.emptyMap(), Collections.emptyMap());
    }
}
