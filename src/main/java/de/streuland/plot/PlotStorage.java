package de.streuland.plot;

import de.streuland.plot.skin.PlotTheme;
import de.streuland.plot.upgrade.PlotProgressionState;
import de.streuland.plot.upgrade.PlotUpgradePersistence;
import de.streuland.quest.QuestProgress;
import de.streuland.plot.snapshot.PlotSnapshotMetadata;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles persistent storage of plots using YAML files.
 *
 * One plot = one YAML file in the data folder.
 * All plots are indexed in an index.yml file for fast lookup.
 */
public class PlotStorage {
    private static final Pattern NUMERIC_SUFFIX = Pattern.compile(".*_(\\d+)$");

    private final JavaPlugin plugin;
    private final File dataFolder;
    private final File indexFile;
    private final String worldName;
    private final Map<String, Plot> cachedPlots;
    private final Map<String, PlotData> plotData;
    private final Map<UUID, Set<String>> ownerToPlotIds;
    
    public PlotStorage(JavaPlugin plugin, String worldName, PlotStoragePartitioner partitioner) {
        this.plugin = plugin;
        this.worldName = worldName;
        this.dataFolder = partitioner.resolveWorldFolder(worldName);
        this.indexFile = new File(dataFolder, "index.yml");
        this.cachedPlots = new HashMap<>();
        this.plotData = new HashMap<>();
        this.ownerToPlotIds = new HashMap<>();

        
        // Create folders if they don't exist
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        loadAllPlots();
    }

    /**
     * Saves a plot to disk with state persistence.
     */
    public synchronized void savePlot(Plot plot) {
        File plotFile = new File(dataFolder, plot.getPlotId() + ".yml");
        FileConfiguration config = new YamlConfiguration();

        config.set("id", plot.getPlotId());
        config.set("centerX", plot.getCenterX());
        config.set("centerZ", plot.getCenterZ());
        config.set("size", plot.getSize());
        config.set("owner", plot.getOwner() != null ? plot.getOwner().toString() : null);
        config.set("state", plot.getState().name());
        config.set("createdAt", plot.getCreatedAt());
        config.set("spawnY", plot.getSpawnY());
        Map<String, List<String>> serializedAssignments = new HashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : plot.getRoleAssignments().entrySet()) {
            serializedAssignments.put(entry.getKey().toString(), new ArrayList<>(entry.getValue()));
        }
        config.set("roleAssignments", serializedAssignments);
        Map<String, List<String>> serializedDefinitions = new LinkedHashMap<>();
        for (Map.Entry<String, Set<Permission>> entry : plot.getRoleDefinitions().entrySet()) {
            List<String> permissions = new ArrayList<>();
            for (Permission permission : entry.getValue()) {
                permissions.add(permission.name());
            }
            serializedDefinitions.put(entry.getKey(), permissions);
        }
        config.set("roleDefinitions", serializedDefinitions);
        PlotData data = plotData.getOrDefault(plot.getPlotId(), new PlotData());
        config.set("theme", data.getTheme().name());
        config.set("rewards.storageSlots", data.getBonusStorageSlots());
        config.set("rewards.abilities", new ArrayList<>(data.getUnlockedAbilities()));
        config.set("rewards.cosmetics", new ArrayList<>(data.getCosmeticInventory()));
        config.set("rewards.stats", new HashMap<>(data.getStatBonuses()));
        config.set("flags", new HashMap<>(data.getFlagOverrides()));

        for (Map.Entry<String, QuestProgress> entry : data.getQuestProgress().entrySet()) {
            String base = "quests.progress." + entry.getKey();
            QuestProgress progress = entry.getValue();
            config.set(base + ".value", progress.getValue());
            config.set(base + ".completed", progress.isCompleted());
            config.set(base + ".completedAt", progress.getCompletedAt());
        }

        try {
            config.save(plotFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save plot " + plot.getPlotId() + ": " + e.getMessage());
        }

        Plot oldPlot = cachedPlots.put(plot.getPlotId(), plot);
        updateOwnerIndexForPlotReplacement(oldPlot, plot);
        plotData.putIfAbsent(plot.getPlotId(), new PlotData());
        saveIndex();
    }

    private synchronized void loadAllPlots() {
        cachedPlots.clear();
        plotData.clear();
        ownerToPlotIds.clear();

        
        if (!dataFolder.exists()) {
            return;
        }

        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml") && !name.equals("index.yml"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            Plot plot = loadPlotFromFile(file);
            if (plot != null) {
                cachedPlots.put(plot.getPlotId(), plot);
                FileConfiguration config = YamlConfiguration.loadConfiguration(file);
                String themeRaw = config.getString("theme", PlotTheme.NATURE.name());
                PlotTheme theme = PlotTheme.fromInput(themeRaw);
                PlotData data = new PlotData(theme);
                data.setBonusStorageSlots(config.getInt("rewards.storageSlots", 0));
                data.getUnlockedAbilities().addAll(config.getStringList("rewards.abilities"));
                data.getCosmeticInventory().addAll(config.getStringList("rewards.cosmetics"));
                data.setFeatured(config.getBoolean("featured", false));
                data.setPublicVisitEnabled(config.getBoolean("showcase.public", false));
                data.setShowcaseTitle(config.getString("showcase.title", ""));
                data.setShowcaseDescription(config.getString("showcase.description", ""));
                data.setShowcaseTags(new LinkedHashSet<>(config.getStringList("showcase.tags")));
                data.setShowcaseSpawn(
                        config.getInt("showcase.spawn.x", 0),
                        config.getInt("showcase.spawn.y", 0),
                        config.getInt("showcase.spawn.z", 0));
                if (config.isConfigurationSection("rewards.stats")) {
                    for (String key : config.getConfigurationSection("rewards.stats").getKeys(false)) {
                        data.getStatBonuses().put(key, config.getDouble("rewards.stats." + key));
                    }
                }
                if (config.isConfigurationSection("flags")) {
                    for (String key : config.getConfigurationSection("flags").getKeys(false)) {
                        data.getFlagOverrides().put(key, config.getBoolean("flags." + key));
                    }
                }
                if (config.isConfigurationSection("quests.progress")) {
                    for (String questId : config.getConfigurationSection("quests.progress").getKeys(false)) {
                        QuestProgress progress = new QuestProgress();
                        progress.setValue(config.getInt("quests.progress." + questId + ".value", 0));
                        progress.setCompleted(config.getBoolean("quests.progress." + questId + ".completed", false));
                        progress.setCompletedAt(config.getLong("quests.progress." + questId + ".completedAt", 0L));
                        data.getQuestProgress().put(questId, progress);
                    }
                }
                plotData.put(plot.getPlotId(), data);
                addToOwnerIndex(plot);
            }
        }

        plugin.getLogger().info("Loaded " + cachedPlots.size() + " plots from disk for world " + worldName);
    }

    private Plot loadPlotFromFile(File file) {
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);

            String id = config.getString("id");
            int centerX = config.getInt("centerX");
            int centerZ = config.getInt("centerZ");
            int size = config.getInt("size");
            String ownerStr = config.getString("owner");
            UUID owner = ownerStr != null && !ownerStr.equals("null") ? UUID.fromString(ownerStr) : null;
            long createdAt = config.getLong("createdAt");
            int spawnY = config.getInt("spawnY", 64);

            String stateStr = config.getString("state", owner == null ? "UNCLAIMED" : "CLAIMED");
            Plot.PlotState state = Plot.PlotState.valueOf(stateStr);

            Plot plot = new Plot(id, centerX, centerZ, size, owner, createdAt, spawnY, state);

            if (config.isConfigurationSection("roleDefinitions")) {
                Map<String, Set<Permission>> definitions = new LinkedHashMap<>();
                for (String roleId : config.getConfigurationSection("roleDefinitions").getKeys(false)) {
                    Set<Permission> permissions = EnumSet.noneOf(Permission.class);
                    for (String raw : config.getStringList("roleDefinitions." + roleId)) {
                        try {
                            permissions.add(Permission.valueOf(raw.toUpperCase(Locale.ROOT)));
                        } catch (IllegalArgumentException ignored) {
                            plugin.getLogger().warning("Invalid permission for role " + roleId + " on plot " + id + ": " + raw);
                        }
                    }
                    definitions.put(roleId, permissions);
                }
                plot.replaceRoleDefinitions(definitions);
            }

            if (config.isConfigurationSection("roleAssignments")) {
                Map<UUID, Set<String>> assignments = new HashMap<>();
                for (String uuidStr : config.getConfigurationSection("roleAssignments").getKeys(false)) {
                    try {
                        assignments.put(UUID.fromString(uuidStr), new LinkedHashSet<>(config.getStringList("roleAssignments." + uuidStr)));
                    } catch (Exception e) {
                        plugin.getLogger().warning("Invalid role assignment for plot " + id + ": " + uuidStr);
                    }
                }
                plot.replaceRoleAssignments(assignments);
            } else {
                Map<UUID, Role> loadedRoles = new HashMap<>();
                if (config.isConfigurationSection("roles")) {
                    for (String uuidStr : config.getConfigurationSection("roles").getKeys(false)) {
                        try {
                            UUID playerId = UUID.fromString(uuidStr);
                            String roleRaw = config.getString("roles." + uuidStr, Role.VISITOR.name());
                            Role role = Role.fromId(roleRaw).orElse(Role.VISITOR);
                            loadedRoles.put(playerId, role);
                        } catch (Exception e) {
                            plugin.getLogger().warning("Invalid role entry for plot " + id + ": " + uuidStr);
                        }
                    }
                } else {
                    List<String> trustedList = config.getStringList("trusted");
                    for (String uuidStr : trustedList) {
                        try {
                            loadedRoles.put(UUID.fromString(uuidStr), Role.BUILDER);
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Invalid UUID in trusted list for plot " + id + ": " + uuidStr);
                        }
                    }
                }
                plot.replaceRoles(loadedRoles);
            }

            return plot;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load plot from " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    public synchronized Plot getPlot(String plotId) {
        return cachedPlots.get(plotId);
    }

    public synchronized List<Plot> getPlayerPlots(UUID player) {
        Set<String> ids = ownerToPlotIds.getOrDefault(player, Collections.emptySet());
        List<Plot> plots = new ArrayList<>();
        for (String id : ids) {
            Plot plot = cachedPlots.get(id);
            if (plot != null) {
                plots.add(plot);
            }
        }
        return plots;
    }

    public synchronized Collection<Plot> getAllPlots() {
        return new ArrayList<>(cachedPlots.values());
    }

    public synchronized Set<String> getPlotDataIds() {
        return new HashSet<>(plotData.keySet());
    }

    public synchronized boolean deletePlotData(String plotId) {
        return plotData.remove(plotId) != null;
    }

    public synchronized void reload() {
        loadAllPlots();
    }

    public synchronized boolean exists(String plotId) {
        return cachedPlots.containsKey(plotId);
    }

    /**
     * Claims an unclaimed plot and returns the updated plot instance.
     */
    public synchronized Plot claimPlot(String plotId, UUID player) {
        Plot plot = cachedPlots.get(plotId);
        if (plot == null || plot.getState() != Plot.PlotState.UNCLAIMED) {
            return null;
        }

        Plot claimedPlot = new Plot(plot.getPlotId(), plot.getCenterX(), plot.getCenterZ(),
                plot.getSize(), player, System.currentTimeMillis(), plot.getSpawnY(),
                Plot.PlotState.CLAIMED);

        claimedPlot.replaceRoles(plot.getRoles());

        savePlot(claimedPlot);
        plugin.getLogger().info("Plot " + plotId + " claimed by " + player);
        return claimedPlot;
    }

    /**
     * Releases a claimed plot back to unclaimed state.
     */
    public synchronized Plot unclaimPlot(String plotId) {
        Plot plot = cachedPlots.get(plotId);
        if (plot == null || plot.getState() != Plot.PlotState.CLAIMED) {
            return null;
        }

        Plot unclaimedPlot = new Plot(plot.getPlotId(), plot.getCenterX(), plot.getCenterZ(),
                plot.getSize(), null, System.currentTimeMillis(), plot.getSpawnY(),
                Plot.PlotState.UNCLAIMED);

        savePlot(unclaimedPlot);
        plugin.getLogger().info("Plot " + plotId + " released and is now UNCLAIMED");
        return unclaimedPlot;
    }

    /**
     * Transfers ownership to a new owner and resets trust/plot data for a fresh start.
     */
    public synchronized Plot transferOwnership(String plotId, UUID expectedOwner, UUID newOwner) {
        Plot plot = cachedPlots.get(plotId);
        if (plot == null || plot.getState() != Plot.PlotState.CLAIMED || plot.getOwner() == null) {
            return null;
        }
        if (expectedOwner != null && !expectedOwner.equals(plot.getOwner())) {
            return null;
        }

        Plot transferred = new Plot(plot.getPlotId(), plot.getCenterX(), plot.getCenterZ(),
                plot.getSize(), newOwner, System.currentTimeMillis(), plot.getSpawnY(),
                Plot.PlotState.CLAIMED);

        plotData.put(plotId, new PlotData());
        savePlot(transferred);
        return transferred;
    }

    /**
     * Deletes a plot from storage and returns the removed plot if present.
     */
    public synchronized Plot deletePlot(String plotId) {
        Plot plot = cachedPlots.remove(plotId);
        if (plot != null) {
            removeFromOwnerIndex(plot);
            plotData.remove(plotId);
            File plotFile = new File(dataFolder, plotId + ".yml");
            if (plotFile.exists() && !plotFile.delete()) {
                plugin.getLogger().warning("Failed to delete plot file: " + plotFile.getName());
            }
            saveIndex();
        }
        return plot;
    }


    public synchronized void saveMany(List<Plot> plots) {
        if (plots == null) {
            return;
        }
        for (Plot plot : plots) {
            if (plot != null) {
                savePlot(plot);
            }
        }
    }

    public synchronized void deleteMany(List<Integer> plotIds) {
        if (plotIds == null) {
            return;
        }
        Set<String> toDelete = new HashSet<>();
        for (Integer id : plotIds) {
            if (id == null) {
                continue;
            }
            String suffix = "_" + id;
            for (String plotId : cachedPlots.keySet()) {
                if (plotId.endsWith(suffix)) {
                    toDelete.add(plotId);
                }
            }
        }
        for (String plotId : toDelete) {
            deletePlot(plotId);
        }
    }

    /**
     * Determines the next plot number based on existing plot IDs.
     */
    public synchronized int getNextPlotNumber() {
        int max = 0;
        for (String plotId : cachedPlots.keySet()) {
            Matcher matcher = NUMERIC_SUFFIX.matcher(plotId);
            if (matcher.matches()) {
                try {
                    int value = Integer.parseInt(matcher.group(1));
                    if (value > max) {
                        max = value;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max + 1;
    }



    public synchronized PlotSnapshotMetadata snapshotMetadata(String plotId, String authorName, String note) {
        Plot plot = cachedPlots.get(plotId);
        if (plot == null) {
            return null;
        }
        return new PlotSnapshotMetadata(copyPlot(plot), copyPlotData(plotData.get(plotId)), authorName, note);
    }

    public synchronized boolean restoreSnapshotMetadata(String plotId, PlotSnapshotMetadata metadata) {
        if (metadata == null || metadata.getPlot() == null || !cachedPlots.containsKey(plotId)) {
            return false;
        }
        Plot restoredPlot = copyPlot(metadata.getPlot());
        PlotData restoredData = copyPlotData(metadata.getPlotData());
        plotData.put(plotId, restoredData);
        savePlot(restoredPlot);
        return true;
    }

    private Plot copyPlot(Plot plot) {
        if (plot == null) {
            return null;
        }
        Plot copy = new Plot(plot.getPlotId(), plot.getCenterX(), plot.getCenterZ(), plot.getSize(), plot.getOwner(),
                plot.getCreatedAt(), plot.getSpawnY(), plot.getState());
        copy.replaceRoles(plot.getRoles());
        return copy;
    }

    private PlotData copyPlotData(PlotData source) {
        PlotData copy = new PlotData(source == null ? PlotTheme.NATURE : source.getTheme());
        if (source == null) {
            return copy;
        }
        copy.setBonusStorageSlots(source.getBonusStorageSlots());
        copy.getUnlockedAbilities().addAll(source.getUnlockedAbilities());
        copy.getCosmeticInventory().addAll(source.getCosmeticInventory());
        copy.getStatBonuses().putAll(source.getStatBonuses());
        for (Map.Entry<String, QuestProgress> entry : source.getQuestProgress().entrySet()) {
            QuestProgress progress = new QuestProgress();
            progress.setValue(entry.getValue().getValue());
            progress.setCompleted(entry.getValue().isCompleted());
            progress.setCompletedAt(entry.getValue().getCompletedAt());
            copy.getQuestProgress().put(entry.getKey(), progress);
        }
        copy.getFlagOverrides().putAll(source.getFlagOverrides());
        copy.setFeatured(source.isFeatured());
        return copy;
    }

    public synchronized PlotData getPlotData(String plotId) {
        return plotData.computeIfAbsent(plotId, ignored -> new PlotData());
    }

    public synchronized void savePlotData(String plotId, PlotData data) {
        plotData.put(plotId, data == null ? new PlotData() : data);
        Plot plot = cachedPlots.get(plotId);
        if (plot != null) {
            savePlot(plot);
        }
    }

    private void saveIndex() {
        FileConfiguration config = new YamlConfiguration();
        config.set("world", worldName);
        config.set("plots", new ArrayList<>(cachedPlots.keySet()));
        config.set("count", cachedPlots.size());
        config.set("last-updated", System.currentTimeMillis());

        try {
            config.save(indexFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save plot index: " + e.getMessage());
        }
    }

    private void updateOwnerIndexForPlotReplacement(Plot oldPlot, Plot newPlot) {
        if (oldPlot != null) {
            removeFromOwnerIndex(oldPlot);
        }
        addToOwnerIndex(newPlot);
    }

    private void addToOwnerIndex(Plot plot) {
        UUID owner = plot.getOwner();
        if (owner == null) {
            return;
        }
        ownerToPlotIds.computeIfAbsent(owner, ignored -> new HashSet<>()).add(plot.getPlotId());
    }


    public String getWorldName() {
        return worldName;
    }

    private void removeFromOwnerIndex(Plot plot) {
        UUID owner = plot.getOwner();
        if (owner == null) {
            return;
        }
        Set<String> ids = ownerToPlotIds.get(owner);
        if (ids == null) {
            return;
        }
        ids.remove(plot.getPlotId());
        if (ids.isEmpty()) {
            ownerToPlotIds.remove(owner);
        }
    }
}
