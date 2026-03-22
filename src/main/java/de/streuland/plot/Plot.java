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
        this.roleDefinitions = new LinkedHashMap<>();
        this.playerRoles = new HashMap<>();
        seedDefaultRoleDefinitions();
        if (owner != null) {
            assignRole(owner, Role.OWNER);
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
        if (role != null) {
            assignRole(player, role.getId());
        }
    }

    public void assignRole(UUID player, String roleId) {
        String normalized = normalizeRoleId(roleId);
        if (player == null || normalized == null || !roleDefinitions.containsKey(normalized)) {
            return;
        }
        playerRoles.computeIfAbsent(player, ignored -> new LinkedHashSet<>()).add(normalized);
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
            replacePlayerRoles(player, Collections.singleton(Role.OWNER.getId()));
            return;
        }
        playerRoles.remove(player);
    }

    public void removeRole(UUID player, String roleId) {
        String normalized = normalizeRoleId(roleId);
        if (player == null || normalized == null) {
            return;
        }
        if (owner != null && owner.equals(player) && Role.OWNER.matches(normalized)) {
            return;
        }
        Set<String> assigned = playerRoles.get(player);
        if (assigned == null) {
            return;
        }
        assigned.remove(normalized);
        if (assigned.isEmpty()) {
            playerRoles.remove(player);
        }
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
        return resolved;
    }

    public Map<UUID, Role> getRoles() {
        Map<UUID, Role> roles = new HashMap<>();
        for (UUID playerId : playerRoles.keySet()) {
            roles.put(playerId, getRole(playerId));
        }
        return roles;
    }

    public Map<UUID, Set<String>> getRoleAssignments() {
        Map<UUID, Set<String>> assignments = new HashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : playerRoles.entrySet()) {
            assignments.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
        return assignments;
    }

    public Map<String, Set<Permission>> getRoleDefinitions() {
        Map<String, Set<Permission>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Set<Permission>> entry : roleDefinitions.entrySet()) {
            copy.put(entry.getKey(), copyPermissions(entry.getValue()));
        }
        return copy;
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
        Set<String> assigned = getAssignedRoleIds(player);
        for (Role role : Role.valuesByPriority()) {
            if (assigned.contains(role.getId())) {
                return role;
            }
        }
        return Role.VISITOR;
    }

    public Set<String> getAssignedRoleIds(UUID player) {
        if (player == null) {
            return Collections.emptySet();
        }
        if (owner != null && owner.equals(player)) {
            return Collections.singleton(Role.OWNER.getId());
        }
        Set<String> assigned = playerRoles.get(player);
        return assigned == null ? Collections.emptySet() : Collections.unmodifiableSet(assigned);
    }

    public Set<UUID> getTrustedPlayers() {
        Set<UUID> players = new HashSet<>(playerRoles.keySet());
        if (owner != null) {
            players.remove(owner);
        }
        return players;
    }

    public void replaceRoles(Map<UUID, Role> replacements) {
        playerRoles.clear();
        if (replacements != null) {
            for (Map.Entry<UUID, Role> entry : replacements.entrySet()) {
                assignRole(entry.getKey(), entry.getValue());
            }
        }
        if (owner != null) {
            assignRole(owner, Role.OWNER);
        }
    }

    public void replaceRoleAssignments(Map<UUID, Set<String>> replacements) {
        playerRoles.clear();
        if (replacements != null) {
            for (Map.Entry<UUID, Set<String>> entry : replacements.entrySet()) {
                replacePlayerRoles(entry.getKey(), entry.getValue());
            }
        }
        if (owner != null) {
            assignRole(owner, Role.OWNER);
        }
    }

    public void replaceRoleDefinitions(Map<String, Set<Permission>> replacements) {
        seedDefaultRoleDefinitions();
        if (replacements == null) {
            return;
        }
        for (Map.Entry<String, Set<Permission>> entry : replacements.entrySet()) {
            String normalized = normalizeRoleId(entry.getKey());
            if (normalized != null) {
                roleDefinitions.put(normalized, copyPermissions(entry.getValue()));
            }
        }
    }

    private void replacePlayerRoles(UUID player, Collection<String> roleIds) {
        if (player == null) {
            return;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (roleIds != null) {
            for (String roleId : roleIds) {
                String normalizedRoleId = normalizeRoleId(roleId);
                if (normalizedRoleId != null && roleDefinitions.containsKey(normalizedRoleId)) {
                    normalized.add(normalizedRoleId);
                }
            }
        }
        if (normalized.isEmpty()) {
            playerRoles.remove(player);
        } else {
            playerRoles.put(player, normalized);
        }
    }

    public static void setRolePermissions(Map<Role, Set<Permission>> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            return;
        }
        DEFAULT_ROLE_PERMISSIONS.clear();
        for (Role role : Role.values()) {
            DEFAULT_ROLE_PERMISSIONS.put(role, copyPermissions(mappings.get(role)));
        }
    }

    public static void resetRolePermissions() {
        DEFAULT_ROLE_PERMISSIONS.clear();
        for (Role role : Role.values()) {
            DEFAULT_ROLE_PERMISSIONS.put(role, role.getDefaultPermissions());
        }
    }

    private static Set<Permission> copyPermissions(Set<Permission> permissions) {
        return permissions == null || permissions.isEmpty()
                ? EnumSet.noneOf(Permission.class)
                : EnumSet.copyOf(permissions);
    }

    private static String normalizeRoleId(String roleId) {
        if (roleId == null) {
            return null;
        }
        String normalized = roleId.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        return normalized.isEmpty() ? null : normalized;
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
