package de.streuland.plot.upgrade;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class InMemoryPlotUpgradeStorage implements PlotUpgradeStorage {
    private final Map<String, PlotProgressionState> states = new HashMap<>();
    public Optional<PlotProgressionState> load(String plotId) { return Optional.ofNullable(states.get(plotId)); }
    public void save(String plotId, PlotProgressionState state) { states.put(plotId, state); }
}
