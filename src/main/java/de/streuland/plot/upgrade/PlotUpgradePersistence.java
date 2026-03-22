package de.streuland.plot.upgrade;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PlotUpgradePersistence {
    private PlotUpgradePersistence() {}

    public static Map<String, Object> serialize(PlotProgressionState state) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("overallLevel", state.getOverallLevel());
        root.put("lastUpgradeAt", state.getLastUpgradeAt() == null ? null : state.getLastUpgradeAt().toEpochMilli());
        root.put("upgradeLevels", new LinkedHashMap<>(state.getUpgradeLevels()));
        root.put("settings", new LinkedHashMap<>(state.getActiveSettings()));
        return root;
    }

    @SuppressWarnings("unchecked")
    public static PlotProgressionState deserialize(Map<String, Object> root) {
        if (root == null || root.isEmpty()) {
            return PlotProgressionState.initial();
        }
        int overallLevel = ((Number) root.getOrDefault("overallLevel", 0)).intValue();
        Object lastUpgradeAtValue = root.get("lastUpgradeAt");
        Instant lastUpgradeAt = lastUpgradeAtValue == null ? null : Instant.ofEpochMilli(((Number) lastUpgradeAtValue).longValue());
        Map<String, Integer> levels = new LinkedHashMap<>();
        Object upgradeLevels = root.get("upgradeLevels");
        if (upgradeLevels instanceof Map) {
            ((Map<String, Object>) upgradeLevels).forEach((key, value) -> levels.put(key, ((Number) value).intValue()));
        }
        Map<String, String> settings = new LinkedHashMap<>();
        Object settingsValue = root.get("settings");
        if (settingsValue instanceof Map) {
            ((Map<String, Object>) settingsValue).forEach((key, value) -> settings.put(key, String.valueOf(value)));
        }
        return new PlotProgressionState(overallLevel, lastUpgradeAt, levels, settings);
    }
}
