package de.streuland.rules;

import de.streuland.plot.Plot;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

public class RuleContext {
    private final RuleTrigger trigger;
    private final Event event;
    private final Player player;
    private final Plot plot;
    private final Location location;
    private final int plotLevel;
    private final Biome biome;
    private final RuleEngine engine;

    public RuleContext(RuleTrigger trigger, Event event, Player player, Plot plot, Location location,
                       int plotLevel, Biome biome, RuleEngine engine) {
        this.trigger = trigger;
        this.event = event;
        this.player = player;
        this.plot = plot;
        this.location = location;
        this.plotLevel = plotLevel;
        this.biome = biome;
        this.engine = engine;
    }

    public RuleTrigger getTrigger() {
        return trigger;
    }

    public Event getEvent() {
        return event;
    }

    public Player getPlayer() {
        return player;
    }

    public Plot getPlot() {
        return plot;
    }

    public Location getLocation() {
        return location;
    }

    public int getPlotLevel() {
        return plotLevel;
    }

    public Biome getBiome() {
        return biome;
    }

    public RuleEngine getEngine() {
        return engine;
    }
}
