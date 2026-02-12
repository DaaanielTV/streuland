package de.streuland.district;

import de.streuland.event.PlotClaimedEvent;
import de.streuland.event.PlotCreatedEvent;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Orchestrates district clustering, progress tracking, and persistence.
 */
public class DistrictManager implements Listener {
    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final DistrictStorage storage;
    private final DistrictClusterService clusterService;
    private final DistrictGoalCatalog goalCatalog;
    private final Map<String, District> districts = new HashMap<>();
    private final Map<String, String> plotToDistrict = new HashMap<>();
    private int districtCounter = 0;

    public DistrictManager(JavaPlugin plugin, PlotManager plotManager) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.storage = new DistrictStorage(plugin);
        this.clusterService = new DistrictClusterService();
        this.goalCatalog = new DistrictGoalCatalog();

        for (District district : storage.getAllDistricts()) {
            districts.put(district.getId(), district);
            for (String plotId : district.getPlotIds()) {
                plotToDistrict.put(plotId, district.getId());
            }
            districtCounter++;
        }
    }

    public DistrictStorage getStorage() {
        return storage;
    }

    public District getDistrictForPlot(Plot plot) {
        if (plot == null) {
            return null;
        }
        String districtId = plotToDistrict.get(plot.getPlotId());
        return districtId != null ? districts.get(districtId) : null;
    }

    public District getDistrictById(String id) {
        return districts.get(id);
    }

    public Collection<District> getAllDistricts() {
        return new ArrayList<>(districts.values());
    }

    public CompletableFuture<Void> rebuildClustersAsync() {
        return CompletableFuture.supplyAsync(() -> {
            List<Set<Plot>> allClusters = new ArrayList<>();
            for (String worldName : plotManager.getManagedWorlds()) {
                org.bukkit.World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    continue;
                }
                Collection<Plot> plotsSnapshot = new ArrayList<>(plotManager.getAllPlots(world));
                allClusters.addAll(clusterService.clusterPlots(plotsSnapshot));
            }
            return allClusters;
        }).thenAccept(clusters -> Bukkit.getScheduler().runTask(plugin, () -> applyClusters(clusters)));
    }

    private void applyClusters(List<Set<Plot>> clusters) {
        Set<String> touchedDistricts = new HashSet<>();

        for (Set<Plot> cluster : clusters) {
            Set<String> clusterPlotIds = new HashSet<>();
            for (Plot plot : cluster) {
                clusterPlotIds.add(plot.getPlotId());
            }

            District district = findExistingDistrict(clusterPlotIds);
            if (district == null) {
                district = new District("district_" + (++districtCounter), "Viertel " + districtCounter, clusterPlotIds, DistrictLevel.DORF, System.currentTimeMillis());
                districts.put(district.getId(), district);
            } else {
                for (String plotId : new HashSet<>(district.getPlotIds())) {
                    if (!clusterPlotIds.contains(plotId)) {
                        district.removePlot(plotId);
                    }
                }
                for (String plotId : clusterPlotIds) {
                    district.addPlot(plotId);
                }
            }

            touchedDistricts.add(district.getId());
            for (String plotId : clusterPlotIds) {
                plotToDistrict.put(plotId, district.getId());
            }
            storage.saveDistrictAsync(district);
        }

        List<String> removed = new ArrayList<>();
        for (String districtId : new HashSet<>(districts.keySet())) {
            if (!touchedDistricts.contains(districtId)) {
                removed.add(districtId);
            }
        }
        for (String districtId : removed) {
            districts.remove(districtId);
            storage.deleteDistrict(districtId);
        }
    }

    private District findExistingDistrict(Set<String> plotIds) {
        for (String plotId : plotIds) {
            String districtId = plotToDistrict.get(plotId);
            if (districtId != null && districts.containsKey(districtId)) {
                return districts.get(districtId);
            }
        }
        return null;
    }

    public void updateBuiltBlocks(Plot plot, int delta) {
        District district = getDistrictForPlot(plot);
        if (district == null) {
            return;
        }
        district.getProgress().addBuiltBlocks(delta);
        evaluateGoalsAndLevel(district);
        storage.saveDistrictAsync(district);
    }

    public void updateActivePlayers(Map<String, Set<UUID>> districtPlayers) {
        for (Map.Entry<String, Set<UUID>> entry : districtPlayers.entrySet()) {
            District district = districts.get(entry.getKey());
            if (district == null) {
                continue;
            }
            district.getProgress().setActivePlayers(entry.getValue().size());
            evaluateGoalsAndLevel(district);
            storage.saveDistrictAsync(district);
        }
    }

    private void evaluateGoalsAndLevel(District district) {
        for (DistrictGoal goal : goalCatalog.getDefaultGoals()) {
            if (!district.getProgress().getCompletedGoals().contains(goal.getId()) && goal.isCompleted(district)) {
                district.getProgress().markGoalCompleted(goal.getId());
            }
        }

        boolean leveledUp;
        do {
            leveledUp = false;
            DistrictLevel next = district.getLevel().next();
            if (next != district.getLevel()
                    && district.getProgress().getBuiltBlocks() >= next.getRequiredBuiltBlocks()
                    && district.getProgress().getActivePlayers() >= next.getRequiredActivePlayers()
                    && district.getProgress().getCompletedGoals().size() >= next.getRequiredGoals()) {
                district.setLevel(next);
                leveledUp = true;
            }
        } while (leveledUp);
    }

    @EventHandler
    public void onPlotCreated(PlotCreatedEvent event) {
        rebuildClustersAsync();
    }

    @EventHandler
    public void onPlotClaimed(PlotClaimedEvent event) {
        rebuildClustersAsync();
    }
}
