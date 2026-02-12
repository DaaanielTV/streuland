package de.streuland.quest;

import de.streuland.district.District;
import de.streuland.district.DistrictManager;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class QuestTracker implements Listener {
    private final PlotManager plotManager;
    private final DistrictManager districtManager;
    private final QuestService questService;

    public QuestTracker(PlotManager plotManager, DistrictManager districtManager, QuestService questService) {
        this.plotManager = plotManager;
        this.districtManager = districtManager;
        this.questService = questService;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Plot plot = plotManager.getPlotAt(event.getBlock().getWorld(), event.getBlock().getX(), event.getBlock().getZ());
        if (plot == null || plot.getOwner() == null || !plot.getOwner().equals(event.getPlayer().getUniqueId())) {
            return;
        }

        Material material = event.getBlock().getType();
        if (material.name().endsWith("_DOOR")) {
            questService.updateProgress(event.getPlayer(), plot.getPlotId(), QuestService.OBJECTIVE_BUILD_HOUSES, 1);
        }

        syncDistrictQuest(event.getPlayer(), plot);
    }

    public void onNeighborAdded(Player player) {
        Plot plot = plotManager.getPlotAt(player.getWorld(), player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (plot == null || plot.getOwner() == null || !plot.getOwner().equals(player.getUniqueId())) {
            return;
        }
        questService.updateProgress(player, plot.getPlotId(), QuestService.OBJECTIVE_BEFRIEND_PLOTS, 1);
    }

    public void syncDistrictQuest(Player player, Plot plot) {
        if (plot == null) {
            return;
        }
        District district = districtManager.getDistrictForPlot(plot);
        if (district == null) {
            return;
        }
        questService.syncMilestoneProgress(player, plot.getPlotId(), QuestService.OBJECTIVE_DISTRICT_LEVEL, district.getLevel().ordinal() + 1);
    }
}
