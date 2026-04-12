package de.streuland.district;

import de.streuland.event.PlotClaimedEvent;
import de.streuland.event.PlotCreatedEvent;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
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
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages district membership, shared settings, progress tracking, and persistence.
 */
public class DistrictManager implements Listener {
    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final DistrictStorage storage;
    private final DistrictGoalCatalog goalCatalog;
    private final Map<String, District> districts = new HashMap<>();
    private final Map<String, String> plotToDistrict = new HashMap<>();
    private int districtCounter = 0;

    public DistrictManager(JavaPlugin plugin, PlotManager plotManager) {
        this(plugin, plotManager, new DistrictStorage(plugin));
    }

    DistrictManager(JavaPlugin plugin, PlotManager plotManager, DistrictStorage storage) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.storage = storage;
        this.goalCatalog = new DistrictGoalCatalog();

        for (District district : storage.getAllDistricts()) {
            registerDistrict(district);
        }
    }

    public DistrictStorage getStorage() { return storage; }

    public District getDistrictForPlot(Plot plot) {
        if (plot == null) return null;
        String districtId = plotToDistrict.get(plot.getPlotId());
        return districtId != null ? districts.get(districtId) : null;
    }

    public District getDistrictById(String id) { return districts.get(id); }
    public Collection<District> getAllDistricts() { return new ArrayList<>(districts.values()); }
    public District getDistrictForPlayer(UUID playerId) {
        if (playerId == null) {
            return null;
        }
        for (District district : districts.values()) {
            if (district.hasMember(playerId)) {
                return district;
            }
        }
        return null;
    }

    public synchronized District createDistrict(String name, Collection<Plot> plots) {
        UUID ownerId = null;
        Set<String> plotIds = new HashSet<>();
        for (Plot plot : plots) {
            if (plot != null) {
                plotIds.add(plot.getPlotId());
                if (ownerId == null) {
                    ownerId = plot.getOwner();
                }
            }
        }
        if (plotIds.isEmpty()) {
            throw new IllegalArgumentException("District requires at least one plot");
        }
        District district = new District(nextDistrictId(), name == null || name.trim().isEmpty() ? defaultDistrictName() : name.trim(), plotIds, DistrictLevel.DORF, System.currentTimeMillis());
        for (String plotId : plotIds) {
            movePlotToDistrict(plotId, district);
        }
        if (ownerId != null) {
            district.upsertMember(ownerId, DistrictRole.OWNER);
        }
        registerDistrict(district);
        storage.saveDistrict(district);
        return district;
    }

    public synchronized String createInvite(UUID actor) {
        District district = getDistrictForPlayer(actor);
        if (district == null) {
            return null;
        }
        DistrictRole role = district.getRole(actor);
        if (role == null || !role.canManageDistrict()) {
            return null;
        }
        String code = Long.toHexString(ThreadLocalRandom.current().nextLong()).substring(0, 6).toUpperCase();
        district.putInviteCode(code, actor);
        storage.saveDistrict(district);
        return code;
    }

    public synchronized boolean joinByInviteCode(UUID playerId, String inviteCode) {
        if (playerId == null || inviteCode == null || inviteCode.isBlank() || getDistrictForPlayer(playerId) != null) {
            return false;
        }
        for (District district : districts.values()) {
            UUID invitedBy = district.consumeInviteCode(inviteCode.toUpperCase());
            if (invitedBy != null) {
                district.upsertMember(playerId, DistrictRole.VISITOR);
                storage.saveDistrict(district);
                return true;
            }
        }
        return false;
    }

    public synchronized boolean leaveDistrict(UUID playerId) {
        District district = getDistrictForPlayer(playerId);
        if (district == null) {
            return false;
        }
        DistrictRole role = district.getRole(playerId);
        if (role == DistrictRole.OWNER) {
            return false;
        }
        district.removeMember(playerId);
        storage.saveDistrict(district);
        return true;
    }

    public synchronized boolean disbandDistrict(UUID actor) {
        District district = getDistrictForPlayer(actor);
        if (district == null || district.getRole(actor) != DistrictRole.OWNER) {
            return false;
        }
        districts.remove(district.getId());
        for (String plotId : new HashSet<>(district.getPlotIds())) {
            plotToDistrict.remove(plotId);
        }
        storage.deleteDistrict(district.getId());
        return true;
    }

    public synchronized boolean addPlotToDistrict(String districtId, Plot plot) {
        District district = districts.get(districtId);
        if (district == null || plot == null) {
            return false;
        }
        movePlotToDistrict(plot.getPlotId(), district);
        storage.saveDistrict(district);
        return true;
    }

    public synchronized boolean removePlotFromDistrict(String districtId, String plotId) {
        District district = districts.get(districtId);
        if (district == null || plotId == null || !district.getPlotIds().contains(plotId)) {
            return false;
        }
        district.removePlot(plotId);
        plotToDistrict.remove(plotId);
        if (district.getPlotIds().isEmpty()) {
            districts.remove(districtId);
            storage.deleteDistrict(districtId);
        } else {
            storage.saveDistrict(district);
        }
        return true;
    }

    public synchronized boolean renameDistrict(String districtId, String name) {
        District district = districts.get(districtId);
        if (district == null || name == null || name.trim().isEmpty()) {
            return false;
        }
        district.setName(name.trim());
        storage.saveDistrict(district);
        return true;
    }

    public synchronized boolean renameDistrict(UUID actor, String name) {
        District district = getDistrictForPlayer(actor);
        if (district == null) {
            return false;
        }
        DistrictRole role = district.getRole(actor);
        if (role == null || !role.canManageDistrict()) {
            return false;
        }
        return renameDistrict(district.getId(), name);
    }

    public synchronized boolean setSharedRule(String districtId, String ruleKey, boolean value) {
        District district = districts.get(districtId);
        if (district == null || ruleKey == null || ruleKey.trim().isEmpty()) {
            return false;
        }
        district.getSharedRules().put(ruleKey.trim(), value);
        storage.saveDistrict(district);
        return true;
    }

    public synchronized boolean configureSharedBank(String districtId, boolean enabled, double balance) {
        District district = districts.get(districtId);
        if (district == null) {
            return false;
        }
        district.setSharedBankEnabled(enabled);
        district.setSharedBankBalance(Math.max(0D, balance));
        storage.saveDistrict(district);
        return true;
    }

    public synchronized boolean setDistrictSpawn(String districtId, Plot plot) {
        District district = districts.get(districtId);
        if (district == null || plot == null) {
            return false;
        }
        district.setSpawn(plotManager.getWorldForPlot(plot.getPlotId()).getName(), plot.getCenterX(), plot.getSpawnY(), plot.getCenterZ());
        storage.saveDistrict(district);
        return true;
    }

    public synchronized boolean claimPlotToActorDistrict(UUID actor, String plotId) {
        District district = getDistrictForPlayer(actor);
        if (district == null || plotId == null || plotId.isBlank()) {
            return false;
        }
        DistrictRole role = district.getRole(actor);
        if (role == null || !role.canManageDistrict()) {
            return false;
        }
        Plot plot = resolvePlot(plotId);
        if (plot == null) {
            return false;
        }
        movePlotToDistrict(plotId, district);
        storage.saveDistrict(district);
        return true;
    }

    public synchronized boolean unclaimPlotFromActorDistrict(UUID actor, String plotId) {
        District district = getDistrictForPlayer(actor);
        if (district == null || plotId == null || plotId.isBlank()) {
            return false;
        }
        DistrictRole role = district.getRole(actor);
        if (role == null || !role.canManageDistrict()) {
            return false;
        }
        return removePlotFromDistrict(district.getId(), plotId);
    }

    public boolean canUseDistrictPermissionOverride(Plot plot, UUID actor, de.streuland.plot.Permission permission) {
        if (plot == null || actor == null || permission == null) {
            return false;
        }
        District district = getDistrictForPlot(plot);
        if (district == null || !district.isRoleOverrideEnabled()) {
            return false;
        }
        DistrictRole role = district.getRole(actor);
        return role != null && role.grants(permission);
    }

    public synchronized boolean setRoleOverrideEnabled(UUID actor, boolean enabled) {
        District district = getDistrictForPlayer(actor);
        if (district == null) {
            return false;
        }
        DistrictRole role = district.getRole(actor);
        if (role == null || !role.canManageDistrict()) {
            return false;
        }
        district.setRoleOverrideEnabled(enabled);
        storage.saveDistrict(district);
        return true;
    }

    public synchronized boolean clearDistrictSpawn(String districtId) {
        District district = districts.get(districtId);
        if (district == null) {
            return false;
        }
        district.clearSpawn();
        storage.saveDistrict(district);
        return true;
    }

    public boolean getEffectiveRule(Plot plot, String ruleKey, boolean fallback) {
        District district = getDistrictForPlot(plot);
        if (district != null && district.getSharedRules().containsKey(ruleKey)) {
            return district.getSharedRules().get(ruleKey);
        }
        PlotData data = getPlotData(plot);
        if (data != null && data.getFlagOverrides().containsKey(ruleKey)) {
            return data.getFlagOverrides().get(ruleKey);
        }
        return fallback;
    }

    public CompletableFuture<Void> rebuildClustersAsync() {
        return CompletableFuture.runAsync(this::ensureDistrictsForAllPlots);
    }

    public synchronized void ensureDistrictsForAllPlots() {
        Set<String> existingPlots = new HashSet<>();
        for (Plot plot : plotManager.getAllPlots()) {
            existingPlots.add(plot.getPlotId());
            if (!plotToDistrict.containsKey(plot.getPlotId())) {
                District district = new District(nextDistrictId(), plot.getPlotId(), java.util.Collections.singleton(plot.getPlotId()), DistrictLevel.DORF, System.currentTimeMillis());
                registerDistrict(district);
                storage.saveDistrict(district);
            }
        }
        for (District district : new ArrayList<>(districts.values())) {
            for (String plotId : new HashSet<>(district.getPlotIds())) {
                if (!existingPlots.contains(plotId)) {
                    district.removePlot(plotId);
                    plotToDistrict.remove(plotId);
                }
            }
            if (district.getPlotIds().isEmpty()) {
                districts.remove(district.getId());
                storage.deleteDistrict(district.getId());
            } else {
                storage.saveDistrict(district);
            }
        }
    }

    public void updateBuiltBlocks(Plot plot, int delta) {
        District district = getDistrictForPlot(plot);
        if (district == null) return;
        district.getProgress().addBuiltBlocks(delta);
        evaluateGoalsAndLevel(district);
        storage.saveDistrictAsync(district);
    }

    public void updateActivePlayers(Map<String, Set<UUID>> districtPlayers) {
        for (Map.Entry<String, Set<UUID>> entry : districtPlayers.entrySet()) {
            District district = districts.get(entry.getKey());
            if (district == null) continue;
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

    private PlotData getPlotData(Plot plot) {
        if (plot == null) return null;
        PlotStorage plotStorage = plotManager.getStorage(plotManager.getWorldForPlot(plot.getPlotId()));
        return plotStorage == null ? null : plotStorage.getPlotData(plot.getPlotId());
    }

    private void registerDistrict(District district) {
        districts.put(district.getId(), district);
        districtCounter = Math.max(districtCounter, extractDistrictNumber(district.getId()));
        for (String plotId : district.getPlotIds()) {
            plotToDistrict.put(plotId, district.getId());
        }
    }

    private void movePlotToDistrict(String plotId, District targetDistrict) {
        String previousDistrictId = plotToDistrict.get(plotId);
        if (previousDistrictId != null && !previousDistrictId.equals(targetDistrict.getId())) {
            District previous = districts.get(previousDistrictId);
            if (previous != null) {
                previous.removePlot(plotId);
                if (previous.getPlotIds().isEmpty()) {
                    districts.remove(previousDistrictId);
                    storage.deleteDistrict(previousDistrictId);
                } else {
                    storage.saveDistrict(previous);
                }
            }
        }
        targetDistrict.addPlot(plotId);
        plotToDistrict.put(plotId, targetDistrict.getId());
    }

    private Plot resolvePlot(String plotId) {
        for (Plot plot : plotManager.getAllPlots()) {
            if (plotId.equalsIgnoreCase(plot.getPlotId())) {
                return plot;
            }
        }
        return null;
    }

    private String nextDistrictId() { return "district_" + (++districtCounter); }
    private String defaultDistrictName() { return "District " + districtCounter; }

    private int extractDistrictNumber(String districtId) {
        try {
            int idx = districtId.lastIndexOf('_');
            return idx >= 0 ? Integer.parseInt(districtId.substring(idx + 1)) : 0;
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    @EventHandler public void onPlotCreated(PlotCreatedEvent event) { ensureDistrictsForAllPlots(); }
    @EventHandler public void onPlotClaimed(PlotClaimedEvent event) { ensureDistrictsForAllPlots(); }
}
