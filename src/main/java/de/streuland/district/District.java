package de.streuland.district;

import org.bukkit.Location;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Represents a district made up of one or more plots with shared settings.
 */
public class District {
    private final String id;
    private String name;
    private final Set<String> plotIds;
    private DistrictLevel level;
    private final DistrictProgress progress;
    private final long createdAt;
    private final Map<String, Boolean> sharedRules;
    private boolean sharedBankEnabled;
    private double sharedBankBalance;
    private String spawnWorld;
    private Double spawnX;
    private Double spawnY;
    private Double spawnZ;

    public District(String id, String name, Set<String> plotIds, DistrictLevel level, long createdAt) {
        this.id = id;
        this.name = name;
        this.plotIds = new HashSet<>(plotIds);
        this.level = level;
        this.progress = new DistrictProgress();
        this.createdAt = createdAt;
        this.sharedRules = new HashMap<>();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Set<String> getPlotIds() { return Collections.unmodifiableSet(plotIds); }
    public void addPlot(String plotId) { if (plotId != null) plotIds.add(plotId); }
    public void removePlot(String plotId) { plotIds.remove(plotId); }
    public DistrictLevel getLevel() { return level; }
    public void setLevel(DistrictLevel level) { this.level = level; }
    public DistrictProgress getProgress() { return progress; }
    public long getCreatedAt() { return createdAt; }
    public Map<String, Boolean> getSharedRules() { return sharedRules; }
    public boolean isSharedBankEnabled() { return sharedBankEnabled; }
    public void setSharedBankEnabled(boolean sharedBankEnabled) { this.sharedBankEnabled = sharedBankEnabled; }
    public double getSharedBankBalance() { return sharedBankBalance; }
    public void setSharedBankBalance(double sharedBankBalance) { this.sharedBankBalance = sharedBankBalance; }
    public String getSpawnWorld() { return spawnWorld; }
    public Double getSpawnX() { return spawnX; }
    public Double getSpawnY() { return spawnY; }
    public Double getSpawnZ() { return spawnZ; }

    public void setSpawn(String world, double x, double y, double z) {
        this.spawnWorld = world;
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
    }

    public void clearSpawn() {
        this.spawnWorld = null;
        this.spawnX = null;
        this.spawnY = null;
        this.spawnZ = null;
    }

    public boolean hasSpawn() {
        return spawnWorld != null && spawnX != null && spawnY != null && spawnZ != null;
    }

    public Location toLocation(org.bukkit.World world) {
        if (!hasSpawn() || world == null || !world.getName().equals(spawnWorld)) {
            return null;
        }
        return new Location(world, spawnX, spawnY, spawnZ);
    }
}
