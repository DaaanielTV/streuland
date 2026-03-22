package de.streuland.plot.upgrade;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Defines a modular plot upgrade option. */
public class PlotUpgradeDefinition {
    private final String id;
    private final PlotUpgradeType type;
    private final String displayName;
    private final int level;
    private final PlotUpgradeCost cost;
    private final List<PlotUpgradeRequirement> requirements;
    private final Map<String, String> settings;

    public PlotUpgradeDefinition(String id,
                                 PlotUpgradeType type,
                                 String displayName,
                                 int level,
                                 PlotUpgradeCost cost,
                                 List<PlotUpgradeRequirement> requirements,
                                 Map<String, String> settings) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.displayName = displayName == null ? id : displayName;
        this.level = Math.max(1, level);
        this.cost = Objects.requireNonNull(cost, "cost");
        this.requirements = requirements == null ? new ArrayList<>() : new ArrayList<>(requirements);
        this.settings = settings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(settings);
    }

    public String getId() { return id; }
    public PlotUpgradeType getType() { return type; }
    public String getDisplayName() { return displayName; }
    public int getLevel() { return level; }
    public PlotUpgradeCost getCost() { return cost; }
    public List<PlotUpgradeRequirement> getRequirements() { return Collections.unmodifiableList(requirements); }
    public Map<String, String> getSettings() { return Collections.unmodifiableMap(settings); }
}
