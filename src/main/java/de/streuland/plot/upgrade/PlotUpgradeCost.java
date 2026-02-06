package de.streuland.plot.upgrade;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Defines the costs for a plot upgrade.
 */
public class PlotUpgradeCost {
    private final double vaultCost;
    private final Map<String, Integer> itemCosts;
    private final Duration cooldown;

    public PlotUpgradeCost(double vaultCost, Map<String, Integer> itemCosts, Duration cooldown) {
        this.vaultCost = vaultCost;
        this.itemCosts = itemCosts == null ? new HashMap<>() : new HashMap<>(itemCosts);
        this.cooldown = cooldown == null ? Duration.ZERO : cooldown;
    }

    public double getVaultCost() {
        return vaultCost;
    }

    public Map<String, Integer> getItemCosts() {
        return Collections.unmodifiableMap(itemCosts);
    }

    public Duration getCooldown() {
        return cooldown;
    }
}
