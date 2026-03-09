package de.streuland.schematic;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads schematic templates from plugins/Streuland/schematics.
 *
 * Supported formats:
 * - simple JSON format with "blocks": [{"x":0,"y":0,"z":0,"type":"STONE"}]
 * - simple SNBT-like text snippets containing blocks:[{pos:[x,y,z],Name:"minecraft:stone"}]
 *
 * Binary WorldEdit .schem files are not fully interpreted here; if the file content is text SNBT it is parsed.
 */
public class SchematicLoader {
    private static final List<String> EXTENSIONS = Arrays.asList(".schem", ".snbt", ".json", ".schematic");
    private static final Pattern SNBT_BLOCK_PATTERN = Pattern.compile("\\{\\s*pos\\s*:\\s*\\[(-?\\d+),(-?\\d+),(-?\\d+)\\]\\s*,\\s*(?:state|Name)\\s*:\\s*([^}]+)\\}");
    private static final Pattern SNBT_PALETTE_PATTERN = Pattern.compile("palette\\s*:\\s*\\{([^}]*)}");
    private static final Pattern SNBT_PALETTE_ENTRY_PATTERN = Pattern.compile("([\\w:.]+)\\s*:\\s*(\\d+)");
    private final JavaPlugin plugin;
    private final Gson gson = new Gson();

    public SchematicLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Schematic load(String name) {
        File source = resolveTemplateFile(name);
        if (source == null) {
            throw new IllegalArgumentException("Template nicht gefunden: " + name);
        }
        try {
            String content = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);
            String ext = source.getName().toLowerCase(Locale.ROOT);
            if (ext.endsWith(".json")) {
                return parseJson(stripExtension(source.getName()), content);
            }
            return parseSnbt(stripExtension(source.getName()), content);
        } catch (IOException e) {
            throw new IllegalStateException("Fehler beim Laden von Template " + name, e);
        }
    }

    public List<String> listTemplates() {
        File dir = templatesDirectory();
        File[] files = dir.listFiles();
        List<String> names = new ArrayList<>();
        if (files == null) {
            return names;
        }
        for (File file : files) {
            String lower = file.getName().toLowerCase(Locale.ROOT);
            for (String extension : EXTENSIONS) {
                if (lower.endsWith(extension)) {
                    names.add(stripExtension(file.getName()));
                    break;
                }
            }
        }
        return names;
    }

    private Schematic parseJson(String name, String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray blocksJson = root.getAsJsonArray("blocks");
        List<Schematic.SchematicBlock> blocks = new ArrayList<>();
        if (blocksJson != null) {
            for (JsonElement element : blocksJson) {
                JsonObject block = element.getAsJsonObject();
                blocks.add(new Schematic.SchematicBlock(
                        block.get("x").getAsInt(),
                        block.get("y").getAsInt(),
                        block.get("z").getAsInt(),
                        parseMaterial(block.get("type").getAsString())
                ));
            }
        }
        return new Schematic(name, blocks);
    }

    private Schematic parseSnbt(String name, String snbt) {
        java.util.Map<Integer, Material> palette = new java.util.HashMap<>();
        Matcher paletteMatcher = SNBT_PALETTE_PATTERN.matcher(snbt);
        if (paletteMatcher.find()) {
            Matcher entryMatcher = SNBT_PALETTE_ENTRY_PATTERN.matcher(paletteMatcher.group(1));
            while (entryMatcher.find()) {
                palette.put(Integer.parseInt(entryMatcher.group(2)), parseMaterial(entryMatcher.group(1)));
            }
        }

        List<Schematic.SchematicBlock> blocks = new ArrayList<>();
        Matcher blockMatcher = SNBT_BLOCK_PATTERN.matcher(snbt.replace("\n", ""));
        while (blockMatcher.find()) {
            int x = Integer.parseInt(blockMatcher.group(1));
            int y = Integer.parseInt(blockMatcher.group(2));
            int z = Integer.parseInt(blockMatcher.group(3));
            String stateOrName = blockMatcher.group(4).replace("\"", "").trim();

            Material material;
            if (stateOrName.matches("\\d+")) {
                material = palette.getOrDefault(Integer.parseInt(stateOrName), Material.AIR);
            } else {
                material = parseMaterial(stateOrName);
            }
            blocks.add(new Schematic.SchematicBlock(x, y, z, material));
        }
        return new Schematic(name, blocks);
    }

    private Material parseMaterial(String raw) {
        String materialName = raw.replace("minecraft:", "").toUpperCase(Locale.ROOT);
        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            plugin.getLogger().warning("Unbekanntes Material in Schematic: " + raw + " -> AIR");
            return Material.AIR;
        }
        return material;
    }

    private File resolveTemplateFile(String name) {
        File dir = templatesDirectory();
        for (String extension : EXTENSIONS) {
            File candidate = new File(dir, name + extension);
            if (candidate.exists()) {
                return candidate;
            }
        }
        File direct = new File(dir, name);
        return direct.exists() ? direct : null;
    }

    private File templatesDirectory() {
        File dir = new File(plugin.getDataFolder(), "schematics");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }
}
