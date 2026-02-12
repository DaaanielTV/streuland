package de.streuland.quest;

import de.streuland.plot.PlotData;
import de.streuland.plot.PlotStorage;
import de.streuland.rules.RuleEngine;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class QuestService {
    public static final String OBJECTIVE_BUILD_HOUSES = "build_houses";
    public static final String OBJECTIVE_BEFRIEND_PLOTS = "befriend_plots";
    public static final String OBJECTIVE_DISTRICT_LEVEL = "district_level";

    private final JavaPlugin plugin;
    private final PlotStorage plotStorage;
    private final RuleEngine ruleEngine;
    private final File questFile;
    private final Map<String, QuestDefinition> activeQuests = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> completionHistory = new HashMap<>();

    public QuestService(JavaPlugin plugin, PlotStorage plotStorage, RuleEngine ruleEngine) {
        this.plugin = plugin;
        this.plotStorage = plotStorage;
        this.ruleEngine = ruleEngine;
        this.questFile = new File(plugin.getDataFolder(), "quests.yml");
        ensureQuestFile();
        load();
    }

    public Collection<QuestDefinition> getActiveQuests() {
        return Collections.unmodifiableCollection(activeQuests.values());
    }

    public List<QuestDefinition> getQuestsForObjective(String objectiveKey) {
        List<QuestDefinition> quests = new ArrayList<>();
        for (QuestDefinition quest : activeQuests.values()) {
            if (objectiveKey.equals(quest.getObjectiveKey()) && !quest.isExpired()) {
                quests.add(quest);
            }
        }
        return quests;
    }

    public QuestProgress getOrCreateProgress(PlotData data, String questId) {
        return data.getQuestProgress().computeIfAbsent(questId, ignored -> new QuestProgress());
    }

    public boolean updateProgress(Player player, String plotId, String objectiveKey, int amount) {
        PlotData plotData = plotStorage.getPlotData(plotId);
        boolean anyCompletion = false;

        for (QuestDefinition quest : getQuestsForObjective(objectiveKey)) {
            QuestProgress progress = getOrCreateProgress(plotData, quest.getId());
            if (progress.isCompleted()) {
                continue;
            }
            progress.increment(amount);
            if (progress.getValue() >= quest.getTarget()) {
                completeQuest(player, plotId, plotData, quest, progress);
                anyCompletion = true;
            }
        }

        plotStorage.savePlotData(plotId, plotData);
        return anyCompletion;
    }

    public boolean syncMilestoneProgress(Player player, String plotId, String objectiveKey, int currentValue) {
        PlotData plotData = plotStorage.getPlotData(plotId);
        boolean anyCompletion = false;
        for (QuestDefinition quest : getQuestsForObjective(objectiveKey)) {
            QuestProgress progress = getOrCreateProgress(plotData, quest.getId());
            if (progress.isCompleted()) {
                continue;
            }
            progress.setValue(Math.max(progress.getValue(), currentValue));
            if (progress.getValue() >= quest.getTarget()) {
                completeQuest(player, plotId, plotData, quest, progress);
                anyCompletion = true;
            }
        }
        plotStorage.savePlotData(plotId, plotData);
        return anyCompletion;
    }

    private void completeQuest(Player player, String plotId, PlotData plotData, QuestDefinition quest, QuestProgress progress) {
        progress.setCompleted(true);
        applyRewards(plotData, quest.getReward());
        completionHistory.computeIfAbsent(player.getUniqueId(), ignored -> new HashSet<>()).add(quest.getId());
        save();
        ruleEngine.notifyQuestCompleted(player, plotStorage.getPlot(plotId), quest, plotData);

        player.sendMessage("§6Quest abgeschlossen: §a" + quest.getTitle());
        player.sendMessage("§7Belohnungen: " + describeRewards(quest.getReward()));
    }

    private String describeRewards(QuestReward reward) {
        List<String> rewards = new ArrayList<>();
        if (reward.getStorageSlots() > 0) {
            rewards.add("+" + reward.getStorageSlots() + " Lagerplätze");
        }
        if (!reward.getAbilityUnlocks().isEmpty()) {
            rewards.add("Fähigkeit: " + String.join(", ", reward.getAbilityUnlocks()));
        }
        if (!reward.getStatBonuses().isEmpty()) {
            rewards.add("Stats: " + reward.getStatBonuses());
        }
        if (!reward.getCosmeticItems().isEmpty()) {
            rewards.add("Kosmetik: " + String.join(", ", reward.getCosmeticItems()));
        }
        return String.join(" | ", rewards);
    }

    private void applyRewards(PlotData plotData, QuestReward reward) {
        plotData.setBonusStorageSlots(plotData.getBonusStorageSlots() + reward.getStorageSlots());
        plotData.getUnlockedAbilities().addAll(reward.getAbilityUnlocks());
        plotData.getCosmeticInventory().addAll(reward.getCosmeticItems());

        for (Map.Entry<String, Double> entry : reward.getStatBonuses().entrySet()) {
            String key = entry.getKey();
            double total = plotData.getStatBonuses().getOrDefault(key, 0.0D) + entry.getValue();
            plotData.getStatBonuses().put(key, total);
        }
    }

    private void ensureQuestFile() {
        if (questFile.exists()) {
            return;
        }
        plugin.saveResource("quests.yml", false);
    }

    public void load() {
        activeQuests.clear();
        completionHistory.clear();
        FileConfiguration config = YamlConfiguration.loadConfiguration(questFile);
        ConfigurationSection questsSection = config.getConfigurationSection("quests");
        if (questsSection != null) {
            for (String questId : questsSection.getKeys(false)) {
                String base = "quests." + questId;
                QuestReward reward = new QuestReward(
                        config.getInt(base + ".rewards.storageSlots", 0),
                        config.getStringList(base + ".rewards.cosmetics"),
                        config.getStringList(base + ".rewards.abilities"),
                        loadStatBonuses(config.getConfigurationSection(base + ".rewards.stats"))
                );
                QuestType type = QuestType.valueOf(config.getString(base + ".type", QuestType.DAILY.name()).toUpperCase());
                long expiresAt = config.getLong(base + ".expiresAt", 0L);
                if (expiresAt <= 0L) {
                    expiresAt = System.currentTimeMillis() + (type == QuestType.DAILY ? Duration.ofDays(1).toMillis() : Duration.ofDays(7).toMillis());
                }
                QuestDefinition definition = new QuestDefinition(
                        questId,
                        config.getString(base + ".title", questId),
                        config.getString(base + ".description", ""),
                        type,
                        config.getString(base + ".objective", ""),
                        config.getInt(base + ".target", 1),
                        expiresAt,
                        reward
                );
                activeQuests.put(questId, definition);
            }
        }

        ConfigurationSection historySection = config.getConfigurationSection("history");
        if (historySection != null) {
            for (String uuidRaw : historySection.getKeys(false)) {
                try {
                    UUID playerId = UUID.fromString(uuidRaw);
                    completionHistory.put(playerId, new HashSet<>(historySection.getStringList(uuidRaw)));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Invalid UUID in quest history: " + uuidRaw);
                }
            }
        }
    }

    public void save() {
        FileConfiguration config = YamlConfiguration.loadConfiguration(questFile);
        for (Map.Entry<UUID, Set<String>> entry : completionHistory.entrySet()) {
            config.set("history." + entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        try {
            config.save(questFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save quest history: " + e.getMessage());
        }
    }

    private Map<String, Double> loadStatBonuses(ConfigurationSection section) {
        Map<String, Double> stats = new HashMap<>();
        if (section == null) {
            return stats;
        }
        for (String key : section.getKeys(false)) {
            stats.put(key, section.getDouble(key));
        }
        return stats;
    }
}
