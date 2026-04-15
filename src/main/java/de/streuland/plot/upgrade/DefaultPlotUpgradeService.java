package de.streuland.plot.upgrade;

import de.streuland.economy.PlotEconomyHook;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class DefaultPlotUpgradeService implements PlotUpgradeService {
    private final PlotUpgradeTree tree;
    private final PlotUpgradeStorage storage;
    private final PlotEconomyHook economyHook;
    private final PlotOwnershipResolver ownershipResolver;
    private final PlotUpgradeConstraintValidator constraintValidator;
    private final ConcurrentMap<String, Long> experienceThrottle = new ConcurrentHashMap<>();

    public DefaultPlotUpgradeService(PlotUpgradeTree tree,
                                     PlotUpgradeStorage storage,
                                     PlotEconomyHook economyHook,
                                     PlotOwnershipResolver ownershipResolver) {
        this(tree, storage, economyHook, ownershipResolver, PlotUpgradeConstraintValidator.allowAll());
    }

    public DefaultPlotUpgradeService(PlotUpgradeTree tree,
                                     PlotUpgradeStorage storage,
                                     PlotEconomyHook economyHook,
                                     PlotOwnershipResolver ownershipResolver,
                                     PlotUpgradeConstraintValidator constraintValidator) {
        this.tree = tree;
        this.storage = storage;
        this.economyHook = economyHook;
        this.ownershipResolver = ownershipResolver;
        this.constraintValidator = constraintValidator == null ? PlotUpgradeConstraintValidator.allowAll() : constraintValidator;
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
        double vaultCost = definition.get().getCost().getVaultCost();
        if (vaultCost > 0D) {
            if (!economyHook.hasEconomy() || !economyHook.withdraw(playerId, vaultCost)) {
                return false;
            }
        }

        int gainedPoints = tree.getProgressionTrack().getMode() == PlotProgressionTrack.Mode.XP
                ? definition.get().getXpReward()
                : 0;
        PlotProgressionState updated = state.withUpgrade(definition.get(), Instant.now(), tree.getProgressionTrack(), gainedPoints, vaultCost);
        PlotProgressionState rewarded = applyLevelRewards(plotId, updated, null);
        storage.save(plotId, rewarded);
        return true;
    }

    @Override
    public boolean canPrestige(String plotId, UUID playerId) {
        PlotProgressionState state = getState(plotId).orElse(PlotProgressionState.initial());
        if (playerId == null || !ownershipResolver.isOwner(plotId, playerId)) {
            return false;
        }
        return state.getOverallLevel() >= tree.getMaxLevel();
    }

    @Override
    public boolean prestige(String plotId, UUID playerId) {
        if (!canPrestige(plotId, playerId)) {
            return false;
        }
        PlotProgressionState state = getState(plotId).orElse(PlotProgressionState.initial());
        storage.save(plotId, state.withPrestigeReset());
        return true;
    }

    @Override
    public PlotExperienceResult grantExperience(String plotId, UUID playerId, String source, int suggestedAmount) {
        if (plotId == null || plotId.isBlank() || playerId == null) {
            return PlotExperienceResult.none();
        }
        PlotProgressionTrack track = tree.getProgressionTrack();
        if (track.getMode() != PlotProgressionTrack.Mode.XP) {
            return PlotExperienceResult.none();
        }
        if (!ownershipResolver.isOwner(plotId, playerId)) {
            return PlotExperienceResult.none();
        }

        PlotExperienceRuleSet ruleSet = tree.getExperienceRuleSet();
        long now = System.currentTimeMillis();
        long cooldown = ruleSet.getCooldownMillis();
        String throttleKey = plotId + ":" + playerId + ":" + (source == null ? "unknown" : source.toLowerCase());
        if (cooldown > 0L) {
            Long previous = experienceThrottle.get(throttleKey);
            if (previous != null && (now - previous) < cooldown) {
                return PlotExperienceResult.none();
            }
            experienceThrottle.put(throttleKey, now);
        }

        int gainedXp = ruleSet.resolveXp(source, suggestedAmount);
        if (gainedXp <= 0) {
            return PlotExperienceResult.none();
        }

        PlotProgressionState state = getState(plotId).orElse(PlotProgressionState.initial());
        int previousLevel = state.getOverallLevel();
        PlotProgressionState updated = state.withExperienceGain(gainedXp, track, Instant.now());
        LinkedHashSet<Integer> unlockedRewards = new LinkedHashSet<>();
        PlotProgressionState rewarded = applyLevelRewards(plotId, updated, unlockedRewards);
        storage.save(plotId, rewarded);
        return new PlotExperienceResult(true, gainedXp, previousLevel, rewarded.getOverallLevel(), unlockedRewards);
    }

    private PlotProgressionState applyLevelRewards(String plotId, PlotProgressionState updated, LinkedHashSet<Integer> unlockedOut) {
        if (!(storage instanceof PlotStorageBackedUpgradeStorage)) {
            return updated;
        }
        PlotStorageBackedUpgradeStorage storageBacked = (PlotStorageBackedUpgradeStorage) storage;
        PlotProgressionState working = updated;
        for (int level = 1; level <= updated.getOverallLevel(); level++) {
            if (working.getAwardedRewardLevels().contains(level)) {
                continue;
            }
            Optional<PlotLevelReward> reward = tree.getLevelReward(level);
            if (!reward.isPresent()) {
                continue;
            }
            storageBacked.applyLevelReward(plotId, reward.get());
            working = working.withAwardedRewardLevel(level);
            if (unlockedOut != null) {
                unlockedOut.add(level);
            }
        }
        return working;
    }

    private String validate(String plotId, UUID playerId, PlotUpgradeDefinition definition, PlotProgressionState state) {
        if (playerId == null || !ownershipResolver.isOwner(plotId, playerId)) {
            return "not-owner";
        }
        if (state.getLevel(definition.getId()) >= definition.getLevel()) {
            return "already-owned";
        }
        if (state.getOverallLevel() < definition.getRequiredPlotLevel()) {
            return "plot-level-too-low";
        }
        if (state.getPrestigeLevel() < definition.getRequiredPrestigeLevel()) {
            return "prestige-level-too-low";
        }
        for (PlotUpgradeRequirement requirement : definition.getRequirements()) {
            if (state.getLevel(requirement.getUpgradeId()) < requirement.getMinimumLevel()) {
                return "missing-requirement:" + requirement.getUpgradeId();
            }
        }
        String constraintReason = constraintValidator.validate(plotId, definition, state);
        if (constraintReason != null) {
            return constraintReason;
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
