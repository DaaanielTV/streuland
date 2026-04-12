package de.streuland.plot.upgrade;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlPlotUpgradeCatalogTest {
    @Test
    void loadsPricingRequirementsAndProgressionFromYaml() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("progression.mode", "xp");
        yaml.set("progression.levels.1", 0);
        yaml.set("progression.levels.2", 100);

        yaml.set("upgrades.size_1.type", "SIZE_EXPANSION");
        yaml.set("upgrades.size_1.name", "Plot Size I");
        yaml.set("upgrades.size_1.level", 1);
        yaml.set("upgrades.size_1.reward.xp", 40);
        yaml.set("upgrades.size_1.cost.vault", 200D);

        yaml.set("upgrades.size_2.type", "SIZE_EXPANSION");
        yaml.set("upgrades.size_2.level", 2);
        yaml.set("upgrades.size_2.required-plot-level", 2);
        yaml.set("upgrades.size_2.cost.vault", 500D);
        yaml.set("upgrades.size_2.requirements", java.util.Collections.singletonList(java.util.Collections.singletonMap("id", "size_1")));

        PlotUpgradeTree tree = YamlPlotUpgradeCatalog.load(yaml);

        assertEquals(PlotProgressionTrack.Mode.XP, tree.getProgressionTrack().getMode());
        assertEquals(2, tree.getProgressionTrack().resolveLevel(100));
        assertTrue(tree.getUpgrade("size_1").isPresent());
        assertEquals(200D, tree.getUpgrade("size_1").get().getCost().getVaultCost());
        assertEquals(40, tree.getUpgrade("size_1").get().getXpReward());
        assertEquals(500D, tree.getUpgrade("size_2").get().getCost().getVaultCost());
        assertEquals("size_1", tree.getUpgrade("size_2").get().getRequirements().get(0).getUpgradeId());
    }
}
