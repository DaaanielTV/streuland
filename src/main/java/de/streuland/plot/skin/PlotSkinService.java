package de.streuland.plot.skin;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;
import de.streuland.plot.PlotStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Manages visual plot skins with animated border particles.
 */
public class PlotSkinService {
    private final JavaPlugin plugin;
    private final PlotStorage plotStorage;
    private BukkitTask borderTask;
    private int borderPhase;

    public PlotSkinService(JavaPlugin plugin, PlotStorage plotStorage) {
        this.plugin = plugin;
        this.plotStorage = plotStorage;
    }

    public PlotTheme getTheme(Plot plot) {
        PlotData data = plotStorage.getPlotData(plot.getPlotId());
        return data.getTheme();
    }

    public void setTheme(Plot plot, PlotTheme theme) {
        PlotData data = plotStorage.getPlotData(plot.getPlotId());
        data.setTheme(theme);
        plotStorage.savePlotData(plot.getPlotId(), data);
    }

    public void start() {
        if (borderTask != null) {
            borderTask.cancel();
        }
        borderTask = Bukkit.getScheduler().runTaskTimer(plugin, this::animateBorders, 40L, 20L);
    }

    public void stop() {
        if (borderTask != null) {
            borderTask.cancel();
            borderTask = null;
        }
    }

    private void animateBorders() {
        borderPhase++;
        for (Plot plot : plotStorage.getAllPlots()) {
            PlotTheme theme = getTheme(plot);
            World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (world == null) {
                return;
            }
            int y = plot.getSpawnY() + 1;
            int minX = plot.getMinX();
            int maxX = plot.getMaxX() - 1;
            int minZ = plot.getMinZ();
            int maxZ = plot.getMaxZ() - 1;
            int step = 6;
            for (int i = 0; i <= (maxX - minX); i += step) {
                spawnForNearbyPlayers(world, theme, minX + i, y, minZ);
                spawnForNearbyPlayers(world, theme, minX + i, y, maxZ);
            }
            for (int i = 0; i <= (maxZ - minZ); i += step) {
                spawnForNearbyPlayers(world, theme, minX, y, minZ + i);
                spawnForNearbyPlayers(world, theme, maxX, y, minZ + i);
            }
        }
    }

    private void spawnForNearbyPlayers(World world, PlotTheme theme, int x, int y, int z) {
        Location loc = new Location(world, x + (borderPhase % 2 == 0 ? 0.25 : 0.75), y, z + 0.5);
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(loc) <= 64 * 64) {
                player.spawnParticle(theme.getBorderParticle(), loc, 2, 0.12, 0.12, 0.12, 0.01);
            }
        }
    }
}
