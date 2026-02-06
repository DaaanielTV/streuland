package de.streuland.api.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired when a biome effect is applied to a plot.
 */
public class PlotBiomeEffectAppliedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final String plotId;
    private final String biomeKey;
    private final String effectId;

    public PlotBiomeEffectAppliedEvent(String plotId, String biomeKey, String effectId) {
        this.plotId = plotId;
        this.biomeKey = biomeKey;
        this.effectId = effectId;
    }

    public String getPlotId() {
        return plotId;
    }

    public String getBiomeKey() {
        return biomeKey;
    }

    public String getEffectId() {
        return effectId;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
