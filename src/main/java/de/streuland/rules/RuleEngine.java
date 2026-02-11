package de.streuland.rules;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RuleEngine {
    private final PlotManager plotManager;
    private final PlotLevelProvider plotLevelProvider;
    private final List<RuleProvider> providers = new ArrayList<>();
    private final List<Rule> rules = new ArrayList<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public RuleEngine(PlotManager plotManager, PlotLevelProvider plotLevelProvider) {
        this.plotManager = plotManager;
        this.plotLevelProvider = plotLevelProvider;
    }

    public void registerProvider(RuleProvider provider) {
        providers.add(provider);
    }

    public void reload() {
        rules.clear();
        for (RuleProvider provider : providers) {
            Collection<Rule> loaded = provider.loadRules(this);
            if (loaded != null) {
                rules.addAll(loaded);
            }
        }
    }

    public void handle(RuleTrigger trigger, Event event, Player player, Location location) {
        Plot plot = location != null ? plotManager.getPlotAt(location.getBlockX(), location.getBlockZ()) : null;
        int plotLevel = plot != null ? plotLevelProvider.getPlotLevel(plot) : 0;
        Biome biome = location != null ? location.getBlock().getBiome() : null;
        RuleContext context = new RuleContext(trigger, event, player, plot, location, plotLevel, biome, this);
        for (Rule rule : rules) {
            if (rule.getTrigger() == trigger && rule.matches(context)) {
                rule.execute(context);
            }
        }
    }

    public boolean isOnCooldown(Player player, String key) {
        if (player == null) {
            return false;
        }
        Map<String, Long> playerCooldowns = cooldowns.get(player.getUniqueId());
        if (playerCooldowns == null) {
            return false;
        }
        Long expires = playerCooldowns.get(key);
        return expires != null && expires > System.currentTimeMillis();
    }

    public void setCooldown(Player player, String key, long durationMillis) {
        if (player == null) {
            return;
        }
        cooldowns.computeIfAbsent(player.getUniqueId(), id -> new ConcurrentHashMap<>())
                .put(key, System.currentTimeMillis() + durationMillis);
    }
}
