package de.streuland.weather;

import de.streuland.analytics.PlotAnalyticsRecord;
import de.streuland.analytics.PlotAnalyticsService;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.MoistureChangeEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class SeasonalEffectListener implements Listener {
    private final SeasonalWeatherService seasonalWeatherService;
    private final PlotAnalyticsService analyticsService;

    public SeasonalEffectListener(SeasonalWeatherService seasonalWeatherService, PlotAnalyticsService analyticsService) {
        this.seasonalWeatherService = seasonalWeatherService;
        this.analyticsService = analyticsService;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCropBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (!isCropMaterial(type)) {
            return;
        }
        Biome biome = block.getBiome();
        double multiplier = seasonalWeatherService.getHarvestYieldMultiplier(biome);
        if (multiplier <= 1.0D) {
            return;
        }
        int extraDrops = (int) Math.floor(multiplier - 1.0D);
        double chance = (multiplier - 1.0D) - extraDrops;
        extraDrops += ThreadLocalRandom.current().nextDouble() < chance ? 1 : 0;
        if (extraDrops > 0) {
            block.getWorld().dropItemNaturally(block.getLocation(), new org.bukkit.inventory.ItemStack(type, extraDrops));
            analyticsService.record(new PlotAnalyticsRecord(null, event.getPlayer().getUniqueId(), "seasonal_harvest_bonus", Instant.now(), extraDrops));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCropGrow(BlockGrowEvent event) {
        BlockData data = event.getNewState().getBlockData();
        if (!(data instanceof Ageable)) {
            return;
        }
        double multiplier = seasonalWeatherService.getCropGrowthMultiplier();
        if (multiplier <= 1.0D) {
            return;
        }
        Ageable ageable = (Ageable) data;
        int boost = (int) Math.floor((multiplier - 1.0D) * 2.0D);
        if (boost <= 0) {
            boost = ThreadLocalRandom.current().nextDouble() < (multiplier - 1.0D) ? 1 : 0;
        }
        if (boost > 0) {
            ageable.setAge(Math.min(ageable.getMaximumAge(), ageable.getAge() + boost));
            event.getNewState().setBlockData(ageable);
            analyticsService.record(new PlotAnalyticsRecord(null, null, "seasonal_crop_growth_boost", Instant.now(), boost));
        }

        if (seasonalWeatherService.isAutoHarvestEnabled(event.getBlock().getBiome()) && ageable.getAge() >= ageable.getMaximumAge()) {
            Material cropType = event.getNewState().getType();
            Block block = event.getBlock();
            block.getWorld().dropItemNaturally(block.getLocation(), new org.bukkit.inventory.ItemStack(cropType, 1));
            ageable.setAge(0);
            event.getNewState().setBlockData(ageable);
            analyticsService.record(new PlotAnalyticsRecord(null, null, "seasonal_auto_harvest", Instant.now(), 1.0));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMoistureChange(MoistureChangeEvent event) {
        double waterNeedMultiplier = seasonalWeatherService.getSummerWaterNeedMultiplier();
        if (waterNeedMultiplier <= 1.0D) {
            return;
        }
        if (event.getBlock().getType() != Material.FARMLAND) {
            return;
        }
        BlockData newData = event.getNewState().getBlockData();
        if (!(newData instanceof org.bukkit.block.data.type.Farmland)) {
            return;
        }
        org.bukkit.block.data.type.Farmland farmland = (org.bukkit.block.data.type.Farmland) newData;
        double extraDryChance = Math.min(0.9D, 0.2D + ((waterNeedMultiplier - 1.0D) * 0.4D));
        if (farmland.getMoisture() > 0 && ThreadLocalRandom.current().nextDouble() < extraDryChance) {
            farmland.setMoisture(Math.max(0, farmland.getMoisture() - 1));
            event.getNewState().setBlockData(farmland);
            analyticsService.record(new PlotAnalyticsRecord(null, null, "seasonal_water_penalty", Instant.now(), 1.0));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        double decayMultiplier = seasonalWeatherService.getDecayRateMultiplier(event.getBlock().getBiome());
        if (decayMultiplier >= 1.0D) {
            return;
        }
        double cancelChance = 1.0D - decayMultiplier;
        if (ThreadLocalRandom.current().nextDouble() < cancelChance) {
            event.setCancelled(true);
            analyticsService.record(new PlotAnalyticsRecord(null, null, "seasonal_decay_prevented", Instant.now(), 1.0));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Biome biome = event.getLocation().getBlock().getBiome();
        double spawnMultiplier = seasonalWeatherService.getMobSpawnMultiplier(biome);
        if (spawnMultiplier >= 1.0D) {
            return;
        }
        double cancelChance = 1.0D - spawnMultiplier;
        if (ThreadLocalRandom.current().nextDouble() < cancelChance) {
            event.setCancelled(true);
            analyticsService.record(new PlotAnalyticsRecord(null, null, "seasonal_spawn_reduced_" + biome.name().toLowerCase(Locale.ROOT), Instant.now(), 1.0));
        }
    }

    private boolean isCropMaterial(Material material) {
        String name = material.name();
        return name.contains("WHEAT") || name.contains("CARROT") || name.contains("POTATO") || name.contains("BEETROOT") || name.contains("NETHER_WART");
    }
}
