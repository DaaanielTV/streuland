package de.streuland.listener;

import de.streuland.plot.Permission;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;

import java.util.UUID;

public final class PlotAccessController {
    private final PlotManager plotManager;

    public PlotAccessController(PlotManager plotManager) {
        this.plotManager = plotManager;
    }

    public boolean canBuild(Plot plot, UUID playerId) {
        return plot != null && plotManager.hasPermission(plot, playerId, Permission.BUILD);
    }

    public boolean canBreak(Plot plot, UUID playerId) {
        return plot != null && plotManager.hasPermission(plot, playerId, Permission.BREAK);
    }

    public boolean canInteract(Plot plot, UUID playerId, Permission permission) {
        return plot != null && plotManager.hasPermission(plot, playerId, permission);
    }
}
