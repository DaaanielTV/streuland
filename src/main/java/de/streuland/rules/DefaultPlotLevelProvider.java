package de.streuland.rules;

import de.streuland.plot.Plot;

public class DefaultPlotLevelProvider implements PlotLevelProvider {
    @Override
    public int getPlotLevel(Plot plot) {
        return 0;
    }
}
