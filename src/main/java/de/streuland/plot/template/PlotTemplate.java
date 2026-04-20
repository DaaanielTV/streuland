package de.streuland.plot.template;

import org.bukkit.Material;

public class PlotTemplate {
    private final String id;
    private final String name;
    private final String description;
    private final Material icon;
    private final String schematicName;
    private final int cost;

    public PlotTemplate(String id, String name, String description, Material icon, String schematicName, int cost) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.schematicName = schematicName;
        this.cost = cost;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Material getIcon() {
        return icon;
    }

    public String getSchematicName() {
        return schematicName;
    }

    public int getCost() {
        return cost;
    }

    public boolean hasCost() {
        return cost > 0;
    }
}