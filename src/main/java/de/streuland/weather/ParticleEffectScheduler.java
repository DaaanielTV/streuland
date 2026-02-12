package de.streuland.weather;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class ParticleEffectScheduler {
    private final JavaPlugin plugin;
    private final SeasonalWeatherService seasonalWeatherService;
    private BukkitTask task;

    public ParticleEffectScheduler(JavaPlugin plugin, SeasonalWeatherService seasonalWeatherService) {
        this.plugin = plugin;
        this.seasonalWeatherService = seasonalWeatherService;
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 60L, 40L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        SeasonalWeatherService.Season season = seasonalWeatherService.getActiveSeason();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Location loc = player.getLocation().add(0, 1.1, 0);
            switch (season) {
                case SPRING:
                    player.getWorld().spawnParticle(
                            Particle.REDSTONE,
                            loc,
                            8,
                            0.8,
                            0.4,
                            0.8,
                            0,
                            new Particle.DustOptions(Color.fromRGB(70, 200, 70), 1.1F)
                    );
                    break;
                case FALL:
                    player.getWorld().spawnParticle(
                            Particle.REDSTONE,
                            loc,
                            8,
                            0.8,
                            0.4,
                            0.8,
                            0,
                            new Particle.DustOptions(Color.fromRGB(255, 140, 40), 1.1F)
                    );
                    break;
                case WINTER:
                    player.getWorld().spawnParticle(Particle.CLOUD, loc, 6, 0.7, 0.5, 0.7, 0.01);
                    break;
                default:
                    break;
            }
        }
    }
}
