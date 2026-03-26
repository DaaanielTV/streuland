package de.streuland.admin;

public class DiagnosticsIssue {
    private final DiagnosticsIssueType type;
    private final String worldName;
    private final String plotId;
    private final String details;

    public DiagnosticsIssue(DiagnosticsIssueType type, String worldName, String plotId, String details) {
        this.type = type;
        this.worldName = worldName;
        this.plotId = plotId;
        this.details = details;
    }

    public DiagnosticsIssueType getType() {
        return type;
    }

    public String getWorldName() {
        return worldName;
    }

    public String getPlotId() {
        return plotId;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return type + " world=" + worldName + " plot=" + plotId + " details=" + details;
    }
}
