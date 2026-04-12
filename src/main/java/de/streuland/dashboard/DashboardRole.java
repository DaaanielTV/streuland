package de.streuland.dashboard;

public enum DashboardRole {
    VIEWER,
    OPERATOR,
    ADMIN;

    public boolean allows(DashboardRole required) {
        return this.ordinal() >= required.ordinal();
    }
}
