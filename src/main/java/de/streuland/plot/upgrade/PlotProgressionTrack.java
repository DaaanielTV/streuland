package de.streuland.plot.upgrade;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/** Level thresholds for plot progression. */
public class PlotProgressionTrack {
    public enum Mode {
        XP,
        CURRENCY
    }

    private final Mode mode;
    private final TreeMap<Integer, Integer> levelThresholds;

    public PlotProgressionTrack(Mode mode, Map<Integer, Integer> levelThresholds) {
        this.mode = mode == null ? Mode.CURRENCY : mode;
        this.levelThresholds = new TreeMap<>();
        this.levelThresholds.put(1, 0);
        if (levelThresholds != null) {
            for (Map.Entry<Integer, Integer> entry : levelThresholds.entrySet()) {
                int level = Math.max(1, entry.getKey());
                int threshold = Math.max(0, entry.getValue());
                this.levelThresholds.put(level, threshold);
            }
        }
    }

    public static PlotProgressionTrack defaultCurrencyTrack() {
        Map<Integer, Integer> defaults = new LinkedHashMap<>();
        defaults.put(1, 0);
        defaults.put(2, 500);
        defaults.put(3, 1500);
        defaults.put(4, 3500);
        return new PlotProgressionTrack(Mode.CURRENCY, defaults);
    }

    public Mode getMode() {
        return mode;
    }

    public Map<Integer, Integer> getLevelThresholds() {
        return Collections.unmodifiableMap(levelThresholds);
    }

    public int resolveLevel(int progressionValue) {
        int value = Math.max(0, progressionValue);
        int level = 1;
        for (Map.Entry<Integer, Integer> entry : levelThresholds.entrySet()) {
            if (value >= entry.getValue()) {
                level = entry.getKey();
            }
        }
        return level;
    }

    public int getMaxLevel() {
        return levelThresholds.isEmpty() ? 1 : levelThresholds.lastKey();
    }
}
