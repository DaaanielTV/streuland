package de.streuland.plot;

import de.streuland.plot.skin.PlotTheme;
import de.streuland.plot.upgrade.PlotProgressionState;
import de.streuland.quest.QuestProgress;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private boolean featured;
    private boolean publicVisitEnabled;
    private String showcaseTitle;
    private String showcaseDescription;
    private final Set<String> showcaseTags;
    private int showcaseSpawnX;
    private int showcaseSpawnY;
    private int showcaseSpawnZ;
    private PlotProgressionState progressionState;

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
        this.featured = false;
        this.publicVisitEnabled = false;
        this.showcaseTitle = "";
        this.showcaseDescription = "";
        this.showcaseTags = new LinkedHashSet<>();
        this.showcaseSpawnX = 0;
        this.showcaseSpawnY = 0;
        this.showcaseSpawnZ = 0;
        this.progressionState = PlotProgressionState.initial();
    }

    public PlotTheme getTheme() { return theme; }
    public void setTheme(PlotTheme theme) { this.theme = theme == null ? PlotTheme.NATURE : theme; }
    public int getBonusStorageSlots() { return bonusStorageSlots; }
    public void setBonusStorageSlots(int bonusStorageSlots) { this.bonusStorageSlots = Math.max(0, bonusStorageSlots); }
    public Set<String> getUnlockedAbilities() { return unlockedAbilities; }
    public Set<String> getCosmeticInventory() { return cosmeticInventory; }
    public Map<String, Double> getStatBonuses() { return statBonuses; }
    public Map<String, QuestProgress> getQuestProgress() { return questProgress; }
    public Map<String, Boolean> getFlagOverrides() { return flagOverrides; }
    public boolean isFeatured() { return featured; }
    public void setFeatured(boolean featured) { this.featured = featured; }

    public boolean isPublicVisitEnabled() { return publicVisitEnabled; }
    public void setPublicVisitEnabled(boolean publicVisitEnabled) { this.publicVisitEnabled = publicVisitEnabled; }
    public String getShowcaseTitle() { return showcaseTitle; }
    public void setShowcaseTitle(String showcaseTitle) { this.showcaseTitle = normalizeText(showcaseTitle, 64); }
    public String getShowcaseDescription() { return showcaseDescription; }
    public void setShowcaseDescription(String showcaseDescription) { this.showcaseDescription = normalizeText(showcaseDescription, 280); }
    public Set<String> getShowcaseTags() { return Collections.unmodifiableSet(showcaseTags); }
    public void setShowcaseTags(Set<String> tags) {
        showcaseTags.clear();
        if (tags != null) {
            for (String tag : tags) {
                String normalized = normalizeTag(tag);
                if (!normalized.isEmpty()) {
                    showcaseTags.add(normalized);
                }
            }
        }
    }

    public void setShowcaseTagsFromText(String rawTags) {
        if (rawTags == null || rawTags.trim().isEmpty()) {
            setShowcaseTags(Collections.<String>emptySet());
            return;
        }
        setShowcaseTags(Arrays.stream(rawTags.split(","))
                .map(String::trim)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    public String getShowcaseTagsAsText() { return String.join(", ", showcaseTags); }
    public int getShowcaseSpawnX() { return showcaseSpawnX; }
    public int getShowcaseSpawnY() { return showcaseSpawnY; }
    public int getShowcaseSpawnZ() { return showcaseSpawnZ; }

    public void setShowcaseSpawn(int x, int y, int z) {
        this.showcaseSpawnX = x;
        this.showcaseSpawnY = y;
        this.showcaseSpawnZ = z;
    }

    public boolean hasCustomShowcaseSpawn() {
        return showcaseSpawnX != 0 || showcaseSpawnY != 0 || showcaseSpawnZ != 0;
    }

    public PlotProgressionState getProgressionState() {
        return progressionState == null ? PlotProgressionState.initial() : progressionState;
    }

    public void setProgressionState(PlotProgressionState progressionState) {
        this.progressionState = progressionState == null ? PlotProgressionState.initial() : progressionState;
    }

    private static String normalizeText(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        String normalized = input.trim().replaceAll("\\s+", " ");
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength).trim();
    }

    private static String normalizeTag(String input) {
        String tag = normalizeText(input, 24).toLowerCase(Locale.ROOT).replace(' ', '-');
        return tag.replaceAll("[^a-z0-9:_-]", "");
    }
}
