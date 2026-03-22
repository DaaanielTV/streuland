package de.streuland.plot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PlotRolePermissionTest {

    @AfterEach
    void resetDefaults() {
        Plot.resetRolePermissions();
    }

    @Test
    void permissionsAreUnionedAcrossMultipleRoles() {
        UUID owner = UUID.randomUUID();
        UUID teammate = UUID.randomUUID();
        Plot plot = new Plot("plot_1", 0, 0, 64, owner, System.currentTimeMillis(), 64, Plot.PlotState.CLAIMED);

        plot.assignRole(teammate, Role.BUILDER);
        plot.assignRole(teammate, Role.REDSTONE);

        assertTrue(plot.isAllowed(teammate, Permission.BUILD));
        assertTrue(plot.isAllowed(teammate, Permission.CONTAINER_ACCESS));
        assertTrue(plot.isAllowed(teammate, Permission.TELEPORT));
        assertFalse(plot.isAllowed(teammate, Permission.TRANSFER));
    }

    @Test
    void customPlotRolesCanBeCreatedUpdatedAndRemoved() {
        UUID owner = UUID.randomUUID();
        UUID helper = UUID.randomUUID();
        Plot plot = new Plot("plot_2", 0, 0, 64, owner, System.currentTimeMillis(), 64, Plot.PlotState.CLAIMED);

        assertTrue(plot.createRole("farmhand", EnumSet.of(Permission.BREAK, Permission.CONTAINER_ACCESS)));
        plot.assignRole(helper, "farmhand");
        assertTrue(plot.isAllowed(helper, Permission.CONTAINER_ACCESS));
        assertFalse(plot.isAllowed(helper, Permission.BUILD));

        assertTrue(plot.updateRole("farmhand", EnumSet.of(Permission.BUILD, Permission.CONTAINER_ACCESS)));
        assertTrue(plot.isAllowed(helper, Permission.BUILD));

        assertTrue(plot.removeRoleDefinition("farmhand"));
        assertFalse(plot.isAllowed(helper, Permission.BUILD));
    }

    @Test
    void customRoleMappingsAreAppliedToDefaultRoles() {
        Map<Role, Set<Permission>> mappings = new EnumMap<>(Role.class);
        for (Role role : Role.values()) {
            mappings.put(role, EnumSet.noneOf(Permission.class));
        }
        mappings.put(Role.OWNER, EnumSet.allOf(Permission.class));
        mappings.put(Role.CO_OWNER, EnumSet.of(Permission.BUILD));
        Plot.setRolePermissions(mappings);

        UUID owner = UUID.randomUUID();
        UUID coOwner = UUID.randomUUID();
        Plot plot = new Plot("plot_3", 0, 0, 64, owner, System.currentTimeMillis(), 64, Plot.PlotState.CLAIMED);
        plot.assignRole(coOwner, Role.CO_OWNER);

        assertTrue(plot.isAllowed(coOwner, Permission.BUILD));
        assertFalse(plot.isAllowed(coOwner, Permission.TRANSFER));
    }
}
