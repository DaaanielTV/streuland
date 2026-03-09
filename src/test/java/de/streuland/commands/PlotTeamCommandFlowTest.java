package de.streuland.commands;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.Role;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlotTeamCommandFlowTest {

    @Test
    void ownerInvitesBuilderAndPromotesToCoOwner() {
        PlotManager manager = mock(PlotManager.class);
        PlotTeamCommand command = new PlotTeamCommand(manager);

        Player owner = mock(Player.class);
        World world = mock(World.class);
        when(owner.getWorld()).thenReturn(world);
        when(owner.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        UUID ownerId = UUID.randomUUID();
        when(owner.getUniqueId()).thenReturn(ownerId);

        UUID targetId = UUID.randomUUID();
        Plot plot = new Plot("plot_1", 0, 0, 64, ownerId, System.currentTimeMillis(), 64, Plot.PlotState.CLAIMED);
        when(manager.getPlotAt(any(World.class), anyInt(), anyInt())).thenReturn(plot);
        when(manager.assignRole(eq("plot_1"), eq(ownerId), eq(targetId), any(Role.class))).thenReturn(true);

        OfflinePlayer target = mock(OfflinePlayer.class);
        when(target.getUniqueId()).thenReturn(targetId);
        when(target.getName()).thenReturn("BuilderGuy");

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getOfflinePlayer("BuilderGuy")).thenReturn(target);

            command.execute(owner, new String[]{"team", "invite", "BuilderGuy", "BUILDER"});
            verify(manager).assignRole("plot_1", ownerId, targetId, Role.BUILDER);

            plot.assignRole(targetId, Role.BUILDER);
            command.execute(owner, new String[]{"team", "promote", "BuilderGuy"});
            verify(manager).assignRole("plot_1", ownerId, targetId, Role.MEMBER);

            plot.assignRole(targetId, Role.MEMBER);
            command.execute(owner, new String[]{"team", "promote", "BuilderGuy"});
            verify(manager).assignRole("plot_1", ownerId, targetId, Role.CO_OWNER);
        }
    }
}
