package de.streuland.plot.upgrade;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;

public final class DefaultPlotUpgradeCatalog {
    private DefaultPlotUpgradeCatalog() {}

    public static PlotUpgradeTree create() {
        PlotUpgradeTree tree = new PlotUpgradeTree();
        tree.addUpgrade(new PlotUpgradeDefinition("size_1", PlotUpgradeType.SIZE_EXPANSION, "Plot Size I", 1,
                new PlotUpgradeCost(200D, Collections.emptyMap(), Duration.ZERO), Collections.emptyList(), Collections.singletonMap("size", "96")));
        tree.addUpgrade(new PlotUpgradeDefinition("size_2", PlotUpgradeType.SIZE_EXPANSION, "Plot Size II", 2,
                new PlotUpgradeCost(500D, Collections.emptyMap(), Duration.ZERO), Arrays.asList(new PlotUpgradeRequirement("size_1", 1)), Collections.singletonMap("size", "128")));
        tree.addUpgrade(new PlotUpgradeDefinition("trusted_slots_1", PlotUpgradeType.TRUSTED_MEMBERS, "Trusted Slots I", 1,
                new PlotUpgradeCost(150D, Collections.emptyMap(), Duration.ZERO), Collections.emptyList(), Collections.singletonMap("trusted.max", "8")));
        tree.addUpgrade(new PlotUpgradeDefinition("trusted_slots_2", PlotUpgradeType.TRUSTED_MEMBERS, "Trusted Slots II", 2,
                new PlotUpgradeCost(325D, Collections.emptyMap(), Duration.ZERO), Arrays.asList(new PlotUpgradeRequirement("trusted_slots_1", 1)), Collections.singletonMap("trusted.max", "12")));
        tree.addUpgrade(new PlotUpgradeDefinition("cleanup_speed_1", PlotUpgradeType.AUTO_CLEANUP, "Auto Cleanup I", 1,
                new PlotUpgradeCost(180D, Collections.emptyMap(), Duration.ZERO), Collections.emptyList(), Collections.singletonMap("cleanup.interval-seconds", "600")));
        tree.addUpgrade(new PlotUpgradeDefinition("cleanup_speed_2", PlotUpgradeType.AUTO_CLEANUP, "Auto Cleanup II", 2,
                new PlotUpgradeCost(360D, Collections.emptyMap(), Duration.ZERO), Arrays.asList(new PlotUpgradeRequirement("cleanup_speed_1", 1)), Collections.singletonMap("cleanup.interval-seconds", "300")));
        tree.addUpgrade(new PlotUpgradeDefinition("title_glow_1", PlotUpgradeType.COSMETIC_TITLE_EFFECT, "Title Glow", 1,
                new PlotUpgradeCost(250D, Collections.emptyMap(), Duration.ZERO), Collections.emptyList(), Collections.singletonMap("title.effect", "GLOW")));
        return tree;
    }
}
