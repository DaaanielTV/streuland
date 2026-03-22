package de.streuland.plot.upgrade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class PlotUpgradeTree {
    private final Map<String, PlotUpgradeDefinition> upgrades = new HashMap<>();

    public void addUpgrade(PlotUpgradeDefinition definition) { upgrades.put(definition.getId(), definition); }
    public Optional<PlotUpgradeDefinition> getUpgrade(String id) { return Optional.ofNullable(upgrades.get(id)); }
    public Collection<PlotUpgradeDefinition> getAllUpgrades() { return Collections.unmodifiableCollection(upgrades.values()); }
    public List<PlotUpgradeDefinition> getAvailableOrder() {
        return upgrades.values().stream()
                .sorted(Comparator.comparing(PlotUpgradeDefinition::getType).thenComparingInt(PlotUpgradeDefinition::getLevel))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
