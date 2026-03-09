package de.streuland.compat;

import de.streuland.flags.Flag;
import de.streuland.flags.PlotFlagManager;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class WorldGuardCompat implements PlotFlagManager.FlagSyncHook {
    interface RegionSyncAdapter {
        void sync(Plot plot, Map<Flag, Boolean> flags);
    }

    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final PlotFlagManager plotFlagManager;
    private final boolean worldGuardPresent;
    private final RegionSyncAdapter adapter;

    public WorldGuardCompat(JavaPlugin plugin, PlotManager plotManager, PlotFlagManager plotFlagManager) {
        this(plugin, plotManager, plotFlagManager, null);
    }

    WorldGuardCompat(JavaPlugin plugin, PlotManager plotManager, PlotFlagManager plotFlagManager, RegionSyncAdapter adapter) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.plotFlagManager = plotFlagManager;

        Plugin wgPlugin = plugin.getServer().getPluginManager().getPlugin("WorldGuard");
        this.worldGuardPresent = wgPlugin != null && wgPlugin.isEnabled();
        this.adapter = adapter == null ? (plot, flags) -> plugin.getLogger().fine("WorldGuard sync hook invoked for " + plot.getPlotId()) : adapter;

        if (worldGuardPresent) {
            plugin.getLogger().info("WorldGuard detected; plot flags will be mirrored through compat hook.");
        }
    }

    public boolean isWorldGuardPresent() {
        return worldGuardPresent;
    }

    public void syncAllPlots() {
        if (!worldGuardPresent) {
            return;
        }
        for (Plot plot : plotManager.getAllPlots()) {
            adapter.sync(plot, plotFlagManager.resolveFlags(plot));
        }
    }

    @Override
    public void onFlagsUpdated(Plot plot, Map<Flag, Boolean> resolvedFlags) {
        if (!worldGuardPresent || plot == null) {
            return;
        }
        adapter.sync(plot, resolvedFlags);
    }
}
