package de.streuland.plot.snapshot;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;

public class PlotSnapshotMetadata {
    private final Plot plot;
    private final PlotData plotData;
    private final String authorName;
    private final String note;

    public PlotSnapshotMetadata(Plot plot, PlotData plotData, String authorName, String note) {
        this.plot = plot;
        this.plotData = plotData;
        this.authorName = authorName;
        this.note = note;
    }

    public Plot getPlot() {
        return plot;
    }

    public PlotData getPlotData() {
        return plotData;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getNote() {
        return note;
    }
}
