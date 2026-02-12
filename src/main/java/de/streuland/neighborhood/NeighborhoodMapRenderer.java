package de.streuland.neighborhood;

import de.streuland.plot.Plot;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collection;

/**
 * Renders visual links between connected neighborhood plots.
 */
public class NeighborhoodMapRenderer {
    private final JavaPlugin plugin;

    public NeighborhoodMapRenderer(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void renderConnections(World world, Collection<Plot> connectedPlots) {
        if (world == null || connectedPlots == null || connectedPlots.size() < 2) {
            return;
        }

        Plot[] plots = connectedPlots.toArray(new Plot[0]);
        for (int i = 0; i < plots.length; i++) {
            for (int j = i + 1; j < plots.length; j++) {
                Plot a = plots[i];
                Plot b = plots[j];
                drawGlowingLine(world, a.getCenterX(), a.getCenterZ(), b.getCenterX(), b.getCenterZ());
            }
        }
    }

    private void drawGlowingLine(World world, int x1, int z1, int x2, int z2) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        if (steps == 0) {
            return;
        }

        int y = world.getHighestBlockYAt(x1, z1) + 2;
        for (int step = 0; step <= steps; step += 6) {
            double t = (double) step / (double) steps;
            double px = x1 + (x2 - x1) * t;
            double pz = z1 + (z2 - z1) * t;
            Location location = new Location(world, px + 0.5, y, pz + 0.5);
            world.spawnParticle(Particle.REDSTONE, location, 2,
                    new Particle.DustOptions(Color.AQUA, 1.5F));
        }
        plugin.getLogger().fine("Rendered neighborhood link between " + x1 + "," + z1 + " and " + x2 + "," + z2);
    }
}
