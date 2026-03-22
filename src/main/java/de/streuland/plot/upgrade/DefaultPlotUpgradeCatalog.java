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
        tree.addUpgrade(new PlotUpgradeDefinition("biome_desert", PlotUpgradeType.BIOME_SELECTION, "Desert Biome", 1,
                new PlotUpgradeCost(150D, Collections.emptyMap(), Duration.ZERO), Arrays.asList(new PlotUpgradeRequirement("size_1", 1)), Collections.singletonMap("biome", "DESERT")));
        tree.addUpgrade(new PlotUpgradeDefinition("border_quartz", PlotUpgradeType.BORDER_STYLE, "Quartz Border", 1,
                new PlotUpgradeCost(100D, Collections.emptyMap(), Duration.ZERO), Collections.emptyList(), Collections.singletonMap("borderStyle", "QUARTZ")));
        tree.addUpgrade(new PlotUpgradeDefinition("lighting_warm", PlotUpgradeType.LIGHTING_THEME, "Warm Lights", 1,
                new PlotUpgradeCost(75D, Collections.emptyMap(), Duration.ZERO), Collections.emptyList(), Collections.singletonMap("lighting", "WARM")));
        tree.addUpgrade(new PlotUpgradeDefinition("particles_end", PlotUpgradeType.PARTICLE_EFFECT, "End Aura", 1,
                new PlotUpgradeCost(120D, Collections.emptyMap(), Duration.ZERO), Collections.emptyList(), Collections.singletonMap("particleEffect", "END_ROD")));
        tree.addUpgrade(new PlotUpgradeDefinition("rules_safe", PlotUpgradeType.GAMEPLAY_RULE, "Peaceful Rules", 1,
                new PlotUpgradeCost(180D, Collections.emptyMap(), Duration.ZERO), Collections.emptyList(), Collections.singletonMap("rules.mobGriefing", "false")));
        return tree;
    }
}
