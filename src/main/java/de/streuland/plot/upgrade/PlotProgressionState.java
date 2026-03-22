package de.streuland.plot.upgrade;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Tracks all purchased upgrades and selected settings for a plot. */
public class PlotProgressionState {
    private final int overallLevel;
    private final Instant lastUpgradeAt;
    private final Map<String, Integer> upgradeLevels;
    private final Map<String, String> activeSettings;

    public PlotProgressionState(int overallLevel,
                                Instant lastUpgradeAt,
                                Map<String, Integer> upgradeLevels,
                                Map<String, String> activeSettings) {
        this.overallLevel = Math.max(0, overallLevel);
        this.lastUpgradeAt = lastUpgradeAt;
        this.upgradeLevels = upgradeLevels == null ? new LinkedHashMap<>() : new LinkedHashMap<>(upgradeLevels);
        this.activeSettings = activeSettings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(activeSettings);
    }

    public static PlotProgressionState initial() {
        return new PlotProgressionState(0, null, Collections.emptyMap(), Collections.emptyMap());
    }

    public int getOverallLevel() { return overallLevel; }
    public Instant getLastUpgradeAt() { return lastUpgradeAt; }
    public Map<String, Integer> getUpgradeLevels() { return Collections.unmodifiableMap(upgradeLevels); }
    public Map<String, String> getActiveSettings() { return Collections.unmodifiableMap(activeSettings); }
    public int getLevel(String upgradeId) { return upgradeLevels.getOrDefault(upgradeId, 0); }
    public String getSetting(String key) { return activeSettings.get(key); }

    public PlotProgressionState withUpgrade(PlotUpgradeDefinition definition, Instant upgradedAt) {
        Objects.requireNonNull(definition, "definition");
        Map<String, Integer> newLevels = new LinkedHashMap<>(upgradeLevels);
        newLevels.put(definition.getId(), definition.getLevel());
        Map<String, String> newSettings = new LinkedHashMap<>(activeSettings);
        newSettings.putAll(definition.getSettings());
        return new PlotProgressionState(Math.max(overallLevel, definition.getLevel()), upgradedAt, newLevels, newSettings);
    }
}
