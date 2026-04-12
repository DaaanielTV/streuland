package de.streuland.plot.upgrade;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlotUpgradePersistenceTest {
    @Test
    void serializesAndRestoresUpgradeState() {
        Map<String, Integer> levels = new LinkedHashMap<>();
        levels.put("size_1", 1);
        levels.put("border_quartz", 2);
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("borderStyle", "QUARTZ");
        settings.put("rules.mobGriefing", "false");
        PlotProgressionState original = new PlotProgressionState(2, 140, 1, 1234D, Instant.ofEpochMilli(123456789L), levels, settings);

        Map<String, Object> serialized = PlotUpgradePersistence.serialize(original);
        PlotProgressionState restored = PlotUpgradePersistence.deserialize(serialized);

        assertEquals(2, restored.getOverallLevel());
        assertEquals(140, restored.getProgressionPoints());
        assertEquals(1, restored.getPrestigeLevel());
        assertEquals(1234D, restored.getLifetimeCurrencySpent());
        assertEquals(Instant.ofEpochMilli(123456789L), restored.getLastUpgradeAt());
        assertEquals(1, restored.getLevel("size_1"));
        assertEquals(2, restored.getLevel("border_quartz"));
        assertEquals("QUARTZ", restored.getSetting("borderStyle"));
        assertEquals("false", restored.getSetting("rules.mobGriefing"));
    }

    @Test
    void migratesMissingDataToLevelOneDefaults() {
        PlotProgressionState restored = PlotUpgradePersistence.deserialize(new LinkedHashMap<>());
        assertEquals(1, restored.getOverallLevel());
        assertEquals(0, restored.getPrestigeLevel());
    }
}
