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
    private final int requiredPlotLevel;
    private final int requiredPrestigeLevel;
    private final int xpReward;
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
        this(id, type, displayName, level, 1, 0, 0, cost, requirements, settings);
    }

    public PlotUpgradeDefinition(String id,
                                 PlotUpgradeType type,
                                 String displayName,
                                 int level,
                                 int requiredPlotLevel,
                                 int requiredPrestigeLevel,
                                 int xpReward,
                                 PlotUpgradeCost cost,
                                 List<PlotUpgradeRequirement> requirements,
                                 Map<String, String> settings) {
        this.id = Objects.requireNonNull(id, "id");
        this.type = Objects.requireNonNull(type, "type");
        this.displayName = displayName == null ? id : displayName;
        this.level = Math.max(1, level);
        this.requiredPlotLevel = Math.max(1, requiredPlotLevel);
        this.requiredPrestigeLevel = Math.max(0, requiredPrestigeLevel);
        this.xpReward = Math.max(0, xpReward);
        this.cost = Objects.requireNonNull(cost, "cost");
        this.requirements = requirements == null ? new ArrayList<>() : new ArrayList<>(requirements);
        this.settings = settings == null ? new LinkedHashMap<>() : new LinkedHashMap<>(settings);
    }

    public String getId() { return id; }
    public PlotUpgradeType getType() { return type; }
    public String getDisplayName() { return displayName; }
    public int getLevel() { return level; }
    public int getRequiredPlotLevel() { return requiredPlotLevel; }
    public int getRequiredPrestigeLevel() { return requiredPrestigeLevel; }
    public int getXpReward() { return xpReward; }
    public PlotUpgradeCost getCost() { return cost; }
    public List<PlotUpgradeRequirement> getRequirements() { return Collections.unmodifiableList(requirements); }
    public Map<String, String> getSettings() { return Collections.unmodifiableMap(settings); }
}
