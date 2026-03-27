package de.streuland.approval;

public enum PlotApprovalActionType {
    CREATE,
    DELETE,
    UNCLAIM,
    RESIZE,
    TRANSFER;

    public String configKey() {
        return name().toLowerCase();
    }
}
