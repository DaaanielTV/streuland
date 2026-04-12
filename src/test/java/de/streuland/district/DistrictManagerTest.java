package de.streuland.district;

import de.streuland.dashboard.DashboardDataExporter;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DistrictManagerTest {
    private JavaPlugin plugin;
    private PlotManager plotManager;
    private DistrictManager districtManager;
    private Plot plotOne;
    private Plot plotTwo;
    private Plot plotThree;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(new File(tempDir.toFile(), "plugin-data"));
        when(plugin.getLogger()).thenReturn(Logger.getLogger("district-test"));

        plotManager = mock(PlotManager.class);
        plotOne = new Plot("plot_1", 0, 0, 32, UUID.randomUUID(), 1L, 64, Plot.PlotState.CLAIMED);
        plotTwo = new Plot("plot_2", 32, 0, 32, UUID.randomUUID(), 2L, 64, Plot.PlotState.CLAIMED);
        plotThree = new Plot("plot_3", 64, 0, 32, UUID.randomUUID(), 3L, 64, Plot.PlotState.CLAIMED);
        when(plotManager.getAllPlots()).thenReturn(Arrays.asList(plotOne, plotTwo, plotThree));

        World districtWorld = mock(World.class);
        when(districtWorld.getName()).thenReturn("world_main");
        when(plotManager.getWorldForPlot(anyString())).thenReturn(districtWorld);

        PlotStorage managerStorage = mock(PlotStorage.class);
        when(managerStorage.getPlotData(anyString())).thenReturn(new de.streuland.plot.PlotData());
        when(plotManager.getStorage(any(World.class))).thenReturn(managerStorage);

        districtManager = new DistrictManager(plugin, plotManager);
    }

    @Test
    void createDistrictStoresMultiplePlotsAndLookup() {
        District district = districtManager.createDistrict("Harbor", Arrays.asList(plotOne, plotTwo));

        assertEquals(2, district.getPlotIds().size());
        assertSame(district, districtManager.getDistrictForPlot(plotOne));
        assertSame(district, districtManager.getDistrictForPlot(plotTwo));
        assertNull(districtManager.getDistrictForPlot(plotThree));
    }

    @Test
    void addPlotMovesMembershipBetweenDistricts() {
        District alpha = districtManager.createDistrict("Alpha", Collections.singletonList(plotOne));
        District beta = districtManager.createDistrict("Beta", Collections.singletonList(plotTwo));

        assertTrue(districtManager.addPlotToDistrict(alpha.getId(), plotTwo));

        assertEquals(alpha.getId(), districtManager.getDistrictForPlot(plotTwo).getId());
        assertFalse(beta.getPlotIds().contains(plotTwo.getPlotId()));
        assertTrue(alpha.getPlotIds().contains(plotTwo.getPlotId()));
    }

    @Test
    void districtOverridesPlotDefaultsAndDashboardShowsMembership() {
        District district = districtManager.createDistrict("Market", Arrays.asList(plotOne, plotTwo));
        districtManager.setSharedRule(district.getId(), "pvp", true);
        districtManager.configureSharedBank(district.getId(), true, 250.5D);
        districtManager.setDistrictSpawn(district.getId(), plotOne);

        assertTrue(districtManager.getEffectiveRule(plotOne, "pvp", false));
        assertTrue(district.isSharedBankEnabled());
        assertTrue(district.hasSpawn());

        PlotStorage plotStorage = mock(PlotStorage.class);
        when(plotStorage.getPlotData(anyString())).thenReturn(new de.streuland.plot.PlotData());
        DashboardDataExporter exporter = new DashboardDataExporter(plotStorage, districtManager);

        World world = mock(World.class);
        Block block = mock(Block.class);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(block);
        when(block.getBiome()).thenReturn(Biome.PLAINS);

        String json = exporter.toGeoJson(Collections.singletonList(plotOne), world);
        assertTrue(json.contains("\"districtId\":\"" + district.getId() + "\""));
        assertTrue(json.contains("\"districtName\":\"Market\""));
        assertTrue(json.contains("\"districtSharedBank\":true"));
        assertTrue(json.contains("\"districtHasSpawn\":true"));
    }

    @Test
    void membershipInviteJoinLeaveFlow() {
        UUID ownerId = plotOne.getOwner();
        District district = districtManager.createDistrict("Harbor", Collections.singletonList(plotOne));
        district.upsertMember(ownerId, DistrictRole.OWNER);
        districtManager.getStorage().saveDistrict(district);

        String inviteCode = districtManager.createInvite(ownerId);
        assertNotNull(inviteCode);

        UUID memberId = UUID.randomUUID();
        assertTrue(districtManager.joinByInviteCode(memberId, inviteCode));
        assertEquals(DistrictRole.VISITOR, districtManager.getDistrictForPlayer(memberId).getRole(memberId));
        assertTrue(districtManager.leaveDistrict(memberId));
        assertNull(districtManager.getDistrictForPlayer(memberId));
    }

    @Test
    void districtRoleOverrideCanGrantAccessBeyondPlotTrust() {
        UUID ownerId = plotOne.getOwner();
        UUID builderId = UUID.randomUUID();
        District district = districtManager.createDistrict("Forge", Collections.singletonList(plotOne));
        district.upsertMember(ownerId, DistrictRole.OWNER);
        district.upsertMember(builderId, DistrictRole.BUILDER);
        district.setRoleOverrideEnabled(true);
        districtManager.getStorage().saveDistrict(district);

        assertTrue(districtManager.canUseDistrictPermissionOverride(plotOne, builderId, de.streuland.plot.Permission.BUILD));
        assertFalse(districtManager.canUseDistrictPermissionOverride(plotOne, builderId, de.streuland.plot.Permission.TRANSFER));
    }

    @Test
    void districtStorageBackwardsCompatibilityLoadsWithoutMembersSection() {
        District district = districtManager.createDistrict("Compat", Collections.singletonList(plotOne));
        districtManager.getStorage().saveDistrict(district);

        DistrictStorage reloaded = new DistrictStorage(plugin);
        District loaded = reloaded.getDistrict(district.getId());
        assertNotNull(loaded);
        assertNotNull(loaded.getMembers());
        assertEquals(Collections.singleton(plotOne.getPlotId()), loaded.getPlotIds());
    }
}
