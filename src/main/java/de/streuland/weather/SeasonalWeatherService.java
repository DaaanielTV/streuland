package de.streuland.weather;

import de.streuland.analytics.PlotAnalyticsRecord;
import de.streuland.analytics.PlotAnalyticsService;
import org.bukkit.Bukkit;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SeasonalWeatherService {
    public enum Season {
        SPRING,
        SUMMER,
        FALL,
        WINTER;

        public Season next() {
            int idx = (ordinal() + 1) % values().length;
            return values()[idx];
        }
    }

    private final JavaPlugin plugin;
    private final PlotAnalyticsService analyticsService;
    private final Map<String, Object> progressCache = new ConcurrentHashMap<>();
    private final File cacheFile;

    private BukkitTask task;
    private Season activeSeason;
    private int dayInSeason;
    private long ticksInDay;
    private final int seasonDurationDays;
    private final long dayLengthTicks;

    public SeasonalWeatherService(JavaPlugin plugin, PlotAnalyticsService analyticsService) {
        this.plugin = plugin;
        this.analyticsService = analyticsService;
        this.seasonDurationDays = Math.max(1, plugin.getConfig().getInt("weather.season-duration-days", 7));
        this.dayLengthTicks = Math.max(1200L, plugin.getConfig().getLong("weather.day-length-ticks", 24000L));
        this.cacheFile = new File(plugin.getDataFolder(), "season-cache.yml");
        loadCache();
        updateProgressCache();
    }

    public void start() {
        stop();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 200L, 200L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        saveCache();
    }

    public Season getActiveSeason() {
        return activeSeason;
    }

    public int getDayInSeason() {
        return dayInSeason;
    }

    public int getSeasonDurationDays() {
        return seasonDurationDays;
    }

    public Map<String, Object> getProgressCache() {
        return Collections.unmodifiableMap(progressCache);
    }

    public List<String> getCurrentEffectsSummary() {
        List<String> lines = new ArrayList<>();
        switch (activeSeason) {
            case SPRING:
                lines.add("JUNGLE: +30% Ernte-Ertrag");
                lines.add("Pflanzen wachsen 20% schneller");
                break;
            case SUMMER:
                lines.add("DESERT: Geschwindigkeit I");
                lines.add("Pflanzen brauchen mehr Wasser");
                break;
            case FALL:
                lines.add("PLAINS: Reife Felder werden auto-geerntet");
                break;
            case WINTER:
                lines.add("SNOWY: Zerfall um 50% reduziert");
                lines.add("SNOWY: 30% weniger Mob-Spawns");
                break;
            default:
                break;
        }
        return lines;
    }

    public double getHarvestYieldMultiplier(Biome biome) {
        if (activeSeason == Season.SPRING && isJungle(biome)) {
            return plugin.getConfig().getDouble("weather.effects.spring.jungle-harvest-yield-multiplier", 1.3D);
        }
        return 1.0D;
    }

    public double getCropGrowthMultiplier() {
        if (activeSeason == Season.SPRING) {
            return plugin.getConfig().getDouble("weather.effects.spring.crop-growth-multiplier", 1.2D);
        }
        return 1.0D;
    }

    public double getSummerWaterNeedMultiplier() {
        if (activeSeason == Season.SUMMER) {
            return plugin.getConfig().getDouble("weather.effects.summer.crop-water-need-multiplier", 1.35D);
        }
        return 1.0D;
    }

    public boolean isAutoHarvestEnabled(Biome biome) {
        return activeSeason == Season.FALL && isPlains(biome);
    }

    public double getDecayRateMultiplier(Biome biome) {
        if (activeSeason == Season.WINTER && isSnowy(biome)) {
            return plugin.getConfig().getDouble("weather.effects.winter.snowy-decay-rate-multiplier", 0.5D);
        }
        return 1.0D;
    }

    public double getMobSpawnMultiplier(Biome biome) {
        if (activeSeason == Season.WINTER && isSnowy(biome)) {
            return plugin.getConfig().getDouble("weather.effects.winter.snowy-mob-spawn-multiplier", 0.7D);
        }
        return 1.0D;
    }

    private void tick() {
        ticksInDay += 200L;
        if (ticksInDay >= dayLengthTicks) {
            ticksInDay = 0L;
            advanceDay();
        }
        applySummerSpeedEffects();
    }

    private void applySummerSpeedEffects() {
        if (activeSeason != Season.SUMMER) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Biome biome = player.getLocation().getBlock().getBiome();
            if (isDesert(biome)) {
                int amplifier = Math.max(0, plugin.getConfig().getInt("weather.effects.summer.desert-speed-amplifier", 1) - 1);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 260, amplifier, true, false, true));
            }
        }
    }

    private void advanceDay() {
        dayInSeason++;
        analyticsService.record(new PlotAnalyticsRecord(null, null, "season_day_advanced", Instant.now(), dayInSeason));
        if (dayInSeason > seasonDurationDays) {
            activeSeason = activeSeason.next();
            dayInSeason = 1;
            Bukkit.broadcastMessage("§b[Wetter] Neue Saison: §e" + activeSeason.name());
            analyticsService.record(new PlotAnalyticsRecord(null, null, "season_changed_" + activeSeason.name().toLowerCase(Locale.ROOT), Instant.now(), 1.0));
        }
        Bukkit.broadcastMessage("§7[Wetter] Tag " + dayInSeason + "/" + seasonDurationDays + " in " + activeSeason.name());
        updateProgressCache();
        saveCache();
    }

    private void updateProgressCache() {
        progressCache.put("activeSeason", activeSeason.name());
        progressCache.put("dayInSeason", dayInSeason);
        progressCache.put("seasonDurationDays", seasonDurationDays);
        progressCache.put("ticksInDay", ticksInDay);
        progressCache.put("effects", getCurrentEffectsSummary());
    }

    private void loadCache() {
        activeSeason = Season.SPRING;
        dayInSeason = 1;
        ticksInDay = 0L;
        if (!cacheFile.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(cacheFile);
        String seasonName = yaml.getString("activeSeason", "SPRING");
        try {
            activeSeason = Season.valueOf(seasonName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            activeSeason = Season.SPRING;
        }
        dayInSeason = Math.max(1, yaml.getInt("dayInSeason", 1));
        ticksInDay = Math.max(0L, yaml.getLong("ticksInDay", 0L));
    }

    private void saveCache() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("activeSeason", activeSeason.name());
        yaml.set("dayInSeason", dayInSeason);
        yaml.set("ticksInDay", ticksInDay);
        yaml.set("seasonDurationDays", seasonDurationDays);
        try {
            yaml.save(cacheFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not persist seasonal cache: " + e.getMessage());
        }
    }

    private boolean isJungle(Biome biome) {
        return biome != null && biome.name().contains("JUNGLE");
    }

    private boolean isDesert(Biome biome) {
        return biome != null && biome.name().contains("DESERT");
    }

    private boolean isPlains(Biome biome) {
        return biome != null && biome.name().contains("PLAINS") && !isSnowy(biome);
    }

    private boolean isSnowy(Biome biome) {
        if (biome == null) {
            return false;
        }
        String name = biome.name();
        return name.contains("SNOW") || name.contains("ICE") || name.contains("FROZEN");
    }
}
