package de.streuland.plot.upgrade;

import de.streuland.economy.PlotEconomyHook;
import net.milkbowl.vault.economy.Economy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlotUpgradeServiceTest {
    @Test
    void validatesRequirementsAndFundsBeforePurchase() {
        UUID owner = UUID.randomUUID();
        FakePlotEconomyHook economyHook = new FakePlotEconomyHook();
        economyHook.setBalance(owner, 500D);

        PlotUpgradeTree tree = new PlotUpgradeTree();
        tree.addUpgrade(new PlotUpgradeDefinition("size_1", PlotUpgradeType.SIZE_EXPANSION, "Size I", 1,
                new PlotUpgradeCost(200D, Collections.emptyMap(), Duration.ZERO), Collections.emptyList(), Collections.singletonMap("size", "96")));
        tree.addUpgrade(new PlotUpgradeDefinition("biome_desert", PlotUpgradeType.BIOME_SELECTION, "Desert", 1,
                new PlotUpgradeCost(50D, Collections.emptyMap(), Duration.ZERO), Arrays.asList(new PlotUpgradeRequirement("size_1", 1)), Collections.singletonMap("biome", "DESERT")));

        DefaultPlotUpgradeService service = new DefaultPlotUpgradeService(tree, new InMemoryPlotUpgradeStorage(), economyHook, (plotId, playerId) -> owner.equals(playerId));

        assertFalse(service.canUpgrade("plot-1", owner, "biome_desert"));
        assertTrue(service.canUpgrade("plot-1", owner, "size_1"));
        assertTrue(service.applyUpgrade("plot-1", owner, "size_1"));
        assertEquals(300D, economyHook.getBalance(owner));
        assertTrue(service.canUpgrade("plot-1", owner, "biome_desert"));
        assertTrue(service.applyUpgrade("plot-1", owner, "biome_desert"));

        PlotProgressionState state = service.getState("plot-1").orElseThrow(AssertionError::new);
        assertEquals(1, state.getLevel("size_1"));
        assertEquals(1, state.getLevel("biome_desert"));
        assertEquals("DESERT", state.getSetting("biome"));
    }

    @Test
    void rejectsNonOwnerAndInsufficientFunds() {
        UUID owner = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        FakePlotEconomyHook economyHook = new FakePlotEconomyHook();
        economyHook.setBalance(owner, 25D);

        PlotUpgradeTree tree = new PlotUpgradeTree();
        tree.addUpgrade(new PlotUpgradeDefinition("lights_1", PlotUpgradeType.LIGHTING_THEME, "Warm Lights", 1,
                new PlotUpgradeCost(50D, Collections.emptyMap(), Duration.ZERO), Collections.emptyList(), Collections.singletonMap("lighting", "WARM")));
        DefaultPlotUpgradeService service = new DefaultPlotUpgradeService(tree, new InMemoryPlotUpgradeStorage(), economyHook, (plotId, playerId) -> owner.equals(playerId));

        assertFalse(service.canUpgrade("plot-1", stranger, "lights_1"));
        assertFalse(service.canUpgrade("plot-1", owner, "lights_1"));
        assertFalse(service.applyUpgrade("plot-1", owner, "lights_1"));
    }

    private static final class FakePlotEconomyHook extends PlotEconomyHook {
        private final Map<UUID, Double> balances = new HashMap<>();

        private FakePlotEconomyHook() {
            super((Economy) null);
        }

        void setBalance(UUID playerId, double balance) {
            balances.put(playerId, balance);
        }

        @Override
        public boolean hasEconomy() {
            return true;
        }

        @Override
        public double getBalance(UUID uuid) {
            return balances.getOrDefault(uuid, 0D);
        }

        @Override
        public boolean withdraw(UUID uuid, double amount) {
            double current = getBalance(uuid);
            if (current < amount) {
                return false;
            }
            balances.put(uuid, current - amount);
            return true;
        }
    }
}
