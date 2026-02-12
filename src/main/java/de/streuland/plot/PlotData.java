package de.streuland.plot;

import de.streuland.plot.skin.PlotTheme;

/**
 * Mutable metadata for plot customizations.
 */
public class PlotData {
    private PlotTheme theme;

    public PlotData() {
        this(PlotTheme.NATURE);
    }

    public PlotData(PlotTheme theme) {
        this.theme = theme == null ? PlotTheme.NATURE : theme;
    }

    public PlotTheme getTheme() {
        return theme;
    }

    public void setTheme(PlotTheme theme) {
        this.theme = theme == null ? PlotTheme.NATURE : theme;
    }
}
