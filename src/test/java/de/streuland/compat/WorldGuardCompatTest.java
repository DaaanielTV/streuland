package de.streuland.compat;

import de.streuland.flags.Flag;
import de.streuland.flags.PlotFlagManager;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import de.streuland.plot.PlotStorage;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class WorldGuardCompatTest {

    @Test
    void syncHappensWhenWorldGuardIsPresent() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);
        Plugin worldGuard = mock(Plugin.class);

        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(pluginManager);
        when(pluginManager.getPlugin("WorldGuard")).thenReturn(worldGuard);
        when(worldGuard.isEnabled()).thenReturn(true);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());

        PlotManager plotManager = mock(PlotManager.class);
        PlotStorage storage = mock(PlotStorage.class);
        World world = mock(World.class);
        Plot plot = new Plot("world_plot_5", 0, 0, 32, UUID.randomUUID(), System.currentTimeMillis(), 64, Plot.PlotState.CLAIMED);

        when(plotManager.getAllPlots()).thenReturn(Arrays.asList(plot));
        when(plotManager.getWorldForPlot(plot.getPlotId())).thenReturn(world);
        when(plotManager.getStorage(world)).thenReturn(storage);
        when(storage.getPlotData(plot.getPlotId())).thenReturn(new de.streuland.plot.PlotData());

        PlotFlagManager flagManager = new PlotFlagManager(plotManager);
        AtomicBoolean synced = new AtomicBoolean(false);
        WorldGuardCompat compat = new WorldGuardCompat(plugin, plotManager, flagManager,
                (p, flags) -> synced.set(p.getPlotId().equals(plot.getPlotId()) && flags.containsKey(Flag.PVP)));

        compat.syncAllPlots();
        assertTrue(synced.get());

        synced.set(false);
        HashMap<Flag, Boolean> update = new HashMap<>();
        update.put(Flag.PVP, false);
        compat.onFlagsUpdated(plot, update);
        assertTrue(synced.get());
    }
}
