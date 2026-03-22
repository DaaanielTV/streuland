package de.streuland.approval;

import de.streuland.discord.DiscordNotifier;
import de.streuland.path.PathGenerator;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class PlotApprovalFlowTest {

    @Test
    void createPendingThenApprove(@TempDir File tempDir) {
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        FileConfiguration config = Mockito.mock(FileConfiguration.class);
        PlotManager plotManager = Mockito.mock(PlotManager.class);
        PathGenerator pathGenerator = Mockito.mock(PathGenerator.class);
        DiscordNotifier notifier = Mockito.mock(DiscordNotifier.class);
        Player player = Mockito.mock(Player.class);
        World world = Mockito.mock(World.class);
        Server server = Mockito.mock(Server.class);
        BukkitScheduler scheduler = Mockito.mock(BukkitScheduler.class);

        Mockito.when(plugin.getDataFolder()).thenReturn(tempDir);
        Mockito.when(plugin.getConfig()).thenReturn(config);
        Mockito.when(plugin.getServer()).thenReturn(server);
        Mockito.when(server.getScheduler()).thenReturn(scheduler);
        Mockito.when(config.getBoolean("plot.create-requires-approval", false)).thenReturn(true);
        Mockito.when(config.getStringList("plot.create-approval-whitelist")).thenReturn(Collections.<String>emptyList());
        Mockito.when(config.getString("discord.approval-base-url", "")).thenReturn("http://localhost:8080");
        Mockito.when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        Mockito.when(player.getName()).thenReturn("Tester");
        Mockito.when(player.getWorld()).thenReturn(world);
        Mockito.when(world.getName()).thenReturn("world_main");
        Mockito.when(pathGenerator.generatePath(Mockito.any(Plot.class))).thenReturn(Collections.<PathGenerator.BlockPosition>emptyList());

        Plot plot = Mockito.mock(Plot.class);
        Mockito.when(plot.getPlotId()).thenReturn("world_main_plot_1");
        Mockito.when(plotManager.createPlotAsync(Mockito.any(UUID.class), Mockito.eq(world))).thenReturn(CompletableFuture.completedFuture(plot));

        PlotApprovalService service = new PlotApprovalService(plugin, plotManager, pathGenerator, notifier);
        PlotApprovalRequest req = service.createPending(player);
        assertNotNull(service.getPending(req.getId()));

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getWorld("world_main")).thenReturn(world);
            bukkit.when(() -> Bukkit.getPlayer(Mockito.any(UUID.class))).thenReturn(null);
            assertTrue(service.approve(req.getId()));
            assertNull(service.getPending(req.getId()));
        }
    }
}
