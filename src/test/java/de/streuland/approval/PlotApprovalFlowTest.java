package de.streuland.approval;

import de.streuland.discord.DiscordNotifier;
import de.streuland.path.PathGenerator;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlotApprovalFlowTest {

    @Test
    void requestThenApproveTransitionsToApproved(@TempDir File tempDir) {
        Fixture fixture = Fixture.create(tempDir);
        Mockito.when(fixture.config.getLong("plot.approval.expiration-seconds", 90L)).thenReturn(120L);

        PlotApprovalRequest request = fixture.service.request(fixture.requester, PlotApprovalActionType.DELETE, "plot_1", Collections.emptyMap());
        assertEquals(PlotApprovalRequest.Status.PENDING, fixture.service.getPending(request.getId()).getStatus());

        Player moderator = Mockito.mock(Player.class);
        Mockito.when(moderator.hasPermission("streuland.plot.approval")).thenReturn(true);
        Mockito.when(fixture.plotManager.deletePlot("plot_1", fixture.requester.getUniqueId(), true)).thenReturn(true);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(fixture.requester.getUniqueId())).thenReturn(null);
            assertTrue(fixture.service.approve(request.getId(), moderator, "looks good"));
        }

        PlotApprovalRequest stored = fixture.service.getAllRequests().stream()
                .filter(req -> req.getId().equals(request.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(PlotApprovalRequest.Status.APPROVED, stored.getStatus());
    }

    @Test
    void denyAndCancelTransitionsPersist(@TempDir File tempDir) {
        Fixture fixture = Fixture.create(tempDir);
        Mockito.when(fixture.config.getLong("plot.approval.expiration-seconds", 90L)).thenReturn(300L);

        PlotApprovalRequest denyReq = fixture.service.request(fixture.requester, PlotApprovalActionType.UNCLAIM, "plot_2", Collections.emptyMap());
        Player moderator = Mockito.mock(Player.class);
        Mockito.when(moderator.hasPermission("streuland.plot.approval")).thenReturn(true);

        try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.getPlayer(fixture.requester.getUniqueId())).thenReturn(null);
            assertTrue(fixture.service.deny(denyReq.getId(), moderator, "policy"));
        }

        PlotApprovalRequest denied = fixture.service.getAllRequests().stream()
                .filter(req -> req.getId().equals(denyReq.getId())).findFirst().orElseThrow();
        assertEquals(PlotApprovalRequest.Status.DENIED, denied.getStatus());

        PlotApprovalRequest cancelReq = fixture.service.request(fixture.requester, PlotApprovalActionType.DELETE, "plot_3", Collections.emptyMap());
        assertTrue(fixture.service.cancel(cancelReq.getId(), fixture.requester, "changed mind"));

        PlotApprovalRequest canceled = fixture.service.getAllRequests().stream()
                .filter(req -> req.getId().equals(cancelReq.getId())).findFirst().orElseThrow();
        assertEquals(PlotApprovalRequest.Status.CANCELED, canceled.getStatus());
    }

    @Test
    void pendingRequestExpiresAndIsNotListed(@TempDir File tempDir) {
        Fixture fixture = Fixture.create(tempDir);
        Mockito.when(fixture.config.getLong("plot.approval.expiration-seconds", 90L)).thenReturn(1L);

        PlotApprovalRequest request = fixture.service.request(fixture.requester, PlotApprovalActionType.DELETE, "plot_9", Collections.emptyMap());
        assertNotNull(fixture.service.getPending(request.getId()));

        try {
            Thread.sleep(1200L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("sleep interrupted", e);
        }

        fixture.service.expireRequests();
        assertNull(fixture.service.getPending(request.getId()));
        assertTrue(fixture.service.listPending().isEmpty());

        PlotApprovalRequest expired = fixture.service.getAllRequests().stream()
                .filter(req -> req.getId().equals(request.getId())).findFirst().orElseThrow();
        assertEquals(PlotApprovalRequest.Status.EXPIRED, expired.getStatus());
    }

    private static final class Fixture {
        private final PlotApprovalService service;
        private final FileConfiguration config;
        private final PlotManager plotManager;
        private final Player requester;

        private Fixture(PlotApprovalService service, FileConfiguration config, PlotManager plotManager, Player requester) {
            this.service = service;
            this.config = config;
            this.plotManager = plotManager;
            this.requester = requester;
        }

        static Fixture create(File tempDir) {
            JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
            FileConfiguration config = Mockito.mock(FileConfiguration.class);
            PlotManager plotManager = Mockito.mock(PlotManager.class);
            PathGenerator pathGenerator = Mockito.mock(PathGenerator.class);
            DiscordNotifier notifier = Mockito.mock(DiscordNotifier.class);
            Player requester = Mockito.mock(Player.class);
            World world = Mockito.mock(World.class);
            Server server = Mockito.mock(Server.class);
            BukkitScheduler scheduler = Mockito.mock(BukkitScheduler.class);

            Mockito.when(plugin.getDataFolder()).thenReturn(tempDir);
            Mockito.when(plugin.getConfig()).thenReturn(config);
            Mockito.when(plugin.getServer()).thenReturn(server);
            Mockito.when(server.getScheduler()).thenReturn(scheduler);

            Mockito.when(config.getBoolean("plot.create-requires-approval", false)).thenReturn(true);
            Mockito.when(config.getStringList("plot.create-approval-whitelist")).thenReturn(Collections.emptyList());
            Mockito.when(config.getString("discord.approval-base-url", "")).thenReturn("http://localhost:8080");
            Mockito.when(config.getString("plot.approval.rules.delete", null)).thenReturn("MODERATOR_ONLY");
            Mockito.when(config.getString("plot.approval.rules.unclaim", null)).thenReturn("MODERATOR_ONLY");
            Mockito.when(config.getStringList("plot.approval.configurable.approvers")).thenReturn(Collections.emptyList());

            Mockito.when(requester.getUniqueId()).thenReturn(UUID.randomUUID());
            Mockito.when(requester.getName()).thenReturn("Requester");
            Mockito.when(requester.getWorld()).thenReturn(world);
            Mockito.when(world.getName()).thenReturn("world_main");

            Plot plot = Mockito.mock(Plot.class);
            Mockito.when(plot.getPlotId()).thenReturn("plot_1");
            Mockito.when(plot.getOwner()).thenReturn(requester.getUniqueId());
            Mockito.when(plotManager.getAllPlots()).thenReturn(Collections.singletonList(plot));

            return new Fixture(new PlotApprovalService(plugin, plotManager, pathGenerator, notifier), config, plotManager, requester);
        }
    }
}
