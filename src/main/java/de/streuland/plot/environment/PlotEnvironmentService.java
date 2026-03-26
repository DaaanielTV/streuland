package de.streuland.plot.environment;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles per-plot biome and weather customization.
 */
public class PlotEnvironmentService {
    public static final String PERMISSION_MANAGE = "streuland.plot.environment.manage";

    public enum ChangeResult {
        SUCCESS,
        NO_PLOT,
        NOT_OWNER,
        MISSING_PERMISSION,
        UPGRADE_REQUIRED,
        BIOME_NOT_ALLOWED,
        INVALID_BIOME
    }

    private final PlotManager plotManager;
    private final Set<String> allowedBiomes;
    private final int protectedBorder;
    private final int minBiomeY;
    private final int maxBiomeY;
    private final int requiredUpgradeLevel;
    private final String requiredAbility;

    public PlotEnvironmentService(PlotManager plotManager, FileConfiguration config) {
        this.plotManager = plotManager;
        this.allowedBiomes = loadAllowedBiomes(config);
        this.protectedBorder = Math.max(0, config.getInt("plot.environment.protected-path-border", config.getInt("path.width", 4) / 2));
        this.minBiomeY = config.getInt("plot.environment.biome-apply-min-y", 0);
        this.maxBiomeY = Math.max(this.minBiomeY, config.getInt("plot.environment.biome-apply-max-y", 319));
        this.requiredUpgradeLevel = Math.max(0, config.getInt("plot.environment.minimum-upgrade-level", 0));
        this.requiredAbility = config.getString("plot.environment.required-ability", "").trim().toLowerCase(Locale.ROOT);
    }

    public Set<String> getAllowedBiomes() {
        return allowedBiomes;
    }

    public ChangeResult setBiome(Plot plot, UUID actor, boolean hasPermission, String biomeName) {
        ChangeResult access = validateAccess(plot, actor, hasPermission);
        if (access != ChangeResult.SUCCESS) {
            return access;
        }

        String normalized = biomeName == null ? "" : biomeName.trim().toUpperCase(Locale.ROOT);
        if (!allowedBiomes.isEmpty() && !allowedBiomes.contains(normalized)) {
            return ChangeResult.BIOME_NOT_ALLOWED;
        }

        final Biome biome;
        try {
            biome = Biome.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return ChangeResult.INVALID_BIOME;
        }

        World world = plotManager.getWorldForPlot(plot.getPlotId());
        int minX = plot.getMinX() + protectedBorder;
        int maxX = plot.getMaxX() - protectedBorder;
        int minZ = plot.getMinZ() + protectedBorder;
        int maxZ = plot.getMaxZ() - protectedBorder;

        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                for (int y = minBiomeY; y <= maxBiomeY; y++) {
                    world.setBiome(x, y, z, biome);
                }
            }
        }

        PlotData data = storage(world).getPlotData(plot.getPlotId());
        data.setSelectedBiome(normalized);
        storage(world).savePlotData(plot.getPlotId(), data);
        return ChangeResult.SUCCESS;
    }

    public ChangeResult lockWeather(Plot plot, UUID actor, boolean hasPermission, boolean locked) {
        ChangeResult access = validateAccess(plot, actor, hasPermission);
        if (access != ChangeResult.SUCCESS) {
            return access;
        }
        World world = plotManager.getWorldForPlot(plot.getPlotId());
        PlotStorage storage = storage(world);
        PlotData data = storage.getPlotData(plot.getPlotId());
        data.setWeatherLocked(locked);
        storage.savePlotData(plot.getPlotId(), data);
        return ChangeResult.SUCCESS;
    }

    public ChangeResult setCosmeticOption(Plot plot, UUID actor, boolean hasPermission, String key, String value, int maxLength) {
        ChangeResult access = validateAccess(plot, actor, hasPermission);
        if (access != ChangeResult.SUCCESS) {
            return access;
        }
        String normalizedKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        if (normalizedKey.isEmpty()) {
            return ChangeResult.INVALID_BIOME;
        }

        String normalizedValue = value == null ? "" : value.trim();
        if (normalizedValue.length() > maxLength) {
            normalizedValue = normalizedValue.substring(0, maxLength);
        }

        World world = plotManager.getWorldForPlot(plot.getPlotId());
        PlotStorage storage = storage(world);
        PlotData data = storage.getPlotData(plot.getPlotId());
        data.getEnvironmentCosmetics().put(normalizedKey, normalizedValue);
        storage.savePlotData(plot.getPlotId(), data);
        return ChangeResult.SUCCESS;
    }

    private ChangeResult validateAccess(Plot plot, UUID actor, boolean hasPermission) {
        if (plot == null || plot.getOwner() == null) {
            return ChangeResult.NO_PLOT;
        }
        if (!hasPermission) {
            return ChangeResult.MISSING_PERMISSION;
        }
        if (!plot.getOwner().equals(actor)) {
            return ChangeResult.NOT_OWNER;
        }
        if (!meetsUpgradeRequirement(plot)) {
            return ChangeResult.UPGRADE_REQUIRED;
        }
        return ChangeResult.SUCCESS;
    }

    private boolean meetsUpgradeRequirement(Plot plot) {
        if (requiredUpgradeLevel <= 0 && requiredAbility.isEmpty()) {
            return true;
        }
        PlotData data = storage(plotManager.getWorldForPlot(plot.getPlotId())).getPlotData(plot.getPlotId());
        if (requiredUpgradeLevel > 0) {
            double level = data.getStatBonuses().getOrDefault("plotLevel", 0D);
            if (level < requiredUpgradeLevel) {
                return false;
            }
        }
        return requiredAbility.isEmpty() || data.getUnlockedAbilities().contains(requiredAbility);
    }

    private PlotStorage storage(World world) {
        return plotManager.getStorage(world);
    }

    private static Set<String> loadAllowedBiomes(FileConfiguration config) {
        Set<String> raw = new LinkedHashSet<>(config.getStringList("plot.environment.biome-options"));
        if (raw.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String entry : raw) {
            if (entry != null && !entry.trim().isEmpty()) {
                normalized.add(entry.trim().toUpperCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(normalized);
    }
}
