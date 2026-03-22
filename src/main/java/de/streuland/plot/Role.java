package de.streuland.plot;

import java.util.*;

public enum Role {
    OWNER("owner", EnumSet.allOf(Permission.class), 100),
    ADMIN_HELPER("admin_helper", EnumSet.of(Permission.INTERACT, Permission.CONTAINER_ACCESS, Permission.TELEPORT, Permission.ROLE_MANAGE), 80),
    CO_OWNER("co_owner", EnumSet.of(Permission.BUILD, Permission.BREAK, Permission.INTERACT, Permission.CONTAINER_ACCESS, Permission.TELEPORT, Permission.TRANSFER, Permission.ROLE_MANAGE), 70),
    MEMBER("member", EnumSet.of(Permission.BUILD, Permission.BREAK, Permission.INTERACT, Permission.CONTAINER_ACCESS, Permission.TELEPORT), 60),
    BUILDER("builder", EnumSet.of(Permission.BUILD, Permission.BREAK, Permission.TELEPORT), 50),
    DECORATOR("decorator", EnumSet.of(Permission.BUILD, Permission.INTERACT, Permission.TELEPORT), 40),
    REDSTONE("redstone", EnumSet.of(Permission.INTERACT, Permission.CONTAINER_ACCESS, Permission.TELEPORT), 30),
    VISITOR("visitor", EnumSet.of(Permission.TELEPORT), 10);

    private final String id;
    private final Set<Permission> defaultPermissions;
    private final int priority;

    Role(String id, Set<Permission> defaultPermissions, int priority) {
        this.id = id;
        this.defaultPermissions = EnumSet.copyOf(defaultPermissions);
        this.priority = priority;
    }

    public String getId() { return id; }
    public Set<Permission> getDefaultPermissions() { return EnumSet.copyOf(defaultPermissions); }
    public int getPriority() { return priority; }
    public boolean matches(String roleId) { return id.equalsIgnoreCase(roleId) || name().equalsIgnoreCase(roleId); }

    public static boolean isCoreRole(String roleId) {
        return fromId(roleId).isPresent();
    }

    public static Optional<Role> fromId(String roleId) {
        if (roleId == null) {
            return Optional.empty();
        }
        for (Role role : values()) {
            if (role.matches(roleId)) {
                return Optional.of(role);
            }
        }
        return Optional.empty();
    }

    public static List<Role> valuesByPriority() {
        List<Role> roles = new ArrayList<>(Arrays.asList(values()));
        roles.sort(Comparator.comparingInt(Role::getPriority).reversed());
        return roles;
    }
}
