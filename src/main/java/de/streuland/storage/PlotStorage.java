package de.streuland.storage;

import de.streuland.plot.Plot;

import java.util.Collection;
import java.util.Optional;

public interface PlotStorage {
    void save(Plot plot);

    void delete(String id);

    Optional<Plot> findById(String id);

    Optional<Plot> findNearest(String world, int x, int z, int radius);

    Collection<Plot> listAll();
}
