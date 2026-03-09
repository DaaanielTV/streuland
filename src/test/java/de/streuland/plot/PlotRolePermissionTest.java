package de.streuland.plot;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PlotRolePermissionTest {

    @Test
    void builderCannotTransferButCanBuild() {
        UUID owner = UUID.randomUUID();
        UUID builder = UUID.randomUUID();
        Plot plot = new Plot("plot_1", 0, 0, 64, owner, System.currentTimeMillis(), 64, Plot.PlotState.CLAIMED);
        plot.assignRole(builder, Role.BUILDER);

        assertTrue(plot.isAllowed(builder, Permission.BUILD));
        assertFalse(plot.isAllowed(builder, Permission.TRANSFER));
    }

    @Test
    void customRoleMappingsAreApplied() {
        Map<Role, Set<Permission>> mappings = new EnumMap<>(Role.class);
        mappings.put(Role.OWNER, EnumSet.allOf(Permission.class));
        mappings.put(Role.CO_OWNER, EnumSet.of(Permission.BUILD));
        mappings.put(Role.MEMBER, EnumSet.noneOf(Permission.class));
        mappings.put(Role.BUILDER, EnumSet.of(Permission.BUILD));
        mappings.put(Role.VISITOR, EnumSet.noneOf(Permission.class));
        Plot.setRolePermissions(mappings);

        UUID owner = UUID.randomUUID();
        UUID coOwner = UUID.randomUUID();
        Plot plot = new Plot("plot_2", 0, 0, 64, owner, System.currentTimeMillis(), 64, Plot.PlotState.CLAIMED);
        plot.assignRole(coOwner, Role.CO_OWNER);

        assertTrue(plot.isAllowed(coOwner, Permission.BUILD));
        assertFalse(plot.isAllowed(coOwner, Permission.TRANSFER));
    }
}
