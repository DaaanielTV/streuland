package de.streuland.plot.template;

import de.streuland.schematic.Schematic;
import de.streuland.schematic.SchematicLoader;
import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlotTemplateRegistry {
    private final SchematicLoader schematicLoader;
    private final Map<String, PlotTemplate> templates = new ConcurrentHashMap<>();

    public PlotTemplateRegistry(SchematicLoader schematicLoader) {
        this.schematicLoader = schematicLoader;
    }

    public void register(PlotTemplate template) {
        templates.put(template.getId(), template);
    }

    public void registerDefaults() {
        register(new PlotTemplate("basic_house", "Einfaches Haus", "Ein einfaches Wohnhaus mit Keller",
                Material.WHITE_CONCRETE, "basic_house", 0));
        register(new PlotTemplate("farm", "Bauernhof", "Funktionaler Bauernhof mit Feldern",
                Material.WHEAT, "farm", 0));
        register(new PlotTemplate("garden", "Garten", "Dekorativer Garten mit Teich",
                Material.OAK_SAPLING, "garden", 0));
        register(new PlotTemplate("shop", "Laden", "Kleiner Laden mit Tresen",
                Material.CHEST, "shop", 500));
        register(new PlotTemplate("arena", "Arena", "Kampfarena für PvP",
                Material.REDSTONE_BLOCK, "arena", 1000));
    }

    public PlotTemplate get(String id) {
        return templates.get(id);
    }

    public List<PlotTemplate> list() {
        return new ArrayList<>(templates.values());
    }

    public boolean exists(String id) {
        return templates.containsKey(id);
    }

    public PlotTemplate getBySchematic(String schematicName) {
        for (PlotTemplate template : templates.values()) {
            if (template.getSchematicName().equalsIgnoreCase(schematicName)) {
                return template;
            }
        }
        return null;
    }

    public Schematic loadSchematic(PlotTemplate template) {
        try {
            return schematicLoader.load(template.getSchematicName());
        } catch (Exception e) {
            return null;
        }
    }
}