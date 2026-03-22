package de.streuland.plot.upgrade;

import de.streuland.economy.PlotEconomyHook;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class DefaultPlotUpgradeService implements PlotUpgradeService {
    private final PlotUpgradeTree tree;
    private final PlotUpgradeStorage storage;
    private final PlotEconomyHook economyHook;
    private final PlotOwnershipResolver ownershipResolver;

    public DefaultPlotUpgradeService(PlotUpgradeTree tree,
                                     PlotUpgradeStorage storage,
                                     PlotEconomyHook economyHook,
                                     PlotOwnershipResolver ownershipResolver) {
        this.tree = tree;
        this.storage = storage;
        this.economyHook = economyHook;
        this.ownershipResolver = ownershipResolver;
    }

    @Override
    public Optional<PlotProgressionState> getState(String plotId) {
        return storage.load(plotId).isPresent() ? storage.load(plotId) : Optional.of(PlotProgressionState.initial());
    }

    @Override
    public List<PlotUpgradeView> getAvailableUpgrades(String plotId, UUID playerId) {
        PlotProgressionState state = getState(plotId).orElse(PlotProgressionState.initial());
        List<PlotUpgradeView> views = new ArrayList<>();
        for (PlotUpgradeDefinition definition : tree.getAvailableOrder()) {
            String reason = validate(plotId, playerId, definition, state);
            views.add(new PlotUpgradeView(definition, reason == null, reason));
        }
        return views;
    }

    @Override
    public boolean canUpgrade(String plotId, UUID playerId, String upgradeId) {
        Optional<PlotUpgradeDefinition> definition = tree.getUpgrade(upgradeId);
        if (!definition.isPresent()) {
            return false;
        }
        return validate(plotId, playerId, definition.get(), getState(plotId).orElse(PlotProgressionState.initial())) == null;
    }

    @Override
    public boolean applyUpgrade(String plotId, UUID playerId, String upgradeId) {
        Optional<PlotUpgradeDefinition> definition = tree.getUpgrade(upgradeId);
        if (!definition.isPresent()) {
            return false;
        }
        PlotProgressionState state = getState(plotId).orElse(PlotProgressionState.initial());
        String validation = validate(plotId, playerId, definition.get(), state);
        if (validation != null) {
            return false;
        }
        if (definition.get().getCost().getVaultCost() > 0D) {
            if (!economyHook.hasEconomy() || !economyHook.withdraw(playerId, definition.get().getCost().getVaultCost())) {
                return false;
            }
        }
        storage.save(plotId, state.withUpgrade(definition.get(), Instant.now()));
        return true;
    }

    private String validate(String plotId, UUID playerId, PlotUpgradeDefinition definition, PlotProgressionState state) {
        if (playerId == null || !ownershipResolver.isOwner(plotId, playerId)) {
            return "not-owner";
        }
        if (state.getLevel(definition.getId()) >= definition.getLevel()) {
            return "already-owned";
        }
        for (PlotUpgradeRequirement requirement : definition.getRequirements()) {
            if (state.getLevel(requirement.getUpgradeId()) < requirement.getMinimumLevel()) {
                return "missing-requirement:" + requirement.getUpgradeId();
            }
        }
        if (definition.getCost().getVaultCost() > 0D) {
            if (!economyHook.hasEconomy()) {
                return "economy-unavailable";
            }
            if (economyHook.getBalance(playerId) < definition.getCost().getVaultCost()) {
                return "insufficient-funds";
            }
        }
        return null;
    }
}
