package de.streuland.plot;

import de.streuland.plot.skin.PlotTheme;
import de.streuland.quest.QuestProgress;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Mutable metadata for plot customizations.
 */
public class PlotData {
    private PlotTheme theme;
    private int bonusStorageSlots;
    private final Set<String> unlockedAbilities;
    private final Set<String> cosmeticInventory;
    private final Map<String, Double> statBonuses;
    private final Map<String, QuestProgress> questProgress;
    private final Map<String, Boolean> flagOverrides;

    public PlotData() {
        this(PlotTheme.NATURE);
    }

    public PlotData(PlotTheme theme) {
        this.theme = theme == null ? PlotTheme.NATURE : theme;
        this.bonusStorageSlots = 0;
        this.unlockedAbilities = new HashSet<>();
        this.cosmeticInventory = new HashSet<>();
        this.statBonuses = new HashMap<>();
        this.questProgress = new HashMap<>();
        this.flagOverrides = new HashMap<>();
    }

    public PlotTheme getTheme() {
        return theme;
    }

    public void setTheme(PlotTheme theme) {
        this.theme = theme == null ? PlotTheme.NATURE : theme;
    }

    public int getBonusStorageSlots() {
        return bonusStorageSlots;
    }

    public void setBonusStorageSlots(int bonusStorageSlots) {
        this.bonusStorageSlots = Math.max(0, bonusStorageSlots);
    }

    public Set<String> getUnlockedAbilities() {
        return unlockedAbilities;
    }

    public Set<String> getCosmeticInventory() {
        return cosmeticInventory;
    }

    public Map<String, Double> getStatBonuses() {
        return statBonuses;
    }

    public Map<String, QuestProgress> getQuestProgress() {
        return questProgress;
    }

    public Map<String, Boolean> getFlagOverrides() {
        return flagOverrides;
    }
}
