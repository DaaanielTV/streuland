package de.streuland.api;

import de.streuland.plot.Plot;
import de.streuland.plot.biome.BiomeProfile;
import de.streuland.plot.upgrade.PlotProgressionState;

import java.util.Optional;

/**
 * Public API entry point for addons and extensions.
 */
public interface PlotApi {
    Optional<Plot> getPlot(String plotId);

    Optional<BiomeProfile> getBiomeProfile(String plotId);

    Optional<PlotProgressionState> getProgressionState(String plotId);
}
