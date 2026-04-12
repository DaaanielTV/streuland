package de.streuland.district;

import org.bukkit.Location;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private final Map<UUID, DistrictMember> members;
    private final Map<String, UUID> inviteCodes;
    private boolean sharedBankEnabled;
    private double sharedBankBalance;
    private boolean roleOverrideEnabled;
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
        this.members = new HashMap<>();
        this.inviteCodes = new HashMap<>();
        this.roleOverrideEnabled = false;
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
    public Map<UUID, DistrictMember> getMembers() { return Collections.unmodifiableMap(members); }
    public Map<String, UUID> getInviteCodes() { return Collections.unmodifiableMap(inviteCodes); }
    public boolean isSharedBankEnabled() { return sharedBankEnabled; }
    public void setSharedBankEnabled(boolean sharedBankEnabled) { this.sharedBankEnabled = sharedBankEnabled; }
    public double getSharedBankBalance() { return sharedBankBalance; }
    public void setSharedBankBalance(double sharedBankBalance) { this.sharedBankBalance = sharedBankBalance; }
    public boolean isRoleOverrideEnabled() { return roleOverrideEnabled; }
    public void setRoleOverrideEnabled(boolean roleOverrideEnabled) { this.roleOverrideEnabled = roleOverrideEnabled; }
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

    public DistrictRole getRole(UUID playerId) {
        DistrictMember member = members.get(playerId);
        return member == null ? null : member.getRole();
    }

    public boolean hasMember(UUID playerId) {
        return playerId != null && members.containsKey(playerId);
    }

    public void upsertMember(UUID playerId, DistrictRole role) {
        if (playerId == null || role == null) {
            return;
        }
        members.put(playerId, new DistrictMember(playerId, role, System.currentTimeMillis()));
    }

    public void removeMember(UUID playerId) {
        if (playerId == null) {
            return;
        }
        members.remove(playerId);
    }

    public void putInviteCode(String code, UUID invitedBy) {
        if (code == null || code.isBlank() || invitedBy == null) {
            return;
        }
        inviteCodes.put(code, invitedBy);
    }

    public UUID consumeInviteCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return inviteCodes.remove(code);
    }
}
