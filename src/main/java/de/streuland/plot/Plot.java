package de.streuland.plot;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a single plot in the Streuland world.
 *
 * Data is immutable after creation; modifications should go through PlotManager.
 */
public class Plot {
    /**
     * Enum representing the three possible plot states:
     * - UNCLAIMED: Plot exists but has no owner (anyone can claim it, anyone can build in it)
     * - CLAIMED: Plot has an owner (only owner/trusted can build)
     */
    public enum PlotState {
        UNCLAIMED,
        CLAIMED;

        public AreaType toAreaType() {
            return this == UNCLAIMED ? AreaType.PLOT_UNCLAIMED : AreaType.PLOT_CLAIMED;
        }
    }

    private static final Map<Role, Set<Permission>> ROLE_PERMISSIONS = new EnumMap<>(Role.class);

    static {
        ROLE_PERMISSIONS.put(Role.OWNER, EnumSet.allOf(Permission.class));
        ROLE_PERMISSIONS.put(Role.CO_OWNER, EnumSet.of(Permission.BUILD, Permission.BREAK, Permission.INTERACT, Permission.TRANSFER));
        ROLE_PERMISSIONS.put(Role.MEMBER, EnumSet.of(Permission.BUILD, Permission.BREAK, Permission.INTERACT));
        ROLE_PERMISSIONS.put(Role.BUILDER, EnumSet.of(Permission.BUILD, Permission.BREAK));
        ROLE_PERMISSIONS.put(Role.VISITOR, EnumSet.of(Permission.INTERACT));
    }

    private final String plotId;
    private final int centerX;
    private final int centerZ;
    private final int size;
    private final UUID owner;
    private final PlotState state;
    private final long createdAt;
    private final int spawnY;
    private final Map<UUID, Role> roles;

    public Plot(String plotId, int centerX, int centerZ, int size, UUID owner, long createdAt, int spawnY) {
        this(plotId, centerX, centerZ, size, owner, createdAt, spawnY,
                owner == null ? PlotState.UNCLAIMED : PlotState.CLAIMED);
    }

    public Plot(String plotId, int centerX, int centerZ, int size, UUID owner, long createdAt, int spawnY, PlotState state) {
        this.plotId = plotId;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.size = size;
        this.owner = owner;
        this.state = state;
        this.createdAt = createdAt;
        this.spawnY = spawnY;
        this.roles = new HashMap<>();
        if (owner != null) {
            this.roles.put(owner, Role.OWNER);
        }
    }

    public String getPlotId() {
        return plotId;
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public int getSize() {
        return size;
    }

    public UUID getOwner() {
        return owner;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public int getSpawnY() {
        return spawnY;
    }

    public PlotState getState() {
        return state;
    }

    public AreaType getAreaType() {
        return state.toAreaType();
    }

    public void assignRole(UUID player, Role role) {
        if (player == null || role == null) {
            return;
        }
        roles.put(player, role);
    }

    public void addTrusted(UUID player) {
        if (player == null) {
            return;
        }
        assignRole(player, Role.BUILDER);
    }

    public void removeRole(UUID player) {
        if (player == null) {
            return;
        }
        if (owner != null && owner.equals(player)) {
            roles.put(player, Role.OWNER);
            return;
        }
        roles.remove(player);
    }

    public void removeTrusted(UUID player) {
        removeRole(player);
    }

    public boolean isAllowed(UUID player) {
        return isAllowed(player, Permission.BUILD);
    }

    public boolean isAllowed(UUID player, Permission permission) {
        if (state == PlotState.UNCLAIMED) {
            return true;
        }
        Role role = getRole(player);
        Set<Permission> permissions = ROLE_PERMISSIONS.get(role);
        return permissions != null && permissions.contains(permission);
    }

    public Map<UUID, Role> getRoles() {
        return new HashMap<>(roles);
    }

    public Set<UUID> getTrustedPlayers() {
        Set<UUID> trusted = new HashSet<>();
        for (Map.Entry<UUID, Role> entry : roles.entrySet()) {
            if (entry.getValue() != Role.OWNER) {
                trusted.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(trusted);
    }

    public Role getRole(UUID player) {
        if (player == null) {
            return Role.VISITOR;
        }
        if (owner != null && owner.equals(player)) {
            return Role.OWNER;
        }
        return roles.getOrDefault(player, Role.VISITOR);
    }

    public void replaceRoles(Map<UUID, Role> replacements) {
        roles.clear();
        if (replacements != null) {
            roles.putAll(replacements);
        }
        if (owner != null) {
            roles.put(owner, Role.OWNER);
        }
    }

    public static void setRolePermissions(Map<Role, Set<Permission>> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return;
        }
        ROLE_PERMISSIONS.clear();
        for (Role role : Role.values()) {
            Set<Permission> permissions = mappings.get(role);
            ROLE_PERMISSIONS.put(role, permissions == null || permissions.isEmpty()
                    ? EnumSet.noneOf(Permission.class)
                    : EnumSet.copyOf(permissions));
        }
    }

    public boolean contains(int x, int z) {
        int minX = centerX - (size / 2);
        int maxX = centerX + (size / 2);
        int minZ = centerZ - (size / 2);
        int maxZ = centerZ + (size / 2);
        return x >= minX && x < maxX && z >= minZ && z < maxZ;
    }

    public int getMinX() {
        return centerX - (size / 2);
    }

    public int getMaxX() {
        return centerX + (size / 2);
    }

    public int getMinZ() {
        return centerZ - (size / 2);
    }

    public int getMaxZ() {
        return centerZ + (size / 2);
    }

    public double distance(Plot other) {
        double dx = this.centerX - other.centerX;
        double dz = this.centerZ - other.centerZ;
        return Math.sqrt(dx * dx + dz * dz);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "Plot{id='%s', center=(%d,%d), size=%d, owner=%s}",
                plotId, centerX, centerZ, size, owner);
    }
}
