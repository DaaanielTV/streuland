package de.streuland.plot;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class PlotStoragePartitioner {
    private final File rootFolder;

    public PlotStoragePartitioner(JavaPlugin plugin) {
        this.rootFolder = new File(plugin.getDataFolder(), "plots");
        if (!rootFolder.exists()) {
            rootFolder.mkdirs();
        }
    }

    public File resolveWorldFolder(String worldName) {
        File worldFolder = new File(rootFolder, sanitize(worldName));
        if (!worldFolder.exists()) {
            worldFolder.mkdirs();
        }
        return worldFolder;
    }

    private String sanitize(String value) {
        return value == null ? "world_main" : value.toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
    }
}
