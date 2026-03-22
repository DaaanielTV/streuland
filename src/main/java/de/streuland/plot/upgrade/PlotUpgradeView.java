package de.streuland.plot.upgrade;

public class PlotUpgradeView {
    private final PlotUpgradeDefinition definition;
    private final boolean available;
    private final String reason;

    public PlotUpgradeView(PlotUpgradeDefinition definition, boolean available, String reason) {
        this.definition = definition;
        this.available = available;
        this.reason = reason;
    }

    public PlotUpgradeDefinition getDefinition() { return definition; }
    public boolean isAvailable() { return available; }
    public String getReason() { return reason; }
}
