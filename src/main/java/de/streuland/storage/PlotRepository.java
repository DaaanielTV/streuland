package de.streuland.storage;

import de.streuland.plot.Plot;

import java.util.Collection;
import java.util.Optional;

/**
 * Storage abstraction for plot persistence.
 */
public interface PlotRepository {
    Optional<Plot> findById(String plotId);

    Collection<Plot> findAll();

    void save(Plot plot);
}
