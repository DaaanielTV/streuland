package de.streuland.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a plot biome is detected or refreshed.
 */
public class PlotBiomeDetectedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String plotId;
    private final String biomeKey;

    public PlotBiomeDetectedEvent(String plotId, String biomeKey) {
        this.plotId = plotId;
        this.biomeKey = biomeKey;
    }

    public String getPlotId() {
        return plotId;
    }

    public String getBiomeKey() {
        return biomeKey;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
