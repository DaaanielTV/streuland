package de.streuland.plot;

import de.streuland.plot.skin.PlotTheme;
import de.streuland.plot.snapshot.PlotSnapshotMetadata;
import de.streuland.plot.upgrade.PlotProgressionState;
import de.streuland.plot.upgrade.PlotUpgradePersistence;
import de.streuland.quest.QuestProgress;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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

        ensureDataFolder();
        loadAllPlots();
    }

    /**
     * Saves a plot to disk with state persistence.
     */
    public synchronized void savePlot(Plot plot) {
        if (plot == null) {
            plugin.getLogger().warning("Refusing to save null plot for world " + worldName);
            return;
        }

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
        config.set("upgrades", PlotUpgradePersistence.serialize(data.getProgressionState()));

        for (Map.Entry<String, QuestProgress> entry : data.getQuestProgress().entrySet()) {
            String base = "quests.progress." + entry.getKey();
            QuestProgress progress = entry.getValue();
            config.set(base + ".value", progress.getValue());
            config.set(base + ".completed", progress.isCompleted());
            config.set(base + ".completedAt", progress.getCompletedAt());
        }

        if (!safeSaveConfiguration(config, plotFile, "plot " + plot.getPlotId())) {
            return;
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
            plugin.getLogger().warning("Could not list plot files for world " + worldName + " in " + dataFolder.getAbsolutePath());
            return;
        }

        int skipped = 0;
        for (File file : files) {
            Plot plot = loadPlotFromFile(file);
            if (plot == null) {
                skipped++;
                continue;
            }

            cachedPlots.put(plot.getPlotId(), plot);
            plotData.put(plot.getPlotId(), loadPlotData(file));
            addToOwnerIndex(plot);
        }

        plugin.getLogger().info("Loaded " + cachedPlots.size() + " plots from disk for world " + worldName
                + (skipped > 0 ? " (skipped invalid files: " + skipped + ")" : ""));
    }

    private PlotData loadPlotData(File file) {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        PlotTheme theme = PlotTheme.fromInput(config.getString("theme", PlotTheme.NATURE.name()));
        PlotData data = new PlotData(theme);

        data.setBonusStorageSlots(Math.max(0, config.getInt("rewards.storageSlots", 0)));
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

        data.setSelectedBiome(config.getString("environment.selectedBiome", ""));
        data.setWeatherLocked(config.getBoolean("environment.weatherLocked", false));
        ConfigurationSection cosmetics = config.getConfigurationSection("environment.cosmetics");
        if (cosmetics != null) {
            for (String key : cosmetics.getKeys(false)) {
                data.getEnvironmentCosmetics().put(key, cosmetics.getString(key, ""));
            }
        }

        ConfigurationSection stats = config.getConfigurationSection("rewards.stats");
        if (stats != null) {
            for (String key : stats.getKeys(false)) {
                data.getStatBonuses().put(key, stats.getDouble(key));
            }
        }

        ConfigurationSection flags = config.getConfigurationSection("flags");
        if (flags != null) {
            for (String key : flags.getKeys(false)) {
                data.getFlagOverrides().put(key, flags.getBoolean(key));
            }
        }

        ConfigurationSection upgrades = config.getConfigurationSection("upgrades");
        if (upgrades != null) {
            data.setProgressionState(PlotUpgradePersistence.deserialize(upgrades.getValues(true)));
        } else {
            data.setProgressionState(PlotProgressionState.initial());
        }

        ConfigurationSection questProgress = config.getConfigurationSection("quests.progress");
        if (questProgress != null) {
            for (String questId : questProgress.getKeys(false)) {
                QuestProgress progress = new QuestProgress();
                progress.setValue(config.getInt("quests.progress." + questId + ".value", 0));
                progress.setCompleted(config.getBoolean("quests.progress." + questId + ".completed", false));
                progress.setCompletedAt(config.getLong("quests.progress." + questId + ".completedAt", 0L));
                data.getQuestProgress().put(questId, progress);
            }
        }

        return data;
    }

    private Plot loadPlotFromFile(File file) {
        try {
            FileConfiguration config = YamlConfiguration.loadConfiguration(file);

            String id = config.getString("id");
            if (id == null || id.isBlank()) {
                plugin.getLogger().warning("Skipping plot file with missing id: " + file.getName());
                return null;
            }

            int size = config.getInt("size", 0);
            if (size <= 0) {
                plugin.getLogger().warning("Skipping plot " + id + " due to invalid size: " + size);
                return null;
            }

            int centerX = config.getInt("centerX");
            int centerZ = config.getInt("centerZ");
            UUID owner = parseUuid(config.getString("owner"), "owner", id);
            long createdAt = config.getLong("createdAt", System.currentTimeMillis());
            int spawnY = config.getInt("spawnY", 64);

            String stateRaw = config.getString("state", owner == null ? "UNCLAIMED" : "CLAIMED");
            Plot.PlotState state = parseEnum(Plot.PlotState.class, stateRaw, owner == null ? Plot.PlotState.UNCLAIMED : Plot.PlotState.CLAIMED,
                    "state", id);

            Plot plot = new Plot(id, centerX, centerZ, size, owner, createdAt, spawnY, state);

            ConfigurationSection roleDefinitions = config.getConfigurationSection("roleDefinitions");
            if (roleDefinitions != null) {
                Map<String, Set<Permission>> definitions = new LinkedHashMap<>();
                for (String roleId : roleDefinitions.getKeys(false)) {
                    Set<Permission> permissions = EnumSet.noneOf(Permission.class);
                    for (String raw : config.getStringList("roleDefinitions." + roleId)) {
                        Permission permission = parseEnum(Permission.class, raw, null, "permission", id);
                        if (permission != null) {
                            permissions.add(permission);
                        }
                    }
                    definitions.put(roleId, permissions);
                }
                plot.replaceRoleDefinitions(definitions);
            }

            ConfigurationSection assignmentsSection = config.getConfigurationSection("roleAssignments");
            if (assignmentsSection != null) {
                Map<UUID, Set<String>> assignments = new HashMap<>();
                for (String uuidStr : assignmentsSection.getKeys(false)) {
                    UUID playerId = parseUuid(uuidStr, "role assignment", id);
                    if (playerId != null) {
                        assignments.put(playerId, new LinkedHashSet<>(config.getStringList("roleAssignments." + uuidStr)));
                    }
                }
                plot.replaceRoleAssignments(assignments);
            } else {
                loadLegacyRoles(config, plot, id);
            }

            return plot;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load plot from " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private void loadLegacyRoles(FileConfiguration config, Plot plot, String plotId) {
        Map<UUID, Role> loadedRoles = new HashMap<>();
        ConfigurationSection roles = config.getConfigurationSection("roles");
        if (roles != null) {
            for (String uuidStr : roles.getKeys(false)) {
                UUID playerId = parseUuid(uuidStr, "role", plotId);
                if (playerId == null) {
                    continue;
                }
                String roleRaw = config.getString("roles." + uuidStr, Role.VISITOR.name());
                Role role = Role.fromId(roleRaw).orElse(Role.VISITOR);
                loadedRoles.put(playerId, role);
            }
        } else {
            for (String uuidStr : config.getStringList("trusted")) {
                UUID trusted = parseUuid(uuidStr, "trusted", plotId);
                if (trusted != null) {
                    loadedRoles.put(trusted, Role.BUILDER);
                }
            }
        }
        plot.replaceRoles(loadedRoles);
    }

    private UUID parseUuid(String raw, String source, String plotId) {
        if (raw == null || raw.isBlank() || "null".equalsIgnoreCase(raw)) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid " + source + " UUID for plot " + plotId + ": " + raw);
            return null;
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback, String source, String plotId) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid " + source + " value for plot " + plotId + ": " + raw);
            return fallback;
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

    public synchronized int getNextPlotNumber() {
        int max = 0;
        for (String plotId : cachedPlots.keySet()) {
            Matcher matcher = NUMERIC_SUFFIX.matcher(plotId);
            if (matcher.matches()) {
                try {
                    max = Math.max(max, Integer.parseInt(matcher.group(1)));
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
        copy.setProgressionState(source.getProgressionState());
        copy.setFeatured(source.isFeatured());
        copy.setSelectedBiome(source.getSelectedBiome());
        copy.setWeatherLocked(source.isWeatherLocked());
        copy.getEnvironmentCosmetics().putAll(source.getEnvironmentCosmetics());
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
        safeSaveConfiguration(config, indexFile, "plot index for " + worldName);
    }

    private boolean safeSaveConfiguration(FileConfiguration config, File target, String label) {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().severe("Failed to create directory for " + label + ": " + parent.getAbsolutePath());
            return false;
        }

        File tempFile = new File(parent, target.getName() + ".tmp");
        try {
            config.save(tempFile);
            try {
                Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save " + label + ": " + e.getMessage());
            return false;
        } finally {
            if (tempFile.exists() && !tempFile.equals(target) && !tempFile.delete()) {
                tempFile.deleteOnExit();
            }
        }
    }

    private void ensureDataFolder() {
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().severe("Failed to create plot data folder: " + dataFolder.getAbsolutePath());
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
