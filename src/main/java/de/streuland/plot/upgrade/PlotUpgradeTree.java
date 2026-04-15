package de.streuland.plot.upgrade;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class PlotUpgradeTree {
    private final Map<String, PlotUpgradeDefinition> upgrades = new HashMap<>();
    private final Map<Integer, PlotLevelReward> levelRewards = new HashMap<>();
    private PlotProgressionTrack progressionTrack = PlotProgressionTrack.defaultCurrencyTrack();
    private PlotExperienceRuleSet experienceRuleSet = PlotExperienceRuleSet.defaults();

    public void addUpgrade(PlotUpgradeDefinition definition) { upgrades.put(definition.getId(), definition); }
    public Optional<PlotUpgradeDefinition> getUpgrade(String id) { return Optional.ofNullable(upgrades.get(id)); }
    public Collection<PlotUpgradeDefinition> getAllUpgrades() { return Collections.unmodifiableCollection(upgrades.values()); }

    public PlotProgressionTrack getProgressionTrack() {
        return progressionTrack;
    }

    public void setProgressionTrack(PlotProgressionTrack progressionTrack) {
        if (progressionTrack != null) {
            this.progressionTrack = progressionTrack;
        }
    }

    public int getMaxLevel() {
        return progressionTrack.getMaxLevel();
    }

    public void putLevelReward(PlotLevelReward reward) {
        if (reward != null) {
            levelRewards.put(reward.getLevel(), reward);
        }
    }

    public Optional<PlotLevelReward> getLevelReward(int level) {
        return Optional.ofNullable(levelRewards.get(level));
    }

    public Map<Integer, PlotLevelReward> getLevelRewards() {
        return Collections.unmodifiableMap(levelRewards);
    }

    public PlotExperienceRuleSet getExperienceRuleSet() {
        return experienceRuleSet;
    }

    public void setExperienceRuleSet(PlotExperienceRuleSet experienceRuleSet) {
        if (experienceRuleSet != null) {
            this.experienceRuleSet = experienceRuleSet;
        }
    }

    public List<PlotUpgradeDefinition> getAvailableOrder() {
        return upgrades.values().stream()
                .sorted(Comparator.comparing(PlotUpgradeDefinition::getType)
                        .thenComparingInt(PlotUpgradeDefinition::getLevel)
                        .thenComparing(PlotUpgradeDefinition::getId))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
