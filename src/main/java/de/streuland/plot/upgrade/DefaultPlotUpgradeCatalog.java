package de.streuland.plot.upgrade;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DefaultPlotUpgradeCatalog {
    private DefaultPlotUpgradeCatalog() {}

    public static PlotUpgradeTree create() {
        PlotUpgradeTree tree = new PlotUpgradeTree();
        tree.setProgressionTrack(PlotProgressionTrack.defaultCurrencyTrack());

        tree.addUpgrade(new PlotUpgradeDefinition("size_1", PlotUpgradeType.SIZE_EXPANSION, "Plot Size I", 1,
                1, 0, 30, new PlotUpgradeCost(200D, Collections.emptyMap(), Duration.ZERO), Collections.emptyList(), Collections.singletonMap("size", "96")));
        tree.addUpgrade(new PlotUpgradeDefinition("size_2", PlotUpgradeType.SIZE_EXPANSION, "Plot Size II", 2,
                2, 0, 60, new PlotUpgradeCost(500D, Collections.emptyMap(), Duration.ZERO), Arrays.asList(new PlotUpgradeRequirement("size_1", 1)), Collections.singletonMap("size", "128")));
        tree.addUpgrade(new PlotUpgradeDefinition("trusted_slots_1", PlotUpgradeType.TRUSTED_PLAYER_SLOTS, "Trusted Slots I", 1,
                1, 0, 20, new PlotUpgradeCost(150D, Collections.emptyMap(), Duration.ZERO), Collections.emptyList(), Collections.singletonMap("trusted.max", "8")));
        tree.addUpgrade(new PlotUpgradeDefinition("trusted_slots_2", PlotUpgradeType.TRUSTED_PLAYER_SLOTS, "Trusted Slots II", 2,
                2, 0, 35, new PlotUpgradeCost(325D, Collections.emptyMap(), Duration.ZERO), Arrays.asList(new PlotUpgradeRequirement("trusted_slots_1", 1)), Collections.singletonMap("trusted.max", "12")));
        tree.addUpgrade(new PlotUpgradeDefinition("cleanup_speed_1", PlotUpgradeType.UTILITY, "Auto Cleanup I", 1,
                1, 0, 20, new PlotUpgradeCost(180D, Collections.emptyMap(), Duration.ZERO), Collections.emptyList(), Collections.singletonMap("cleanup.interval-seconds", "600")));
        tree.addUpgrade(new PlotUpgradeDefinition("cleanup_speed_2", PlotUpgradeType.UTILITY, "Auto Cleanup II", 2,
                2, 0, 40, new PlotUpgradeCost(360D, Collections.emptyMap(), Duration.ZERO), Arrays.asList(new PlotUpgradeRequirement("cleanup_speed_1", 1)), Collections.singletonMap("cleanup.interval-seconds", "300")));
        tree.addUpgrade(new PlotUpgradeDefinition("biome_desert", PlotUpgradeType.BIOME_UNLOCK, "Biome Unlock: Desert", 1,
                2, 0, 15, new PlotUpgradeCost(120D, Collections.emptyMap(), Duration.ZERO), Collections.emptyList(), Collections.singletonMap("biome.unlock", "DESERT")));

        return tree;
    }
}
