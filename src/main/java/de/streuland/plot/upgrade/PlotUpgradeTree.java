package de.streuland.plot.upgrade;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory representation of all available upgrades.
 */
public class PlotUpgradeTree {
    private final Map<String, PlotUpgradeDefinition> upgrades = new HashMap<>();

    public void addUpgrade(PlotUpgradeDefinition definition) {
        upgrades.put(definition.getId(), definition);
    }

    public Optional<PlotUpgradeDefinition> getUpgrade(String id) {
        return Optional.ofNullable(upgrades.get(id));
    }

    public Collection<PlotUpgradeDefinition> getAllUpgrades() {
        return upgrades.values();
    }
}
