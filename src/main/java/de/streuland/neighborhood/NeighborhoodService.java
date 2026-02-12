package de.streuland.neighborhood;

import de.streuland.analytics.InMemoryPlotAnalyticsService;
import de.streuland.district.DistrictClusterService;
import de.streuland.plot.Plot;
import de.streuland.plot.PlotManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles neighborhood trust graph, resource pools and connected plot mapping.
 */
public class NeighborhoodService {
    public static final int CLUSTER_DISTANCE_BLOCKS = 100;

    private final JavaPlugin plugin;
    private final PlotManager plotManager;
    private final DistrictClusterService districtClusterService;
    private final NeighborhoodStorage storage;
    private final NeighborhoodMapRenderer mapRenderer;
    private final InMemoryPlotAnalyticsService analyticsService;

    private final Map<UUID, Set<UUID>> trustedNeighbors = new HashMap<>();
    private final Map<String, SharedResourcePool> clusterResources = new HashMap<>();

    public NeighborhoodService(JavaPlugin plugin,
                               PlotManager plotManager,
                               DistrictClusterService districtClusterService,
                               InMemoryPlotAnalyticsService analyticsService) {
        this.plugin = plugin;
        this.plotManager = plotManager;
        this.districtClusterService = districtClusterService;
        this.analyticsService = analyticsService;
        this.storage = new NeighborhoodStorage(plugin);
        this.mapRenderer = new NeighborhoodMapRenderer(plugin);

        trustedNeighbors.putAll(storage.load());
        refreshEconomicClusters();
    }

    public synchronized boolean addTrustedNeighbor(UUID owner, UUID neighbor) {
        if (owner == null || neighbor == null || owner.equals(neighbor)) {
            return false;
        }

        trustedNeighbors.computeIfAbsent(owner, ignored -> new HashSet<>()).add(neighbor);
        trustedNeighbors.computeIfAbsent(neighbor, ignored -> new HashSet<>()).add(owner);
        storage.save(trustedNeighbors);
        refreshEconomicClusters();
        return true;
    }

    public synchronized Set<UUID> getTrustedNeighbors(UUID owner) {
        return new HashSet<>(trustedNeighbors.getOrDefault(owner, Collections.emptySet()));
    }

    public synchronized List<String> formatNeighborhoodList(UUID owner) {
        Set<UUID> neighbors = trustedNeighbors.getOrDefault(owner, Collections.emptySet());
        if (neighbors.isEmpty()) {
            return Collections.singletonList("§7Keine vertrauenswürdigen Nachbarn gesetzt.");
        }

        List<String> lines = new ArrayList<>();
        lines.add("§6=== Vertrauensnachbarn ===");
        for (UUID neighborId : neighbors) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(neighborId);
            String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : neighborId.toString();
            lines.add("§e- §f" + name);
        }

        SharedResourcePool pool = getSharedPoolForPlayer(owner);
        lines.add("§6=== Shared Inventory ===");
        lines.add("§b" + pool.toString());
        return lines;
    }

    public synchronized SharedResourcePool getSharedPoolForPlayer(UUID owner) {
        for (Map.Entry<String, SharedResourcePool> entry : clusterResources.entrySet()) {
            if (entry.getKey().contains(owner.toString())) {
                return entry.getValue().copy();
            }
        }
        return new SharedResourcePool();
    }

    public synchronized void balanceClusterResources() {
        if (clusterResources.isEmpty()) {
            refreshEconomicClusters();
        }

        for (Map.Entry<String, SharedResourcePool> entry : clusterResources.entrySet()) {
            SharedResourcePool pool = entry.getValue();
            int average = (pool.getWater() + pool.getXp() + pool.getMaterials()) / 3;
            pool.setWater((pool.getWater() + average) / 2);
            pool.setXp((pool.getXp() + average) / 2);
            pool.setMaterials((pool.getMaterials() + average) / 2);
        }
        publishAnalytics();
    }

    public synchronized void refreshEconomicClusters() {
        Collection<Plot> claimedPlots = plotManager.getAllPlots().stream()
                .filter(plot -> plot.getOwner() != null)
                .collect(Collectors.toList());

        List<Set<Plot>> clusters = districtClusterService.clusterPlotsWithinDistance(claimedPlots, CLUSTER_DISTANCE_BLOCKS);
        clusterResources.clear();

        for (Set<Plot> cluster : clusters) {
            if (cluster.isEmpty()) {
                continue;
            }
            String clusterKey = buildClusterKey(cluster);
            SharedResourcePool pool = calculateResourcePool(cluster);
            clusterResources.put(clusterKey, pool);
            renderCluster(cluster);
        }
        publishAnalytics();
    }

    private SharedResourcePool calculateResourcePool(Set<Plot> cluster) {
        int water = cluster.size() * 100;
        int xp = cluster.size() * 60;
        int materials = cluster.size() * 80;
        return new SharedResourcePool(water, xp, materials);
    }

    private void renderCluster(Set<Plot> cluster) {
        Plot first = cluster.iterator().next();
        if (plotManager.getWorld() == null || first == null) {
            return;
        }
        mapRenderer.renderConnections(plotManager.getWorld(), cluster);
    }

    private void publishAnalytics() {
        for (Plot plot : plotManager.getAllPlots()) {
            if (plot.getOwner() == null) {
                continue;
            }
            SharedResourcePool pool = getSharedPoolForPlayer(plot.getOwner());
            String summary = "Nachbarn=" + getTrustedNeighbors(plot.getOwner()).size() + ", " + pool.toString();
            analyticsService.setNeighborhoodSummary(plot.getPlotId(), summary);
        }
    }

    private String buildClusterKey(Set<Plot> cluster) {
        List<String> ids = cluster.stream()
                .map(Plot::getOwner)
                .filter(Objects::nonNull)
                .map(UUID::toString)
                .sorted()
                .collect(Collectors.toList());
        return String.join("|", ids);
    }




    public synchronized Map<String, Object> buildNeighborhoodGraph(String plotId) {
        Plot source = null;
        for (Plot plot : plotManager.getAllPlots()) {
            if (plot.getPlotId().equals(plotId)) {
                source = plot;
                break;
            }
        }
        if (source == null || source.getOwner() == null) {
            return Collections.emptyMap();
        }

        Set<UUID> neighborOwners = new HashSet<>(getTrustedNeighbors(source.getOwner()));
        neighborOwners.add(source.getOwner());

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Plot plot : plotManager.getAllPlots()) {
            if (plot.getOwner() != null && neighborOwners.contains(plot.getOwner())) {
                Map<String, Object> node = new HashMap<>();
                node.put("plotId", plot.getPlotId());
                node.put("owner", plot.getOwner().toString());
                node.put("x", plot.getCenterX());
                node.put("z", plot.getCenterZ());
                nodes.add(node);
            }
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            Map<String, Object> left = nodes.get(i);
            for (int j = i + 1; j < nodes.size(); j++) {
                Map<String, Object> right = nodes.get(j);
                double dx = ((Number) left.get("x")).doubleValue() - ((Number) right.get("x")).doubleValue();
                double dz = ((Number) left.get("z")).doubleValue() - ((Number) right.get("z")).doubleValue();
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance <= CLUSTER_DISTANCE_BLOCKS * 1.5D) {
                    Map<String, Object> edge = new HashMap<>();
                    edge.put("from", left.get("plotId"));
                    edge.put("to", right.get("plotId"));
                    edge.put("resourceFlow", Math.max(1, (int) Math.round((CLUSTER_DISTANCE_BLOCKS * 2D) / Math.max(1D, distance))));
                    edges.add(edge);
                }
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("centerPlotId", plotId);
        payload.put("nodes", nodes);
        payload.put("edges", edges);
        return payload;
    }

    public String getAnalyticsSummary(String plotId) {
        return analyticsService.getNeighborhoodSummary(plotId);
    }

    public synchronized void showNeighborhoodMap(Player player) {
        if (player == null) {
            return;
        }
        Plot current = plotManager.getPlotAt(player.getLocation().getBlockX(), player.getLocation().getBlockZ());
        if (current == null || current.getOwner() == null) {
            player.sendMessage("§cDu stehst in keinem beanspruchten Plot.");
            return;
        }

        Set<UUID> neighborhood = new HashSet<>(getTrustedNeighbors(current.getOwner()));
        neighborhood.add(current.getOwner());
        List<Plot> clusterPlots = plotManager.getAllPlots().stream()
                .filter(plot -> plot.getOwner() != null && neighborhood.contains(plot.getOwner()))
                .collect(Collectors.toList());

        mapRenderer.renderConnections(plotManager.getWorld(), clusterPlots);
        player.sendMessage("§aNachbarschafts-Karte wurde visualisiert.");
    }
}
