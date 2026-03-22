package de.streuland.plot.upgrade;

import de.streuland.plot.PlotData;
import de.streuland.plot.PlotStorage;

import java.util.Optional;

public class PlotStorageBackedUpgradeStorage implements PlotUpgradeStorage {
    private final PlotStorage plotStorage;

    public PlotStorageBackedUpgradeStorage(PlotStorage plotStorage) {
        this.plotStorage = plotStorage;
    }

    @Override
    public Optional<PlotProgressionState> load(String plotId) {
        PlotData data = plotStorage.getPlotData(plotId);
        return Optional.ofNullable(data == null ? PlotProgressionState.initial() : data.getProgressionState());
    }

    @Override
    public void save(String plotId, PlotProgressionState state) {
        PlotData data = plotStorage.getPlotData(plotId);
        data.setProgressionState(state);
        plotStorage.savePlotData(plotId, data);
    }
}
