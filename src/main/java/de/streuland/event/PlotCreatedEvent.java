package de.streuland.event;

import de.streuland.plot.Plot;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * Fired after a plot has been created and stored.
 */
public class PlotCreatedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Plot plot;

    public PlotCreatedEvent(Plot plot) {
        this.plot = plot;
    }

    public Plot getPlot() {
        return plot;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
