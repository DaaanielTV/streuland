package de.streuland.commands;

import de.streuland.plot.Plot;
import de.streuland.plot.PlotData;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlotShowcaseCommandTest {

    @Test
    void visitTeleportsToStoredPublicSpawn() {
        PlotManager manager = mock(PlotManager.class);
        PlotShowcaseCommand command = new PlotShowcaseCommand(manager);
        Player player = mock(Player.class);
        World world = mock(World.class);
        Block block = mock(Block.class);
        Location location = new Location(world, 10, 70, 20);

        when(manager.canVisitPublicPlot("world_main_plot_1")).thenReturn(true);
        when(manager.getWorldForPlot("world_main_plot_1")).thenReturn(world);
        when(manager.getTeleportCoordinates("world_main_plot_1")).thenReturn(new int[]{10, 70, 20});
        when(world.getBlockAt(10, 70, 20)).thenReturn(block);
        when(block.getLocation()).thenReturn(location);

        command.handle(player, new String[]{"showcase", "visit", "world_main_plot_1"});

        verify(player).teleport(location);
    }

    @Test
    void listUsesSearchAndIncludesTags() {
        PlotManager manager = mock(PlotManager.class);
        PlotStorage storage = mock(PlotStorage.class);
        PlotShowcaseCommand command = new PlotShowcaseCommand(manager);
        Player player = mock(Player.class);
        World world = mock(World.class);
        UUID owner = UUID.randomUUID();
        Plot plot = new Plot("world_main_plot_9", 0, 0, 64, owner, 1L, 70, Plot.PlotState.CLAIMED);
        PlotData data = new PlotData();
        data.setShowcaseTitle("Museum");
        data.setShowcaseTagsFromText("art, build");

        when(player.getWorld()).thenReturn(world);
        when(manager.searchPublicPlots(world, "museum")).thenReturn(Arrays.asList(plot));
        when(manager.getStorage(world)).thenReturn(storage);
        when(storage.getPlotData(plot.getPlotId())).thenReturn(data);

        command.handle(player, new String[]{"showcase", "search", "museum"});

        verify(manager).searchPublicPlots(world, "museum");
        verify(player, atLeastOnce()).sendMessage(contains("Museum"));
    }
}
