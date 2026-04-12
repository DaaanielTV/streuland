package de.streuland.district;

import de.streuland.plot.Permission;

import java.util.EnumSet;
import java.util.Set;

public enum DistrictRole {
    OWNER(EnumSet.allOf(Permission.class)),
    CO_OWNER(EnumSet.of(Permission.BUILD, Permission.BREAK, Permission.INTERACT, Permission.CONTAINER_ACCESS, Permission.TRANSFER, Permission.ROLE_MANAGE)),
    MANAGER(EnumSet.of(Permission.BUILD, Permission.BREAK, Permission.INTERACT, Permission.CONTAINER_ACCESS, Permission.ROLE_MANAGE)),
    BUILDER(EnumSet.of(Permission.BUILD, Permission.BREAK, Permission.INTERACT)),
    VISITOR(EnumSet.of(Permission.INTERACT));

    private final Set<Permission> grantedPermissions;

    DistrictRole(Set<Permission> grantedPermissions) {
        this.grantedPermissions = grantedPermissions;
    }

    public boolean grants(Permission permission) {
        return permission != null && grantedPermissions.contains(permission);
    }

    public boolean canManageDistrict() {
        return this == OWNER || this == CO_OWNER || this == MANAGER;
    }
}
